package io.github.yetyman.vulkan.graph.scheduling;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.util.ArrayList;
import java.util.HashSet;
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
        List<RenderNode> sorted = TopologicalSort.sort(nodes, this::priority);
        return buildBuckets(sorted, availableQueues);
    }

    private List<ExecutionBucket> buildBuckets(List<RenderNode> sorted,
                                               Map<QueueCapability, QueueAssignment> availableQueues) {
        List<ExecutionBucket> buckets = new ArrayList<>();
        Set<GraphResource> currentBucketWrites = new HashSet<>();
        List<RenderNode> currentBucket = new ArrayList<>();

        for (RenderNode node : sorted) {
            boolean dependsOnCurrentBucket = false;
            for (ResourceEdge readEdge : node.reads()) {
                if (currentBucketWrites.contains(readEdge.resource())) {
                    dependsOnCurrentBucket = true;
                    break;
                }
            }

            if (dependsOnCurrentBucket && !currentBucket.isEmpty()) {
                QueueAssignment queue = resolveQueue(currentBucket, availableQueues);
                buckets.add(new ExecutionBucket(queue, currentBucket));
                currentBucket = new ArrayList<>();
                currentBucketWrites = new HashSet<>();
            }

            currentBucket.add(node);
            for (ResourceEdge writeEdge : node.writes()) {
                currentBucketWrites.add(writeEdge.resource());
            }
        }

        if (!currentBucket.isEmpty()) {
            QueueAssignment queue = resolveQueue(currentBucket, availableQueues);
            buckets.add(new ExecutionBucket(queue, currentBucket));
        }

        return buckets;
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
        QueueCapability needed = QueueCapability.ANY;
        for (RenderNode node : bucket) {
            QueueCapability req = node.requiredQueue();
            if (req.ordinal() < needed.ordinal()) {
                needed = req;
            }
        }
        QueueAssignment assignment = queues.get(needed);
        if (assignment == null) {
            assignment = queues.get(QueueCapability.GRAPHICS);
        }
        return assignment;
    }
}
