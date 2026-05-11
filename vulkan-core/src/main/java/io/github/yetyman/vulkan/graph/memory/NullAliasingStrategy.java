package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.util.List;

/**
 * No-op aliasing strategy. Each resource gets its own memory. Useful for debugging
 * aliasing-related issues by disabling all memory sharing.
 */
public class NullAliasingStrategy implements AliasingStrategy {

    @Override
    public List<ResourceAlias> alias(List<GraphResource> transientResources) {
        return transientResources.stream()
            .map(r -> new ResourceAlias(List.of(r)))
            .toList();
    }
}
