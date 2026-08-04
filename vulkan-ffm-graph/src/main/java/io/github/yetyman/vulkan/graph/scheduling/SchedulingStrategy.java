package io.github.yetyman.vulkan.graph.scheduling;

import io.github.yetyman.vulkan.graph.edges.DependencyEdge;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for assigning nodes to queues and ordering them into execution buckets.
 */
public interface SchedulingStrategy {

    /**
     * Assigns each node to a queue and produces an ordered list of execution buckets.
     *
     * @param nodes the active nodes to schedule (already topologically valid)
     * @param availableQueues map of capability to queue handles
     * @return ordered list of execution buckets
     */
    default List<ExecutionBucket> schedule(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> availableQueues) {
        return schedule(nodes, availableQueues, List.of());
    }

    /**
     * Assigns each node to a queue and produces an ordered list of execution buckets,
     * respecting manual dependency edges in addition to resource-derived ordering.
     *
     * @param nodes the active nodes to schedule
     * @param availableQueues map of capability to queue handles
     * @param dependencyEdges manual ordering constraints
     * @return ordered list of execution buckets
     */
    List<ExecutionBucket> schedule(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> availableQueues,
                                   List<DependencyEdge> dependencyEdges);
}
