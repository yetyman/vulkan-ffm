package io.github.yetyman.vulkan.graph.scheduling;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default greedy list scheduling strategy. Performs topological sort respecting resource
 * dependencies, assigns nodes to queues based on their requiredQueue capability,
 * and groups independent nodes into parallel execution buckets.
 */
public class ListSchedulingStrategy implements SchedulingStrategy {

    @Override
    public List<ExecutionBucket> schedule(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> availableQueues) {
        // Topological sort via Kahn's algorithm
        List<RenderNode> sorted = topologicalSort(nodes);

        // Group into sequential buckets. Nodes that share no resource dependencies
        // with each other within the same bucket can run in parallel.
        List<ExecutionBucket> buckets = new ArrayList<>();
        Set<GraphResource> completedWrites = new HashSet<>();

        List<RenderNode> currentBucket = new ArrayList<>();
        Set<GraphResource> currentBucketWrites = new HashSet<>();

        for (RenderNode node : sorted) {
            // Check if this node depends on something written in the current bucket
            boolean dependsOnCurrentBucket = false;
            for (ResourceEdge readEdge : node.reads()) {
                if (currentBucketWrites.contains(readEdge.resource())) {
                    dependsOnCurrentBucket = true;
                    break;
                }
            }

            if (dependsOnCurrentBucket && !currentBucket.isEmpty()) {
                // Flush current bucket
                QueueAssignment queue = resolveQueue(currentBucket, availableQueues);
                buckets.add(new ExecutionBucket(queue, currentBucket));
                completedWrites.addAll(currentBucketWrites);
                currentBucket = new ArrayList<>();
                currentBucketWrites = new HashSet<>();
            }

            currentBucket.add(node);
            for (ResourceEdge writeEdge : node.writes()) {
                currentBucketWrites.add(writeEdge.resource());
            }
        }

        // Flush final bucket
        if (!currentBucket.isEmpty()) {
            QueueAssignment queue = resolveQueue(currentBucket, availableQueues);
            buckets.add(new ExecutionBucket(queue, currentBucket));
        }

        return buckets;
    }

    /**
     * Topological sort using Kahn's algorithm. Ordering is derived from resource edges:
     * if node A writes resource R and node B reads resource R, then A must come before B.
     */
    private List<RenderNode> topologicalSort(List<RenderNode> nodes) {
        // Build adjacency: writer -> readers (edges derived from shared resources)
        Map<RenderNode, Set<RenderNode>> successors = new HashMap<>();
        Map<RenderNode, Integer> inDegree = new HashMap<>();

        for (RenderNode node : nodes) {
            successors.put(node, new HashSet<>());
            inDegree.put(node, 0);
        }

        // Map resource -> writer nodes
        Map<GraphResource, List<RenderNode>> resourceWriters = new HashMap<>();
        for (RenderNode node : nodes) {
            for (ResourceEdge edge : node.writes()) {
                resourceWriters.computeIfAbsent(edge.resource(), k -> new ArrayList<>()).add(node);
            }
        }

        // For each reader, add edges from all writers of that resource
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

        // Kahn's algorithm
        List<RenderNode> queue = new ArrayList<>();
        for (RenderNode node : nodes) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        List<RenderNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            // Prefer EARLY hints first, LATE hints last, CRITICAL_PATH nodes first among equals
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
        return switch (node.scheduleHint()) {
            case EARLY -> 3;
            case CRITICAL_PATH -> 2;
            case NONE -> 1;
            case LATE -> 0;
        };
    }

    private QueueAssignment resolveQueue(List<RenderNode> bucket, Map<QueueCapability, QueueAssignment> queues) {
        // Use the most specific queue required by any node in the bucket
        // GRAPHICS > COMPUTE > TRANSFER > ANY
        QueueCapability needed = QueueCapability.ANY;
        for (RenderNode node : bucket) {
            QueueCapability req = node.requiredQueue();
            if (req.ordinal() < needed.ordinal()) {
                needed = req;
            }
        }
        // Fall back to graphics if the specific queue isn't available
        QueueAssignment assignment = queues.get(needed);
        if (assignment == null) {
            assignment = queues.get(QueueCapability.GRAPHICS);
        }
        return assignment;
    }
}
