package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static io.github.yetyman.vulkan.enums.VkBufferCreateFlagBits.VK_BUFFER_CREATE_SPARSE_BINDING_BIT;

/**
 * Sparse virtual memory allocation with on-demand page-level binding.
 * Allocates a VkBuffer with the SPARSE_BINDING flag (no backing memory bound at creation)
 * and owns a {@link SparsePageAllocator} for page lifecycle (bind/unbind/map/unmap).
 * Never persistently mapped — mapping happens per page, driven by {@link SparseTransferStrategy}.
 */
public final class SparseAllocationStrategy implements AllocationStrategy {
    private final int pageMemoryPropertyFlags;
    private final VkQueue sparseQueue;
    private boolean allocated = false;

    /**
     * The page allocator for the buffer most recently created by {@link #allocate}.
     * Populated during allocate() and consumed by {@link BufferFactory} to build the
     * {@link TransferContext}. Package-private — one SparseAllocationStrategy instance is
     * only ever used for a single buffer's lifetime.
     */
    private SparsePageAllocator pages;

    public SparseAllocationStrategy(int pageMemoryPropertyFlags, VkQueue sparseQueue) {
        this.pageMemoryPropertyFlags = pageMemoryPropertyFlags;
        this.sparseQueue = sparseQueue;
    }

    @Override
    public VkBuffer allocate(VkDevice device, long size, int usageFlags, Arena arena) {
        if (allocated)
            throw new IllegalStateException("SparseAllocationStrategy instance already used to allocate a buffer — create a new instance per buffer");
        if (!device.physicalDevice().supportsSparseResidencyBuffer())
            throw new UnsupportedOperationException("Device does not support sparse binding");

        VkBuffer buffer = VkBuffer.builder()
                .device(device)
                .size(size)
                .usage(usageFlags)
                .flags(VK_BUFFER_CREATE_SPARSE_BINDING_BIT.value())
                .build(arena);

        long pageSize = querySparsePageSize(device, buffer.handle());
        this.pages = new SparsePageAllocator(device, buffer.handle(), size, pageSize, pageMemoryPropertyFlags, sparseQueue);
        this.allocated = true;
        return buffer;
    }

    @Override
    public MemorySegment persistentMap(VkDevice device, VkBuffer buffer, Arena arena) {
        return null;
    }

    @Override
    public int memoryPropertyFlags() {
        return pageMemoryPropertyFlags;
    }

    /**
     * @return the page allocator created by the most recent {@link #allocate} call.
     */
    SparsePageAllocator pages() {
        return pages;
    }

    private static long querySparsePageSize(VkDevice device, MemorySegment bufferHandle) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment req = io.github.yetyman.vulkan.generated.VkMemoryRequirements.allocate(tmp);
            device.getBufferMemoryRequirements(bufferHandle, req);
            return io.github.yetyman.vulkan.generated.VkMemoryRequirements.alignment(req);
        }
    }
}
