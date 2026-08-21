package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.buffers.IBuffer;

import java.lang.foreign.MemorySegment;

/**
 * Adapts an IBuffer to the GraphBufferResource interface.
 */
public class VkBufferGraphResource implements GraphBufferResource {

    private final String name;
    private final IBuffer buffer;
    private final boolean transientResource;
    private final boolean imported;
    private final ResourceLifetime lifetime = new ResourceLifetime();
    private boolean manualFlush;

    private volatile int lastAccessMask = 0;
    private volatile int lastStageMask;
    private volatile int owningQueueFamily;

    private VkBufferGraphResource(String name, IBuffer buffer, boolean transientResource, boolean imported) {
        this.name = name;
        this.buffer = buffer;
        this.transientResource = transientResource;
        this.imported = imported;
        this.manualFlush = false;
        this.lastStageMask = 0x00000001; // VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
        this.owningQueueFamily = ~0; // VK_QUEUE_FAMILY_IGNORED
    }

    /** Creates a transient (graph-managed) buffer resource */
    public static VkBufferGraphResource transientResource(String name, IBuffer buffer) {
        return new VkBufferGraphResource(name, buffer, true, false);
    }

    /** Creates an imported buffer resource (externally owned) */
    public static VkBufferGraphResource imported(String name, IBuffer buffer) {
        return new VkBufferGraphResource(name, buffer, false, true);
    }

    /** Creates a persistent buffer resource (survives across frames) */
    public static VkBufferGraphResource persistent(String name, IBuffer buffer) {
        return new VkBufferGraphResource(name, buffer, false, false);
    }

    /** @return the underlying IBuffer */
    public IBuffer managedBuffer() { return buffer; }

    /** @return the underlying VkBuffer handle for identity checks by the allocator */
    public Object bufferHandle() { return buffer; }

    /**
     * Returns true if this resource's current size/usage match the given descriptor.
     * Used by TransientResourceAllocator to skip re-allocation when nothing changed.
     */
    public boolean matchesDesc(BufferDesc desc) {
        return buffer.size() == desc.size();
    }

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

    /**
     * @return true if this buffer's dirty flush is managed manually by the user, and the graph
     * should NOT auto-insert flush nodes for it.
     */
    public boolean isManualFlush() { return manualFlush; }

    /**
     * Sets whether this buffer's dirty flush is managed manually. When true, the graph will not
     * auto-insert flush/readDiff nodes for this buffer even if it detects deferred mirrored state.
     */
    public void setManualFlush(boolean manualFlush) { this.manualFlush = manualFlush; }
}
