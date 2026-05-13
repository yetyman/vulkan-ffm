package io.github.yetyman.vulkan.graph.memory;

import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;

import java.util.ArrayList;
import java.util.List;

/**
 * Default aliasing strategy. Groups transient resources with non-overlapping lifetimes
 * into aliasing groups that can share physical memory. Uses a greedy first-fit bin packing.
 *
 * For transient resources (which are dead by frame end), uses submission order as the
 * ordering guarantee across queues. This is less conservative than requiring explicit
 * semaphore edges -- since the executor submits buckets in order and transient resources
 * don't survive across frames, submission order is sufficient to guarantee non-overlap.
 *
 * For persistent resources that survive across frames, falls back to the strict
 * semaphore-derived partial order.
 */
public class LifetimeAliasingStrategy implements AliasingStrategy {

    private ResourceLifetime.PartialOrder partialOrder;
    private ResourceLifetime.PartialOrder transientOrder;

    @Override
    public void setPartialOrder(ResourceLifetime.PartialOrder partialOrder) {
        this.partialOrder = partialOrder;
        // Transient resources use submission order (less conservative, safe for frame-scoped resources)
        this.transientOrder = SemaphorePartialOrder.submissionOrder();
    }

    @Override
    public List<ResourceAlias> alias(List<GraphResource> transientResources) {
        List<AliasGroupBuilder> groups = new ArrayList<>();

        for (GraphResource resource : transientResources) {
            ResourceLifetime lifetime = resource.lifetime();
            if (!lifetime.isValid()) continue;

            boolean placed = false;
            for (int i = 0; i < groups.size(); i++) {
                if (canFit(groups.get(i), lifetime, resource.isTransient())) {
                    groups.get(i).add(resource);
                    placed = true;
                    break;
                }
            }

            if (!placed) {
                AliasGroupBuilder newGroup = new AliasGroupBuilder();
                newGroup.add(resource);
                groups.add(newGroup);
            }
        }

        List<ResourceAlias> result = new ArrayList<>(groups.size());
        for (AliasGroupBuilder group : groups) {
            result.add(group.build());
        }
        return result;
    }

    private boolean canFit(AliasGroupBuilder group, ResourceLifetime candidate, boolean candidateIsTransient) {
        for (int i = 0; i < group.size(); i++) {
            ResourceLifetime memberLifetime = group.lifetime(i);
            boolean memberIsTransient = group.isTransient(i);
            boolean overlaps;

            // Both transient: use submission order (less conservative, safe for frame-scoped)
            // Mixed or persistent: use strict semaphore partial order
            if (candidateIsTransient && memberIsTransient && transientOrder != null) {
                overlaps = memberLifetime.overlaps(candidate, transientOrder);
            } else if (partialOrder != null) {
                overlaps = memberLifetime.overlaps(candidate, partialOrder);
            } else {
                overlaps = memberLifetime.overlaps(candidate);
            }
            if (overlaps) return false;
        }
        return true;
    }

    /**
     * Mutable builder for an aliasing group. Avoids creating new ResourceAlias instances
     * on every insertion.
     */
    private static class AliasGroupBuilder {
        private final List<GraphResource> members = new ArrayList<>();

        void add(GraphResource resource) {
            members.add(resource);
        }

        int size() { return members.size(); }

        ResourceLifetime lifetime(int index) {
            return members.get(index).lifetime();
        }

        boolean isTransient(int index) {
            return members.get(index).isTransient();
        }

        ResourceAlias build() {
            return new ResourceAlias(List.copyOf(members));
        }
    }
}
