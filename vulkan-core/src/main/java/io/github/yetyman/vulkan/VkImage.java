package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.*;

/**
 * Wrapper for Vulkan image (VkImage) with automatic resource management and state tracking.
 */
public class VkImage implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    private final MemorySegment memory;
    private final int format;
    private final int width;
    private final int height;
    private final int depth;
    private final int layers;
    private final int mipLevels;
    private final int sampleCount;

    private volatile int currentLayout;
    private volatile int lastAccessMask;
    private volatile int lastStageMask;
    private volatile int owningQueueFamily;

    private VkImage(MemorySegment handle, VkDevice device, MemorySegment memory,
                    int format, int width, int height, int depth,
                    int layers, int mipLevels, int sampleCount) {
        this.handle = handle;
        this.device = device;
        this.memory = memory;
        this.format = format;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.layers = layers;
        this.mipLevels = mipLevels;
        this.sampleCount = sampleCount;
        this.currentLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value();
        this.lastAccessMask = 0;
        this.lastStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value();
        this.owningQueueFamily = ~0; // VK_QUEUE_FAMILY_IGNORED
        if (Boolean.getBoolean("vulkan.trackAllocations")) {
            System.err.println("[TRACK] VkImage created: handle=0x" + Long.toHexString(handle.address())
                    + " memory=0x" + (memory != null ? Long.toHexString(memory.address()) : "null")
                    + " " + width + "x" + height
                    + " @ " + Thread.currentThread().getStackTrace()[3]);
        }
    }

    /**
     * @return a new builder for configuring image creation
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Wraps an existing image handle without memory ownership (e.g. swapchain images).
     */
    public static VkImage wrap(MemorySegment handle, VkDevice device) {
        return new VkImage(handle, device, MemorySegment.NULL, 0, 0, 0, 1, 1, 1, 1);
    }

    /**
     * Wraps an existing image handle with known format and dimensions.
     */
    public static VkImage wrap(MemorySegment handle, VkDevice device, int format, int width, int height) {
        return new VkImage(handle, device, MemorySegment.NULL, format, width, height, 1, 1, 1, 1);
    }

    /**
     * @return the VkImage handle
     */
    public MemorySegment handle() {
        return handle;
    }

    /** @return the owning device */
    public VkDevice device() { return device; }

    /** @return the image format */
    public int format() { return format; }

    /** @return image width in pixels */
    public int width() { return width; }

    /** @return image height in pixels */
    public int height() { return height; }

    /** @return image depth (1 for 2D) */
    public int depth() { return depth; }

    /** @return array layer count */
    public int layers() { return layers; }

    /** @return mip level count */
    public int mipLevels() { return mipLevels; }

    /** @return sample count */
    public int sampleCount() { return sampleCount; }

    /** @return current image layout */
    public int currentLayout() { return currentLayout; }

    /** @return last access mask applied */
    public int lastAccessMask() { return lastAccessMask; }

    /** @return last pipeline stage mask */
    public int lastStageMask() { return lastStageMask; }

    /** @return queue family that currently owns this image */
    public int owningQueueFamily() { return owningQueueFamily; }

    /**
     * Updates tracked synchronization state. Called by the render graph and TransientCommandBuffer
     * after any state-changing operation.
     */
    public void updateState(int layout, int accessMask, int stageMask, int queueFamily) {
        this.currentLayout = layout;
        this.lastAccessMask = accessMask;
        this.lastStageMask = stageMask;
        this.owningQueueFamily = queueFamily;
    }

    @Override
    public void close() {
        if (Boolean.getBoolean("vulkan.trackAllocations")) {
            System.err.println("[TRACK] VkImage closing: handle=0x" + Long.toHexString(handle.address())
                    + " memory=0x" + (memory != null ? Long.toHexString(memory.address()) : "null")
                    + " @ " + Thread.currentThread().getStackTrace()[2]);
        }
        if (memory != null && !memory.equals(MemorySegment.NULL)) {
            Vulkan.freeMemory(device.handle(), memory);
        }
        Vulkan.destroyImage(device.handle(), handle);
    }

    /**
     * Builder for image creation with automatic memory allocation.
     */
    public static class Builder {
        private VkDevice device;
        private int width, height, depth = 1;
        private int format = VkFormat.VK_FORMAT_R8G8B8A8_UNORM.value();
        private int usage = VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value();
        private int tiling = VkImageTiling.VK_IMAGE_TILING_OPTIMAL.value();
        private int samples = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT.value();
        private int mipLevels = 1;
        private int arrayLayers = 1;
        private int flags = 0;

        private Builder() {
        }

        /**
         * Sets the logical device
         */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }

        /**
         * Sets image dimensions
         */
        public Builder dimensions(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            return this;
        }

        /**
         * Sets image format
         */
        public Builder format(int format) {
            this.format = format;
            return this;
        }

        /**
         * Sets image usage flags
         */
        public Builder usage(int usage) {
            this.usage = usage;
            return this;
        }

        /**
         * Sets image tiling
         */
        public Builder tiling(int tiling) {
            this.tiling = tiling;
            return this;
        }

        /**
         * Sets sample count
         */
        public Builder samples(int samples) {
            this.samples = samples;
            return this;
        }

        /**
         * Sets mip levels
         */
        public Builder mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        /**
         * Sets creation flags (e.g. VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT)
         */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }

        public Builder arrayLayers(int layers) {
            this.arrayLayers = layers;
            return this;
        }

        /**
         * Configures as a cube map (6 array layers, CUBE_COMPATIBLE flag).
         */
        public Builder cubeMap() {
            this.arrayLayers = 6;
            this.flags |= VkImageCreateFlagBits.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT.value();
            return this;
        }

        /**
         * Configures as a cube map array (layerCount must be a multiple of 6).
         */
        public Builder cubeMapArray(int cubeCount) {
            this.arrayLayers = cubeCount * 6;
            this.flags |= VkImageCreateFlagBits.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT.value();
            return this;
        }

        /**
         * Creates the image with automatic memory allocation
         */
        public VkImage build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (width <= 0 || height <= 0 || depth <= 0) throw new IllegalStateException("invalid dimensions");

            // Create image
            MemorySegment imageInfo = VkImageCreateInfo.allocate(arena);
            VkImageCreateInfo.sType(imageInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO.value());
            VkImageCreateInfo.pNext(imageInfo, MemorySegment.NULL);
            VkImageCreateInfo.flags(imageInfo, flags);
            VkImageCreateInfo.imageType(imageInfo, depth > 1 ? VkImageType.VK_IMAGE_TYPE_3D.value() :
                    height > 1 ? VkImageType.VK_IMAGE_TYPE_2D.value() :
                            VkImageType.VK_IMAGE_TYPE_1D.value());
            VkImageCreateInfo.format(imageInfo, format);

            MemorySegment extent = VkImageCreateInfo.extent(imageInfo);
            VkExtent3D.width(extent, width);
            VkExtent3D.height(extent, height);
            VkExtent3D.depth(extent, depth);

            VkImageCreateInfo.mipLevels(imageInfo, mipLevels);
            VkImageCreateInfo.arrayLayers(imageInfo, arrayLayers);
            VkImageCreateInfo.samples(imageInfo, samples);
            VkImageCreateInfo.tiling(imageInfo, tiling);
            VkImageCreateInfo.usage(imageInfo, usage);
            VkImageCreateInfo.sharingMode(imageInfo, VkSharingMode.VK_SHARING_MODE_EXCLUSIVE.value());
            VkImageCreateInfo.queueFamilyIndexCount(imageInfo, 0);
            VkImageCreateInfo.pQueueFamilyIndices(imageInfo, MemorySegment.NULL);
            VkImageCreateInfo.initialLayout(imageInfo, VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value());

            MemorySegment imagePtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createImage(device.handle(), imageInfo, imagePtr).check();
            MemorySegment image = imagePtr.get(ValueLayout.ADDRESS, 0);

            // Get memory requirements
            MemorySegment memReqs = VkMemoryRequirements.allocate(arena);
            Vulkan.getImageMemoryRequirements(device.handle(), image, memReqs);

            // Allocate memory
            MemorySegment allocInfo = VkMemoryAllocateInfo.allocate(arena);
            VkMemoryAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO.value());
            VkMemoryAllocateInfo.pNext(allocInfo, MemorySegment.NULL);
            VkMemoryAllocateInfo.allocationSize(allocInfo, VkMemoryRequirements.size(memReqs));

            // Use first available memory type (simplified)
            int typeFilter = VkMemoryRequirements.memoryTypeBits(memReqs);
            int memoryTypeIndex = Integer.numberOfTrailingZeros(typeFilter);
            VkMemoryAllocateInfo.memoryTypeIndex(allocInfo, memoryTypeIndex);

            MemorySegment memoryPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.allocateMemory(device.handle(), allocInfo, memoryPtr).check();
            MemorySegment memory = memoryPtr.get(ValueLayout.ADDRESS, 0);

            // Bind memory
            Vulkan.bindImageMemory(device.handle(), image, memory, 0).check();

            return new VkImage(image, device, memory, format, width, height, depth, arrayLayers, mipLevels, samples);
        }
    }
}