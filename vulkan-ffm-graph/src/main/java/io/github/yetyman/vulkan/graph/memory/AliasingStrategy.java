package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;

import java.util.List;

/**
 * Strategy interface for memory aliasing of transient resources.
 */
public interface AliasingStrategy {

    /**
     * Groups transient resources with non-overlapping lifetimes into aliasing groups
     * that can share physical memory.
     *
     * @param transientResources all transient resources with computed lifetimes
     * @return list of aliasing groups
     */
    List<ResourceAlias> alias(List<GraphResource> transientResources);

    /**
     * Provides the partial order for multi-queue lifetime reasoning.
     * Implementations that support multi-queue aliasing should use this to determine
     * true overlap across queues. Default implementation is a no-op.
     *
     * @param partialOrder the partial order derived from inter-queue semaphore edges
     */
    default void setPartialOrder(ResourceLifetime.PartialOrder partialOrder) {}
}
