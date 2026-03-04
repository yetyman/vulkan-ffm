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
    private final boolean persistentStaging;

    private VkBuffer stagingBuffer;
    private MemorySegment mappedMemory;

    public DeviceLocalBuffer(VkDevice device, long size, BufferUsage usage,
                             VkQueue transferQueue, boolean persistentStaging) {
        super(device, size, usage, persistentStaging ? MemoryStrategy.STAGING : MemoryStrategy.DEVICE_LOCAL);
        if (transferQueue == null) {
            throw new IllegalArgumentException("transferQueue required");
        }
        this.transferQueue = transferQueue;
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
    public TransferCompletion writeAsync(ByteBuffer data, long offset, VkQueue queue) {
        TransferBatch batch = TransferBatchManager.getOrCreate(device, queue);
        if (persistentStaging) {
            MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mappedMemory, offset, data.remaining());
            return batch.record(stagingBuffer.handle(), handle(), offset, offset, data.remaining());
        } else {
            Arena stagingArena = Arena.ofShared();
            VkBuffer tempStaging = VkBuffer.builder().device(device).size(data.remaining()).transferSrc().hostVisible().build(stagingArena);
            MemorySegment tempMapped = tempStaging.map(stagingArena);
            MemorySegment.copy(MemorySegment.ofBuffer(data), 0, tempMapped, 0, data.remaining());
            return batch.record(tempStaging.handle(), handle(), 0, offset, data.remaining(), tempStaging, stagingArena);
        }
    }

    @Override
    public ByteBuffer read(long offset, long readSize) {
        System.err.println("WARNING: Synchronous read from device-local buffer stalls the pipeline.");
        VkCommandPool commandPool = device.getOrCreateCommandPool(transferQueue.familyIndex());
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
