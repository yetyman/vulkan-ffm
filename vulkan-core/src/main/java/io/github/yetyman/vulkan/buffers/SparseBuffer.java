package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkMemoryAllocateInfo;
import io.github.yetyman.vulkan.generated.VkMemoryType;
import io.github.yetyman.vulkan.generated.VkPhysicalDeviceMemoryProperties;
import static io.github.yetyman.vulkan.enums.VkBufferCreateFlagBits.VK_BUFFER_CREATE_SPARSE_BINDING_BIT;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkAllocateMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdCopyBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFlushMappedMemoryRanges;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFreeMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceFeatures;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceMemoryProperties;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkMapMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkQueueBindSparse;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkUnmapMemory;

/**
 * Sparse buffer with dynamic page-level memory binding.
 * Allows huge virtual address space with on-demand physical memory allocation.
 */
public class SparseBuffer extends AbstractBuffer {
    private final long pageSize;
    private final VkQueue sparseQueue;
    private final VkCommandPool commandPool;
    private final VkQueue transferQueue;
    private final Map<Long, MemorySegment> boundPages = new HashMap<>();
    private final Queue<MemorySegment> freeMemoryPool = new ArrayDeque<>();
    private final int memoryProperties;
    private final boolean isHostVisible;
    private final boolean isHostCoherent;

    /**
     * Per-page map depth counter. Index is pageIndex = offset / pageSize.
     * A value > 0 means the page is currently mapped by an external caller and must not be unmapped.
     * Sized lazily after pageSize is known.
     */
    private int[] mapDepth;

    public SparseBuffer(VkDevice device, Arena arena,
                       long size, BufferUsage usage, MemoryStrategy underlyingStrategy,
                       VkQueue sparseQueue, VkQueue transferQueue, VkCommandPool commandPool) {
        super(device, arena, size, usage, MemoryStrategy.SPARSE);
        this.sparseQueue = sparseQueue;
        this.transferQueue = transferQueue;
        this.commandPool = commandPool;

        if (underlyingStrategy == MemoryStrategy.SPARSE) {
            throw new IllegalArgumentException("Cannot nest sparse buffers");
        }

        this.memoryProperties = switch (underlyingStrategy) {
            case MAPPED, MAPPED_CACHED -> VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value() | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value();
            case DEVICE_LOCAL -> VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value();
            default -> throw new IllegalArgumentException("Unsupported underlying strategy for sparse buffer: " + underlyingStrategy);
        };

        this.isHostVisible = (memoryProperties & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value()) != 0;
        this.isHostCoherent = (memoryProperties & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value()) != 0;

        checkSparseSupport();
        createSparseBuffer();
        this.pageSize = querySparsePageSize();
        // pageCount = ceil(size / pageSize)
        this.mapDepth = new int[(int) ((size + pageSize - 1) / pageSize)];
    }

    private void checkSparseSupport() {
        MemorySegment features = arena.allocate(220);
        vkGetPhysicalDeviceFeatures(device.physicalDevice().handle(), features);
        int sparseBinding = features.get(ValueLayout.JAVA_INT, 0);
        if (sparseBinding == 0) {
            throw new UnsupportedOperationException("Device does not support sparse binding");
        }
    }

    private void createSparseBuffer() {
        vkBuffer = VkBuffer.builder()
            .device(device)
            .size(size)
            .usage(usage.toVkFlags())
            .flags(VK_BUFFER_CREATE_SPARSE_BINDING_BIT.value())
            .build(arena);
    }

    private long querySparsePageSize() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment req = io.github.yetyman.vulkan.generated.VkMemoryRequirements.allocate(tmp);
            io.github.yetyman.vulkan.generated.VulkanFFM.vkGetBufferMemoryRequirements(device.handle(), vkBuffer.handle(), req);
            return io.github.yetyman.vulkan.generated.VkMemoryRequirements.alignment(req);
        }
    }

    /** @return the page size for this sparse buffer */
    public long pageSize() { return pageSize; }

    private void bindPage(long offset) {
        if (offset % pageSize != 0) throw new IllegalArgumentException("Offset must be page-aligned");
        if (boundPages.containsKey(offset)) return;

        MemorySegment memory = freeMemoryPool.poll();
        if (memory == null) memory = allocatePageMemory();

        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment bind = VkSparseMemoryBind.builder()
                .resourceOffset(offset).size(pageSize).memory(memory).memoryOffset(0).flags(0).build(tmp);
            MemorySegment bufferBind = VkSparseBufferMemoryBindInfo.builder()
                .buffer(vkBuffer.handle()).binds(bind).build(tmp);
            MemorySegment bindInfo = VkBindSparseInfo.builder().bufferBinds(bufferBind).build(tmp);

            VkFence fence = VkFence.builder().device(device).build(tmp);
            int result = vkQueueBindSparse(sparseQueue.handle(), 1, bindInfo, fence.handle());
            if (result != 0) throw new RuntimeException("Failed to bind sparse memory: " + result);
            VkFenceOps.waitFor(device).fence(fence.handle()).execute(tmp).check();
            fence.close();
        }

        boundPages.put(offset, memory);
    }

    private void unbindPage(long offset) {
        MemorySegment memory = boundPages.remove(offset);
        if (memory == null) return;

        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment bind = VkSparseMemoryBind.builder()
                .resourceOffset(offset).size(pageSize).memory(MemorySegment.NULL).memoryOffset(0).flags(0).build(tmp);
            MemorySegment bufferBind = VkSparseBufferMemoryBindInfo.builder()
                .buffer(vkBuffer.handle()).binds(bind).build(tmp);
            MemorySegment bindInfo = VkBindSparseInfo.builder().bufferBinds(bufferBind).build(tmp);

            VkFence fence = VkFence.builder().device(device).build(tmp);
            vkQueueBindSparse(sparseQueue.handle(), 1, bindInfo, fence.handle());
            VkFenceOps.waitFor(device).fence(fence.handle()).execute(tmp).check();
            fence.close();
        }

        freeMemoryPool.add(memory);
    }

    private boolean isPageBound(long offset) { return boundPages.containsKey(offset); }

    private MemorySegment allocatePageMemory() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment req = io.github.yetyman.vulkan.generated.VkMemoryRequirements.allocate(tmp);
            io.github.yetyman.vulkan.generated.VulkanFFM.vkGetBufferMemoryRequirements(device.handle(), vkBuffer.handle(), req);
            int memTypeBits = io.github.yetyman.vulkan.generated.VkMemoryRequirements.memoryTypeBits(req);

            MemorySegment memProps = VkPhysicalDeviceMemoryProperties.allocate(tmp);
            vkGetPhysicalDeviceMemoryProperties(device.physicalDevice().handle(), memProps);
            int memoryTypeIndex = findMemoryType(memProps, memTypeBits, memoryProperties);

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

    /**
     * Increments the map depth for a page, mapping it if this is the first request.
     * Each call must be paired with a {@link #unmapPage(long)} call.
     * @return the mapped MemorySegment for this page
     */
    private MemorySegment mapPage(long pageIndex) {
        long pageOffset = pageIndex * pageSize;
        MemorySegment pageMemory = boundPages.get(pageOffset);
        if (pageMemory == null) throw new IllegalStateException("Page at index " + pageIndex + " is not bound");

        if (mapDepth[(int) pageIndex] == 0) {
            try (Arena tmp = Arena.ofConfined()) {
                MemorySegment mappedPtr = tmp.allocate(ValueLayout.ADDRESS);
                int result = vkMapMemory(device.handle(), pageMemory, 0, pageSize, 0, mappedPtr);
                if (result != 0) throw new RuntimeException("Failed to map page memory: " + result);
                // Store mapped pointer — reinterpret with arena so it stays valid
                MemorySegment mapped = mappedPtr.get(ValueLayout.ADDRESS, 0).reinterpret(pageSize, arena, null);
                // We need to store the mapped address per page; reuse boundPages isn't possible since it holds VkDeviceMemory.
                // Store in a parallel map.
                mappedPages.put(pageIndex, mapped);
            }
        }
        mapDepth[(int) pageIndex]++;
        return mappedPages.get(pageIndex);
    }

    /** Decrements map depth for a page, unmapping it when depth reaches zero. */
    private void unmapPage(long pageIndex) {
        if (mapDepth[(int) pageIndex] <= 0) return;
        mapDepth[(int) pageIndex]--;
        if (mapDepth[(int) pageIndex] == 0) {
            long pageOffset = pageIndex * pageSize;
            MemorySegment pageMemory = boundPages.get(pageOffset);
            if (pageMemory != null) vkUnmapMemory(device.handle(), pageMemory);
            mappedPages.remove(pageIndex);
        }
    }

    /** Parallel map to boundPages, holding the CPU-side mapped pointer per page index. */
    private final Map<Long, MemorySegment> mappedPages = new HashMap<>();

    private void ensurePagesCommitted(long offset, long length) {
        long startPage = offset / pageSize;
        long endPage = (offset + length - 1) / pageSize;
        for (long page = startPage; page <= endPage; page++) {
            if (!isPageBound(page * pageSize)) bindPage(page * pageSize);
        }
    }

    private void validatePagesCommitted(long offset, long length) {
        long startPage = offset / pageSize;
        long endPage = (offset + length - 1) / pageSize;
        for (long page = startPage; page <= endPage; page++) {
            if (!isPageBound(page * pageSize))
                throw new IllegalStateException("Attempting to read from uncommitted sparse buffer page at offset " + (page * pageSize));
        }
    }

    private int findMemoryType(MemorySegment memProps, int typeBits, int properties) {
        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(memProps);
        for (int i = 0; i < typeCount; i++) {
            if ((typeBits & (1 << i)) == 0) continue;
            MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(memProps, i);
            int props = VkMemoryType.propertyFlags(memType);
            if ((props & properties) == properties) return i;
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }

    /**
     * Writes data to the sparse buffer, automatically committing pages as needed.
     * For host-visible memory, each page is mapped, written, and unmapped individually.
     * For device-local memory, a host-visible staging buffer is used.
     */
    @Override
    public void write(ByteBuffer data, long offset) {
        ensurePagesCommitted(offset, data.remaining());

        if (isHostVisible) {
            long startPage = offset / pageSize;
            long endPage = (offset + data.remaining() - 1) / pageSize;
            int dataPos = data.position();

            for (long pageIndex = startPage; pageIndex <= endPage; pageIndex++) {
                MemorySegment mapped = mapPage(pageIndex);
                long pageStart = pageIndex * pageSize;
                long writeStart = Math.max(offset, pageStart);
                long writeEnd = Math.min(offset + data.remaining(), pageStart + pageSize);
                long writeLen = writeEnd - writeStart;
                long inPageOffset = writeStart - pageStart;
                int dataOffset = (int) (writeStart - offset) + dataPos;

                MemorySegment src = MemorySegment.ofBuffer(data.slice(dataOffset, (int) writeLen));
                MemorySegment.copy(src, 0, mapped, inPageOffset, writeLen);

                if (!isHostCoherent) {
                    MemorySegment pageMemory = boundPages.get(pageStart);
                    MemorySegment range = VkMappedMemoryRange.allocate(arena, pageMemory, inPageOffset, writeLen);
                    vkFlushMappedMemoryRanges(device.handle(), 1, range);
                }

                unmapPage(pageIndex);
            }
        } else {
            // Device-local: use a plain host-visible staging buffer (single hop)
            Arena stagingArena = Arena.ofShared();
            try {
                VkBuffer staging = VkBuffer.builder().device(device).size(data.remaining()).transferSrc().hostVisible().build(stagingArena);
                MemorySegment mapped = staging.map(stagingArena);
                MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mapped, 0, data.remaining());

                VkFence fence = VkFence.builder().device(device).build(stagingArena);
                VkCommandBuffer[] cmds = VkCommandBufferAlloc.builder()
                    .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(stagingArena);
                VkCommandBuffer cmd = cmds[0];
                VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(stagingArena);
                MemorySegment copyRegion = VkBufferCopy.allocate(stagingArena, 0, offset, data.remaining());
                vkCmdCopyBuffer(cmd.handle(), staging.handle(), vkBuffer.handle(), 1, copyRegion);
                vkEndCommandBuffer(cmd.handle());
                VkSubmit.builder().commandBuffer(cmd).submit(transferQueue.handle(), fence.handle(), stagingArena).check();
                try (Arena waitArena = Arena.ofConfined()) {
                    VkFenceOps.wait(device, fence, Long.MAX_VALUE, waitArena).check();
                }
                fence.close();
                staging.close();
            } finally {
                stagingArena.close();
            }
        }
    }

    /**
     * Writes data asynchronously, automatically committing pages as needed.
     * Host-visible writes complete immediately. Device-local writes return a TransferCompletion.
     */
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        ensurePagesCommitted(offset, data.remaining());

        if (isHostVisible) {
            write(data, offset);
            return TransferCompletion.completed();
        } else {
            Arena transferArena = Arena.ofShared();
            VkBuffer staging = VkBuffer.builder().device(device).size(data.remaining()).transferSrc().hostVisible().build(transferArena);
            MemorySegment mapped = staging.map(transferArena);
            MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mapped, 0, data.remaining());

            VkFence fence = VkFence.builder().device(device).build(transferArena);
            VkCommandBuffer[] cmds = VkCommandBufferAlloc.builder()
                .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(transferArena);
            VkCommandBuffer cmd = cmds[0];
            VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(transferArena);
            MemorySegment copyRegion = VkBufferCopy.allocate(transferArena, 0, offset, data.remaining());
            vkCmdCopyBuffer(cmd.handle(), staging.handle(), vkBuffer.handle(), 1, copyRegion);
            vkEndCommandBuffer(cmd.handle());
            VkSubmit.builder().commandBuffer(cmd).submit(transferQueue.handle(), fence.handle(), transferArena).check();

            // staging passed as ownedObject so vkDestroyBuffer is called explicitly on close()
            return new TransferCompletion(device, fence, transferArena, staging);
        }
    }

    /**
     * Reads from sparse buffer synchronously. Throws if pages are not committed.
     * Device-local reads require a staging buffer and will stall the pipeline.
     */
    @Override
    public ByteBuffer read(long offset, long length) {
        validatePagesCommitted(offset, length);

        if (isHostVisible) {
            ByteBuffer result = ByteBuffer.allocate((int) length);
            long startPage = offset / pageSize;
            long endPage = (offset + length - 1) / pageSize;

            for (long pageIndex = startPage; pageIndex <= endPage; pageIndex++) {
                MemorySegment mapped = mapPage(pageIndex);
                long pageStart = pageIndex * pageSize;
                long readStart = Math.max(offset, pageStart);
                long readEnd = Math.min(offset + length, pageStart + pageSize);
                long readLen = readEnd - readStart;
                long inPageOffset = readStart - pageStart;
                int resultOffset = (int) (readStart - offset);

                MemorySegment dst = MemorySegment.ofBuffer(result.slice(resultOffset, (int) readLen));
                MemorySegment.copy(mapped, inPageOffset, dst, 0, readLen);

                unmapPage(pageIndex);
            }

            return result.rewind();
        } else {
            System.err.println("WARNING: Synchronous read from device-local sparse buffer requires staging and will stall the pipeline.");
            Arena readArena = Arena.ofShared();
            try {
                VkBuffer readback = VkBuffer.builder().device(device).size(length).transferDst().hostVisible().build(readArena);
                VkFence fence = VkFence.builder().device(device).build(readArena);
                VkCommandBuffer[] cmds = VkCommandBufferAlloc.builder()
                    .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(readArena);
                VkCommandBuffer cmd = cmds[0];
                VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(readArena);
                MemorySegment copyRegion = VkBufferCopy.allocate(readArena, offset, 0, length);
                vkCmdCopyBuffer(cmd.handle(), vkBuffer.handle(), readback.handle(), 1, copyRegion);
                vkEndCommandBuffer(cmd.handle());
                VkSubmit.builder().commandBuffer(cmd).submit(transferQueue.handle(), fence.handle(), readArena).check();
                try (Arena waitArena = Arena.ofConfined()) {
                    VkFenceOps.wait(device, fence, Long.MAX_VALUE, waitArena).check();
                }
                MemorySegment mapped = readback.map(readArena);
                ByteBuffer result = ByteBuffer.allocate((int) length);
                MemorySegment.copy(mapped, 0, MemorySegment.ofBuffer(result), 0, length);
                fence.close();
                readback.close();
                return result.rewind();
            } finally {
                readArena.close();
            }
        }
    }

    /**
     * Flushes non-coherent host-visible pages. No-op for coherent or device-local memory.
     * For non-coherent host-visible sparse buffers, flushes all currently bound pages.
     */
    @Override
    public void flush() {
        if (!isHostVisible || isHostCoherent) return;
        for (Map.Entry<Long, MemorySegment> entry : boundPages.entrySet()) {
            MemorySegment range = VkMappedMemoryRange.allocate(arena, entry.getValue(), 0, pageSize);
            vkFlushMappedMemoryRanges(device.handle(), 1, range);
        }
    }

    @Override
    public void closeImpl() {
        for (long offset : boundPages.keySet().toArray(new Long[0])) {
            unbindPage(offset);
        }
        for (MemorySegment memory : freeMemoryPool) {
            vkFreeMemory(device.handle(), memory, MemorySegment.NULL);
        }
        super.closeImpl();
    }
}
