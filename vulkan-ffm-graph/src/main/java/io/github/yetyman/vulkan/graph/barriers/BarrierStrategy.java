package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.lang.foreign.SegmentAllocator;

/**
 * Strategy interface for synthesizing barriers between passes.
 */
public interface BarrierStrategy {

    /**
     * Emits barriers needed to transition a resource from its previous state to the state
     * required by the consuming edge.
     *
     * @param resource the resource being transitioned
     * @param consumer the edge declaring how the next pass will access the resource
     * @param consumerQueueFamily the queue family index of the consuming node
     * @param batch accumulator for the barriers to emit
     * @param allocator scratch allocator for barrier struct allocation (frame-scoped)
     */
    void emit(GraphResource resource, ResourceEdge consumer, int consumerQueueFamily,
              BarrierBatch batch, SegmentAllocator allocator);
}
