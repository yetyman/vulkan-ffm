package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkBufferCreateFlagBits.VK_BUFFER_CREATE_SPARSE_BINDING_BIT;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdCopyBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFlushMappedMemoryRanges;

/**
 * Sparse buffer with dynamic page-level memory binding.
 * Allows huge virtual address space with on-demand physical memory allocation.
 * Page management is handled by {@link SparsePageAllocator}.
 * Data transfers delegate to {@link DeviceLocalBuffer} (device-local) or direct page mapping (host-visible).
 */
public class SparseBuffer extends AbstractBuffer {
    private final SparsePageAllocator pages;
    private final VkQueue transferQueue;

    public SparseBuffer(VkDevice device,
                        long size, BufferUsage usage, MemoryStrategy underlyingStrategy,
                        VkQueue sparseQueue, VkQueue transferQueue) {
        super(device, size, usage, MemoryStrategy.SPARSE);
        this.transferQueue = transferQueue;

        if (underlyingStrategy == MemoryStrategy.SPARSE)
            throw new IllegalArgumentException("Cannot nest sparse buffers");

        int memoryProperties = switch (underlyingStrategy) {
            case MAPPED, MAPPED_CACHED -> VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value() | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value();
            case DEVICE_LOCAL          -> VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value();
            default -> throw new IllegalArgumentException("Unsupported underlying strategy for sparse buffer: " + underlyingStrategy);
        };

        SparsePageAllocator pagesLocal = null;
        try {
            checkSparseSupport();
            createSparseBuffer();
            long pageSize = querySparsePageSize();
            pagesLocal = new SparsePageAllocator(device, vkBuffer.handle(), size, pageSize, memoryProperties, sparseQueue);
        } catch (Exception e) {
            if (pagesLocal != null) pagesLocal.close();
            if (vkBuffer != null) vkBuffer.close();
            arena.close();
            throw e;
        }
        this.pages = pagesLocal;
    }

    /** @return the sparse page size in bytes */
    public long pageSize() { return pages.pageSize; }

    private void checkSparseSupport() {
        if (!device.physicalDevice().supportsSparseResidencyBuffer())
            throw new UnsupportedOperationException("Device does not support sparse binding");
    }

    private void createSparseBuffer() {
        vkBuffer = VkBuffer.builder()
            .device(device).size(size).usage(usage.toVkFlags())
            .flags(VK_BUFFER_CREATE_SPARSE_BINDING_BIT.value())
            .build(arena);
    }

    private long querySparsePageSize() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment req = io.github.yetyman.vulkan.generated.VkMemoryRequirements.allocate(tmp);
            device.getBufferMemoryRequirements(vkBuffer.handle(), req);
            return io.github.yetyman.vulkan.generated.VkMemoryRequirements.alignment(req);
        }
    }

    /**
     * Writes data, automatically committing pages as needed.
     * Host-visible: maps each page individually and copies directly.
     * Device-local: delegates to a transient staging pipeline.
     */
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        pages.ensurePagesCommitted(offset, data.remaining());

        if (pages.isHostVisible) {
            writeHostVisible(data, offset);
            return TransferCompletion.completed();
        } else {
            VkCommandPool commandPool = device.getOrCreateCommandPool(queue.familyIndex());
            Arena transferArena = Arena.ofShared();
            VkBuffer tempHost = null;
            VkFence fence = null;
            try {
                tempHost = VkBuffer.builder().device(device).size(data.remaining()).transferSrc().hostVisible().build(transferArena);
                MemorySegment mapped = tempHost.map(transferArena);
                MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mapped, 0, data.remaining());

                fence = VkFence.builder().device(device).build(transferArena);
                VkCommandBuffer[] cmds = VkCommandBufferAlloc.builder()
                    .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(transferArena);
                VkCommandBuffer cmd = cmds[0];
                VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(transferArena);
                MemorySegment copyRegion = VkBufferCopy.allocate(transferArena, 0, offset, data.remaining());
                vkCmdCopyBuffer(cmd.handle(), tempHost.handle(), vkBuffer.handle(), 1, copyRegion);
                vkEndCommandBuffer(cmd.handle());
                VkSubmit.builder().commandBuffer(cmd).submit(queue.handle(), fence.handle(), transferArena).check();

                return new TransferCompletion(device, fence, transferArena, tempHost);
            } catch (Exception e) {
                if (fence != null) fence.close();
                if (tempHost != null) tempHost.close();
                transferArena.close();
                throw e;
            }
        }
    }

    /**
     * Reads data. Throws if pages are not committed.
     * Host-visible: maps each page and copies directly.
     * Device-local: uses a transient readback buffer (stalls pipeline).
     */
    @Override
    public ByteBuffer read(long offset, long length) {
        pages.validatePagesCommitted(offset, length);

        if (pages.isHostVisible) {
            return readHostVisible(offset, length);
        } else {
            System.err.println("WARNING: Synchronous read from device-local sparse buffer requires staging and will stall the pipeline.");
            VkCommandPool commandPool = device.getOrCreateCommandPool(transferQueue.familyIndex());
            Arena readArena = Arena.ofShared();
            VkBuffer readback = null;
            VkFence fence = null;
            try {
                readback = VkBuffer.builder().device(device).size(length).transferDst().hostVisible().build(readArena);
                fence = VkFence.builder().device(device).build(readArena);
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
            } catch (Exception e) {
                if (fence != null) fence.close();
                if (readback != null) readback.close();
                throw e;
            } finally {
                readArena.close();
            }
        }
    }

    private void writeHostVisible(ByteBuffer data, long offset) {
        long startPage = offset / pages.pageSize;
        long endPage = (offset + data.remaining() - 1) / pages.pageSize;
        int dataPos = data.position();

        for (long pageIndex = startPage; pageIndex <= endPage; pageIndex++) {
            MemorySegment mapped = pages.mapPage(pageIndex);
            long pageStart = pageIndex * pages.pageSize;
            long writeStart = Math.max(offset, pageStart);
            long writeEnd = Math.min(offset + data.remaining(), pageStart + pages.pageSize);
            long writeLen = writeEnd - writeStart;
            long inPageOffset = writeStart - pageStart;
            int dataOffset = (int) (writeStart - offset) + dataPos;

            MemorySegment.copy(MemorySegment.ofBuffer(data.slice(dataOffset, (int) writeLen)), 0, mapped, inPageOffset, writeLen);

            if (!pages.isHostCoherent) {
                MemorySegment pageMemory = pages.pageMemoryAt(pageStart);
                MemorySegment range = VkMappedMemoryRange.allocate(arena, pageMemory, inPageOffset, writeLen);
                vkFlushMappedMemoryRanges(device.handle(), 1, range);
            }
            pages.unmapPage(pageIndex);
        }
    }

    private ByteBuffer readHostVisible(long offset, long length) {
        ByteBuffer result = ByteBuffer.allocate((int) length);
        long startPage = offset / pages.pageSize;
        long endPage = (offset + length - 1) / pages.pageSize;

        for (long pageIndex = startPage; pageIndex <= endPage; pageIndex++) {
            MemorySegment mapped = pages.mapPage(pageIndex);
            long pageStart = pageIndex * pages.pageSize;
            long readStart = Math.max(offset, pageStart);
            long readEnd = Math.min(offset + length, pageStart + pages.pageSize);
            long readLen = readEnd - readStart;
            long inPageOffset = readStart - pageStart;
            int resultOffset = (int) (readStart - offset);

            MemorySegment.copy(mapped, inPageOffset, MemorySegment.ofBuffer(result.slice(resultOffset, (int) readLen)), 0, readLen);
            pages.unmapPage(pageIndex);
        }
        return result.rewind();
    }

    /**
     * Flushes non-coherent host-visible pages. No-op for coherent or device-local memory.
     */
    @Override
    public void flush() {
        if (!pages.isHostVisible || pages.isHostCoherent) return;
        for (long pageOffset = 0; pageOffset < size; pageOffset += pages.pageSize) {
            MemorySegment pageMemory = pages.pageMemoryAt(pageOffset);
            if (pageMemory != null) {
                MemorySegment range = VkMappedMemoryRange.allocate(arena, pageMemory, 0, pages.pageSize);
                vkFlushMappedMemoryRanges(device.handle(), 1, range);
            }
        }
    }

    @Override
    public void closeImpl() {
        pages.close();
        super.closeImpl();
    }
}
