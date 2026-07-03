package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkCommandBufferAlloc;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkFence;
import io.github.yetyman.vulkan.VkFenceOps;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkSubmit;
import io.github.yetyman.vulkan.command.VkCopy;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;

/**
 * Writes via a staging buffer + {@code vkCmdCopyBuffer} into device-local memory.
 * When {@code persistent} is true, one persistently-mapped staging buffer is allocated
 * up front and reused for every write (no allocation per write, but writes are serialized
 * against reuse of the same staging region by the caller). When false, a transient staging
 * buffer is allocated per write and owned by the {@link TransferBatch} until the copy completes.
 *
 * <p>Reads always stall the pipeline — a transient readback buffer is created, the copy is
 * submitted and waited on synchronously. Prefer a CPU mirror ({@link MirroredBuffer}) or async
 * writes for hot paths.
 */
public final class StagingTransferStrategy implements TransferStrategy {
    private final boolean persistent;
    private final VkQueue transferQueue;

    private VkBuffer persistentStagingBuffer;
    private MemorySegment persistentMapped;

    public StagingTransferStrategy(boolean persistent, VkQueue transferQueue) {
        if (transferQueue == null) throw new IllegalArgumentException("transferQueue required");
        this.persistent = persistent;
        this.transferQueue = transferQueue;
    }

    /**
     * Lazily allocates the persistent staging buffer on first use, once the owning
     * {@link ManagedBuffer}'s arena is available.
     */
    private void ensurePersistentStaging(TransferContext ctx) {
        if (persistent && persistentStagingBuffer == null) {
            persistentStagingBuffer = VkBuffer.builder()
                    .device(ctx.device).size(ctx.size).transferSrc().hostVisible().build(ctx.arena);
            persistentMapped = persistentStagingBuffer.map(ctx.arena);
        }
    }

    @Override
    public TransferCompletion writeAsync(TransferContext ctx, ByteBuffer data, long offset, VkQueue queue) {
        TransferBatch batch = TransferBatchManager.getOrCreate(ctx.device, queue);
        if (persistent) {
            ensurePersistentStaging(ctx);
            MemorySegment.copy(MemorySegment.ofBuffer(data), 0, persistentMapped, offset, data.remaining());
            return batch.record(persistentStagingBuffer.handle(), ctx.vkBuffer.handle(), offset, offset, data.remaining());
        } else {
            Arena stagingArena = Arena.ofShared();
            VkBuffer tempStaging = VkBuffer.builder().device(ctx.device).size(data.remaining()).transferSrc().hostVisible().build(stagingArena);
            MemorySegment tempMapped = tempStaging.map(stagingArena);
            MemorySegment.copy(MemorySegment.ofBuffer(data), 0, tempMapped, 0, data.remaining());
            return batch.record(tempStaging.handle(), ctx.vkBuffer.handle(), 0, offset, data.remaining(), tempStaging, stagingArena);
        }
    }

    @Override
    public ByteBuffer read(TransferContext ctx, long offset, long length) {
        System.err.println("WARNING: Synchronous read from device-local buffer stalls the pipeline.");
        VkCommandPool commandPool = ctx.device.getOrCreateCommandPool(transferQueue.familyIndex());
        Arena readArena = Arena.ofShared();
        VkBuffer readbackBuf = null;
        VkFence fence = null;
        try {
            readbackBuf = VkBuffer.builder().device(ctx.device).size(length).transferDst().hostVisible().build(readArena);
            fence = VkFence.builder().device(ctx.device).build(readArena);

            VkCommandBuffer[] cmdBuffers = VkCommandBufferAlloc.builder()
                    .device(ctx.device).commandPool(commandPool.handle()).primary().count(1).allocate(readArena);
            VkCommandBuffer cmdBuffer = cmdBuffers[0];

            VkCommandBuffer.begin(cmdBuffer).oneTimeSubmit().execute(readArena);
            VkCopy.copyBuffer(cmdBuffer, ctx.vkBuffer, readbackBuf, offset, 0, length);
            vkEndCommandBuffer(cmdBuffer.handle());

            transferQueue.submit(
                    VkSubmit.builder().commandBuffer(cmdBuffer).build(fence.handle(), readArena), fence.handle());

            try (Arena waitArena = Arena.ofConfined()) {
                VkFenceOps.wait(ctx.device, fence, Long.MAX_VALUE, waitArena).check();
            }

            MemorySegment mapped = readbackBuf.map(readArena);
            ByteBuffer result = ByteBuffer.allocate((int) length);
            MemorySegment.copy(mapped, 0, MemorySegment.ofBuffer(result), 0, length);
            fence.close();
            readbackBuf.close();
            return result.rewind();
        } catch (Exception e) {
            if (fence != null) fence.close();
            if (readbackBuf != null) readbackBuf.close();
            throw e;
        } finally {
            readArena.close();
        }
    }

    @Override
    public void flush(TransferContext ctx) {
        // no-op — staged writes flush via TransferBatchManager, not here
    }

    @Override
    public void close(TransferContext ctx) {
        if (persistentStagingBuffer != null) {
            persistentStagingBuffer.close();
            persistentStagingBuffer = null;
        }
    }
}
