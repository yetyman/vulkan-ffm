package io.github.yetyman.vulkan.graph.feedback;

/**
 * Receives per-frame statistics and can influence the next frame's scheduling.
 */
public interface FeedbackHandler {

    /**
     * Called after a frame completes with its timing data.
     * Implementations may adjust scheduling weights, toggle nodes, etc.
     */
    void onStats(FrameStats stats);
}
