package io.github.yetyman.vulkan.graph.resources;

import io.github.yetyman.vulkan.VkImage;

import java.lang.foreign.MemorySegment;

/**
 * Adapts a VkImage wrapper to the GraphImageResource interface.
 * Delegates state tracking to the underlying VkImage.
 */
public class VkImageGraphResource implements GraphImageResource {

    private final String name;
    private final VkImage image;
    private final boolean transientResource;
    private final boolean imported;
    private final ResourceLifetime lifetime = new ResourceLifetime();
    private MemorySegment imageViewHandle = MemorySegment.NULL;

    private VkImageGraphResource(String name, VkImage image, boolean transientResource, boolean imported) {
        this.name = name;
        this.image = image;
        this.transientResource = transientResource;
        this.imported = imported;
    }

    /** Creates a transient (graph-managed) image resource */
    public static VkImageGraphResource transientResource(String name, VkImage image) {
        return new VkImageGraphResource(name, image, true, false);
    }

    /** Creates an imported image resource (externally owned) */
    public static VkImageGraphResource imported(String name, VkImage image) {
        return new VkImageGraphResource(name, image, false, true);
    }

    /** Creates a persistent image resource (survives across frames) */
    public static VkImageGraphResource persistent(String name, VkImage image) {
        return new VkImageGraphResource(name, image, false, false);
    }

    /** @return the underlying VkImage */
    public VkImage vkImage() { return image; }

    /** @return the underlying VkImage (for allocator identity checks) */
    public VkImage image() { return image; }

    /**
     * Returns true if this resource's current dimensions/format match the given descriptor.
     * Used by TransientResourceAllocator to skip re-allocation on resize when nothing changed.
     */
    public boolean matchesDesc(ImageDesc desc) {
        return image.width() == desc.width()
            && image.height() == desc.height()
            && image.format() == desc.format()
            && image.mipLevels() == desc.mipLevels()
            && image.layers() == desc.arrayLayers()
            && image.sampleCount() == desc.samples();
    }

    @Override public String name() { return name; }
    @Override public MemorySegment handle() { return image.handle(); }
    @Override public int lastAccessMask() { return image.lastAccessMask(); }
    @Override public int lastStageMask() { return image.lastStageMask(); }
    @Override public int owningQueueFamily() { return image.owningQueueFamily(); }

    @Override
    public void updateState(int accessMask, int stageMask, int queueFamily) {
        image.updateState(image.currentLayout(), accessMask, stageMask, queueFamily);
    }

    @Override public boolean isTransient() { return transientResource; }
    @Override public boolean isImported() { return imported; }
    @Override public ResourceLifetime lifetime() { return lifetime; }

    // GraphImageResource
    @Override public int format() { return image.format(); }
    @Override public int currentLayout() { return image.currentLayout(); }
    @Override public int width() { return image.width(); }
    @Override public int height() { return image.height(); }
    @Override public int layers() { return image.layers(); }
    @Override public int mipLevels() { return image.mipLevels(); }
    @Override public int sampleCount() { return image.sampleCount(); }

    @Override
    public void updateLayout(int layout) {
        image.updateState(layout, image.lastAccessMask(), image.lastStageMask(), image.owningQueueFamily());
    }

    @Override
    public MemorySegment imageView() { return imageViewHandle; }

    /** Sets the default image view handle for this resource (used by auto-rendering) */
    public void setImageView(MemorySegment imageView) { this.imageViewHandle = imageView; }
}
