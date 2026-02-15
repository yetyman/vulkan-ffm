package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;


public class RingBuffer extends AbstractBuffer {
    private final ManagedBuffer[] buffers;
    private final int frameCount;
    private int currentFrame = 0;
    
    public RingBuffer(VkDevice device, Arena arena,
                     long size, BufferUsage usage, MemoryStrategy underlyingStrategy, int frameCount,
                     VkQueue transferQueue, VkCommandPool commandPool) {
        super(device, arena, size, usage, MemoryStrategy.RING_BUFFER);
        this.frameCount = frameCount;
        this.buffers = new ManagedBuffer[frameCount];
        
        for (int i = 0; i < frameCount; i++) {
            buffers[i] = BufferFactory.create(underlyingStrategy, MemoryStrategy.DEVICE_LOCAL, size, usage, device, transferQueue, commandPool, arena);
        }
    }
    
    public RingBuffer(VkDevice device, Arena arena,
                     long size, BufferUsage usage, 
                     AccessFrequency cpuWrite, AccessFrequency cpuRead,
                     AccessFrequency gpuRead, AccessFrequency gpuWrite,
                      int frameCount,
                     VkQueue transferQueue, VkCommandPool commandPool) {
        super(device, arena, size, usage, MemoryStrategy.RING_BUFFER);
        this.frameCount = frameCount;
        this.buffers = new ManagedBuffer[frameCount];
        
        for (int i = 0; i < frameCount; i++) {
            buffers[i] = BufferFactory.createAutomatic(
                cpuWrite, cpuRead, gpuRead, gpuWrite, size,
                usage, device, transferQueue, commandPool, arena
            );
        }
    }
    
    @Override
    public void write(ByteBuffer data, long offset) {
        buffers[currentFrame].write(data, offset);
    }
    
    @Override
    public ByteBuffer read(long offset, long size) {
        return buffers[currentFrame].read(offset, size);
    }
    
    @Override
    public void flush() {
        buffers[currentFrame].flush();
    }
    
    /**
     * Returns the handle of the current frame's buffer.
     * This is the buffer that should be bound for the current frame's rendering.
     */
    @Override
    public MemorySegment handle() {
        return buffers[currentFrame].handle();
    }
    
    public void nextFrame() {
        currentFrame = (currentFrame + 1) % frameCount;
    }
    
    public int getCurrentFrame() {
        return currentFrame;
    }
    
    @Override
    public void closeImpl() {
        for (ManagedBuffer buffer : buffers) {
            if (buffer != null) {
                buffer.close();
            }
        }
        // Don't call super.close() as we don't own the primary buffer/memory
    }
    
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        return buffers[currentFrame].writeAsync(data, offset);
    }
}