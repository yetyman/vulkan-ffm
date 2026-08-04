package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.nodes.Priority;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Strategy for graceful degradation when the frame budget is exceeded.
 * Applied after activation predicates but before compilation, deactivating
 * lowest-priority passes until the estimated cost fits within budget.
 */
public interface DegradationStrategy {

    /**
     * Filters the active node list based on budget constraints.
     *
     * @param activeNodes nodes that passed activation predicates
     * @param previousStats stats from the previous frame (may be null on first frame)
     * @return the filtered list of nodes to actually execute
     */
    List<RenderNode> apply(List<RenderNode> activeNodes, FrameStats previousStats);

    /** No degradation - run everything regardless of budget */
    static DegradationStrategy none() {
        return (nodes, stats) -> nodes;
    }

    /** Drop lowest-priority passes until estimated time fits budget */
    static DegradationStrategy dropByPriority(float budgetMs) {
        return (nodes, stats) -> {
            if (stats == null) return nodes;
            float totalMs = stats.totalGpuNanos() / 1_000_000.0f;
            if (totalMs <= budgetMs) return nodes;

            // Sort by priority (lowest first = drop candidates)
            List<RenderNode> sorted = new ArrayList<>(nodes);
            sorted.sort(Comparator.comparingInt(n -> -n.priority().ordinal()));

            // Drop from the end (lowest priority) until within budget
            List<RenderNode> result = new ArrayList<>(sorted);
            float estimated = totalMs;
            while (estimated > budgetMs && !result.isEmpty()) {
                RenderNode candidate = result.get(result.size() - 1);
                if (candidate.priority() == Priority.CRITICAL) break;
                result.remove(result.size() - 1);
                // Estimate savings from node's last known GPU time
                var nodeStats = stats.forNode(candidate.name());
                if (nodeStats != null) {
                    estimated -= nodeStats.gpuTimeNanos() / 1_000_000.0f;
                }
            }
            return result;
        };
    }

    /** Target a specific frame rate, auto-adjust budget */
    static DegradationStrategy targetFrameRate(float targetFps) {
        return dropByPriority(1000.0f / targetFps);
    }
}
