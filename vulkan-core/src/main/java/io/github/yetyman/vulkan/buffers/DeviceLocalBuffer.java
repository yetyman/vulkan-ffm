package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdCopyBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFreeCommandBuffers;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkQueueWaitIdle;

public class DeviceLocalBuffer extends AbstractBuffer {
    private final VkQueue transferQueue;
    private final VkCommandPool commandPool;
    private final boolean persistentStaging;
    
    private VkBuffer stagingBuffer;
    private MemorySegment mappedMemory;

    public DeviceLocalBuffer(VkDevice device, Arena arena, long size, BufferUsage usage, 
                                VkQueue transferQueue, VkCommandPool commandPool, boolean persistentStaging) {
        super(device, arena, size, usage, persistentStaging ? MemoryStrategy.STAGING : MemoryStrategy.DEVICE_LOCAL);
        if (transferQueue == null || commandPool == null) {
            throw new IllegalArgumentException("transferQueue and commandPool required");
        }
        this.transferQueue = transferQueue;
        this.commandPool = commandPool;
        this.persistentStaging = persistentStaging;

        createBuffer(usage.toVkFlags() | VK_BUFFER_USAGE_TRANSFER_DST_BIT.value(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value());
        
        if (persistentStaging) {
            stagingBuffer = VkBuffer.builder().device(device).size(size).transferSrc().hostVisible().build(arena);
            mappedMemory = stagingBuffer.map(arena);
        }
    }

    @Override
    public void write(ByteBuffer data, long offset) {
        if (persistentStaging) {
            writePersistent(data, offset, null);
        } else {
            writeEphemeral(data, offset, null);
        }
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        VkFence fence = VkFence.builder().device(device).build(arena);
        if (persistentStaging) {
            writePersistent(data, offset, fence);
            return new TransferCompletion(device, fence, arena);
        } else {
            return writeEphemeral(data, offset, fence);
        }
    }

    private void writePersistent(ByteBuffer data, long offset, VkFence fence) {
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, mappedMemory, offset, data.remaining());
        copyToDevice(stagingBuffer, offset, data.remaining(), fence);
    }

    private TransferCompletion writeEphemeral(ByteBuffer data, long offset, VkFence fence) {
        VkBuffer tempStaging = VkBuffer.builder().device(device).size(data.remaining()).transferSrc().hostVisible().build(arena);
        MemorySegment tempMapped = tempStaging.map(arena);
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, tempMapped, 0, data.remaining());
        
        copyToDevice(tempStaging, offset, data.remaining(), fence);
        
        if (fence == null) {
            tempStaging.close();
            return TransferCompletion.completed();
        } else {
            return new TransferCompletion(device, fence, arena) {
                @Override
                public void close() {
                    super.close();
                    tempStaging.close();
                }
            };
        }
    }

    private void copyToDevice(VkBuffer srcBuffer, long dstOffset, long copySize, VkFence fence) {
        Arena cmdArena = Arena.ofConfined();
        VkCommandBuffer[] cmdBuffers = VkCommandBufferAlloc.builder()
            .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(cmdArena);
        VkCommandBuffer cmdBuffer = cmdBuffers[0];

        VkCommandBuffer.begin(cmdBuffer).oneTimeSubmit().execute(cmdArena);
        MemorySegment copyRegion = VkBufferCopy.allocate(cmdArena, 0, dstOffset, copySize);
        vkCmdCopyBuffer(cmdBuffer.handle(), srcBuffer.handle(), handle(), 1, copyRegion);
        vkEndCommandBuffer(cmdBuffer.handle());

        VkSubmit.builder().commandBuffer(cmdBuffer)
            .submit(transferQueue.handle(), fence != null ? fence.handle() : MemorySegment.NULL, cmdArena).check();

        if (fence == null) {
            vkQueueWaitIdle(transferQueue.handle());
            freeCommandBuffer(cmdBuffer, cmdArena);
            cmdArena.close();
        } else {
            TransferExecutor.executeAsync(() -> {
                try {
                    VkFenceOps.wait(device, fence, Long.MAX_VALUE, cmdArena).check();
                } finally {
                    freeCommandBuffer(cmdBuffer, cmdArena);
                    cmdArena.close();
                }
            });
        }
    }

    private void freeCommandBuffer(VkCommandBuffer cmdBuffer, Arena cmdArena) {
        MemorySegment cmdBufferPtr = cmdArena.allocate(ValueLayout.ADDRESS);
        cmdBufferPtr.set(ValueLayout.ADDRESS, 0, cmdBuffer.handle());
        vkFreeCommandBuffers(device.handle(), commandPool.handle(), 1, cmdBufferPtr);
    }

    @Override
    public ByteBuffer read(long offset, long readSize) {
        // Warn about performance impact
        System.err.println("WARNING: Synchronous read from device-local buffer requires staging buffer and GPU->CPU transfer. "
                         + "This is extremely slow and will stall the pipeline. Consider using MAPPED/MAPPED_CACHED strategy for frequent reads.");
        
        // Create temporary staging buffer for readback
        VkBuffer stagingBuffer = VkBuffer.builder()
            .device(device)
            .size(readSize)
            .transferDst()
            .hostVisible()
            .build(arena);
        
        try {
            // Copy from device-local to staging
            VkCommandBuffer[] cmdBuffers = VkCommandBufferAlloc.builder()
                .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(arena);
            VkCommandBuffer cmdBuffer = cmdBuffers[0];
            
            VkCommandBuffer.begin(cmdBuffer).oneTimeSubmit().execute(arena);
            MemorySegment copyRegion = VkBufferCopy.allocate(arena, offset, 0, readSize);
            vkCmdCopyBuffer(cmdBuffer.handle(), handle(), stagingBuffer.handle(), 1, copyRegion);
            vkEndCommandBuffer(cmdBuffer.handle());
            
            VkSubmit.builder().commandBuffer(cmdBuffer)
                .submit(transferQueue.handle(), MemorySegment.NULL, arena).check();
            
            // Wait for transfer to complete
            vkQueueWaitIdle(transferQueue.handle());
            
            // Read from staging buffer
            MemorySegment mapped = stagingBuffer.map(arena);
            ByteBuffer result = ByteBuffer.allocate((int)readSize);
            MemorySegment.copy(mapped, 0, MemorySegment.ofBuffer(result), 0, readSize);
            result.rewind();
            
            // Cleanup
            MemorySegment cmdBufferPtr = arena.allocate(ValueLayout.ADDRESS);
            cmdBufferPtr.set(ValueLayout.ADDRESS, 0, cmdBuffer.handle());
            vkFreeCommandBuffers(device.handle(), commandPool.handle(), 1, cmdBufferPtr);
            
            return result;
        } finally {
            stagingBuffer.close();
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
