package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Responsible for where a buffer's memory lives and how it is mapped.
 * Orthogonal to {@link TransferStrategy}, which decides how data actually moves.
 *
 * <p>Implementations allocate (or otherwise obtain) the {@link VkBuffer} backing a
 * {@link ManagedBuffer} and, if applicable, a persistent host mapping for it.
 */
public interface AllocationStrategy {

    /**
     * Allocates the backing {@link VkBuffer} for the given size and Vulkan usage flags.
     */
    VkBuffer allocate(VkDevice device, long size, int usageFlags, Arena arena);

    /**
     * Returns a persistent host mapping of the buffer's memory, or {@code null} if this
     * strategy does not persistently map (e.g. pure device-local memory with staging transfers).
     */
    MemorySegment persistentMap(VkDevice device, VkBuffer buffer, Arena arena);

    /**
     * @return the Vulkan memory property flags this strategy allocates with.
     */
    int memoryPropertyFlags();
}
