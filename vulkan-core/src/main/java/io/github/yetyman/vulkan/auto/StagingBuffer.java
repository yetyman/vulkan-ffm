package io.github.yetyman.vulkan.auto;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdCopyBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFreeCommandBuffers;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkQueueWaitIdle;

public class StagingBuffer extends AbstractBuffer {
    private VkBuffer stagingVkBuffer;
    private MemorySegment mappedMemory;
    private final MemorySegment transferQueue;
    private final VkCommandPool commandPool;
    
    public StagingBuffer(VkDevice device, Arena arena,
                        long size, BufferUsage usage, MemorySegment transferQueue, VkCommandPool commandPool) {
        super(device, arena, size, usage, MemoryStrategy.STAGING);
        this.transferQueue = transferQueue;
        this.commandPool = commandPool;
        
        // Create device-local buffer
        createBuffer(usage.toVkFlags(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value());
        
        // Create staging buffer
        createStagingBuffer();
    }
    
    private void createStagingBuffer() {
        stagingVkBuffer = VkBuffer.builder()
            .device(device)
            .size(size)
            .transferSrc()
            .hostVisible()
            .build(arena);
        
        // VkBuffer.map() returns a reinterpreted segment with the full size
        mappedMemory = stagingVkBuffer.map(arena);
    }
    
    @Override
    public void write(ByteBuffer data, long offset) {
        if (offset + data.remaining() > size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        
        // Write to staging buffer
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, 
                          mappedMemory, offset, data.remaining());
        
        // Copy from staging to device-local buffer
        copyToDeviceLocal(offset, data.remaining(), null);
    }
    
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        if (offset + data.remaining() > size) {
            throw new IllegalArgumentException("Write exceeds buffer size");
        }
        
        MemorySegment.copy(MemorySegment.ofBuffer(data), 0, 
                          mappedMemory, offset, data.remaining());
        
        VkFence fence = VkFence.builder().device(device).build(arena);
        copyToDeviceLocal(offset, data.remaining(), fence);
        return new TransferCompletion(device, fence, arena);
    }
    
    @Override
    public ByteBuffer read(long offset, long readSize) {
        throw new UnsupportedOperationException("Cannot read from device-local buffer without readback");
    }
    
    @Override
    public void flush() {
        // No-op for coherent staging memory
    }
    
    private void copyToDeviceLocal(long offset, long copySize, VkFence fence) {
        VkCommandBuffer[] cmdBuffers = VkCommandBufferAlloc.builder()
            .device(device)
            .commandPool(commandPool.handle())
            .primary()
            .count(1)
            .allocate(arena);
        VkCommandBuffer cmdBuffer = cmdBuffers[0];
        
        VkCommandBuffer.begin(cmdBuffer).oneTimeSubmit().execute(arena);
        
        MemorySegment copyRegion = VkBufferCopy.allocate(arena, offset, offset, copySize);
        vkCmdCopyBuffer(cmdBuffer.handle(), stagingVkBuffer.handle(), getVkBuffer(), 1, copyRegion);
        vkEndCommandBuffer(cmdBuffer.handle());
        
        VkSubmit.builder()
            .commandBuffer(cmdBuffer.handle())
            .submit(transferQueue, fence != null ? fence.handle() : MemorySegment.NULL, arena)
            .check();
        
        if (fence == null) {
            vkQueueWaitIdle(transferQueue);
        }
        
        MemorySegment cmdBufferPtr = arena.allocate(ValueLayout.ADDRESS);
        cmdBufferPtr.set(ValueLayout.ADDRESS, 0, cmdBuffer.handle());
        vkFreeCommandBuffers(device.handle(), commandPool.handle(), 1, cmdBufferPtr);
    }

    
    @Override
    public void close() {
        if (stagingVkBuffer != null) stagingVkBuffer.close();
        super.close();
    }
}