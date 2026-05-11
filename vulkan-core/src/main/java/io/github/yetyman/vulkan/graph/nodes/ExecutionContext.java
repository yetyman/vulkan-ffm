package io.github.yetyman.vulkan.graph.nodes;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;

import java.lang.foreign.Arena;

/**
 * Context provided to nodes during the execute phase.
 */
public interface ExecutionContext {

    /** @return the command buffer to record into */
    VkCommandBuffer commandBuffer();

    /** @return arena scoped to this frame (freed after submit) */
    Arena frameArena();

    /** @return which frame-in-flight slot (0..framesInFlight-1) */
    int frameIndex();

    /** @return monotonic frame counter */
    long frameGeneration();

    /** @return the queue this node is executing on */
    QueueAssignment queue();

    /** @return frame N-1's stats for inline adaptation (may be null on first frame) */
    FrameStats previousStats();
}
