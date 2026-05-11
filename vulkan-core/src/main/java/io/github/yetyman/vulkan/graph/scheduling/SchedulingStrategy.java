package io.github.yetyman.vulkan.graph.scheduling;

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
    List<ExecutionBucket> schedule(List<RenderNode> nodes, Map<QueueCapability, QueueAssignment> availableQueues);
}
