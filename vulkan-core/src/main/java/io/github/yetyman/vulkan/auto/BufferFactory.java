package io.github.yetyman.vulkan.auto;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.VkCommandPool;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class BufferFactory {
    
    public static ManagedBuffer create(
            MemoryStrategy strategy,
            long size,
            BufferUsage usage,
            VkDevice device,
            MemorySegment transferQueue,
            VkCommandPool commandPool,
            Arena arena) {
        
        return switch (strategy) {
            case MAPPED -> new MappedBuffer(device, arena, size, usage, true);
            case MAPPED_CACHED -> new MappedBuffer(device, arena, size, usage, false);
            case DEVICE_LOCAL -> new DeviceLocalBuffer(device, arena, size, usage, transferQueue, commandPool);
            case STAGING -> new StagingBuffer(device, arena, size, usage, transferQueue, commandPool);
            case RING_BUFFER -> throw new IllegalArgumentException("Use BufferFactory.createRingBuffer() for ring buffers");
            case SPARSE -> new SparseBuffer(device, arena, size, usage, transferQueue, transferQueue, commandPool);
        };
    }
    
    public static ManagedBuffer createRingBuffer(
            MemoryStrategy underlyingStrategy,
            int frameCount,
            long size,
            BufferUsage usage,
            VkDevice device,
            MemorySegment transferQueue,
            VkCommandPool commandPool,
            Arena arena) {
        
        if (underlyingStrategy == MemoryStrategy.RING_BUFFER) {
            throw new IllegalArgumentException("Cannot nest ring buffers");
        }
        return new RingBuffer(device, arena, size, usage, underlyingStrategy, frameCount, transferQueue, commandPool);
    }
    
    public static ManagedBuffer createRingBuffer(
            AccessFrequency cpuWrite,
            AccessFrequency cpuRead,
            AccessFrequency gpuRead,
            AccessFrequency gpuWrite,
            DataScale dataScale,
            int frameCount,
            BufferUsage usage,
            VkDevice device,
            MemorySegment transferQueue,
            VkCommandPool commandPool,
            Arena arena) {
        
        return new RingBuffer(device, arena, dataScale.getBytes(), usage, 
            cpuWrite, cpuRead, gpuRead, gpuWrite, dataScale, frameCount,
            transferQueue, commandPool);
    }
    
    public static ManagedBuffer createAutomatic(
            AccessFrequency cpuWrite,
            AccessFrequency cpuRead,
            AccessFrequency gpuRead,
            AccessFrequency gpuWrite,
            DataScale size,
            BufferUsage usage,
            VkDevice device,
            MemorySegment transferQueue,
            VkCommandPool commandPool,
            Arena arena) {
        
        BufferStrategySelection selection = BufferStrategySelector.select(cpuWrite, cpuRead, gpuRead, gpuWrite, size);
        
        if (selection.useRingBuffer()) {
            return new RingBuffer(device, arena, size.getBytes(), usage, 
                selection.memoryStrategy(), selection.recommendedFrameCount(), 
                transferQueue, commandPool);
        }
        
        return create(selection.memoryStrategy(), size.getBytes(), usage, device, transferQueue, commandPool, arena);
    }
}