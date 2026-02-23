package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdCopyBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;

public class DeviceLocalBuffer extends AbstractBuffer {
    private final VkQueue transferQueue;
    private final VkCommandPool commandPool;
    private final boolean persistentStaging;

    private VkBuffer stagingBuffer;
    private MemorySegment mappedMemory;

    public DeviceLocalBuffer(VkDevice device, long size, BufferUsage usage,
                             VkQueue transferQueue, VkCommandPool commandPool, boolean persistentStaging) {
        super(device, size, usage, persistentStaging ? MemoryStrategy.STAGING : MemoryStrategy.DEVICE_LOCAL);
        if (transferQueue == null || commandPool == null) {
            throw new IllegalArgumentException("transferQueue and commandPool required");
        }
        this.transferQueue = transferQueue;
        this.commandPool = commandPool;
        this.persistentStaging = persistentStaging;

        try {
            createBuffer(usage.toVkFlags() | VK_BUFFER_USAGE_TRANSFER_DST_BIT.value(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value());

            if (persistentStaging) {
                stagingBuffer = VkBuffer.builder().device(device).size(size).transferSrc().hostVisible().build(arena);
                mappedMemory = stagingBuffer.map(arena);
            }
        } catch (Exception e) {
            if (stagingBuffer != null) stagingBuffer.close();
            if (vkBuffer != null) vkBuffer.close();
            arena.close();
            throw e;
        }
    }

    @Override
    public void write(ByteBuffer data, long offset) {
        TransferCompletion tc = writeAsync(data, offset);
        tc.await();
        tc.close();
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        Arena transferArena = Arena.ofShared();
        VkFence fence = null;
        try {
            fence = VkFence.builder().device(device).build(transferArena);

            if (persistentStaging) {
                MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mappedMemory, offset, data.remaining());
                copyToDevice(stagingBuffer, offset, offset, data.remaining(), fence, transferArena);
                return new TransferCompletion(device, fence, transferArena);
            } else {
                VkBuffer tempStaging = VkBuffer.builder().device(device).size(data.remaining()).transferSrc().hostVisible().build(transferArena);
                MemorySegment tempMapped = tempStaging.map(transferArena);
                MemorySegment.copy(MemorySegment.ofBuffer(data), 0, tempMapped, 0, data.remaining());
                copyToDevice(tempStaging, 0, offset, data.remaining(), fence, transferArena);
                return new TransferCompletion(device, fence, transferArena, tempStaging);
            }
        } catch (Exception e) {
            if (fence != null) fence.close();
            transferArena.close();
            throw e;
        }
    }

    private void copyToDevice(VkBuffer srcBuffer, long srcOffset, long dstOffset, long copySize, VkFence fence, Arena transferArena) {
        VkCommandBuffer[] cmdBuffers = VkCommandBufferAlloc.builder()
            .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(transferArena);
        VkCommandBuffer cmdBuffer = cmdBuffers[0];

        VkCommandBuffer.begin(cmdBuffer).oneTimeSubmit().execute(transferArena);
        MemorySegment copyRegion = VkBufferCopy.allocate(transferArena, srcOffset, dstOffset, copySize);
        vkCmdCopyBuffer(cmdBuffer.handle(), srcBuffer.handle(), handle(), 1, copyRegion);
        vkEndCommandBuffer(cmdBuffer.handle());

        VkSubmit.builder().commandBuffer(cmdBuffer)
            .submit(transferQueue.handle(), fence.handle(), transferArena).check();
    }

    @Override
    public ByteBuffer read(long offset, long readSize) {
        System.err.println("WARNING: Synchronous read from device-local buffer requires staging buffer and GPU->CPU transfer. "
                         + "This is extremely slow and will stall the pipeline. Consider using MAPPED/MAPPED_CACHED strategy for frequent reads.");

        Arena readArena = Arena.ofShared();
        VkBuffer readbackBuf = null;
        VkFence fence = null;
        try {
            readbackBuf = VkBuffer.builder().device(device).size(readSize).transferDst().hostVisible().build(readArena);
            fence = VkFence.builder().device(device).build(readArena);

            VkCommandBuffer[] cmdBuffers = VkCommandBufferAlloc.builder()
                .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(readArena);
            VkCommandBuffer cmdBuffer = cmdBuffers[0];

            VkCommandBuffer.begin(cmdBuffer).oneTimeSubmit().execute(readArena);
            MemorySegment copyRegion = VkBufferCopy.allocate(readArena, offset, 0, readSize);
            vkCmdCopyBuffer(cmdBuffer.handle(), handle(), readbackBuf.handle(), 1, copyRegion);
            vkEndCommandBuffer(cmdBuffer.handle());

            VkSubmit.builder().commandBuffer(cmdBuffer)
                .submit(transferQueue.handle(), fence.handle(), readArena).check();

            try (Arena waitArena = Arena.ofConfined()) {
                VkFenceOps.wait(device, fence, Long.MAX_VALUE, waitArena).check();
            }

            MemorySegment mapped = readbackBuf.map(readArena);
            ByteBuffer result = ByteBuffer.allocate((int) readSize);
            MemorySegment.copy(mapped, 0, MemorySegment.ofBuffer(result), 0, readSize);
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
    public void flush() {}

    @Override
    public void closeImpl() {
        if (stagingBuffer != null) stagingBuffer.close();
        super.closeImpl();
    }
}
