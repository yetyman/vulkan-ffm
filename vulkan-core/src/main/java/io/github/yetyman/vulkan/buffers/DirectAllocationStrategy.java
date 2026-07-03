package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * One dedicated {@link VkBuffer} + VkDeviceMemory allocation per buffer, with a fixed set of
 * memory property flags. Covers MAPPED, MAPPED_CACHED, DEVICE_LOCAL, and the device-local half
 * of STAGING — the difference between those is entirely in the paired {@link TransferStrategy}.
 */
public final class DirectAllocationStrategy implements AllocationStrategy {
    private final int memoryPropertyFlags;
    private final boolean persistent;

    public DirectAllocationStrategy(int memoryPropertyFlags, boolean persistent) {
        this.memoryPropertyFlags = memoryPropertyFlags;
        this.persistent = persistent;
    }

    @Override
    public VkBuffer allocate(VkDevice device, long size, int usageFlags, Arena arena) {
        return VkBuffer.builder()
                .device(device)
                .size(size)
                .usage(usageFlags)
                .memoryProperties(memoryPropertyFlags)
                .build(arena);
    }

    @Override
    public MemorySegment persistentMap(VkDevice device, VkBuffer buffer, Arena arena) {
        return persistent ? buffer.map(arena) : null;
    }

    @Override
    public int memoryPropertyFlags() {
        return memoryPropertyFlags;
    }
}
