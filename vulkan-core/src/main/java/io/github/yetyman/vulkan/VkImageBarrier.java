package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for VkImageMemoryBarrier - image layout transitions and synchronization.
 */
public class VkImageBarrier extends VkBarrier {
    
    public VkImageBarrier(MemorySegment handle) {
        super(handle);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public BarrierType getType() {
        return BarrierType.IMAGE;
    }
    
    public static class Builder {
        private MemorySegment image;
        private int srcAccessMask = 0;
        private int dstAccessMask = 0;
        private int oldLayout = 0;
        private int newLayout = 0;
        private int srcQueueFamily = ~0;
        private int dstQueueFamily = ~0;
        private int aspectMask = 1; // VK_IMAGE_ASPECT_COLOR_BIT
        private int baseMipLevel = 0;
        private int levelCount = 1;
        private int baseArrayLayer = 0;
        private int layerCount = 1;
        
        public Builder image(MemorySegment image) {
            this.image = image;
            return this;
        }
        
        public Builder srcAccess(int mask) {
            this.srcAccessMask = mask;
            return this;
        }
        
        public Builder dstAccess(int mask) {
            this.dstAccessMask = mask;
            return this;
        }
        
        public Builder transition(int oldLayout, int newLayout) {
            this.oldLayout = oldLayout;
            this.newLayout = newLayout;
            return this;
        }
        
        public Builder queueFamilyTransfer(int srcFamily, int dstFamily) {
            this.srcQueueFamily = srcFamily;
            this.dstQueueFamily = dstFamily;
            return this;
        }
        
        public Builder aspectMask(int mask) {
            this.aspectMask = mask;
            return this;
        }
        
        public Builder mipLevels(int baseMipLevel, int levelCount) {
            this.baseMipLevel = baseMipLevel;
            this.levelCount = levelCount;
            return this;
        }
        
        public Builder arrayLayers(int baseArrayLayer, int layerCount) {
            this.baseArrayLayer = baseArrayLayer;
            this.layerCount = layerCount;
            return this;
        }
        
        public VkImageBarrier build(Arena arena) {
            if (image == null) throw new IllegalStateException("image not set");
            
            MemorySegment barrier = VkImageMemoryBarrier.allocate(arena);
            VkImageMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER.value());
            VkImageMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
            VkImageMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
            VkImageMemoryBarrier.oldLayout(barrier, oldLayout);
            VkImageMemoryBarrier.newLayout(barrier, newLayout);
            VkImageMemoryBarrier.srcQueueFamilyIndex(barrier, srcQueueFamily);
            VkImageMemoryBarrier.dstQueueFamilyIndex(barrier, dstQueueFamily);
            VkImageMemoryBarrier.image(barrier, image);
            
            MemorySegment subresourceRange = VkImageMemoryBarrier.subresourceRange(barrier);
            VkImageSubresourceRange.aspectMask(subresourceRange, aspectMask);
            VkImageSubresourceRange.baseMipLevel(subresourceRange, baseMipLevel);
            VkImageSubresourceRange.levelCount(subresourceRange, levelCount);
            VkImageSubresourceRange.baseArrayLayer(subresourceRange, baseArrayLayer);
            VkImageSubresourceRange.layerCount(subresourceRange, layerCount);
            
            return new VkImageBarrier(barrier);
        }
    }
}