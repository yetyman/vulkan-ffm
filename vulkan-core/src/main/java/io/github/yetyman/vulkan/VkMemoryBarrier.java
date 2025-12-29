package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.VkStructureType;
import java.lang.foreign.*;

/**
 * Wrapper for VkMemoryBarrier - global memory synchronization.
 */
public class VkMemoryBarrier extends VkBarrier {
    
    public VkMemoryBarrier(MemorySegment handle) {
        super(handle);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public BarrierType getType() {
        return BarrierType.MEMORY;
    }
    
    public static class Builder {
        private int srcAccessMask = 0;
        private int dstAccessMask = 0;
        
        public Builder srcAccess(int mask) {
            this.srcAccessMask = mask;
            return this;
        }
        
        public Builder dstAccess(int mask) {
            this.dstAccessMask = mask;
            return this;
        }
        
        public io.github.yetyman.vulkan.VkMemoryBarrier build(Arena arena) {
            MemorySegment barrier = io.github.yetyman.vulkan.generated.VkMemoryBarrier.allocate(arena);
            io.github.yetyman.vulkan.generated.VkMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_BARRIER);
            io.github.yetyman.vulkan.generated.VkMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
            io.github.yetyman.vulkan.generated.VkMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
            return new io.github.yetyman.vulkan.VkMemoryBarrier(barrier);
        }
    }
}