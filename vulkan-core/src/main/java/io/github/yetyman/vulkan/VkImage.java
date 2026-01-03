package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan image (VkImage) with automatic resource management.
 */
public class VkImage implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;
    private final MemorySegment memory;
    
    private VkImage(MemorySegment handle, VkDevice device, MemorySegment memory) {
        this.handle = handle;
        this.device = device;
        this.memory = memory;
    }
    
    /** @return a new builder for configuring image creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkImage handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
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
        private int format = VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
        private int usage = VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        private int tiling = VkImageTiling.VK_IMAGE_TILING_OPTIMAL;
        private int samples = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT;
        private int mipLevels = 1;
        private int arrayLayers = 1;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        /** Sets image dimensions */
        public Builder dimensions(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            return this;
        }
        
        /** Sets image format */
        public Builder format(int format) {
            this.format = format;
            return this;
        }
        
        /** Sets image usage flags */
        public Builder usage(int usage) {
            this.usage = usage;
            return this;
        }
        
        /** Sets image tiling */
        public Builder tiling(int tiling) {
            this.tiling = tiling;
            return this;
        }
        
        /** Sets sample count */
        public Builder samples(int samples) {
            this.samples = samples;
            return this;
        }
        
        /** Sets mip levels */
        public Builder mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }
        
        /** Sets array layers */
        public Builder arrayLayers(int arrayLayers) {
            this.arrayLayers = arrayLayers;
            return this;
        }
        
        /** Creates the image with automatic memory allocation */
        public VkImage build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (width <= 0 || height <= 0 || depth <= 0) throw new IllegalStateException("invalid dimensions");
            
            // Create image
            MemorySegment imageInfo = VkImageCreateInfo.allocate(arena);
            VkImageCreateInfo.sType(imageInfo, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
            VkImageCreateInfo.pNext(imageInfo, MemorySegment.NULL);
            VkImageCreateInfo.flags(imageInfo, 0);
            VkImageCreateInfo.imageType(imageInfo, depth > 1 ? VkImageType.VK_IMAGE_TYPE_3D : 
                                                   height > 1 ? VkImageType.VK_IMAGE_TYPE_2D : 
                                                                VkImageType.VK_IMAGE_TYPE_1D);
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
            VkImageCreateInfo.sharingMode(imageInfo, VkSharingMode.VK_SHARING_MODE_EXCLUSIVE);
            VkImageCreateInfo.queueFamilyIndexCount(imageInfo, 0);
            VkImageCreateInfo.pQueueFamilyIndices(imageInfo, MemorySegment.NULL);
            VkImageCreateInfo.initialLayout(imageInfo, VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED);
            
            MemorySegment imagePtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createImage(device.handle(), imageInfo, imagePtr).check();
            MemorySegment image = imagePtr.get(ValueLayout.ADDRESS, 0);
            
            // Get memory requirements
            MemorySegment memReqs = VkMemoryRequirements.allocate(arena);
            Vulkan.getImageMemoryRequirements(device.handle(), image, memReqs);
            
            // Allocate memory
            MemorySegment allocInfo = VkMemoryAllocateInfo.allocate(arena);
            VkMemoryAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
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
            
            return new VkImage(image, device, memory);
        }
    }
}