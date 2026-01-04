package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkBufferMemoryBarrier;
import java.lang.foreign.*;

/**
 * Wrapper for VkBufferMemoryBarrier - buffer-specific synchronization.
 */
public class VkBufferBarrier extends VkBarrier {
    
    public VkBufferBarrier(MemorySegment handle) {
        super(handle);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public BarrierType getType() {
        return BarrierType.BUFFER;
    }
    
    public static class Builder {
        private MemorySegment buffer;
        private int srcAccessMask = 0;
        private int dstAccessMask = 0;
        private int srcQueueFamily = ~0;
        private int dstQueueFamily = ~0;
        private long offset = 0;
        private long size = ~0L;
        
        public Builder buffer(MemorySegment buffer) {
            this.buffer = buffer;
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
        
        public Builder queueFamilyTransfer(int srcFamily, int dstFamily) {
            this.srcQueueFamily = srcFamily;
            this.dstQueueFamily = dstFamily;
            return this;
        }
        
        public Builder range(long offset, long size) {
            this.offset = offset;
            this.size = size;
            return this;
        }
        
        public VkBufferBarrier build(Arena arena) {
            if (buffer == null) throw new IllegalStateException("buffer not set");
            
            MemorySegment barrier = VkBufferMemoryBarrier.allocate(arena);
            VkBufferMemoryBarrier.sType(barrier, VkStructureType.VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER.value());
            VkBufferMemoryBarrier.srcAccessMask(barrier, srcAccessMask);
            VkBufferMemoryBarrier.dstAccessMask(barrier, dstAccessMask);
            VkBufferMemoryBarrier.srcQueueFamilyIndex(barrier, srcQueueFamily);
            VkBufferMemoryBarrier.dstQueueFamilyIndex(barrier, dstQueueFamily);
            VkBufferMemoryBarrier.buffer(barrier, buffer);
            VkBufferMemoryBarrier.offset(barrier, offset);
            VkBufferMemoryBarrier.size(barrier, size);
            return new VkBufferBarrier(barrier);
        }
    }
}