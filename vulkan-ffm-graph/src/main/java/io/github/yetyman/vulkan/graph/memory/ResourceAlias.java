package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.graph.resources.GraphResource;

import java.util.Collections;
import java.util.List;

/**
 * A group of transient resources with non-overlapping lifetimes that share physical memory.
 */
public class ResourceAlias {

    private final List<GraphResource> members;

    public ResourceAlias(List<GraphResource> members) {
        this.members = Collections.unmodifiableList(members);
    }

    /** @return the resources in this aliasing group */
    public List<GraphResource> members() { return members; }
}
