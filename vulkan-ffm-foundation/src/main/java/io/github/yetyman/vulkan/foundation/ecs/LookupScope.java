package io.github.yetyman.vulkan.foundation.ecs;

/**
 * Lookup scope for dependency resolution.
 */
public enum LookupScope {
    /** Dependency must be a component on the same node. */
    SELF,

    /**
     * Walk up the parent chain from this node (exclusive of self, since SELF already covers
     * the same-node case) until a node carrying the dependency type is found.
     */
    NEAREST_ANCESTOR
}
