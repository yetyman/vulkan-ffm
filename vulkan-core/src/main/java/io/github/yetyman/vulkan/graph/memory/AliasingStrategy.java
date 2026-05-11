package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.graph.resources.GraphResource;

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
}
