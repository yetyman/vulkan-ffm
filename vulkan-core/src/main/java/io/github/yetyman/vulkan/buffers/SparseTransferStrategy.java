package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkCommandBufferAlloc;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkFence;
import io.github.yetyman.vulkan.VkFenceOps;
import io.github.yetyman.vulkan.VkMappedMemoryRange;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkSubmit;
import io.github.yetyman.vulkan.command.VkCopy;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFlushMappedMemoryRanges;

/**
 * Transfer strategy for buffers allocated with {@link SparseAllocationStrategy}.
 * Automatically commits pages covering the accessed range before every write/read.
 * Host-visible pages are memcpy'd directly (page by page); device-local pages go through
 * a transient staging + copy pipeline identical in shape to {@link StagingTransferStrategy}.
 */
public final class SparseTransferStrategy implements TransferStrategy {
    private final VkQueue transferQueue;

    public SparseTransferStrategy(VkQueue transferQueue) {
        this.transferQueue = transferQueue;
    }

    @Override
    public GpuCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue) {
        int length = data.remaining();
        BufferWriteScope scope = acquireWrite(ctx, offset, length, queue);
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, scope.segment(), 0, length);
        scope.close();
        return scope.completion();
    }

    @Override
    public BufferWriteScope acquireWrite(TransferContext ctx, long offset, long size, VkQueue queue) {
        SparsePageAllocator pages = ctx.sparsePages;
        pages.ensurePagesCommitted(offset, size);

        if (!pages.isHostVisible) {
            // Device-local backing: stage exactly size bytes and copy on commit.
            Arena stagingArena = Arena.ofShared();
            VkBuffer tempHost = VkBuffer.builder()
                    .device(ctx.device).size(size).transferSrc().hostVisible().build(stagingArena);
            MemorySegment mapped = tempHost.map(stagingArena);
            TransferBatch batch = TransferBatchManager.getOrCreate(ctx.device, queue);
            return BufferWriteScope.of(mapped.asSlice(0, size), offset, size,
                    () -> batch.record(tempHost.handle(), ctx.vkBuffer.handle(), 0, offset, size, tempHost, stagingArena));
        }

        long startPage = offset / pages.pageSize;
        long endPage = (offset + size - 1) / pages.pageSize;
        if (startPage == endPage) {
            // Entirely within one page: hand back that page's mapped memory directly, no copy.
            MemorySegment mapped = pages.mapPage(startPage);
            long inPageOffset = offset - startPage * pages.pageSize;
            return BufferWriteScope.of(mapped.asSlice(inPageOffset, size), offset, size, () -> {
                if (!pages.isHostCoherent) {
                    MemorySegment pageMemory = pages.pageMemoryAt(startPage * pages.pageSize);
                    MemorySegment range = VkMappedMemoryRange.allocate(ctx.arena, pageMemory, inPageOffset, size);
                    vkFlushMappedMemoryRanges(ctx.device.handle(), 1, range);
                }
                pages.unmapPage(startPage);
                return GpuCompletion.completed();
            });
        }

        // Spans multiple pages, which are separately mapped and therefore not contiguous in the
        // host address space. Gather into one temporary segment, scatter into pages on commit.
        Arena scratchArena = Arena.ofShared();
        MemorySegment scratch = scratchArena.allocate(size);
        return BufferWriteScope.of(scratch, offset, size, () -> {
            try {
                writeHostVisibleSegment(ctx, pages, scratch, offset, size);
            } finally {
                scratchArena.close();
            }
            return GpuCompletion.completed();
        });
    }

    @Override
    public ByteBuffer read(TransferContext ctx, long offset, long length) {
        SparsePageAllocator pages = ctx.sparsePages;
        pages.validatePagesCommitted(offset, length);

        if (pages.isHostVisible) {
            return readHostVisible(pages, offset, length);
        }

        System.err.println("WARNING: Synchronous read from device-local sparse buffer requires staging and will stall the pipeline.");
        VkCommandPool commandPool = ctx.device.getOrCreateCommandPool(transferQueue.familyIndex());
        Arena readArena = Arena.ofShared();
        VkBuffer readback = null;
        VkFence fence = null;
        try {
            readback = VkBuffer.builder().device(ctx.device).size(length).transferDst().hostVisible().build(readArena);
            fence = VkFence.builder().device(ctx.device).build(readArena);
            VkCommandBuffer[] cmds = VkCommandBufferAlloc.builder()
                    .device(ctx.device).commandPool(commandPool.handle()).primary().count(1).allocate(readArena);
            VkCommandBuffer cmd = cmds[0];
            VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(readArena);
            VkCopy.copyBuffer(cmd, ctx.vkBuffer, readback, offset, 0, length);
            vkEndCommandBuffer(cmd.handle());
            VkSubmit.builder().commandBuffer(cmd).submit(transferQueue.handle(), fence.handle(), readArena).check();
            try (Arena waitArena = Arena.ofConfined()) {
                VkFenceOps.wait(ctx.device, fence, Long.MAX_VALUE, waitArena).check();
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

    @Override
    public void flush(TransferContext ctx) {
        SparsePageAllocator pages = ctx.sparsePages;
        if (!pages.isHostVisible || pages.isHostCoherent) return;
        for (long pageOffset = 0; pageOffset < ctx.size; pageOffset += pages.pageSize) {
            MemorySegment pageMemory = pages.pageMemoryAt(pageOffset);
            if (pageMemory != null) {
                MemorySegment range = VkMappedMemoryRange.allocate(ctx.arena, pageMemory, 0, pages.pageSize);
                vkFlushMappedMemoryRanges(ctx.device.handle(), 1, range);
            }
        }
    }

    private void writeHostVisibleSegment(TransferContext ctx, SparsePageAllocator pages,
                                         MemorySegment src, long offset, long length) {
        long startPage = offset / pages.pageSize;
        long endPage = (offset + length - 1) / pages.pageSize;

        for (long pageIndex = startPage; pageIndex <= endPage; pageIndex++) {
            MemorySegment mapped = pages.mapPage(pageIndex);
            long pageStart = pageIndex * pages.pageSize;
            long writeStart = Math.max(offset, pageStart);
            long writeEnd = Math.min(offset + length, pageStart + pages.pageSize);
            long writeLen = writeEnd - writeStart;
            long inPageOffset = writeStart - pageStart;
            long srcOffset = writeStart - offset;

            MemorySegment.copy(src, srcOffset, mapped, inPageOffset, writeLen);

            if (!pages.isHostCoherent) {
                MemorySegment pageMemory = pages.pageMemoryAt(pageStart);
                MemorySegment range = VkMappedMemoryRange.allocate(ctx.arena, pageMemory, inPageOffset, writeLen);
                vkFlushMappedMemoryRanges(ctx.device.handle(), 1, range);
            }
            pages.unmapPage(pageIndex);
        }
    }

    private ByteBuffer readHostVisible(SparsePageAllocator pages, long offset, long length) {
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
}
