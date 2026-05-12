package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.graph.barriers.BarrierStrategy;
import io.github.yetyman.vulkan.graph.barriers.SplitBarrierStrategy;
import io.github.yetyman.vulkan.graph.feedback.FeedbackHandler;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.memory.AliasingStrategy;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.SchedulingStrategy;
import io.github.yetyman.vulkan.graph.scheduling.ListSchedulingStrategy;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level render graph. Declares resources and nodes, compiles to an execution plan,
 * and executes per-frame with automatic barrier emission and stats collection.
 *
 * stub -- the following planned features are not yet integrated:
 * - resize(w, h): re-describing and re-allocating transient resources on dimension change
 * - persistent resource ring management: PersistentResourceRing exists but is not wired
 *   into the graph builder or executor (no .persistent() / .transient() builder methods)
 * - transient resource allocation: the graph does not allocate or own any GPU resources;
 *   all resources must be pre-allocated externally and wrapped in GraphResource impls
 * - fast recompile paths: topology-unchanged shortcuts are not implemented
 * - multi-command-buffer submission: all nodes record into a single externally-provided
 *   command buffer regardless of queue assignment
 */
public class RenderGraph {

    private final VkDevice device;
    private final int framesInFlight;
    private final List<RenderNode> nodes;
    private final Map<QueueCapability, QueueAssignment> queues;
    private final RenderGraphCompiler compiler;
    private final RenderGraphExecutor executor;
    private final RenderGraphStats stats;
    private final FeedbackHandler feedbackHandler;

    private CompiledGraph compiledGraph;
    private long frameGeneration = 0;
    private FrameStats previousStats;

    private RenderGraph(Builder b) {
        this.device = b.device;
        this.framesInFlight = b.framesInFlight;
        this.nodes = List.copyOf(b.nodes);
        this.queues = Map.copyOf(b.queues);
        this.feedbackHandler = b.feedbackHandler;

        SchedulingStrategy scheduling = b.schedulingStrategy != null ? b.schedulingStrategy : new ListSchedulingStrategy();
        BarrierStrategy barriers = b.barrierStrategy != null ? b.barrierStrategy : new SplitBarrierStrategy();

        this.compiler = new RenderGraphCompiler(scheduling, barriers, b.aliasingStrategy);
        this.executor = new RenderGraphExecutor(device, barriers);
        this.stats = new RenderGraphStats();

        // Compile immediately
        this.compiledGraph = compiler.compile(new ArrayList<>(nodes), queues);
    }

    public static Builder builder() { return new Builder(); }

    /**
     * Executes one frame of the graph. Records commands into the provided command buffer,
     * emits all necessary barriers, and collects CPU timing data.
     */
    public void execute(Arena frameArena, int frameIndex, VkCommandBuffer commandBuffer) {
        long gen = frameGeneration++;
        stats.beginFrame(gen);

        Map<String, Long> cpuTimes = executor.execute(
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
     * Recompiles the graph (e.g. after resize or node activation change).
     */
    public void recompile() {
        this.compiledGraph = compiler.compile(new ArrayList<>(nodes), queues);
    }

    /** @return the compiled execution plan */
    public CompiledGraph compiledGraph() { return compiledGraph; }

    /** @return current frame generation counter */
    public long frameGeneration() { return frameGeneration; }

    /** @return frames in flight count */
    public int framesInFlight() { return framesInFlight; }

    /** @return the most recent frame stats, or null if no frame has completed */
    public FrameStats previousStats() { return previousStats; }

    /** Prints the compiled DAG to stdout as ASCII art */
    public void printGraph() { RenderGraphVisualizer.print(compiledGraph); }

    /** @return ASCII visualization of the compiled DAG */
    public String visualizeGraph() { return RenderGraphVisualizer.visualize(compiledGraph); }

    private void onFrameComplete(FrameStats frameStats) {
        this.previousStats = frameStats;
        if (feedbackHandler != null) {
            feedbackHandler.onStats(frameStats);
        }
        for (RenderNode node : compiledGraph.activeNodes()) {
            var nodeStats = frameStats.forNode(node.name());
            if (nodeStats != null) {
                node.onStats(nodeStats);
            }
        }
    }

    // -- Builder --

    public static class Builder {
        private VkDevice device;
        private int framesInFlight = 2;
        private final List<RenderNode> nodes = new ArrayList<>();
        private final Map<QueueCapability, QueueAssignment> queues = new HashMap<>();
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
