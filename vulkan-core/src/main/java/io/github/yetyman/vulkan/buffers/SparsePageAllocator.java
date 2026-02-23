package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkMemoryAllocateInfo;
import io.github.yetyman.vulkan.generated.VkMemoryType;
import io.github.yetyman.vulkan.generated.VkPhysicalDeviceMemoryProperties;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkAllocateMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFreeMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceMemoryProperties;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkMapMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkQueueBindSparse;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkUnmapMemory;

/**
 * Manages sparse page binding and host-visible mapping for a sparse VkBuffer.
 * Handles page lifecycle (bind/unbind/map/unmap) independently of data transfer strategy.
 * Used internally by {@link SparseBuffer}.
 */
class SparsePageAllocator implements AutoCloseable {
    private final VkDevice device;
    /** Owns all long-lived native allocations (VkMemoryAllocateInfo, pointer-out segments). */
    private final Arena arena;
    private final MemorySegment bufferHandle;
    private final VkQueue sparseQueue;
    final long pageSize;
    final boolean isHostVisible;
    final boolean isHostCoherent;
    private final int memoryProperties;

    private final ConcurrentHashMap<Long, MemorySegment> boundPages = new ConcurrentHashMap<>();
    /** Guards freeMemoryPool and boundPages mutations in bind/unbind — pages at different offsets are independent. */
    private final ReentrantLock bindLock = new ReentrantLock();
    private final Queue<MemorySegment> freeMemoryPool = new ArrayDeque<>();
    private final ConcurrentHashMap<Long, MemorySegment> mappedPages = new ConcurrentHashMap<>();
    private final int[] mapDepth;

    SparsePageAllocator(VkDevice device, MemorySegment bufferHandle,
                        long bufferSize, long pageSize, int memoryProperties,
                        VkQueue sparseQueue) {
        this.device = device;
        this.arena = Arena.ofShared();
        this.bufferHandle = bufferHandle;
        this.sparseQueue = sparseQueue;
        this.pageSize = pageSize;
        this.memoryProperties = memoryProperties;
        this.isHostVisible = (memoryProperties & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value()) != 0;
        this.isHostCoherent = (memoryProperties & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value()) != 0;
        this.mapDepth = new int[(int) ((bufferSize + pageSize - 1) / pageSize)];
    }

    void ensurePagesCommitted(long offset, long length) {
        long startPage = offset / pageSize;
        long endPage = (offset + length - 1) / pageSize;
        for (long page = startPage; page <= endPage; page++) {
            long pageOffset = page * pageSize;
            // computeIfAbsent is atomic per key — two threads binding different pages don't block each other
            boundPages.computeIfAbsent(pageOffset, k -> {
                bindLock.lock();
                try {
                    // re-check inside lock in case another thread bound this page between the check and lock
                    return boundPages.containsKey(k) ? boundPages.get(k) : allocateAndBindPage(k);
                } finally {
                    bindLock.unlock();
                }
            });
        }
    }

    void validatePagesCommitted(long offset, long length) {
        long startPage = offset / pageSize;
        long endPage = (offset + length - 1) / pageSize;
        for (long page = startPage; page <= endPage; page++) {
            if (!boundPages.containsKey(page * pageSize))
                throw new IllegalStateException("Attempting to access uncommitted sparse page at offset " + (page * pageSize));
        }
    }

    /**
     * Increments map depth for a page, mapping it on first request.
     * Must be paired with {@link #unmapPage(long)}.
     * Synchronized per page index — different pages don't block each other.
     */
    MemorySegment mapPage(long pageIndex) {
        synchronized (mapDepth) {
            long pageOffset = pageIndex * pageSize;
            MemorySegment pageMemory = boundPages.get(pageOffset);
            if (pageMemory == null) throw new IllegalStateException("Page at index " + pageIndex + " is not bound");

            if (mapDepth[(int) pageIndex] == 0) {
                try (Arena tmp = Arena.ofConfined()) {
                    MemorySegment mappedPtr = tmp.allocate(ValueLayout.ADDRESS);
                    int result = vkMapMemory(device.handle(), pageMemory, 0, pageSize, 0, mappedPtr);
                    if (result != 0) throw new RuntimeException("Failed to map page memory: " + result);
                    mappedPages.put(pageIndex, mappedPtr.get(ValueLayout.ADDRESS, 0).reinterpret(pageSize, arena, null));
                }
            }
            mapDepth[(int) pageIndex]++;
            return mappedPages.get(pageIndex);
        }
    }

    /** Decrements map depth, unmapping when it reaches zero. */
    void unmapPage(long pageIndex) {
        synchronized (mapDepth) {
            if (mapDepth[(int) pageIndex] <= 0) return;
            mapDepth[(int) pageIndex]--;
            if (mapDepth[(int) pageIndex] == 0) {
                MemorySegment pageMemory = boundPages.get(pageIndex * pageSize);
                if (pageMemory != null) vkUnmapMemory(device.handle(), pageMemory);
                mappedPages.remove(pageIndex);
            }
        }
    }

    /** @return the VkDeviceMemory handle for the page at the given byte offset */
    MemorySegment pageMemoryAt(long pageOffset) {
        return boundPages.get(pageOffset);
    }

    /** Allocates a VkDeviceMemory block and issues the sparse bind. Called with bindLock held. */
    private MemorySegment allocateAndBindPage(long offset) {
        MemorySegment memory = freeMemoryPool.poll();
        if (memory == null) memory = allocatePageMemory();

        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment bind = VkSparseMemoryBind.builder()
                .resourceOffset(offset).size(pageSize).memory(memory).memoryOffset(0).flags(0).build(tmp);
            MemorySegment bufferBind = VkSparseBufferMemoryBindInfo.builder()
                .buffer(bufferHandle).binds(bind).build(tmp);
            MemorySegment bindInfo = VkBindSparseInfo.builder().bufferBinds(bufferBind).build(tmp);

            VkFence fence = VkFence.builder().device(device).build(tmp);
            int result = vkQueueBindSparse(sparseQueue.handle(), 1, bindInfo, fence.handle());
            if (result != 0) throw new RuntimeException("Failed to bind sparse memory: " + result);
            VkFenceOps.waitFor(device).fence(fence.handle()).execute(tmp).check();
            fence.close();
        }
        return memory;
    }

    /** Unbinds a page and returns its memory to the pool. Called with bindLock held. */
    private void unbindPage(long offset) {
        MemorySegment memory = boundPages.remove(offset);
        if (memory == null) return;

        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment bind = VkSparseMemoryBind.builder()
                .resourceOffset(offset).size(pageSize).memory(MemorySegment.NULL).memoryOffset(0).flags(0).build(tmp);
            MemorySegment bufferBind = VkSparseBufferMemoryBindInfo.builder()
                .buffer(bufferHandle).binds(bind).build(tmp);
            MemorySegment bindInfo = VkBindSparseInfo.builder().bufferBinds(bufferBind).build(tmp);

            VkFence fence = VkFence.builder().device(device).build(tmp);
            vkQueueBindSparse(sparseQueue.handle(), 1, bindInfo, fence.handle());
            VkFenceOps.waitFor(device).fence(fence.handle()).execute(tmp).check();
            fence.close();
        }
        freeMemoryPool.add(memory);
    }

    private MemorySegment allocatePageMemory() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment req = io.github.yetyman.vulkan.generated.VkMemoryRequirements.allocate(tmp);
            io.github.yetyman.vulkan.generated.VulkanFFM.vkGetBufferMemoryRequirements(device.handle(), bufferHandle, req);
            int memTypeBits = io.github.yetyman.vulkan.generated.VkMemoryRequirements.memoryTypeBits(req);

            MemorySegment memProps = VkPhysicalDeviceMemoryProperties.allocate(tmp);
            vkGetPhysicalDeviceMemoryProperties(device.physicalDevice().handle(), memProps);
            int memoryTypeIndex = findMemoryType(memProps, memTypeBits);

            MemorySegment allocInfo = VkMemoryAllocateInfo.allocate(arena);
            VkMemoryAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO.value());
            VkMemoryAllocateInfo.pNext(allocInfo, MemorySegment.NULL);
            VkMemoryAllocateInfo.allocationSize(allocInfo, pageSize);
            VkMemoryAllocateInfo.memoryTypeIndex(allocInfo, memoryTypeIndex);

            MemorySegment memoryPtr = arena.allocate(ValueLayout.ADDRESS);
            int result = vkAllocateMemory(device.handle(), allocInfo, MemorySegment.NULL, memoryPtr);
            if (result != 0) throw new RuntimeException("Failed to allocate page memory: " + result);
            return memoryPtr.get(ValueLayout.ADDRESS, 0);
        }
    }

    private int findMemoryType(MemorySegment memProps, int typeBits) {
        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(memProps);
        for (int i = 0; i < typeCount; i++) {
            if ((typeBits & (1 << i)) == 0) continue;
            MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(memProps, i);
            if ((VkMemoryType.propertyFlags(memType) & memoryProperties) == memoryProperties) return i;
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }

    @Override
    public void close() {
        bindLock.lock();
        try {
            for (long offset : boundPages.keySet().toArray(new Long[0])) unbindPage(offset);
            for (MemorySegment memory : freeMemoryPool) vkFreeMemory(device.handle(), memory, MemorySegment.NULL);
        } finally {
            bindLock.unlock();
            arena.close();
        }
    }
}
