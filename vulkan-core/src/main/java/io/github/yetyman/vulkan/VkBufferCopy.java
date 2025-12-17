package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.VulkanFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Wrapper for VkBufferCopy structure
 */
public class VkBufferCopy {
    
    /**
     * Allocates and initializes a VkBufferCopy structure
     */
    public static MemorySegment allocate(Arena arena, long srcOffset, long dstOffset, long size) {
        MemorySegment copy = arena.allocate(io.github.yetyman.vulkan.generated.VkBufferCopy.layout());
        io.github.yetyman.vulkan.generated.VkBufferCopy.srcOffset(copy, srcOffset);
        io.github.yetyman.vulkan.generated.VkBufferCopy.dstOffset(copy, dstOffset);
        io.github.yetyman.vulkan.generated.VkBufferCopy.size(copy, size);
        return copy;
    }
}