package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkTimelineSemaphore;
import io.github.yetyman.vulkan.enums.VkCommandBufferUsageFlagBits;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.generated.VulkanFFM;
import io.github.yetyman.vulkan.graph.barriers.BarrierBatch;
import io.github.yetyman.vulkan.graph.barriers.BarrierStrategy;
import io.github.yetyman.vulkan.graph.barriers.ConservativeBarrierStrategy;
import io.github.yetyman.vulkan.graph.barriers.OwnershipTransfer;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.SemaphoreEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.nodes.ExecutionContext;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.NodeType;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphImageResource;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;
import io.github.yetyman.vulkan.graph.scheduling.FrameCommandBufferPool;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueSubmission;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Executes a compiled render graph for one frame with full multi-queue support.
 *
 * Architecture:
 * - Each distinct queue family gets its own primary command buffer per frame
 * - Nodes within a bucket that share no dependencies are recorded in parallel on secondary
 *   command buffers, then merged via vkCmdExecuteCommands into the primary
 * - Queue ownership transfers emit release barriers on the source queue's command buffer
 *   and acquire barriers on the destination queue's command buffer
 * - Inter-queue ordering is enforced via timeline semaphores: each queue signals a timeline
 *   semaphore after its submission, and dependent queues wait on it
 * - External semaphore waits/signals declared on nodes are wired into the correct queue's
 *   submit info
 * - GPU timestamps are inserted around each node for profiling
 *
 * The executor owns:
 * - Per-frame command buffer pools (one per queue family)
 * - Inter-queue timeline semaphores (one per queue family pair)
 * - A thread pool for parallel secondary command buffer recording
 */
public class RenderGraphExecutor implements AutoCloseable {

    private static final int VK_ACCESS_HOST_WRITE_BIT = 0x00004000;
    private static final int VK_PIPELINE_STAGE_HOST_BIT = 0x00004000;
    private static final int BINDLESS_SHADER_READ = 0x00000020;
    private static final int BINDLESS_ALL_COMMANDS =
        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT.value();
    private static final ConservativeBarrierStrategy CONSERVATIVE = new ConservativeBarrierStrategy();

    private final BarrierStrategy barrierStrategy;
    private final VkDevice device;
    private final FrameCommandBufferPool commandBufferPool;
    private final ExecutorService recordingThreadPool;
    private final int parallelThreshold;

    // Per-queue-family state for the current frame
    private final Map<Integer, QueueSubmission> submissions = new LinkedHashMap<>();
    private final Map<Integer, VkCommandBuffer> primaryCommandBuffers = new HashMap<>();

    // Inter-queue timeline semaphores: key = "srcFamily:dstFamily"
    private final Map<String, VkTimelineSemaphore> interQueueSemaphores = new HashMap<>();
    private final Map<String, Long> interQueueCounters = new HashMap<>();
    private final Arena semaphoreArena;

    // Deferred acquire barriers: key = dstQueueFamily, accumulated during recording
    private final Map<Integer, BarrierBatch> deferredAcquires = new HashMap<>();

    // Tracks which queue family pairs have cross-queue resource dependencies this frame
    private final java.util.HashSet<Long> crossQueueDependencies = new java.util.HashSet<>();

    // Reusable barrier batch (sequential path only)
    private final BarrierBatch barrierBatch = new BarrierBatch();

    // Timing
    private TimestampQueryPool timestampPool;
    private boolean timestampsEnabled = false;
    private boolean debugLabelsEnabled = false;
    private HashMap<String, Long> cpuTimes;

    // Mutable execution context reused across nodes (sequential path)
    private final MutableExecutionContext ctx = new MutableExecutionContext();

    // Temporal resources for slot resolution and auto-advance
    private Map<String, io.github.yetyman.vulkan.graph.resources.TemporalResource> temporalResources = Map.of();

    // Imported resources for final-layout barrier emission
    private List<io.github.yetyman.vulkan.graph.resources.ImportedResource> importedResources = List.of();

    // GPU-to-CPU readback handles
    private List<ReadbackHandle> readbacks = List.of();

    /**
     * @param device the logical device
     * @param barrierStrategy strategy for barrier synthesis
     * @param parallelThreshold minimum number of nodes in a bucket to trigger parallel recording.
     *                          Set to Integer.MAX_VALUE to disable parallel recording.
     */
    public RenderGraphExecutor(VkDevice device, BarrierStrategy barrierStrategy, int parallelThreshold) {
        this.device = device;
        this.barrierStrategy = barrierStrategy;
        this.parallelThreshold = parallelThreshold;
        this.commandBufferPool = FrameCommandBufferPool.create(device);
        this.semaphoreArena = Arena.ofShared();
        this.recordingThreadPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Convenience constructor with default parallel threshold of 4 nodes. */
    public RenderGraphExecutor(VkDevice device, BarrierStrategy barrierStrategy) {
        this(device, barrierStrategy, 4);
    }

    /** Enables GPU timestamp collection. */
    public void enableTimestamps(TimestampQueryPool pool) {
        this.timestampPool = pool;
        this.timestampsEnabled = true;
    }

    /** Disables GPU timestamp collection */
    public void disableTimestamps() {
        this.timestampsEnabled = false;
    }

    /** @return the timestamp query pool, or null if not enabled */
    public TimestampQueryPool timestampPool() { return timestampPool; }

    /** @return true if GPU timestamps are being collected */
    public boolean timestampsEnabled() { return timestampsEnabled; }

    /** Enables debug label insertion around each node (visible in RenderDoc/validation layers) */
    public void enableDebugLabels() { this.debugLabelsEnabled = true; }

    /** Disables debug label insertion */
    public void disableDebugLabels() { this.debugLabelsEnabled = false; }

    /** @return true if debug labels are being inserted */
    public boolean debugLabelsEnabled() { return debugLabelsEnabled; }

    /**
     * Sets the temporal resources for slot resolution during execution.
     * The executor uses these to populate ExecutionContext.temporalRead/Write methods
     * and to auto-advance temporal resources after nodes with writesTemporalCurrent edges.
     */
    public void setTemporalResources(List<io.github.yetyman.vulkan.graph.resources.TemporalResource> resources) {
        if (resources == null || resources.isEmpty()) {
            this.temporalResources = Map.of();
        } else {
            Map<String, io.github.yetyman.vulkan.graph.resources.TemporalResource> map = new HashMap<>();
            for (var tr : resources) {
                map.put(tr.name(), tr);
            }
            this.temporalResources = map;
        }
    }

    /**
     * Sets the imported resources for final-layout barrier emission.
     * After all nodes execute, the executor transitions imported resources to their declared finalLayout.
     */
    public void setImportedResources(List<io.github.yetyman.vulkan.graph.resources.ImportedResource> resources) {
        this.importedResources = resources != null ? resources : List.of();
    }

    /** Sets the readback handles for GPU-to-CPU copy recording */
    public void setReadbacks(List<ReadbackHandle> handles) {
        this.readbacks = handles != null ? handles : List.of();
    }

    /**
     * Executes all buckets of a compiled graph. Records commands into per-queue command buffers,
     * emits barriers (including cross-queue ownership transfers), and builds submission units.
     *
     * After this method returns, call {@link #submit(Arena, MemorySegment)} to submit all queue
     * submissions with the correct inter-queue semaphore dependencies.
     *
     * @return per-node CPU recording times in nanoseconds
     */
    public Map<String, Long> execute(CompiledGraph compiled, Arena frameArena,
                                     int frameIndex, long frameGeneration,
                                     FrameStats previousStats) {
        commandBufferPool.resetAll();
        submissions.clear();
        primaryCommandBuffers.clear();
        deferredAcquires.clear();
        crossQueueDependencies.clear();

        int nodeCount = compiled.activeNodes().size();
        if (cpuTimes == null || cpuTimes.size() < nodeCount) {
            cpuTimes = HashMap.newHashMap(nodeCount);
        } else {
            cpuTimes.clear();
        }

        ctx.frameArena = frameArena;
        ctx.frameIndex = frameIndex;
        ctx.frameGeneration = frameGeneration;
        ctx.previousStats = previousStats;
        ctx.temporalResources = temporalResources;
        ctx.importedResources = importedResources;

        allocatePrimaryCommandBuffers(compiled);

        for (VkCommandBuffer cmd : primaryCommandBuffers.values()) {
            VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(frameArena);
        }

        if (timestampsEnabled && timestampPool != null) {
            VkCommandBuffer firstCmd = primaryCommandBuffers.values().iterator().next();
            timestampPool.reset(firstCmd.handle());
        }

        for (int bucketIdx = 0; bucketIdx < compiled.executionBuckets().size(); bucketIdx++) {
            ExecutionBucket bucket = compiled.executionBuckets().get(bucketIdx);
            int queueFamily = bucket.queue().queueFamilyIndex();
            VkCommandBuffer primaryCmd = primaryCommandBuffers.get(queueFamily);

            emitDeferredAcquires(queueFamily, primaryCmd, frameArena);

            ctx.queue = bucket.queue();
            ctx.commandBuffer = primaryCmd;

            if (bucket.nodes().size() >= parallelThreshold) {
                recordBucketParallel(bucket, primaryCmd, frameArena);
            } else {
                recordBucketSequential(bucket, primaryCmd, frameArena);
            }
        }

        for (VkCommandBuffer cmd : primaryCommandBuffers.values()) {
            cmd.end();
        }

        wireExternalSemaphores(compiled);

        return cpuTimes;
    }

    /**
     * Submits all per-queue command buffers with inter-queue timeline semaphore dependencies.
     * Must be called after {@link #execute}.
     *
     * Only inserts semaphores between queue families that have actual resource dependencies
     * (determined by the compiled graph's bucket ordering and cross-queue resource edges).
     *
     * @param submitArena arena for submit struct allocation
     * @param frameFence fence to signal when the last submission completes, or NULL
     */
    public void submit(Arena submitArena, MemorySegment frameFence) {
        List<QueueSubmission> orderedSubmissions = new ArrayList<>(submissions.values());

        // Only insert semaphores between queues that have cross-queue dependencies.
        // The executor already recorded ownership transfer barriers between dependent queues.
        // We use the deferredAcquires map as evidence of cross-queue dependencies:
        // if queue B had deferred acquires from queue A, then A must signal before B waits.
        for (int i = 0; i < orderedSubmissions.size(); i++) {
            QueueSubmission sub = orderedSubmissions.get(i);
            if (!sub.hasWork()) continue;

            // Check if any later submission on a different queue depends on this one
            for (int j = i + 1; j < orderedSubmissions.size(); j++) {
                QueueSubmission dependent = orderedSubmissions.get(j);
                if (!dependent.hasWork()) continue;
                if (dependent.queueFamilyIndex() == sub.queueFamilyIndex()) continue;

                // Only add semaphore if there's a known dependency (cross-queue resource edge)
                if (!hasCrossQueueDependency(sub.queueFamilyIndex(), dependent.queueFamilyIndex())) {
                    continue;
                }

                String key = sub.queueFamilyIndex() + ":" + dependent.queueFamilyIndex();
                VkTimelineSemaphore sem = getOrCreateInterQueueSemaphore(key);
                long value = interQueueCounters.merge(key, 1L, Long::sum);

                sub.signalTimeline(sem, value);
                dependent.waitTimeline(sem, value,
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value());
            }

            MemorySegment fence = (i == orderedSubmissions.size() - 1) ? frameFence : MemorySegment.NULL;
            sub.submit(fence, submitArena);
        }
    }

    /**
     * Returns true if there is a cross-queue resource dependency from srcFamily to dstFamily
     * in the current frame. Determined by whether ownership transfers were recorded between them.
     */
    private boolean hasCrossQueueDependency(int srcFamily, int dstFamily) {
        // Evidence: the barrier emission phase recorded ownership transfers from src to dst,
        // which means deferred acquires were queued for the dst queue from the src queue.
        // We track this via a set populated during barrier emission.
        return crossQueueDependencies.contains(crossQueueKey(srcFamily, dstFamily));
    }

    private static long crossQueueKey(int srcFamily, int dstFamily) {
        return ((long) srcFamily << 32) | (dstFamily & 0xFFFFFFFFL);
    }

    /**
     * Simplified execute+submit for single-queue graphs or when the caller manages submission.
     * Records into a single externally-provided command buffer (legacy compatibility path).
     */
    public Map<String, Long> executeInto(CompiledGraph compiled, VkCommandBuffer commandBuffer,
                                         Arena frameArena, int frameIndex, long frameGeneration,
                                         FrameStats previousStats) {
        int nodeCount = compiled.activeNodes().size();
        if (cpuTimes == null || cpuTimes.size() < nodeCount) {
            cpuTimes = HashMap.newHashMap(nodeCount);
        } else {
            cpuTimes.clear();
        }

        ctx.commandBuffer = commandBuffer;
        ctx.frameArena = frameArena;
        ctx.frameIndex = frameIndex;
        ctx.frameGeneration = frameGeneration;
        ctx.previousStats = previousStats;
        ctx.temporalResources = temporalResources;
        ctx.importedResources = importedResources;

        if (timestampsEnabled && timestampPool != null) {
            timestampPool.reset(commandBuffer.handle());
        }

        List<ExecutionBucket> buckets = compiled.executionBuckets();
        for (int b = 0, bucketCount = buckets.size(); b < bucketCount; b++) {
            ExecutionBucket bucket = buckets.get(b);
            int queueFamily = bucket.queue().queueFamilyIndex();
            ctx.queue = bucket.queue();

            List<RenderNode> nodes = bucket.nodes();
            for (int n = 0, nCount = nodes.size(); n < nCount; n++) {
                RenderNode node = nodes.get(n);

                barrierBatch.clear();
                emitBarriersForNode(node, barrierBatch, queueFamily, frameArena);
                if (!barrierBatch.hasNoSameQueueBarriers()) {
                    executeBarrierBatch(commandBuffer, barrierBatch, frameArena);
                }
                if (barrierBatch.hasOwnershipTransfers()) {
                    for (int i = 0; i < barrierBatch.transferCount(); i++) {
                        OwnershipTransfer transfer = barrierBatch.getTransfer(i);
                        transfer.releaseBarrier().execute(commandBuffer.handle(),
                            transfer.releaseSrcStage(), transfer.releaseDstStage());
                        transfer.acquireBarrier().execute(commandBuffer.handle(),
                            transfer.acquireSrcStage(), transfer.acquireDstStage());
                    }
                }

                if (timestampsEnabled && timestampPool != null) {
                    timestampPool.writeBeginTimestamp(commandBuffer.handle(), node.name(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value());
                }

                if (debugLabelsEnabled) {
                    DebugLabels.beginForNode(commandBuffer.handle(), node.name(), node.type(), frameArena);
                }

                long startNanos = System.nanoTime();

                // Populate optional availability for this node
                List<io.github.yetyman.vulkan.graph.edges.OptionalEdge> optionals = node.optionalReads();
                if (!optionals.isEmpty()) {
                    if (ctx.availableOptionals == null) ctx.availableOptionals = new java.util.HashSet<>();
                    else ctx.availableOptionals.clear();
                    for (var opt : optionals) {
                        if (opt.resource().lastAccessMask() != 0 || opt.resource().lastStageMask() != 0) {
                            ctx.availableOptionals.add(opt.resource().name());
                        }
                    }
                }

                var rendering = getAutoRendering(node);
                if (rendering != null) {
                    rendering.beginCached(commandBuffer.handle());
                }

                // Set up ping-pong slots for iterative nodes
                if (node instanceof io.github.yetyman.vulkan.graph.nodes.IterativePassNode ipn
                        && !ipn.pingPongSlots().isEmpty()) {
                    ctx.pingPongSlots = ipn.pingPongSlots();
                }

                node.execute(ctx);

                // Clear ping-pong slots after iterative node
                if (node instanceof io.github.yetyman.vulkan.graph.nodes.IterativePassNode) {
                    ctx.pingPongSlots = null;
                }

                if (rendering != null) {
                    io.github.yetyman.vulkan.VkRendering.end(device, commandBuffer.handle());
                }

                cpuTimes.put(node.name(), System.nanoTime() - startNanos);

                if (debugLabelsEnabled) {
                    DebugLabels.end(commandBuffer.handle());
                }

                if (timestampsEnabled && timestampPool != null) {
                    timestampPool.writeEndTimestamp(commandBuffer.handle(), node.name(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());
                }

                updateResourceState(node, queueFamily);
            }
        }

        // Record readback copies (after all nodes, before final-layout barriers)
        emitReadbackCopies(commandBuffer, frameArena, frameGeneration);

        // Emit final-layout transitions for imported resources
        emitFinalLayoutBarriers(commandBuffer, frameArena);

        return cpuTimes;
    }

    @Override
    public void close() {
        recordingThreadPool.shutdown();
        commandBufferPool.close();
        for (VkTimelineSemaphore sem : interQueueSemaphores.values()) {
            sem.close();
        }
        interQueueSemaphores.clear();
        semaphoreArena.close();
    }

    // --- Private implementation ---

    private void allocatePrimaryCommandBuffers(CompiledGraph compiled) {
        for (ExecutionBucket bucket : compiled.executionBuckets()) {
            int family = bucket.queue().queueFamilyIndex();
            if (!primaryCommandBuffers.containsKey(family)) {
                VkCommandBuffer cmd = commandBufferPool.allocatePrimary(family);
                primaryCommandBuffers.put(family, cmd);
                submissions.put(family, new QueueSubmission(bucket.queue()));
            }
        }
        for (Map.Entry<Integer, VkCommandBuffer> entry : primaryCommandBuffers.entrySet()) {
            submissions.get(entry.getKey()).addCommandBuffer(entry.getValue());
        }
    }

    private void recordBucketSequential(ExecutionBucket bucket, VkCommandBuffer primaryCmd, Arena frameArena) {
        int queueFamily = bucket.queue().queueFamilyIndex();
        List<RenderNode> nodes = bucket.nodes();

        for (int n = 0, nCount = nodes.size(); n < nCount; n++) {
            RenderNode node = nodes.get(n);

            barrierBatch.clear();
            emitBarriersForNode(node, barrierBatch, queueFamily, frameArena);

            if (!barrierBatch.hasNoSameQueueBarriers()) {
                executeBarrierBatch(primaryCmd, barrierBatch, frameArena);
            }

            if (barrierBatch.hasOwnershipTransfers()) {
                routeOwnershipTransfers(barrierBatch, queueFamily, primaryCmd);
            }

            if (timestampsEnabled && timestampPool != null) {
                timestampPool.writeBeginTimestamp(primaryCmd.handle(), node.name(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value());
            }

            if (debugLabelsEnabled) {
                DebugLabels.beginForNode(primaryCmd.handle(), node.name(), node.type(), frameArena);
            }

            ctx.commandBuffer = primaryCmd;
            long startNanos = System.nanoTime();

            // Auto-begin dynamic rendering for GraphicsPassNodes with autoRendering enabled
            var rendering = getAutoRendering(node);
            if (rendering != null) {
                rendering.beginCached(primaryCmd.handle());
            }

            node.execute(ctx);

            // Auto-end dynamic rendering
            if (rendering != null) {
                io.github.yetyman.vulkan.VkRendering.end(device, primaryCmd.handle());
            }

            cpuTimes.put(node.name(), System.nanoTime() - startNanos);

            if (debugLabelsEnabled) {
                DebugLabels.end(primaryCmd.handle());
            }

            if (timestampsEnabled && timestampPool != null) {
                timestampPool.writeEndTimestamp(primaryCmd.handle(), node.name(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());
            }

            updateResourceState(node, queueFamily);
        }
    }

    private void recordBucketParallel(ExecutionBucket bucket, VkCommandBuffer primaryCmd, Arena frameArena) {
        int queueFamily = bucket.queue().queueFamilyIndex();
        List<RenderNode> nodes = bucket.nodes();
        int nodeCount = nodes.size();

        // Emit all barriers sequentially (barrier emission depends on mutable resource state)
        BarrierBatch[] nodeBatches = new BarrierBatch[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            BarrierBatch nodeBatch = new BarrierBatch();
            emitBarriersForNode(nodes.get(i), nodeBatch, queueFamily, frameArena);
            nodeBatches[i] = nodeBatch;
        }

        // Emit all barriers on the primary command buffer
        for (int i = 0; i < nodeCount; i++) {
            BarrierBatch nodeBatch = nodeBatches[i];
            if (!nodeBatch.hasNoSameQueueBarriers()) {
                executeBarrierBatch(primaryCmd, nodeBatch, frameArena);
            }
            if (nodeBatch.hasOwnershipTransfers()) {
                routeOwnershipTransfers(nodeBatch, queueFamily, primaryCmd);
            }
        }

        // Allocate secondary command buffers for parallel recording
        VkCommandBuffer[] secondaries = commandBufferPool.allocateSecondaries(queueFamily, nodeCount);

        for (VkCommandBuffer sec : secondaries) {
            VkCommandBuffer.begin(sec)
                .flags(VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value())
                .execute(frameArena);
        }

        // Record nodes in parallel on secondary command buffers
        @SuppressWarnings("unchecked")
        Future<Long>[] futures = new Future[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            final int idx = i;
            final RenderNode node = nodes.get(idx);
            final VkCommandBuffer secondary = secondaries[idx];

            futures[i] = recordingThreadPool.submit(() -> {
                MutableExecutionContext threadCtx = new MutableExecutionContext();
                threadCtx.commandBuffer = secondary;
                threadCtx.frameArena = frameArena;
                threadCtx.frameIndex = ctx.frameIndex;
                threadCtx.frameGeneration = ctx.frameGeneration;
                threadCtx.queue = bucket.queue();
                threadCtx.previousStats = ctx.previousStats;

                long startNanos = System.nanoTime();
                node.execute(threadCtx);
                long elapsed = System.nanoTime() - startNanos;

                secondary.end();
                return elapsed;
            });
        }

        // Wait for all parallel recordings to complete
        for (int i = 0; i < nodeCount; i++) {
            try {
                long elapsed = futures[i].get();
                cpuTimes.put(nodes.get(i).name(), elapsed);
            } catch (Exception e) {
                throw new RenderGraphException("Parallel recording failed for node '" +
                    nodes.get(i).name() + "'", e);
            }
        }

        // Insert per-node timestamps by executing each secondary individually with
        // begin/end timestamps bracketing each one. This gives accurate per-node GPU timing
        // even for parallel-recorded secondaries.
        if (timestampsEnabled && timestampPool != null) {
            MemorySegment singleHandle = frameArena.allocate(ValueLayout.ADDRESS, 1);
            for (int i = 0; i < nodeCount; i++) {
                timestampPool.writeBeginTimestamp(primaryCmd.handle(), nodes.get(i).name(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value());
                singleHandle.setAtIndex(ValueLayout.ADDRESS, 0, secondaries[i].handle());
                VulkanFFM.vkCmdExecuteCommands(primaryCmd.handle(), 1, singleHandle);
                timestampPool.writeEndTimestamp(primaryCmd.handle(), nodes.get(i).name(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());
            }
        } else {
            // No timestamps: execute all secondaries in one call for efficiency
            executeSecondaries(primaryCmd, secondaries, frameArena);
        }

        // Update resource state (sequential -- state is shared)
        for (RenderNode node : nodes) {
            updateResourceState(node, queueFamily);
        }
    }

    private void updateResourceState(RenderNode node, int queueFamily) {
        List<ResourceEdge> writes = node.writes();
        for (int w = 0, wCount = writes.size(); w < wCount; w++) {
            ResourceEdge edge = writes.get(w);
            GraphResource res = edge.resource();
            int effectiveAccessMask;
            int effectiveStageMask;
            if (node.type() == NodeType.CPU_WORK) {
                effectiveAccessMask = VK_ACCESS_HOST_WRITE_BIT;
                effectiveStageMask = VK_PIPELINE_STAGE_HOST_BIT;
            } else {
                effectiveAccessMask = edge.accessMask();
                effectiveStageMask = edge.stageMask();
            }
            res.updateState(effectiveAccessMask, effectiveStageMask, queueFamily);
            if (edge.imageLayout() >= 0 && res instanceof GraphImageResource imgRes) {
                imgRes.updateLayout(edge.imageLayout());
            }
        }

        // Auto-advance temporal resources after nodes that write to them
        // and update the physical slot's tracked state
        if (!temporalResources.isEmpty()) {
            for (TemporalEdge te : node.temporalEdges()) {
                if (te.isWriteCurrent()) {
                    // Update the write slot's state before advancing
                    GraphResource writeSlot = te.temporalResource().currentWriteSlot();
                    writeSlot.updateState(te.accessMask(), te.stageMask(), queueFamily);
                    te.temporalResource().onWriteExecuted();
                } else if (te.isReadPrevious()) {
                    // Update the read slot's state (it was read this frame)
                    GraphResource readSlot = te.temporalResource().previousReadSlot();
                    readSlot.updateState(te.accessMask(), te.stageMask(), queueFamily);
                }
            }
        }
    }

    /**
     * Routes ownership transfer barriers to the correct command buffers.
     */
    private void routeOwnershipTransfers(BarrierBatch batch, int currentQueueFamily,
                                         VkCommandBuffer currentCmd) {
        for (int i = 0; i < batch.transferCount(); i++) {
            OwnershipTransfer transfer = batch.getTransfer(i);

            // Record that this queue pair has a cross-queue dependency
            crossQueueDependencies.add(crossQueueKey(transfer.srcQueueFamily(), transfer.dstQueueFamily()));

            if (transfer.srcQueueFamily() == currentQueueFamily) {
                transfer.releaseBarrier().execute(currentCmd.handle(),
                    transfer.releaseSrcStage(), transfer.releaseDstStage());
            } else {
                VkCommandBuffer srcCmd = primaryCommandBuffers.get(transfer.srcQueueFamily());
                if (srcCmd != null) {
                    transfer.releaseBarrier().execute(srcCmd.handle(),
                        transfer.releaseSrcStage(), transfer.releaseDstStage());
                }
            }

            BarrierBatch deferred = deferredAcquires.computeIfAbsent(
                transfer.dstQueueFamily(), k -> new BarrierBatch());
            deferred.add(transfer.acquireBarrier(),
                transfer.acquireSrcStage(), transfer.acquireDstStage());
        }
    }

    private void emitDeferredAcquires(int queueFamily, VkCommandBuffer cmd, Arena arena) {
        BarrierBatch deferred = deferredAcquires.get(queueFamily);
        if (deferred == null || deferred.count() == 0) return;
        executeBarrierBatch(cmd, deferred, arena);
        deferred.clear();
    }

    private void emitBarriersForNode(RenderNode node, BarrierBatch batch, int consumerQueueFamily, Arena arena) {
        if (barrierStrategy == null) return;

        List<ResourceEdge> reads = node.reads();
        for (int i = 0, size = reads.size(); i < size; i++) {
            barrierStrategy.emit(reads.get(i).resource(), reads.get(i), consumerQueueFamily, batch, arena);
        }

        List<ResourceEdge> writes = node.writes();
        for (int i = 0, size = writes.size(); i < size; i++) {
            ResourceEdge writeEdge = writes.get(i);
            GraphResource resource = writeEdge.resource();
            if (resource.lastAccessMask() != 0 || resource.lastStageMask() != 0) {
                barrierStrategy.emit(resource, writeEdge, consumerQueueFamily, batch, arena);
            }
        }

        // Temporal resource barriers: emit barriers for physical slots accessed by temporal edges
        if (!temporalResources.isEmpty()) {
            for (TemporalEdge te : node.temporalEdges()) {
                io.github.yetyman.vulkan.graph.resources.TemporalResource tr = te.temporalResource();
                if (te.isReadPrevious()) {
                    // The read slot was last written by a previous frame's compute/graphics pass
                    GraphResource readSlot = tr.previousReadSlot();
                    if (readSlot.lastAccessMask() != 0 || readSlot.lastStageMask() != 0) {
                        ResourceEdge syntheticRead = ResourceEdge.read(readSlot, te.accessMask(), te.stageMask());
                        barrierStrategy.emit(readSlot, syntheticRead, consumerQueueFamily, batch, arena);
                    }
                } else if (te.isWriteCurrent()) {
                    // The write slot may have been read by a previous frame
                    GraphResource writeSlot = tr.currentWriteSlot();
                    if (writeSlot.lastAccessMask() != 0 || writeSlot.lastStageMask() != 0) {
                        ResourceEdge syntheticWrite = ResourceEdge.write(writeSlot, te.accessMask(), te.stageMask());
                        barrierStrategy.emit(writeSlot, syntheticWrite, consumerQueueFamily, batch, arena);
                    }
                }
            }
        }

        List<GraphResource> bindless = node.bindlessReads();
        for (int i = 0, size = bindless.size(); i < size; i++) {
            GraphResource bindlessRes = bindless.get(i);
            if (bindlessRes.lastAccessMask() != 0 || bindlessRes.lastStageMask() != 0) {
                CONSERVATIVE.emit(bindlessRes,
                    ResourceEdge.read(bindlessRes, BINDLESS_SHADER_READ, BINDLESS_ALL_COMMANDS),
                    consumerQueueFamily, batch, arena);
            }
        }

        // Optional edges: emit barrier only if the source has been written (writer is active)
        List<io.github.yetyman.vulkan.graph.edges.OptionalEdge> optionals = node.optionalReads();
        for (int i = 0, size = optionals.size(); i < size; i++) {
            var opt = optionals.get(i);
            GraphResource res = opt.resource();
            // Resource is available if it has been written this frame (lastAccessMask != 0)
            if (res.lastAccessMask() != 0 || res.lastStageMask() != 0) {
                ResourceEdge syntheticRead = ResourceEdge.read(res, opt.accessMask(), opt.stageMask());
                barrierStrategy.emit(res, syntheticRead, consumerQueueFamily, batch, arena);
            }
        }
    }

    private void emitReadbackCopies(VkCommandBuffer cmd, Arena arena, long submissionIndex) {
        if (readbacks.isEmpty()) return;
        for (ReadbackHandle rb : readbacks) {
            if (!rb.shouldExecute(submissionIndex)) continue;
            if (rb.stagingBuffer() == null || rb.stagingBuffer().equals(MemorySegment.NULL)) continue;
            if (rb.source().handle().equals(MemorySegment.NULL)) continue;

            // Barrier: source last access -> TRANSFER_READ
            io.github.yetyman.vulkan.VkBufferBarrier.builder()
                .buffer(rb.source().handle())
                .srcAccess(rb.source().lastAccessMask())
                .dstAccess(0x00000800) // VK_ACCESS_TRANSFER_READ_BIT
                .build(arena)
                .execute(cmd.handle(), rb.source().lastStageMask(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value());

            // Copy source -> staging
            io.github.yetyman.vulkan.command.VkCopy.copyBuffer(
                cmd.handle(), rb.source().handle(), rb.stagingBuffer(),
                rb.offset(), 0, rb.size());

            rb.markReady();
        }
    }

    private void emitFinalLayoutBarriers(VkCommandBuffer cmd, Arena arena) {
        if (importedResources.isEmpty()) return;
        for (var imported : importedResources) {
            int currentLayout = imported.currentLayout();
            int finalLayout = imported.finalLayout();
            if (currentLayout == finalLayout) continue;
            if (currentLayout == 0) continue; // VK_IMAGE_LAYOUT_UNDEFINED - not yet transitioned by graph
            if (imported.handle().equals(MemorySegment.NULL)) continue;

            // Emit image layout transition to final layout
            io.github.yetyman.vulkan.VkImageBarrier.builder()
                .image(imported.handle())
                .srcAccess(imported.lastAccessMask())
                .dstAccess(0)
                .transition(currentLayout, finalLayout)
                .build(arena)
                .execute(cmd.handle(), imported.lastStageMask(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());

            imported.updateLayout(finalLayout);
        }
    }

    /**
     * Executes all barriers in the batch via a single coalesced vkCmdPipelineBarrier call.
     * Barriers of the same type are packed into contiguous arrays with combined stage masks.
     */
    private void executeBarrierBatch(VkCommandBuffer cmd, BarrierBatch batch, Arena arena) {
        if (cmd == null || batch.count() == 0) return;
        batch.executeBatched(cmd.handle(), arena);
    }

    private void executeSecondaries(VkCommandBuffer primary, VkCommandBuffer[] secondaries, Arena arena) {
        if (secondaries.length == 0) return;
        MemorySegment handles = arena.allocate(ValueLayout.ADDRESS, secondaries.length);
        for (int i = 0; i < secondaries.length; i++) {
            handles.setAtIndex(ValueLayout.ADDRESS, i, secondaries[i].handle());
        }
        VulkanFFM.vkCmdExecuteCommands(primary.handle(), secondaries.length, handles);
    }

    private void wireExternalSemaphores(CompiledGraph compiled) {
        List<ExecutionBucket> buckets = compiled.executionBuckets();
        for (int b = 0, bCount = buckets.size(); b < bCount; b++) {
            ExecutionBucket bucket = buckets.get(b);
            QueueSubmission sub = submissions.get(bucket.queue().queueFamilyIndex());
            if (sub == null) continue;

            List<RenderNode> nodes = bucket.nodes();
            for (int n = 0, nCount = nodes.size(); n < nCount; n++) {
                RenderNode node = nodes.get(n);
                List<SemaphoreEdge> waits = node.externalWaits();
                for (int i = 0, size = waits.size(); i < size; i++) {
                    sub.addExternalWait(waits.get(i));
                }
                List<SemaphoreEdge> signals = node.externalSignals();
                for (int i = 0, size = signals.size(); i < size; i++) {
                    sub.addExternalSignal(signals.get(i));
                }
            }
        }
    }

    private VkTimelineSemaphore getOrCreateInterQueueSemaphore(String key) {
        return interQueueSemaphores.computeIfAbsent(key,
            k -> VkTimelineSemaphore.create(device, 0, semaphoreArena));
    }

    /**
     * Returns the VkRendering.Builder for a node if it's a GraphicsPassNode with autoRendering
     * enabled and the builder has been cached. Returns null otherwise.
     * If the node has a colorAttachmentImport, patches the image view before returning.
     */
    private static io.github.yetyman.vulkan.VkRendering.Builder getAutoRendering(RenderNode node) {
        if (node instanceof GraphicsPassNode gpn && gpn.autoRendering()) {
            // Patch imported resource image view into the cached rendering struct
            var imported = gpn.colorAttachmentImport();
            if (imported != null && !imported.imageView().equals(MemorySegment.NULL)) {
                gpn.renderingBuilder().patchColorView(gpn.colorAttachmentIndex(), imported.imageView());
            }
            return gpn.renderingBuilder();
        }
        return null;
    }

    /**
     * Mutable execution context reused across all nodes in a frame (sequential path).
     * For parallel recording, each thread creates its own instance.
     */
    private static final class MutableExecutionContext implements ExecutionContext {
        VkCommandBuffer commandBuffer;
        Arena frameArena;
        int frameIndex;
        long frameGeneration;
        QueueAssignment queue;
        FrameStats previousStats;
        Map<String, io.github.yetyman.vulkan.graph.resources.TemporalResource> temporalResources;
        List<io.github.yetyman.vulkan.graph.resources.ImportedResource> importedResources;
        java.util.Set<String> availableOptionals;
        int currentIteration = -1;
        Map<String, MemorySegment[]> pingPongSlots; // resourceName -> [slot0, slot1]

        @Override public VkCommandBuffer commandBuffer() { return commandBuffer; }
        @Override public Arena frameArena() { return frameArena; }
        @Override public int frameIndex() { return frameIndex; }
        @Override public long frameGeneration() { return frameGeneration; }
        @Override public QueueAssignment queue() { return queue; }
        @Override public FrameStats previousStats() { return previousStats; }

        @Override
        public MemorySegment temporalReadHandle(String name) {
            var tr = temporalResources != null ? temporalResources.get(name) : null;
            return tr != null ? tr.previousReadSlot().handle() : MemorySegment.NULL;
        }

        @Override
        public MemorySegment temporalWriteHandle(String name) {
            var tr = temporalResources != null ? temporalResources.get(name) : null;
            return tr != null ? tr.currentWriteSlot().handle() : MemorySegment.NULL;
        }

        @Override
        public GraphResource temporalRead(String name) {
            var tr = temporalResources != null ? temporalResources.get(name) : null;
            return tr != null ? tr.previousReadSlot() : null;
        }

        @Override
        public GraphResource temporalWrite(String name) {
            var tr = temporalResources != null ? temporalResources.get(name) : null;
            return tr != null ? tr.currentWriteSlot() : null;
        }

        @Override
        public io.github.yetyman.vulkan.VkDescriptorSet temporalReadDescriptorSet(String name) {
            var tr = temporalResources != null ? temporalResources.get(name) : null;
            if (tr == null || tr.readDescriptorBinding() == null) return null;
            return tr.readDescriptorBinding().readSet(tr.writeCount(), tr.bufferCount());
        }

        @Override
        public io.github.yetyman.vulkan.VkDescriptorSet temporalWriteDescriptorSet(String name) {
            var tr = temporalResources != null ? temporalResources.get(name) : null;
            if (tr == null || tr.writeDescriptorBinding() == null) return null;
            return tr.writeDescriptorBinding().writeSet(tr.writeCount(), tr.bufferCount());
        }

        @Override
        public io.github.yetyman.vulkan.VkDescriptorSet temporalPairedDescriptorSet(String name) {
            var tr = temporalResources != null ? temporalResources.get(name) : null;
            if (tr == null || tr.pairedDescriptorBinding() == null) return null;
            // The paired binding's writeSet gives the set where read=previous, write=current
            return tr.pairedDescriptorBinding().writeSet(tr.writeCount(), tr.bufferCount());
        }

        @Override
        public MemorySegment importedHandle(String name) {
            if (importedResources == null) return MemorySegment.NULL;
            for (var ir : importedResources) {
                if (ir.name().equals(name)) return ir.handle();
            }
            return MemorySegment.NULL;
        }

        @Override
        public MemorySegment importedImageView(String name) {
            if (importedResources == null) return MemorySegment.NULL;
            for (var ir : importedResources) {
                if (ir.name().equals(name)) return ir.imageView();
            }
            return MemorySegment.NULL;
        }

        @Override
        public int temporalStaleness(String name) {
            var tr = temporalResources != null ? temporalResources.get(name) : null;
            return tr != null ? tr.staleness() : -1;
        }

        @Override
        public boolean isOptionalAvailable(String resourceName) {
            return availableOptionals != null && availableOptionals.contains(resourceName);
        }

        @Override
        public int iterationIndex() { return currentIteration; }

        @Override
        public void setIterationIndex(int index) { this.currentIteration = index; }

        @Override
        public MemorySegment iterationReadHandle(String resourceName) {
            if (pingPongSlots == null || currentIteration < 0) return MemorySegment.NULL;
            MemorySegment[] slots = pingPongSlots.get(resourceName);
            if (slots == null) return MemorySegment.NULL;
            return slots[currentIteration % 2]; // even reads slot 0, odd reads slot 1
        }

        @Override
        public MemorySegment iterationWriteHandle(String resourceName) {
            if (pingPongSlots == null || currentIteration < 0) return MemorySegment.NULL;
            MemorySegment[] slots = pingPongSlots.get(resourceName);
            if (slots == null) return MemorySegment.NULL;
            return slots[(currentIteration + 1) % 2]; // even writes slot 1, odd writes slot 0
        }
    }
}
