package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Wrapper for VkSparseBufferMemoryBindInfo structure.
 * Specifies sparse buffer memory binding operations.
 */
public class VkSparseBufferMemoryBindInfo {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private MemorySegment buffer;
        private MemorySegment[] binds;
        
        private Builder() {}
        
        public Builder buffer(MemorySegment buffer) {
            this.buffer = buffer;
            return this;
        }
        
        public Builder binds(MemorySegment... binds) {
            this.binds = binds;
            return this;
        }
        
        public MemorySegment build(Arena arena) {
            MemorySegment segment = io.github.yetyman.vulkan.generated.VkSparseBufferMemoryBindInfo.allocate(arena);
            io.github.yetyman.vulkan.generated.VkSparseBufferMemoryBindInfo.buffer(segment, buffer);
            io.github.yetyman.vulkan.generated.VkSparseBufferMemoryBindInfo.bindCount(segment, binds != null ? binds.length : 0);
            
            if (binds != null && binds.length > 0) {
                MemorySegment bindsArray = arena.allocate(binds[0].byteSize() * binds.length);
                for (int i = 0; i < binds.length; i++) {
                    MemorySegment.copy(binds[i], 0, bindsArray, i * binds[0].byteSize(), binds[i].byteSize());
                }
                io.github.yetyman.vulkan.generated.VkSparseBufferMemoryBindInfo.pBinds(segment, bindsArray);
            } else {
                io.github.yetyman.vulkan.generated.VkSparseBufferMemoryBindInfo.pBinds(segment, MemorySegment.NULL);
            }
            
            return segment;
        }
    }
}
