package io.github.yetyman.vulkan.graph.scheduling;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.feedback.AdaptiveFeedbackHandler;
import io.github.yetyman.vulkan.graph.nodes.NodeType;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feedback-driven scheduling strategy. Uses GPU timing data from the AdaptiveFeedbackHandler
 * to make queue assignment decisions:
 *
 * - Compute nodes that are expensive (above a threshold) and have no graphics dependencies
 *   within the same bucket are moved to async compute if available
 * - Compute nodes that are cheap or tightly coupled with graphics work stay on the graphics queue
 *   to avoid the overhead of queue ownership transfers and semaphore synchronization
 * - Transfer nodes are moved to the dedicated transfer queue if available and the transfer
 *   is large enough to justify the ownership transfer cost
 *
 * The strategy uses the same topological sort as ListSchedulingStrategy but makes different
 * queue assignment decisions based on measured cost. It converges over several frames as the
 * feedback handler's EMA stabilizes.
 *
 * Decision model:
 * - asyncComputeThresholdMs: minimum GPU cost for a compute node to be worth moving to async compute.
 *   Below this, the semaphore/ownership-transfer overhead exceeds the parallelism benefit.
 * - criticalPathBias: nodes on the critical path (longest dependency chain) are kept on graphics
 *   to minimize latency, even if they could theoretically run on async compute.
 */
public class AdaptiveSchedulingStrategy implements SchedulingStrategy {

    private final AdaptiveFeedbackHandler feedbackHandler;
    private final double asyncComputeThresholdMs;
    private final double criticalPathBias;

    private final ListSchedulingStrategy fallback = new ListSchedulingStrategy();

    /**
     * @param feedbackHandler the feedback handler providing per-node GPU cost estimates
     * @param asyncComputeThresholdMs minimum GPU ms for a compute node to be moved to async compute
     * @param criticalPathBias multiplier applied to critical path nodes' threshold (higher = harder to move)
     */
    public AdaptiveSchedulingStrategy(AdaptiveFeedbackHandler feedbackHandler,
                                      double asyncComputeThresholdMs,
                                      double criticalPathBias) {
        this.feedbackHandler = feedbackHandler;
        this.asyncComputeThresholdMs = asyncComputeThresholdMs;
        this.criticalPathBias = criticalPathBias;
    }

    /** Default: 0.5ms threshold, 2.0x critical path bias */
    public AdaptiveSchedulingStrategy(AdaptiveFeedbackHandler feedbackHandler) {
        this(feedbackHandler, 0.5, 2.0);
    }

    @Override
    public List<ExecutionBucket> schedule(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> availableQueues) {
        if (!feedbackHandler.isWarmedUp()) {
            return fallback.schedule(nodes, availableQueues);
        }

        QueueAssignment asyncCompute = availableQueues.get(QueueCapability.COMPUTE);
        QueueAssignment graphics = availableQueues.get(QueueCapability.GRAPHICS);
        QueueAssignment transfer = availableQueues.get(QueueCapability.TRANSFER);

        if (asyncCompute == null || graphics == null ||
            asyncCompute.queueFamilyIndex() == graphics.queueFamilyIndex()) {
            return fallback.schedule(nodes, availableQueues);
        }

        List<RenderNode> sorted = TopologicalSort.sort(nodes, this::priority);

        Map<RenderNode, Double> criticalPathLength = computeCriticalPathLengths(sorted);
        double maxCriticalPath = criticalPathLength.values().stream().mapToDouble(d -> d).max().orElse(0);

        Map<RenderNode, QueueAssignment> assignments = new HashMap<>();
        for (RenderNode node : sorted) {
            assignments.put(node, assignQueue(node, asyncCompute, graphics,
                transfer, criticalPathLength, maxCriticalPath));
        }

        return buildBuckets(sorted, assignments);
    }

    private QueueAssignment assignQueue(RenderNode node,
                                        QueueAssignment asyncCompute,
                                        QueueAssignment graphics,
                                        QueueAssignment transfer,
                                        Map<RenderNode, Double> criticalPathLength,
                                        double maxCriticalPath) {
        if (node.requiredQueue() == QueueCapability.GRAPHICS || node.type() == NodeType.GRAPHICS) {
            return graphics;
        }

        if (node.type() == NodeType.PRESENT) {
            return graphics;
        }

        if (node.type() == NodeType.TRANSFER && transfer != null &&
            transfer.queueFamilyIndex() != graphics.queueFamilyIndex()) {
            double weight = feedbackHandler.weight(node.name());
            if (weight > asyncComputeThresholdMs * 0.5) {
                return transfer;
            }
        }

        if (node.type() == NodeType.COMPUTE || node.requiredQueue() == QueueCapability.COMPUTE) {
            double weight = feedbackHandler.weight(node.name());

            double pathRatio = criticalPathLength.getOrDefault(node, 0.0) / Math.max(maxCriticalPath, 0.001);
            double effectiveThreshold = asyncComputeThresholdMs;
            if (pathRatio > 0.7 || node.scheduleHint() == ScheduleHint.CRITICAL_PATH) {
                effectiveThreshold *= criticalPathBias;
            }

            if (weight >= effectiveThreshold) {
                return asyncCompute;
            }
        }

        return graphics;
    }

    private Map<RenderNode, Double> computeCriticalPathLengths(List<RenderNode> sorted) {
        Map<RenderNode, Set<RenderNode>> successors = TopologicalSort.buildSuccessorMap(sorted);

        Map<RenderNode, Double> pathLength = new HashMap<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            RenderNode node = sorted.get(i);
            double nodeWeight = feedbackHandler.weight(node.name());
            double maxSuccessorPath = 0;
            for (RenderNode succ : successors.get(node)) {
                maxSuccessorPath = Math.max(maxSuccessorPath, pathLength.getOrDefault(succ, 0.0));
            }
            pathLength.put(node, nodeWeight + maxSuccessorPath);
        }

        return pathLength;
    }

    private List<ExecutionBucket> buildBuckets(List<RenderNode> sorted, Map<RenderNode, QueueAssignment> assignments) {
        List<ExecutionBucket> buckets = new ArrayList<>();
        Set<GraphResource> currentBucketWrites = new HashSet<>();
        List<RenderNode> currentBucket = new ArrayList<>();
        QueueAssignment currentQueue = null;

        for (RenderNode node : sorted) {
            QueueAssignment nodeQueue = assignments.get(node);

            boolean dependsOnCurrentBucket = false;
            for (ResourceEdge readEdge : node.reads()) {
                if (currentBucketWrites.contains(readEdge.resource())) {
                    dependsOnCurrentBucket = true;
                    break;
                }
            }

            boolean queueChanged = currentQueue != null &&
                nodeQueue.queueFamilyIndex() != currentQueue.queueFamilyIndex();

            if ((dependsOnCurrentBucket || queueChanged) && !currentBucket.isEmpty()) {
                buckets.add(new ExecutionBucket(currentQueue, currentBucket));
                currentBucket = new ArrayList<>();
                currentBucketWrites = new HashSet<>();
            }

            currentBucket.add(node);
            currentQueue = nodeQueue;
            for (ResourceEdge writeEdge : node.writes()) {
                currentBucketWrites.add(writeEdge.resource());
            }
        }

        if (!currentBucket.isEmpty()) {
            buckets.add(new ExecutionBucket(currentQueue, currentBucket));
        }

        return buckets;
    }

    private int priority(RenderNode node) {
        double weight = feedbackHandler.weight(node.name());
        int hintBonus = switch (node.scheduleHint()) {
            case EARLY -> 3000;
            case CRITICAL_PATH -> 2000;
            case NONE -> 1000;
            case LATE -> 0;
        };
        return hintBonus + (int)(weight * 100);
    }
}
