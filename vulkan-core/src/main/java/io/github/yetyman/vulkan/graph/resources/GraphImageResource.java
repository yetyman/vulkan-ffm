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
     * Updates the tracked layout after a layout transition.
     */
    void updateLayout(int layout);
}
