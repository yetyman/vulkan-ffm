package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;

/**
 * Resizable BAR / Smart Access Memory allocation: DEVICE_LOCAL | HOST_VISIBLE | HOST_COHERENT.
 * Always persistently mapped — writes go directly into VRAM without a staging buffer.
 * Only instantiate when {@code VulkanCapabilities.reBar} is true.
 */
public final class ReBarAllocationStrategy implements AllocationStrategy {
    private static final int FLAGS = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value()
            | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value()
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value();

    @Override
    public VkBuffer allocate(VkDevice device, long size, int usageFlags, Arena arena) {
        return VkBuffer.builder()
                .device(device)
                .size(size)
                .usage(usageFlags)
                .memoryProperties(FLAGS)
                .build(arena);
    }

    @Override
    public MemorySegment persistentMap(VkDevice device, VkBuffer buffer, Arena arena) {
        return buffer.map(arena);
    }

    @Override
    public int memoryPropertyFlags() {
        return FLAGS;
    }
}
