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

    // Fallback for first frames before feedback is available
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
        // Fall back to list scheduling until feedback is available
        if (!feedbackHandler.isWarmedUp()) {
            return fallback.schedule(nodes, availableQueues);
        }

        QueueAssignment asyncCompute = availableQueues.get(QueueCapability.COMPUTE);
        QueueAssignment graphics = availableQueues.get(QueueCapability.GRAPHICS);
        QueueAssignment transfer = availableQueues.get(QueueCapability.TRANSFER);

        // If no async compute queue is available, fall back entirely
        if (asyncCompute == null || graphics == null ||
            asyncCompute.queueFamilyIndex() == graphics.queueFamilyIndex()) {
            return fallback.schedule(nodes, availableQueues);
        }

        // Topological sort
        List<RenderNode> sorted = topologicalSort(nodes);

        // Compute critical path lengths (longest path from each node to any sink)
        Map<RenderNode, Double> criticalPathLength = computeCriticalPathLengths(sorted);
        double maxCriticalPath = criticalPathLength.values().stream().mapToDouble(d -> d).max().orElse(0);

        // Assign queues based on feedback weights
        Map<RenderNode, QueueAssignment> assignments = new HashMap<>();
        for (RenderNode node : sorted) {
            assignments.put(node, assignQueue(node, availableQueues, asyncCompute, graphics,
                transfer, criticalPathLength, maxCriticalPath));
        }

        // Build buckets respecting dependencies and queue assignments
        return buildBuckets(sorted, assignments);
    }

    private QueueAssignment assignQueue(RenderNode node,
                                        Map<QueueCapability, QueueAssignment> queues,
                                        QueueAssignment asyncCompute,
                                        QueueAssignment graphics,
                                        QueueAssignment transfer,
                                        Map<RenderNode, Double> criticalPathLength,
                                        double maxCriticalPath) {
        // Graphics nodes must stay on graphics
        if (node.requiredQueue() == QueueCapability.GRAPHICS || node.type() == NodeType.GRAPHICS) {
            return graphics;
        }

        // Present nodes must stay on graphics (for swapchain)
        if (node.type() == NodeType.PRESENT) {
            return graphics;
        }

        // Transfer nodes: use dedicated transfer queue if available
        if (node.type() == NodeType.TRANSFER && transfer != null &&
            transfer.queueFamilyIndex() != graphics.queueFamilyIndex()) {
            double weight = feedbackHandler.weight(node.name());
            // Only move to transfer queue if the transfer is substantial
            if (weight > asyncComputeThresholdMs * 0.5) {
                return transfer;
            }
        }

        // Compute nodes: decide between async compute and graphics
        if (node.type() == NodeType.COMPUTE || node.requiredQueue() == QueueCapability.COMPUTE) {
            double weight = feedbackHandler.weight(node.name());

            // Apply critical path bias: nodes on the critical path need a higher threshold
            double pathRatio = criticalPathLength.getOrDefault(node, 0.0) / Math.max(maxCriticalPath, 0.001);
            double effectiveThreshold = asyncComputeThresholdMs;
            if (pathRatio > 0.7 || node.scheduleHint() == ScheduleHint.CRITICAL_PATH) {
                effectiveThreshold *= criticalPathBias;
            }

            // Move to async compute if expensive enough to justify the overhead
            if (weight >= effectiveThreshold) {
                return asyncCompute;
            }
        }

        // Default: graphics queue
        return graphics;
    }

    /**
     * Computes the critical path length from each node to any sink (longest weighted path).
     * Weight = feedback GPU cost estimate for each node.
     */
    private Map<RenderNode, Double> computeCriticalPathLengths(List<RenderNode> sorted) {
        // Build successor map
        Map<GraphResource, List<RenderNode>> resourceWriters = new HashMap<>();
        for (RenderNode node : sorted) {
            for (ResourceEdge edge : node.writes()) {
                resourceWriters.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }

        Map<RenderNode, Set<RenderNode>> successors = new HashMap<>();
        for (RenderNode node : sorted) {
            successors.put(node, new HashSet<>());
        }
        for (RenderNode reader : sorted) {
            for (ResourceEdge readEdge : reader.reads()) {
                List<RenderNode> writers = resourceWriters.get(readEdge.resource());
                if (writers != null) {
                    for (RenderNode writer : writers) {
                        if (writer != reader) {
                            successors.get(writer).add(reader);
                        }
                    }
                }
            }
        }

        // Reverse topological order: compute longest path from each node to any sink
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

    /**
     * Builds execution buckets from sorted nodes with their queue assignments.
     * Nodes on the same queue that share no intra-bucket dependencies are grouped together.
     * Cross-queue transitions force a bucket boundary.
     */
    private List<ExecutionBucket> buildBuckets(List<RenderNode> sorted, Map<RenderNode, QueueAssignment> assignments) {
        List<ExecutionBucket> buckets = new ArrayList<>();
        Set<GraphResource> completedWrites = new HashSet<>();

        List<RenderNode> currentBucket = new ArrayList<>();
        Set<GraphResource> currentBucketWrites = new HashSet<>();
        QueueAssignment currentQueue = null;

        for (RenderNode node : sorted) {
            QueueAssignment nodeQueue = assignments.get(node);

            // Force bucket boundary on queue change or dependency on current bucket
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
                completedWrites.addAll(currentBucketWrites);
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

    /**
     * Topological sort using Kahn's algorithm (same as ListSchedulingStrategy).
     */
    private List<RenderNode> topologicalSort(List<RenderNode> nodes) {
        Map<RenderNode, Set<RenderNode>> successors = new HashMap<>();
        Map<RenderNode, Integer> inDegree = new HashMap<>();

        for (RenderNode node : nodes) {
            successors.put(node, new HashSet<>());
            inDegree.put(node, 0);
        }

        Map<GraphResource, List<RenderNode>> resourceWriters = new HashMap<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                resourceWriters.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }

        for (RenderNode reader : nodes) {
            for (ResourceEdge readEdge : reader.reads()) {
                List<RenderNode> writers = resourceWriters.get(readEdge.resource());
                if (writers != null) {
                    for (RenderNode writer : writers) {
                        if (writer != reader && successors.get(writer).add(reader)) {
                            inDegree.merge(reader, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        List<RenderNode> queue = new ArrayList<>();
        for (RenderNode node : nodes) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        List<RenderNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            RenderNode best = queue.remove(selectBest(queue));
            sorted.add(best);

            for (RenderNode succ : successors.get(best)) {
                int newDegree = inDegree.get(succ) - 1;
                inDegree.put(succ, newDegree);
                if (newDegree == 0) {
                    queue.add(succ);
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            throw new io.github.yetyman.vulkan.graph.RenderGraphException(
                "Cycle detected in render graph: " + sorted.size() + " of " + nodes.size() + " nodes sorted");
        }

        return sorted;
    }

    private int selectBest(List<RenderNode> ready) {
        int bestIdx = 0;
        int bestPriority = priority(ready.get(0));
        for (int i = 1; i < ready.size(); i++) {
            int p = priority(ready.get(i));
            if (p > bestPriority) {
                bestPriority = p;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private int priority(RenderNode node) {
        // Prefer nodes with higher GPU cost (schedule expensive work first for better overlap)
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
