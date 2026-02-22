package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits.*;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdCopyBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;

public abstract class AbstractBuffer implements ManagedBuffer {
    protected final VkDevice device;
    protected final VkPhysicalDevice physicalDevice;
    protected final Arena arena;
    protected final long size;
    protected final BufferUsage usage;
    protected final MemoryStrategy memoryStrategy;
    
    protected VkBuffer vkBuffer;
    private boolean closed = false;
    
    protected AbstractBuffer(VkDevice device, Arena arena, 
                           long size, BufferUsage usage, MemoryStrategy memoryStrategy) {
        this.device = device;
        this.physicalDevice = device.physicalDevice();
        this.arena = arena;
        this.size = size;
        this.usage = usage;
        this.memoryStrategy = memoryStrategy;
    }
    
    @Override
    public MemorySegment handle() {
        return vkBuffer != null ? vkBuffer.handle() : MemorySegment.NULL;
    }
    
    @Override
    public long size() {
        return size;
    }
    
    @Override
    public BufferUsage usage() {
        return usage;
    }
    
    public MemoryStrategy memoryStrategy() {
        return memoryStrategy;
    }
    
    @Override
    public final void close() {
        if (!closed) {
            closed = true;
            closeImpl();
        }
    }
    
    protected void closeImpl() {
        if (vkBuffer != null) vkBuffer.close();
    }
    
    protected void createBuffer(int usageFlags, int memoryProperties) {
        vkBuffer = VkBuffer.builder()
            .device(device)
            .size(size)
            .usage(usageFlags)
            .memoryProperties(memoryProperties)
            .build(arena);
    }
    
    @Override
    public void copyTo(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue, VkCommandPool commandPool) {
        TransferCompletion tc = copyToAsync(dst, srcOffset, dstOffset, length, queue, commandPool);
        tc.await();
        tc.close();
    }

    @Override
    public TransferCompletion copyToAsync(ManagedBuffer dst, long srcOffset, long dstOffset, long length, VkQueue queue, VkCommandPool commandPool) {
        Arena transferArena = Arena.ofShared();
        VkFence fence = VkFence.builder().device(device).build(transferArena);
        VkCommandBuffer[] cmds = VkCommandBufferAlloc.builder()
            .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(transferArena);
        VkCommandBuffer cmd = cmds[0];
        VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(transferArena);
        MemorySegment copyRegion = VkBufferCopy.allocate(transferArena, srcOffset, dstOffset, length);
        vkCmdCopyBuffer(cmd.handle(), handle(), dst.handle(), 1, copyRegion);
        vkEndCommandBuffer(cmd.handle());
        VkSubmit.builder().commandBuffer(cmd).submit(queue.handle(), fence.handle(), transferArena).check();
        return new TransferCompletion(device, fence, transferArena);
    }

    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        throw new UnsupportedOperationException("writeAsync not implemented for " + getClass().getSimpleName());
    }
    
    @Override
    public ByteBuffer read(long offset, long size) {
        throw new UnsupportedOperationException("read not implemented for " + getClass().getSimpleName());
    }
    
    @Override
    public void write(ByteBuffer data, long offset) {
        throw new UnsupportedOperationException("write not implemented for " + getClass().getSimpleName());
    }
}