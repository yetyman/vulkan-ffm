package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;

import java.util.ArrayList;
import java.util.List;

/**
 * Default aliasing strategy. Groups transient resources with non-overlapping lifetimes
 * into aliasing groups that can share physical memory. Uses a greedy first-fit bin packing.
 */
public class LifetimeAliasingStrategy implements AliasingStrategy {

    @Override
    public List<ResourceAlias> alias(List<GraphResource> transientResources) {
        List<ResourceAlias> groups = new ArrayList<>();

        for (GraphResource resource : transientResources) {
            ResourceLifetime lifetime = resource.lifetime();
            if (!lifetime.isValid()) continue;

            // Try to fit into an existing group
            boolean placed = false;
            for (int i = 0; i < groups.size(); i++) {
                if (canFit(groups.get(i), lifetime)) {
                    // Add to this group (rebuild with new member)
                    List<GraphResource> members = new ArrayList<>(groups.get(i).members());
                    members.add(resource);
                    groups.set(i, new ResourceAlias(members));
                    placed = true;
                    break;
                }
            }

            if (!placed) {
                // Start a new group
                List<GraphResource> members = new ArrayList<>();
                members.add(resource);
                groups.add(new ResourceAlias(members));
            }
        }

        return groups;
    }

    /**
     * Returns true if the resource's lifetime does not overlap with any existing member.
     */
    private boolean canFit(ResourceAlias group, ResourceLifetime candidate) {
        for (GraphResource member : group.members()) {
            if (member.lifetime().overlaps(candidate)) {
                return false;
            }
        }
        return true;
    }
}
