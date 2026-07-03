package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Bundle of everything a {@link TransferStrategy} needs to act on a specific
 * {@link ManagedBuffer} instance, without every strategy implementation needing its own
 * constructor access to the device/arena/buffer.
 *
 * <p>{@code mappedMemory} is {@code null} when the paired {@link AllocationStrategy} does not
 * persistently map. {@code sparsePages} is non-null only when the paired allocation strategy is
 * {@link SparseAllocationStrategy}.
 */
public final class TransferContext {
    final VkDevice device;
    final VkPhysicalDevice physicalDevice;
    final Arena arena;
    final VkBuffer vkBuffer;
    final long size;
    final MemorySegment mappedMemory;
    final SparsePageAllocator sparsePages;

    TransferContext(VkDevice device, Arena arena, VkBuffer vkBuffer, long size,
                    MemorySegment mappedMemory, SparsePageAllocator sparsePages) {
        this.device = device;
        this.physicalDevice = device.physicalDevice();
        this.arena = arena;
        this.vkBuffer = vkBuffer;
        this.size = size;
        this.mappedMemory = mappedMemory;
        this.sparsePages = sparsePages;
    }
}
