package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.VkStructureType;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Wrapper for VkBindSparseInfo structure.
 * Specifies sparse binding operations for queue submission.
 */
public class VkBindSparseInfo {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private MemorySegment[] waitSemaphores;
        private MemorySegment[] bufferBinds;
        private MemorySegment[] imageOpaqueBinds;
        private MemorySegment[] imageBinds;
        private MemorySegment[] signalSemaphores;
        
        private Builder() {}
        
        public Builder waitSemaphores(MemorySegment... semaphores) {
            this.waitSemaphores = semaphores;
            return this;
        }
        
        public Builder bufferBinds(MemorySegment... binds) {
            this.bufferBinds = binds;
            return this;
        }
        
        public Builder imageOpaqueBinds(MemorySegment... binds) {
            this.imageOpaqueBinds = binds;
            return this;
        }
        
        public Builder imageBinds(MemorySegment... binds) {
            this.imageBinds = binds;
            return this;
        }
        
        public Builder signalSemaphores(MemorySegment... semaphores) {
            this.signalSemaphores = semaphores;
            return this;
        }
        
        public MemorySegment build(Arena arena) {
            MemorySegment segment = io.github.yetyman.vulkan.generated.VkBindSparseInfo.allocate(arena);
            io.github.yetyman.vulkan.generated.VkBindSparseInfo.sType(segment, VkStructureType.VK_STRUCTURE_TYPE_BIND_SPARSE_INFO.value());
            io.github.yetyman.vulkan.generated.VkBindSparseInfo.pNext(segment, MemorySegment.NULL);
            
            if (waitSemaphores != null && waitSemaphores.length > 0) {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.waitSemaphoreCount(segment, waitSemaphores.length);
                MemorySegment semArray = arena.allocate(ValueLayout.ADDRESS, waitSemaphores.length);
                for (int i = 0; i < waitSemaphores.length; i++) {
                    semArray.setAtIndex(ValueLayout.ADDRESS, i, waitSemaphores[i]);
                }
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pWaitSemaphores(segment, semArray);
            } else {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.waitSemaphoreCount(segment, 0);
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pWaitSemaphores(segment, MemorySegment.NULL);
            }
            
            if (bufferBinds != null && bufferBinds.length > 0) {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.bufferBindCount(segment, bufferBinds.length);
                MemorySegment bindsArray = arena.allocate(bufferBinds[0].byteSize() * bufferBinds.length);
                for (int i = 0; i < bufferBinds.length; i++) {
                    MemorySegment.copy(bufferBinds[i], 0, bindsArray, i * bufferBinds[0].byteSize(), bufferBinds[i].byteSize());
                }
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pBufferBinds(segment, bindsArray);
            } else {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.bufferBindCount(segment, 0);
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pBufferBinds(segment, MemorySegment.NULL);
            }
            
            if (imageOpaqueBinds != null && imageOpaqueBinds.length > 0) {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.imageOpaqueBindCount(segment, imageOpaqueBinds.length);
                MemorySegment bindsArray = arena.allocate(imageOpaqueBinds[0].byteSize() * imageOpaqueBinds.length);
                for (int i = 0; i < imageOpaqueBinds.length; i++) {
                    MemorySegment.copy(imageOpaqueBinds[i], 0, bindsArray, i * imageOpaqueBinds[0].byteSize(), imageOpaqueBinds[i].byteSize());
                }
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pImageOpaqueBinds(segment, bindsArray);
            } else {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.imageOpaqueBindCount(segment, 0);
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pImageOpaqueBinds(segment, MemorySegment.NULL);
            }
            
            if (imageBinds != null && imageBinds.length > 0) {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.imageBindCount(segment, imageBinds.length);
                MemorySegment bindsArray = arena.allocate(imageBinds[0].byteSize() * imageBinds.length);
                for (int i = 0; i < imageBinds.length; i++) {
                    MemorySegment.copy(imageBinds[i], 0, bindsArray, i * imageBinds[0].byteSize(), imageBinds[i].byteSize());
                }
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pImageBinds(segment, bindsArray);
            } else {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.imageBindCount(segment, 0);
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pImageBinds(segment, MemorySegment.NULL);
            }
            
            if (signalSemaphores != null && signalSemaphores.length > 0) {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.signalSemaphoreCount(segment, signalSemaphores.length);
                MemorySegment semArray = arena.allocate(ValueLayout.ADDRESS, signalSemaphores.length);
                for (int i = 0; i < signalSemaphores.length; i++) {
                    semArray.setAtIndex(ValueLayout.ADDRESS, i, signalSemaphores[i]);
                }
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pSignalSemaphores(segment, semArray);
            } else {
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.signalSemaphoreCount(segment, 0);
                io.github.yetyman.vulkan.generated.VkBindSparseInfo.pSignalSemaphores(segment, MemorySegment.NULL);
            }
            
            return segment;
        }
    }
}
