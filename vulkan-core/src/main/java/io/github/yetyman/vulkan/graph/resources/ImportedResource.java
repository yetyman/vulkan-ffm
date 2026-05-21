package io.github.yetyman.vulkan.graph.resources;

import java.lang.foreign.MemorySegment;

/**
 * An externally-owned resource imported into the graph for synchronization tracking.
 * The graph does not allocate or free this resource, but it does:
 * - Insert a barrier at first use to transition from initialLayout to the first consumer's required layout
 * - Insert a barrier at last use to transition to finalLayout (for handoff back to external owner)
 * - Track access/stage state between first and last use
 *
 * Common use case: swapchain images (acquired externally, presented externally).
 *
 * The handle can be rebound each frame via {@link #rebind(MemorySegment)} for resources
 * that change per frame (e.g. swapchain images where the acquired index varies).
 */
public class ImportedResource implements GraphImageResource {

    private final String name;
    private final int format;
    private final int width;
    private final int height;
    private final int initialLayout;
    private final int finalLayout;
    private final ResourceLifetime lifetime = new ResourceLifetime();

    private volatile MemorySegment handle;
    private volatile int currentLayout;
    private volatile int lastAccessMask;
    private volatile int lastStageMask;
    private volatile int owningQueueFamily;

    private ImportedResource(Builder b) {
        this.name = b.name;
        this.format = b.format;
        this.width = b.width;
        this.height = b.height;
        this.initialLayout = b.initialLayout;
        this.finalLayout = b.finalLayout;
        this.handle = b.handle != null ? b.handle : MemorySegment.NULL;
        this.currentLayout = b.initialLayout;
        this.lastAccessMask = 0;
        this.lastStageMask = 0x00000001; // TOP_OF_PIPE
        this.owningQueueFamily = ~0; // VK_QUEUE_FAMILY_IGNORED
    }

    public static Builder builder() { return new Builder(); }

    /** Rebinds the handle (e.g. for per-frame swapchain image selection) */
    public void rebind(MemorySegment newHandle) {
        this.handle = newHandle;
        // Reset state to initial on rebind (new frame, new image)
        this.currentLayout = initialLayout;
        this.lastAccessMask = 0;
        this.lastStageMask = 0x00000001;
    }

    /** @return the layout the graph should leave this resource in after last use */
    public int finalLayout() { return finalLayout; }

    /** @return the layout the resource is in when the graph receives it */
    public int initialLayout() { return initialLayout; }

    // -- GraphResource --
    @Override public String name() { return name; }
    @Override public MemorySegment handle() { return handle; }
    @Override public int lastAccessMask() { return lastAccessMask; }
    @Override public int lastStageMask() { return lastStageMask; }
    @Override public int owningQueueFamily() { return owningQueueFamily; }
    @Override public void updateState(int accessMask, int stageMask, int queueFamily) {
        this.lastAccessMask = accessMask;
        this.lastStageMask = stageMask;
        this.owningQueueFamily = queueFamily;
    }
    @Override public boolean isTransient() { return false; }
    @Override public boolean isImported() { return true; }
    @Override public ResourceLifetime lifetime() { return lifetime; }

    // -- GraphImageResource --
    @Override public int format() { return format; }
    @Override public int currentLayout() { return currentLayout; }
    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public int layers() { return 1; }
    @Override public int mipLevels() { return 1; }
    @Override public int sampleCount() { return 1; }
    @Override public void updateLayout(int layout) { this.currentLayout = layout; }

    public static class Builder {
        private String name;
        private MemorySegment handle;
        private int format;
        private int width;
        private int height;
        private int initialLayout;
        private int finalLayout;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder handle(MemorySegment handle) { this.handle = handle; return this; }
        public Builder format(int format) { this.format = format; return this; }
        public Builder dimensions(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder initialLayout(int layout) { this.initialLayout = layout; return this; }
        public Builder finalLayout(int layout) { this.finalLayout = layout; return this; }

        public ImportedResource build() {
            if (name == null) throw new IllegalStateException("name not set");
            return new ImportedResource(this);
        }
    }
}
