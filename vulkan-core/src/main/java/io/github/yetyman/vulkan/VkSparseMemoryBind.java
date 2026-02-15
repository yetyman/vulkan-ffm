package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Wrapper for VkSparseMemoryBind structure.
 * Specifies a sparse memory binding operation.
 */
public class VkSparseMemoryBind {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private long resourceOffset;
        private long size;
        private MemorySegment memory = MemorySegment.NULL;
        private long memoryOffset;
        private int flags;
        
        private Builder() {}
        
        public Builder resourceOffset(long resourceOffset) {
            this.resourceOffset = resourceOffset;
            return this;
        }
        
        public Builder size(long size) {
            this.size = size;
            return this;
        }
        
        public Builder memory(MemorySegment memory) {
            this.memory = memory;
            return this;
        }
        
        public Builder memoryOffset(long memoryOffset) {
            this.memoryOffset = memoryOffset;
            return this;
        }
        
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        public MemorySegment build(Arena arena) {
            MemorySegment segment = io.github.yetyman.vulkan.generated.VkSparseMemoryBind.allocate(arena);
            io.github.yetyman.vulkan.generated.VkSparseMemoryBind.resourceOffset(segment, resourceOffset);
            io.github.yetyman.vulkan.generated.VkSparseMemoryBind.size(segment, size);
            io.github.yetyman.vulkan.generated.VkSparseMemoryBind.memory(segment, memory);
            io.github.yetyman.vulkan.generated.VkSparseMemoryBind.memoryOffset(segment, memoryOffset);
            io.github.yetyman.vulkan.generated.VkSparseMemoryBind.flags(segment, flags);
            return segment;
        }
    }
}
