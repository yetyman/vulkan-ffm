package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.VkBarrier;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.graph.barriers.BarrierBatch;
import io.github.yetyman.vulkan.graph.barriers.BarrierStrategy;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.nodes.ExecutionContext;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.ExecutionBucket;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;

import java.lang.foreign.Arena;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes a compiled render graph for one frame. Emits barriers between nodes,
 * records commands, and collects timing data.
 *
 * Designed for zero per-frame allocations in steady state: all internal structures
 * are pre-allocated and reused across frames.
 */
public class RenderGraphExecutor {

    private final BarrierStrategy barrierStrategy;
    private final VkDevice device;
    private final BarrierBatch barrierBatch = new BarrierBatch();
    private final MutableExecutionContext ctx = new MutableExecutionContext();
    private HashMap<String, Long> cpuTimes;

    public RenderGraphExecutor(VkDevice device, BarrierStrategy barrierStrategy) {
        this.device = device;
        this.barrierStrategy = barrierStrategy;
    }

    /**
     * Executes all buckets of a compiled graph into the given command buffer.
     *
     * @return per-node CPU recording times (GPU times require timestamp readback).
     *         The returned map is owned by this executor and will be cleared on next execute().
     */
    public Map<String, Long> execute(CompiledGraph compiled, VkCommandBuffer commandBuffer,
                                     Arena frameArena, int frameIndex, long frameGeneration,
                                     FrameStats previousStats) {
        // Ensure cpuTimes map is pre-sized (only reallocate if node count grew)
        int nodeCount = compiled.activeNodes().size();
        if (cpuTimes == null || cpuTimes.size() < nodeCount) {
            cpuTimes = HashMap.newHashMap(nodeCount);
        } else {
            cpuTimes.clear();
        }

        // Set up mutable context fields that are constant for this frame
        ctx.commandBuffer = commandBuffer;
        ctx.frameArena = frameArena;
        ctx.frameIndex = frameIndex;
        ctx.frameGeneration = frameGeneration;
        ctx.previousStats = previousStats;

        for (ExecutionBucket bucket : compiled.executionBuckets()) {
            QueueAssignment queue = bucket.queue();
            ctx.queue = queue;

            for (RenderNode node : bucket.nodes()) {
                // Emit barriers
                barrierBatch.clear();
                emitBarriersForNode(node, barrierBatch, frameArena);
                if (!barrierBatch.isEmpty()) {
                    executeBarrierBatch(commandBuffer, barrierBatch);
                }

                // Record node commands
                long startNanos = System.nanoTime();
                node.execute(ctx);
                cpuTimes.put(node.name(), System.nanoTime() - startNanos);

                // Update resource state after execution
                for (ResourceEdge edge : node.writes()) {
                    GraphResource res = edge.resource();
                    res.updateState(edge.accessMask(), edge.stageMask(), queue.queueFamilyIndex());
                    if (edge.imageLayout() >= 0 && res instanceof io.github.yetyman.vulkan.graph.resources.GraphImageResource imgRes) {
                        imgRes.updateLayout(edge.imageLayout());
                    }
                }
            }
        }

        return cpuTimes;
    }

    private void emitBarriersForNode(RenderNode node, BarrierBatch batch, Arena arena) {
        if (barrierStrategy == null) return;

        for (ResourceEdge readEdge : node.reads()) {
            barrierStrategy.emit(readEdge.resource(), readEdge, batch, arena);
        }

        for (ResourceEdge writeEdge : node.writes()) {
            GraphResource resource = writeEdge.resource();
            if (resource.lastAccessMask() != 0 || resource.lastStageMask() != 0) {
                barrierStrategy.emit(resource, writeEdge, batch, arena);
            }
        }
    }

    private void executeBarrierBatch(VkCommandBuffer cmd, BarrierBatch batch) {
        if (cmd == null) return; // no-op in headless/test mode
        for (int i = 0; i < batch.count(); i++) {
            batch.get(i).execute(cmd.handle(), batch.srcStageMask(), batch.dstStageMask());
        }
    }

    /**
     * Mutable execution context reused across all nodes in a frame.
     * Avoids allocating a new record per node.
     */
    private static final class MutableExecutionContext implements ExecutionContext {
        VkCommandBuffer commandBuffer;
        Arena frameArena;
        int frameIndex;
        long frameGeneration;
        QueueAssignment queue;
        FrameStats previousStats;

        @Override public VkCommandBuffer commandBuffer() { return commandBuffer; }
        @Override public Arena frameArena() { return frameArena; }
        @Override public int frameIndex() { return frameIndex; }
        @Override public long frameGeneration() { return frameGeneration; }
        @Override public QueueAssignment queue() { return queue; }
        @Override public FrameStats previousStats() { return previousStats; }
    }
}
