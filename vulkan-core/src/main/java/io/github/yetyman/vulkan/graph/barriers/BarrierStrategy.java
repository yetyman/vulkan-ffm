package io.github.yetyman.vulkan.graph.barriers;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.lang.foreign.Arena;

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
     * @param batch accumulator for the barriers to emit
     * @param arena arena for barrier struct allocation
     */
    void emit(GraphResource resource, ResourceEdge consumer, BarrierBatch batch, Arena arena);
}
