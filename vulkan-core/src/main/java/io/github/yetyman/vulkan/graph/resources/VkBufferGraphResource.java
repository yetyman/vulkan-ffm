package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.buffers.ManagedBuffer;

import java.lang.foreign.MemorySegment;

/**
 * Adapts a ManagedBuffer to the GraphBufferResource interface.
 */
public class VkBufferGraphResource implements GraphBufferResource {

    private final String name;
    private final ManagedBuffer buffer;
    private final boolean transientResource;
    private final boolean imported;
    private final ResourceLifetime lifetime = new ResourceLifetime();

    private volatile int lastAccessMask = 0;
    private volatile int lastStageMask;
    private volatile int owningQueueFamily;

    private VkBufferGraphResource(String name, ManagedBuffer buffer, boolean transientResource, boolean imported) {
        this.name = name;
        this.buffer = buffer;
        this.transientResource = transientResource;
        this.imported = imported;
        this.lastStageMask = 0x00000001; // VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
        this.owningQueueFamily = ~0; // VK_QUEUE_FAMILY_IGNORED
    }

    /** Creates a transient (graph-managed) buffer resource */
    public static VkBufferGraphResource transientResource(String name, ManagedBuffer buffer) {
        return new VkBufferGraphResource(name, buffer, true, false);
    }

    /** Creates an imported buffer resource (externally owned) */
    public static VkBufferGraphResource imported(String name, ManagedBuffer buffer) {
        return new VkBufferGraphResource(name, buffer, false, true);
    }

    /** Creates a persistent buffer resource (survives across frames) */
    public static VkBufferGraphResource persistent(String name, ManagedBuffer buffer) {
        return new VkBufferGraphResource(name, buffer, false, false);
    }

    /** @return the underlying ManagedBuffer */
    public ManagedBuffer managedBuffer() { return buffer; }

    @Override public String name() { return name; }
    @Override public MemorySegment handle() { return buffer.handle(); }
    @Override public int lastAccessMask() { return lastAccessMask; }
    @Override public int lastStageMask() { return lastStageMask; }
    @Override public int owningQueueFamily() { return owningQueueFamily; }

    @Override
    public void updateState(int accessMask, int stageMask, int queueFamily) {
        this.lastAccessMask = accessMask;
        this.lastStageMask = stageMask;
        this.owningQueueFamily = queueFamily;
    }

    @Override public boolean isTransient() { return transientResource; }
    @Override public boolean isImported() { return imported; }
    @Override public ResourceLifetime lifetime() { return lifetime; }
    @Override public long size() { return buffer.size(); }
}
