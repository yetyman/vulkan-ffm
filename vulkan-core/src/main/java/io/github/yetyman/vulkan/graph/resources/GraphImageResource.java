package io.github.yetyman.vulkan.graph.resources;

/**
 * A graph resource backed by a VkImage. Exposes image-specific state (layout, dimensions).
 */
public interface GraphImageResource extends GraphResource {

    /** @return the VkFormat int value */
    int format();

    /** @return current image layout (VkImageLayout value) */
    int currentLayout();

    /** @return image width in pixels */
    int width();

    /** @return image height in pixels */
    int height();

    /** @return array layer count */
    int layers();

    /** @return mip level count */
    int mipLevels();

    /** @return sample count (1 for non-MSAA) */
    int sampleCount();

    /**
     * Returns the default VkImageView handle for this resource.
     * For transient resources allocated by the graph, this is a full-image view
     * created alongside the image. For imported resources, this must be set by the caller.
     *
     * @return the VkImageView handle, or MemorySegment.NULL if no view is available
     */
    default java.lang.foreign.MemorySegment imageView() { return java.lang.foreign.MemorySegment.NULL; }

    /**
     * Updates the tracked layout after a layout transition.
     */
    void updateLayout(int layout);
}
