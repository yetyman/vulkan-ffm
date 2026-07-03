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
    public TransferCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue) {
        SparsePageAllocator pages = ctx.sparsePages;
        pages.ensurePagesCommitted(offset, data.remaining());

        if (pages.isHostVisible) {
            writeHostVisible(ctx, pages, data, offset);
            return TransferCompletion.completed();
        } else {
            Arena stagingArena = Arena.ofShared();
            VkBuffer tempHost = VkBuffer.builder().device(ctx.device).size(data.remaining()).transferSrc().hostVisible().build(stagingArena);
            MemorySegment mapped = tempHost.map(stagingArena);
            MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mapped, 0, data.remaining());
            TransferBatch batch = TransferBatchManager.getOrCreate(ctx.device, queue);
            return batch.record(tempHost.handle(), ctx.vkBuffer.handle(), 0, offset, data.remaining(), tempHost, stagingArena);
        }
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

    private void writeHostVisible(TransferContext ctx, SparsePageAllocator pages, ByteBuffer data, long offset) {
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
