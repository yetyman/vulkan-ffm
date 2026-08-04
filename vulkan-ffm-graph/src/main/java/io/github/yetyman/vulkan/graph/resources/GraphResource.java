package io.github.yetyman.vulkan.graph.resources;

import java.lang.foreign.MemorySegment;

/**
 * Base interface for all resources managed by the render graph.
 */
public interface GraphResource {

    /** @return human-readable name for debugging and graph visualization */
    String name();

    /** @return the underlying Vulkan handle (VkBuffer or VkImage) */
    MemorySegment handle();

    /** @return last access mask applied to this resource */
    int lastAccessMask();

    /** @return last pipeline stage mask applied to this resource */
    int lastStageMask();

    /** @return queue family index that currently owns this resource */
    int owningQueueFamily();

    /**
     * Updates tracked synchronization state after a pass executes.
     */
    void updateState(int accessMask, int stageMask, int queueFamily);

    /** @return true if this resource is transient (graph-managed lifetime, aliasable) */
    boolean isTransient();

    /** @return true if this resource was imported from outside the graph */
    boolean isImported();

    /** @return the lifetime interval for this resource version */
    ResourceLifetime lifetime();
}
