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

    // Resource declarations (descriptors, not yet allocated)
    private final Map<String, ImageDesc> transientImageDescs;
    private final Map<String, BufferDesc> transientBufferDescs;
    private final Map<String, GraphResource> importedResources;
    private final Map<String, PersistentResourceRing<?>> persistentRings;
    private final PersistentResourceManager persistentManager;

    // Allocated transient resources (owned by this graph)
    private TransientResourceAllocator transientAllocator;
    private final Arena graphArena;
    private TimestampQueryPool timestampPool;

    private CompiledGraph compiledGraph;
    private long frameGeneration = 0;
    private FrameStats previousStats;

    private RenderGraph(Builder b) {
        this.device = b.device;
        this.framesInFlight = b.framesInFlight;
        this.nodes = List.copyOf(b.nodes);
        this.queues = Map.copyOf(b.queues);
        this.feedbackHandler = b.feedbackHandler;
        this.transientImageDescs = new LinkedHashMap<>(b.transientImageDescs);
        this.transientBufferDescs = new LinkedHashMap<>(b.transientBufferDescs);
        this.importedResources = new LinkedHashMap<>(b.importedResources);
        this.persistentRings = new LinkedHashMap<>(b.persistentRings);
        this.persistentManager = new PersistentResourceManager(
            new LinkedHashMap<>(b.persistentRings));
        this.graphArena = Arena.ofShared();

        SchedulingStrategy scheduling = b.schedulingStrategy != null ? b.schedulingStrategy : new ListSchedulingStrategy();
        BarrierStrategy barriers = b.barrierStrategy != null ? b.barrierStrategy : new SplitBarrierStrategy();
        AliasingStrategy aliasing = b.aliasingStrategy != null ? b.aliasingStrategy : new NullAliasingStrategy();

        this.compiler = new RenderGraphCompiler(scheduling, barriers, aliasing);
        this.executor = new RenderGraphExecutor(device, barriers);
        this.stats = new RenderGraphStats();

        // Allocate transient resources
        this.transientAllocator = new TransientResourceAllocator(device, graphArena);
        allocateTransientResources();

        // Initialize persistent resource ring semaphores
        if (!persistentRings.isEmpty()) {
            persistentManager.initializeSemaphores(device, graphArena);
        }

        // Compile immediately
        this.compiledGraph = compiler.compile(new ArrayList<>(nodes), queues);
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
        long gen = frameGeneration++;
        stats.beginFrame(gen);

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
     * @param frameArena arena scoped to this frame
     * @param frameIndex which frame-in-flight slot
     * @param commandBuffer the externally-managed command buffer (must already be in recording state)
     */
    public void executeInto(Arena frameArena, int frameIndex, VkCommandBuffer commandBuffer) {
        long gen = frameGeneration++;
        stats.beginFrame(gen);

        persistentManager.advanceFrame(gen);

        Map<String, Long> cpuTimes = executor.executeInto(
            compiledGraph, commandBuffer, frameArena, frameIndex, gen, previousStats);

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

        // Recompile (fast path: skip validation and versioning since topology is unchanged)
        this.compiledGraph = compiler.recompileFromLifetimes(new ArrayList<>(nodes), queues);
    }

    /**
     * Recompiles the graph (e.g. after node activation change).
     */
    public void recompile() {
        this.compiledGraph = compiler.compile(new ArrayList<>(nodes), queues);
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
        private final Map<String, PersistentResourceRing<?>> persistentRings = new LinkedHashMap<>();
        private SchedulingStrategy schedulingStrategy;
        private BarrierStrategy barrierStrategy;
        private AliasingStrategy aliasingStrategy;
        private FeedbackHandler feedbackHandler;

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

        /** Sets the scheduling strategy */
        public Builder schedulingStrategy(SchedulingStrategy strategy) { this.schedulingStrategy = strategy; return this; }

        /** Sets the barrier synthesis strategy */
        public Builder barrierStrategy(BarrierStrategy strategy) { this.barrierStrategy = strategy; return this; }

        /** Sets the memory aliasing strategy */
        public Builder aliasingStrategy(AliasingStrategy strategy) { this.aliasingStrategy = strategy; return this; }

        /** Sets the feedback handler for adaptive scheduling */
        public Builder onStats(FeedbackHandler handler) { this.feedbackHandler = handler; return this; }

        /** Compiles and returns the render graph */
        public RenderGraph build() {
            if (device == null) throw new IllegalStateException("device not set");
            if (nodes.isEmpty()) throw new IllegalStateException("no nodes added");
            if (queues.isEmpty()) throw new IllegalStateException("no queues registered");
            return new RenderGraph(this);
        }
    }
}
