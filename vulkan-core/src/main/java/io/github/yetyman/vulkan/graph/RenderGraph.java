package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.graph.barriers.BarrierStrategy;
import io.github.yetyman.vulkan.graph.barriers.SplitBarrierStrategy;
import io.github.yetyman.vulkan.graph.feedback.FeedbackHandler;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.memory.AliasingStrategy;
import io.github.yetyman.vulkan.graph.memory.NullAliasingStrategy;
import io.github.yetyman.vulkan.graph.memory.TransientResourceAllocator;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.BufferDesc;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ImageDesc;
import io.github.yetyman.vulkan.graph.resources.PersistentResourceManager;
import io.github.yetyman.vulkan.graph.resources.PersistentResourceRing;
import io.github.yetyman.vulkan.graph.resources.ResourceDescriptor;
import io.github.yetyman.vulkan.graph.resources.ImportedResource;
import io.github.yetyman.vulkan.graph.resources.TemporalResource;
import io.github.yetyman.vulkan.graph.resources.VkImageGraphResource;
import io.github.yetyman.vulkan.graph.resources.VkBufferGraphResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.SchedulingStrategy;
import io.github.yetyman.vulkan.graph.scheduling.ListSchedulingStrategy;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level render graph. Declares resources and nodes, compiles to an execution plan,
 * and executes per-frame with automatic barrier emission, resource allocation, and stats collection.
 *
 * The graph owns all transient resources and allocates/deallocates them at compile time.
 * Persistent resources are managed via PersistentResourceRing for cross-frame access.
 * Imported resources are externally owned and only tracked for synchronization.
 *
 * Multi-queue execution: the executor allocates per-queue-family command buffers, emits
 * queue ownership transfer barriers (release+acquire pairs), and submits with inter-queue
 * timeline semaphores. External semaphore waits/signals declared on nodes are wired into
 * the correct queue's submit info. Nodes within a bucket are recorded in parallel on
 * secondary command buffers when the bucket exceeds the parallel threshold.
 *
 * Feedback loop: GPU timestamps feed into AdaptiveFeedbackHandler which computes per-node
 * cost estimates. AdaptiveSchedulingStrategy consumes these to decide queue assignments.
 * Node activation changes trigger fast recompile (cull + reschedule only).
 *
 * Persistent resource rings use timeline semaphores to prevent overwriting a slot the GPU
 * is still reading. CpuWorkNode writes automatically get HOST_WRITE state so the next
 * consumer gets the correct host barrier. Bindless resources get conservative barriers.
 *
 * Debug labels (vkCmdBeginDebugUtilsLabelEXT/vkCmdEndDebugUtilsLabelEXT) are inserted
 * around each node when enabled, with colors derived from node type.
 *
 * Validation detects: orphan reads, write-after-write hazards, feedback edge cycles.
 */
public class RenderGraph implements AutoCloseable {

    private final VkDevice device;
    private final int framesInFlight;
    private final List<RenderNode> nodes;
    private final Map<QueueCapability, QueueAssignment> queues;
    private final RenderGraphCompiler compiler;
    private final RenderGraphExecutor executor;
    private final RenderGraphStats stats;
    private final FeedbackHandler feedbackHandler;
    private final DegradationStrategy degradationStrategy;

    // Resource declarations (descriptors, not yet allocated)
    private final Map<String, ImageDesc> transientImageDescs;
    private final Map<String, BufferDesc> transientBufferDescs;
    private final Map<String, GraphResource> importedResources;
    private final List<ImportedResource> importedResourceList;
    private final Map<String, PersistentResourceRing<?>> persistentRings;
    private final List<TemporalResource> temporalResources;
    private final PersistentResourceManager persistentManager;

    // Compiled graph cache by PassMask
    private final Map<PassMask, CompiledGraph> compiledCache;

    // Dynamic pass groups
    private final List<PassGroup> passGroups;

    // Manual ordering constraints
    private final List<io.github.yetyman.vulkan.graph.edges.DependencyEdge> dependencyEdges;

    // GPU-to-CPU readback handles
    private final List<ReadbackHandle> readbacks;

    // Allocated transient resources (owned by this graph)
    private TransientResourceAllocator transientAllocator;
    private final Arena graphArena;
    private TimestampQueryPool timestampPool;

    private CompiledGraph compiledGraph;
    private long frameGeneration = 0;
    private FrameStats previousStats;
    private volatile boolean executing = false;

    private RenderGraph(Builder b) {
        this.device = b.device;
        this.framesInFlight = b.framesInFlight;
        this.nodes = List.copyOf(b.nodes);
        this.queues = Map.copyOf(b.queues);
        this.feedbackHandler = b.feedbackHandler;
        this.degradationStrategy = b.degradationStrategy != null ? b.degradationStrategy : DegradationStrategy.none();
        this.transientImageDescs = new LinkedHashMap<>(b.transientImageDescs);
        this.transientBufferDescs = new LinkedHashMap<>(b.transientBufferDescs);
        this.importedResources = new LinkedHashMap<>(b.importedResources);
        this.importedResourceList = List.copyOf(b.importedResourceList);
        this.persistentRings = new LinkedHashMap<>(b.persistentRings);
        this.temporalResources = List.copyOf(b.temporalResources);
        this.persistentManager = new PersistentResourceManager(
            new LinkedHashMap<>(b.persistentRings));
        this.compiledCache = new HashMap<>();
        this.passGroups = new ArrayList<>(b.passGroups);
        this.dependencyEdges = List.copyOf(b.dependencyEdges);
        this.readbacks = List.copyOf(b.readbacks);
        this.graphArena = Arena.ofShared();

        SchedulingStrategy scheduling = b.schedulingStrategy != null ? b.schedulingStrategy : new ListSchedulingStrategy();
        BarrierStrategy barriers = b.barrierStrategy != null ? b.barrierStrategy : new SplitBarrierStrategy();
        AliasingStrategy aliasing = b.aliasingStrategy != null ? b.aliasingStrategy : new NullAliasingStrategy();

        this.compiler = new RenderGraphCompiler(scheduling, barriers, aliasing);
        this.compiler.setDependencyEdges(dependencyEdges);
        this.executor = new RenderGraphExecutor(device, barriers);
        this.executor.setTemporalResources(temporalResources);
        this.executor.setImportedResources(importedResourceList);
        this.executor.setReadbacks(readbacks);
        this.stats = new RenderGraphStats();

        // Allocate transient resources
        this.transientAllocator = new TransientResourceAllocator(device, graphArena);
        allocateTransientResources();

        // Allocate temporal resource physical slots
        allocateTemporalResources();

        // Allocate readback staging buffers (HOST_VISIBLE, persistently mapped)
        allocateReadbackBuffers();

        // Initialize persistent resource ring semaphores
        if (!persistentRings.isEmpty()) {
            persistentManager.initializeSemaphores(device, graphArena);
        }

        // Compile immediately (include pass group nodes)
        this.compiledGraph = compiler.compile(effectiveNodes(), queues, temporalResources);

        // Initialize auto-rendering configs for GraphicsPassNodes
        initializeAutoRendering();
    }

    public static Builder builder() { return new Builder(); }

    /**
     * Executes one frame of the graph using multi-queue submission. Allocates per-queue command
     * buffers internally, records all nodes, and submits to the appropriate queues with
     * inter-queue timeline semaphore synchronization.
     *
     * @param frameArena arena scoped to this frame
     * @param frameIndex which frame-in-flight slot (0..framesInFlight-1)
     * @param frameFence fence to signal when all submissions complete, or MemorySegment.NULL
     */
    public void execute(Arena frameArena, int frameIndex, MemorySegment frameFence) {
        if (executing) throw new IllegalStateException("RenderGraph is already executing (concurrent access)");
        executing = true;
        try {
            executeImpl(frameArena, frameIndex, frameFence);
        } finally {
            executing = false;
        }
    }

    private void executeImpl(Arena frameArena, int frameIndex, MemorySegment frameFence) {
        long gen = frameGeneration++;
        stats.beginFrame(gen);

        // Advance staleness for all temporal resources
        for (TemporalResource tr : temporalResources) {
            tr.advanceSubmission();
        }

        // Advance persistent resource rings to the current frame
        persistentManager.advanceFrame(gen);

        Map<String, Long> cpuTimes = executor.execute(
            compiledGraph, frameArena, frameIndex, gen, previousStats);

        // Submit all per-queue command buffers with inter-queue semaphore dependencies
        executor.submit(frameArena, frameFence);

        stats.recordCpuTimes(cpuTimes);
    }

    /**
     * Executes one frame of the graph into a single externally-provided command buffer.
     * This is the legacy single-queue path for callers that manage their own command buffers
     * and submission. No multi-queue support -- all nodes record into the provided buffer.
     *
     * Automatically selects the correct compiled graph variant based on current pass activation
     * state, using the PassMask cache for repeated patterns.
     *
     * @param frameArena arena scoped to this frame
     * @param frameIndex which frame-in-flight slot
     * @param commandBuffer the externally-managed command buffer (must already be in recording state)
     */
    public void executeInto(Arena frameArena, int frameIndex, VkCommandBuffer commandBuffer) {
        if (executing) throw new IllegalStateException("RenderGraph is already executing (concurrent access)");
        executing = true;
        try {
            executeIntoImpl(frameArena, frameIndex, commandBuffer);
        } finally {
            executing = false;
        }
    }

    private void executeIntoImpl(Arena frameArena, int frameIndex, VkCommandBuffer commandBuffer) {
        long gen = frameGeneration++;
        stats.beginFrame(gen);

        // Advance staleness for all temporal resources (reset by onWriteExecuted if written)
        for (TemporalResource tr : temporalResources) {
            tr.advanceSubmission();
        }

        // Invalidate cache if any pass group changed count
        for (PassGroup group : passGroups) {
            if (group.countChanged()) {
                compiledCache.clear();
                break;
            }
        }

        // Select compiled graph for current activation state (cached)
        List<RenderNode> degraded = degradationStrategy.apply(effectiveNodes(), previousStats);
        PassMask mask = PassMask.evaluate(degraded);
        CompiledGraph active = compiledCache.computeIfAbsent(mask,
            m -> compiler.compile(degraded, queues, temporalResources));

        persistentManager.advanceFrame(gen);

        Map<String, Long> cpuTimes = executor.executeInto(
            active, commandBuffer, frameArena, frameIndex, gen, previousStats);

        stats.recordCpuTimes(cpuTimes);
    }

    /**
     * Provides GPU timestamp data for the most recently executed frame.
     * Call this after the frame's fence has signaled and timestamps have been read back.
     *
     * @param gpuTimesNanos per-node GPU time in nanoseconds (node name -> nanos)
     */
    public void provideGpuTimestamps(Map<String, Long> gpuTimesNanos) {
        for (var entry : gpuTimesNanos.entrySet()) {
            stats.recordGpuTime(entry.getKey(), entry.getValue());
        }
        FrameStats frameStats = stats.build();
        onFrameComplete(frameStats);
    }

    /**
     * Reads back GPU timestamps from the query pool and feeds them into the stats system.
     * Call this after the frame's fence has signaled. Requires timestamps to be enabled.
     *
     * @param readbackArena arena for the readback buffer allocation
     */
    public void readbackTimestamps(Arena readbackArena) {
        if (timestampPool == null) return;
        Map<String, Long> gpuTimes = timestampPool.readback(readbackArena);
        if (!gpuTimes.isEmpty()) {
            provideGpuTimestamps(gpuTimes);
        }
    }

    /**
     * Enables GPU timestamp collection for per-node GPU timing.
     *
     * @param timestampPeriod nanoseconds per timestamp tick (from VkPhysicalDeviceLimits.timestampPeriod)
     * @param maxNodes maximum number of nodes to time
     */
    public void enableTimestamps(float timestampPeriod, int maxNodes) {
        this.timestampPool = TimestampQueryPool.create(device, maxNodes, timestampPeriod, graphArena);
        executor.enableTimestamps(timestampPool);
    }

    /**
     * Enables GPU timestamp collection, auto-sizing to the current node count.
     *
     * @param timestampPeriod nanoseconds per timestamp tick
     */
    public void enableTimestamps(float timestampPeriod) {
        enableTimestamps(timestampPeriod, nodes.size() + 8); // +8 headroom for dynamic nodes
    }

    /** @return the timestamp query pool, or null if timestamps are not enabled */
    public TimestampQueryPool timestampPool() { return timestampPool; }

    /**
     * Resizes all transient resources to new dimensions and recompiles the graph.
     * Persistent and imported resources are not affected (they must be resized externally
     * and re-imported if needed).
     *
     * @param newWidth new width for dimension-dependent resources
     * @param newHeight new height for dimension-dependent resources
     */
    public void resize(int newWidth, int newHeight) {
        // Update descriptors with new dimensions
        Map<String, ImageDesc> resizedImages = new LinkedHashMap<>();
        for (Map.Entry<String, ImageDesc> entry : transientImageDescs.entrySet()) {
            resizedImages.put(entry.getKey(), entry.getValue().withDimensions(newWidth, newHeight));
        }
        transientImageDescs.clear();
        transientImageDescs.putAll(resizedImages);

        // Reallocate physical resources
        transientAllocator.reallocate(transientImageDescs, transientBufferDescs);

        // Apply temporal resize strategies
        for (TemporalResource tr : temporalResources) {
            GraphResource[] oldSlots = tr.physicalSlots();
            tr.resizeStrategy().onResize(tr, oldSlots, oldSlots, null, graphArena);
        }

        // Patch auto-rendering configs with new dimensions (zero allocation)
        for (RenderNode node : nodes) {
            if (node instanceof io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode gpn && gpn.autoRendering()) {
                gpn.renderingBuilder().patchRenderArea(0, 0, newWidth, newHeight);
            }
        }

        // Recompile (fast path: skip validation and versioning since topology is unchanged)
        this.compiledGraph = compiler.recompileFromLifetimes(new ArrayList<>(nodes), queues);
    }

    /**
     * Recompiles the graph (e.g. after node activation change).
     */
    public void recompile() {
        this.compiledGraph = compiler.compile(new ArrayList<>(nodes), queues, temporalResources);
    }

    /**
     * Compiles the graph for the current pass mask, using the cache if available.
     * This is the preferred compilation path for multi-rate rendering where pass masks
     * alternate between a small set of patterns.
     *
     * @return the compiled graph for the current activation state
     */
    public CompiledGraph compileForCurrentMask() {
        PassMask mask = PassMask.evaluate(nodes);
        return compiledCache.computeIfAbsent(mask, m -> compiler.compile(new ArrayList<>(nodes), queues, temporalResources));
    }

    /** Invalidates the compiled graph cache (call after structural changes) */
    public void invalidateCache() {
        compiledCache.clear();
    }

    /**
     * Creates a dynamic pass group. Passes added to the group are included in compilation.
     * The group can be cleared and re-populated each frame.
     *
     * @param name group name
     * @param maxCount maximum expected pass count
     * @return the pass group
     */
    public PassGroup addPassGroup(String name, int maxCount) {
        PassGroup group = new PassGroup(name, maxCount);
        passGroups.add(group);
        return group;
    }

    /** @return all effective nodes (static nodes + dynamic group members) */
    private List<RenderNode> effectiveNodes() {
        if (passGroups.isEmpty()) return new ArrayList<>(nodes);
        List<RenderNode> all = new ArrayList<>(nodes);
        for (PassGroup group : passGroups) {
            all.addAll(group.nodes());
        }
        return all;
    }

    /**
     * Instantiates a subgraph template and returns a builder for connecting inputs/params.
     *
     * @param template the template to instantiate
     * @return an instantiation builder
     */
    public SubgraphTemplate.InstantiationBuilder instantiate(SubgraphTemplate template) {
        return new SubgraphTemplate.InstantiationBuilder(template);
    }

    /** @return the declared temporal resources */
    public List<TemporalResource> temporalResources() { return temporalResources; }

    /** @return an imported resource by name, or null if not found */
    public ImportedResource importedImage(String name) {
        for (ImportedResource ir : importedResourceList) {
            if (ir.name().equals(name)) return ir;
        }
        return null;
    }

    /**
     * Returns the graph resource for a given name. Searches transient, imported, and persistent resources.
     *
     * @param name resource name
     * @return the graph resource, or null if not found
     */
    public GraphResource resource(String name) {
        GraphResource res = transientAllocator.get(name);
        if (res != null) return res;
        res = importedResources.get(name);
        if (res != null) return res;
        return persistentManager.resolveCurrentResource(name);
    }

    /**
     * Returns the persistent resource manager for advanced ring access.
     */
    public PersistentResourceManager persistentResources() {
        return persistentManager;
    }

    /** @return the compiled execution plan */
    public CompiledGraph compiledGraph() { return compiledGraph; }

    /** @return current frame generation counter */
    public long frameGeneration() { return frameGeneration; }

    /** @return frames in flight count */
    public int framesInFlight() { return framesInFlight; }

    /** @return the most recent frame stats, or null if no frame has completed */
    public FrameStats previousStats() { return previousStats; }

    /** @return the device this graph was built for */
    public VkDevice device() { return device; }

    /** Prints the compiled DAG to stdout as ASCII art */
    public void printGraph() { RenderGraphVisualizer.print(compiledGraph); }

    /** @return ASCII visualization of the compiled DAG */
    public String visualizeGraph() { return RenderGraphVisualizer.visualize(compiledGraph); }

    @Override
    public void close() {
        if (timestampPool != null) {
            timestampPool.close();
        }
        // Close temporal descriptor bindings before transient allocator destroys the buffers
        for (TemporalResource tr : temporalResources) {
            if (tr.readDescriptorBinding() != null) tr.readDescriptorBinding().close();
            if (tr.writeDescriptorBinding() != null && tr.writeDescriptorBinding() != tr.readDescriptorBinding()) {
                tr.writeDescriptorBinding().close();
            }
            if (tr.pairedDescriptorBinding() != null) tr.pairedDescriptorBinding().close();
        }
        if (transientAllocator != null) {
            transientAllocator.close();
        }
        persistentManager.closeSemaphores();
        executor.close();
        graphArena.close();
    }

    private void allocateTransientResources() {
        for (Map.Entry<String, ImageDesc> entry : transientImageDescs.entrySet()) {
            transientAllocator.allocateImage(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, BufferDesc> entry : transientBufferDescs.entrySet()) {
            transientAllocator.allocateBuffer(entry.getKey(), entry.getValue());
        }
    }

    private void allocateTemporalResources() {
        for (TemporalResource tr : temporalResources) {
            if (tr.physicalSlots() != null) continue; // already allocated externally

            ResourceDescriptor desc = tr.descriptor();
            GraphResource[] slots = new GraphResource[tr.bufferCount()];

            for (int i = 0; i < tr.bufferCount(); i++) {
                String slotName = tr.name() + "_slot" + i;
                if (desc.kind() == ResourceDescriptor.ResourceKind.BUFFER) {
                    VkBufferGraphResource res = transientAllocator.allocateBuffer(slotName,
                        BufferDesc.custom(desc.bufferSize(), desc.usageFlags(), desc.memoryProperties()));
                    slots[i] = res;
                } else {
                    VkImageGraphResource res = transientAllocator.allocateImage(slotName,
                        ImageDesc.custom(desc.width(), desc.height(), desc.depth(),
                            desc.format(), desc.usageFlags(), 1, 1, 1));
                    slots[i] = res;
                }
            }
            tr.setPhysicalSlots(slots);
        }

        // Create graph-managed descriptor bindings for temporal resources that request them
        for (TemporalResource tr : temporalResources) {
            if (tr.physicalSlots() == null) continue;

            if (tr.hasPairedDescriptorBinding()) {
                // Paired: read+write in same set (for compute shaders)
                var binding = io.github.yetyman.vulkan.graph.resources.TemporalDescriptorBinding
                    .createPairedForBuffer(device, tr.pairedLayout(), tr.descriptorBinding(),
                        tr.pairedWriteBinding(), tr, graphArena);
                if (binding != null) {
                    tr.setPairedDescriptorBinding(binding);
                }
            }
            if (tr.hasDescriptorBinding()) {
                // Single: one buffer per set (for fragment shaders that only read)
                var binding = io.github.yetyman.vulkan.graph.resources.TemporalDescriptorBinding
                    .createForBuffer(device, tr.descriptorLayout(), tr.descriptorBinding(), tr, graphArena);
                if (binding != null) {
                    tr.setReadDescriptorBinding(binding);
                    tr.setWriteDescriptorBinding(binding);
                }
            }
        }
    }

    private void initializeAutoRendering() {
        for (RenderNode node : nodes) {
            if (node instanceof io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode gpn && gpn.autoRendering()) {
                gpn.renderingBuilder().buildAndCache(graphArena);
            }
        }
    }

    private void allocateReadbackBuffers() {
        if (readbacks.isEmpty()) return;
        for (ReadbackHandle rb : readbacks) {
            if (rb.stagingBuffer() != null) continue; // already allocated
            // Allocate a HOST_VISIBLE buffer for staging
            var staging = io.github.yetyman.vulkan.VkBuffer.builder()
                .device(device)
                .size(rb.size())
                .transferDst()
                .hostVisible()
                .build(graphArena);
            try (Arena tmp = Arena.ofConfined()) {
                MemorySegment mapped = staging.map(tmp);
                // Persistently mapped - reinterpret with graph arena lifetime
                MemorySegment persistent = staging.map(graphArena);
                rb.setStagingBuffer(staging.handle(), persistent);
            }
        }
    }

    private void onFrameComplete(FrameStats frameStats) {
        this.previousStats = frameStats;
        if (feedbackHandler != null) {
            feedbackHandler.onStats(frameStats);
        }

        // Deliver stats to nodes and detect activation changes
        boolean activationChanged = false;
        for (RenderNode node : nodes) {
            var nodeStats = frameStats.forNode(node.name());
            if (nodeStats != null) {
                boolean waActive = node.isActive();
                node.onStats(nodeStats);
                if (waActive != node.isActive()) {
                    activationChanged = true;
                }
            }
        }

        // Fast recompile if any node toggled active state
        if (activationChanged) {
            this.compiledGraph = compiler.recompileFromCull(
                nodes, queues,
                compiledGraph.lifetimes(), compiledGraph.aliasingGroups());
        }
    }

    // -- Builder --

    public static class Builder {
        private VkDevice device;
        private int framesInFlight = 2;
        private final List<RenderNode> nodes = new ArrayList<>();
        private final Map<QueueCapability, QueueAssignment> queues = new HashMap<>();
        private final Map<String, ImageDesc> transientImageDescs = new LinkedHashMap<>();
        private final Map<String, BufferDesc> transientBufferDescs = new LinkedHashMap<>();
        private final Map<String, GraphResource> importedResources = new LinkedHashMap<>();
        private final List<ImportedResource> importedResourceList = new ArrayList<>();
        private final Map<String, PersistentResourceRing<?>> persistentRings = new LinkedHashMap<>();
        private final List<TemporalResource> temporalResources = new ArrayList<>();
        private final List<PassGroup> passGroups = new ArrayList<>();
        private final List<io.github.yetyman.vulkan.graph.edges.DependencyEdge> dependencyEdges = new ArrayList<>();
        private final List<ReadbackHandle> readbacks = new ArrayList<>();
        private SchedulingStrategy schedulingStrategy;
        private BarrierStrategy barrierStrategy;
        private AliasingStrategy aliasingStrategy;
        private FeedbackHandler feedbackHandler;
        private DegradationStrategy degradationStrategy;

        private Builder() {}

        /** Sets the logical device */
        public Builder device(VkDevice device) { this.device = device; return this; }

        /** Sets frames in flight count */
        public Builder framesInFlight(int n) { this.framesInFlight = n; return this; }

        /** Adds a render node to the graph */
        public Builder node(RenderNode node) { this.nodes.add(node); return this; }

        /** Registers a queue with its capability */
        public Builder queue(QueueCapability capability, MemorySegment queueHandle, int familyIndex) {
            this.queues.put(capability, new QueueAssignment(queueHandle, familyIndex, capability));
            return this;
        }

        /**
         * Declares a transient image resource. The graph allocates and owns the physical memory.
         * Transient resources are eligible for memory aliasing with non-overlapping lifetimes.
         *
         * @param name unique resource name (used in ResourceEdge declarations)
         * @param desc image descriptor specifying format, dimensions, usage
         */
        public Builder transientImage(String name, ImageDesc desc) {
            this.transientImageDescs.put(name, desc);
            return this;
        }

        /**
         * Declares a transient buffer resource. The graph allocates and owns the physical memory.
         *
         * @param name unique resource name
         * @param desc buffer descriptor specifying size and usage
         */
        public Builder transientBuffer(String name, BufferDesc desc) {
            this.transientBufferDescs.put(name, desc);
            return this;
        }

        /**
         * Imports an externally-owned resource into the graph for synchronization tracking.
         * The graph does not allocate or free this resource.
         *
         * @param name unique resource name
         * @param resource the externally-owned graph resource
         */
        public Builder imported(String name, GraphResource resource) {
            this.importedResources.put(name, resource);
            return this;
        }

        /**
         * Imports an externally-owned image resource with declared initial and final layouts.
         * The graph inserts barriers at first use (initial -> required) and last use (current -> final).
         * Use {@link ImportedResource#rebind(java.lang.foreign.MemorySegment)} each frame for swapchain images.
         *
         * @param resource the imported resource declaration
         */
        public Builder importedImage(ImportedResource resource) {
            this.importedResources.put(resource.name(), resource);
            this.importedResourceList.add(resource);
            return this;
        }

        /**
         * Declares a persistent resource with a ring buffer for cross-frame access.
         * The caller is responsible for creating the ring with the correct number of copies.
         *
         * @param name unique resource name
         * @param ring the persistent resource ring
         */
        public Builder persistent(String name, PersistentResourceRing<?> ring) {
            this.persistentRings.put(name, ring);
            return this;
        }

        /**
         * Declares a temporal resource that participates in cross-frame cycles.
         * The graph manages physical slot allocation and flip logic automatically.
         *
         * @param resource the temporal resource declaration
         */
        public Builder temporal(TemporalResource resource) {
            this.temporalResources.add(resource);
            return this;
        }

        /** Sets the scheduling strategy */
        public Builder schedulingStrategy(SchedulingStrategy strategy) { this.schedulingStrategy = strategy; return this; }

        /** Sets the barrier synthesis strategy */
        public Builder barrierStrategy(BarrierStrategy strategy) { this.barrierStrategy = strategy; return this; }

        /** Sets the memory aliasing strategy */
        public Builder aliasingStrategy(AliasingStrategy strategy) { this.aliasingStrategy = strategy; return this; }

        /** Sets the feedback handler for adaptive scheduling */
        public Builder onStats(FeedbackHandler handler) { this.feedbackHandler = handler; return this; }

        /** Sets the degradation strategy for graceful pass dropping when over budget */
        public Builder degradationStrategy(DegradationStrategy strategy) { this.degradationStrategy = strategy; return this; }

        /** Adds a manual ordering constraint between two nodes */
        public Builder dependencyEdge(io.github.yetyman.vulkan.graph.edges.DependencyEdge edge) {
            this.dependencyEdges.add(edge);
            return this;
        }

        /** Adds a GPU-to-CPU readback handle. The graph allocates staging and records copies. */
        public Builder readback(ReadbackHandle handle) {
            this.readbacks.add(handle);
            return this;
        }

        /** Compiles and returns the render graph */
        public RenderGraph build() {
            if (device == null) throw new IllegalStateException("device not set");
            if (nodes.isEmpty()) throw new IllegalStateException("no nodes added");
            if (queues.isEmpty()) throw new IllegalStateException("no queues registered");
            return new RenderGraph(this);
        }
    }
}
