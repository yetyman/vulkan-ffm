package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkQueue;
import java.lang.foreign.Arena;

public class BufferFactory {
    
    /**
     * Creates a managed buffer with explicit memory strategies.
     * 
     * <p>This is the primary buffer creation method that supports all buffer types:
     * <ul>
     *   <li><b>MAPPED/MAPPED_CACHED</b> - Host-visible buffers for frequent CPU access</li>
     *   <li><b>DEVICE_LOCAL</b> - GPU-optimal buffers with staging transfers</li>
     *   <li><b>STAGING</b> - Temporary host-visible buffers for GPU transfers</li>
     *   <li><b>RING_BUFFER</b> - Multi-frame buffers using secondaryStrategy for underlying memory</li>
     *   <li><b>SPARSE</b> - Large virtual buffers with on-demand page allocation using secondaryStrategy</li>
     *   <li><b>SUBALLOCATOR</b> - Single large buffer with multiple small allocations using secondaryStrategy</li>
     * </ul>
     * 
     * <p>For composite buffer types (RING_BUFFER, SPARSE, SUBALLOCATOR), the secondaryStrategy determines
     * the underlying memory management approach.
     * 
     * @param strategy primary memory strategy
     * @param secondaryStrategy underlying strategy for composite buffers (ignored for simple buffers)
     * @param size buffer size in bytes
     * @param usage buffer usage flags (UNIFORM, STORAGE, VERTEX, etc.)
     * @param device Vulkan logical device
     * @param transferQueue queue for transfer operations
     * @param commandPool command pool for transfer commands
     * @param arena memory arena for Vulkan object allocation
     * @return managed buffer instance
     */
    public static ManagedBuffer create(
            MemoryStrategy strategy,
            MemoryStrategy secondaryStrategy,
            long size,
            BufferUsage usage,
            VkDevice device,
            VkQueue transferQueue,
            VkCommandPool commandPool,
            Arena arena) {
        
        return switch (strategy) {
            case MAPPED -> new MappedBuffer(device, arena, size, usage, true);
            case MAPPED_CACHED -> new MappedBuffer(device, arena, size, usage, false);
            case DEVICE_LOCAL -> new DeviceLocalBuffer(device, arena, size, usage, transferQueue, commandPool, false);
            case DEVICE_LOCAL_MIRRORED -> new MirroredBuffer(device, arena, size, usage, transferQueue, commandPool);
            case STAGING -> new DeviceLocalBuffer(device, arena, size, usage, transferQueue, commandPool, true);
            case REBAR -> new ReBarBuffer(device, arena, size, usage);
            case RING_BUFFER -> new RingBuffer(device, arena, size, usage, secondaryStrategy, 3, transferQueue, commandPool);
            case SPARSE -> new SparseBuffer(device, arena, size, usage, secondaryStrategy, transferQueue, transferQueue, commandPool);
            case SUBALLOCATOR -> throw new IllegalArgumentException("Use BufferFactory.createSlab() for SUBALLOCATOR — slotSize is required");
        };
    }

    /**
     * Creates a fixed-size slab suballocator.
     *
     * @param totalSize      total buffer size in bytes
     * @param slotSize       size of each fixed slot in bytes (aligned up to device requirements)
     * @param usage          buffer usage
     * @param backingStrategy memory strategy for the backing buffer
     */
    public static SuballocatorBuffer createSlab(
            long totalSize,
            long slotSize,
            BufferUsage usage,
            MemoryStrategy backingStrategy,
            VkDevice device,
            VkQueue transferQueue,
            VkCommandPool commandPool,
            Arena arena) {
        return new SuballocatorBuffer(device, arena, totalSize, usage, slotSize, backingStrategy, transferQueue, commandPool);
    }

    /**
     * Creates an optimal managed buffer based on access patterns and data size.
     * 
     * <p>Automatically selects the best buffer strategy by analyzing:
     * <ul>
     *   <li><b>Access frequencies</b> - How often CPU/GPU read/write the data</li>
     *   <li><b>Data size</b> - Actual buffer size in bytes</li>
     * </ul>
     * 
     * <p>Selection logic:
     * <ul>
     *   <li><b>MULTI_FRAME access</b> → RING_BUFFER with underlying strategy</li>
     *   <li><b>Frequent CPU writes</b> → MAPPED for convenience</li>
     *   <li><b>Rare CPU writes + frequent GPU reads</b> → STAGING → DEVICE_LOCAL</li>
     *   <li><b>GPU-only data</b> → DEVICE_LOCAL</li>
     *   <li><b>GPU writes + CPU reads</b> → MAPPED_CACHED for readback</li>
     * </ul>
     * 
     * <p>Ring buffers automatically reduce access frequency to underlying buffers,
     * enabling more optimal memory strategies (e.g., DEVICE_LOCAL instead of MAPPED).
     * 
     * @param cpuWrite how often CPU writes to buffer
     * @param cpuRead how often CPU reads from buffer
     * @param gpuRead how often GPU reads from buffer
     * @param gpuWrite how often GPU writes to buffer
     * @param size buffer size in bytes
     * @param usage buffer usage flags
     * @param device Vulkan logical device
     * @param transferQueue queue for transfer operations
     * @param commandPool command pool for transfer commands
     * @param arena memory arena for Vulkan object allocation
     * @return optimally configured managed buffer
     */
    public static ManagedBuffer createAutomatic(
            AccessFrequency cpuWrite,
            AccessFrequency cpuRead,
            AccessFrequency gpuRead,
            AccessFrequency gpuWrite,
            long size,
            BufferUsage usage,
            VkDevice device,
            VkQueue transferQueue,
            VkCommandPool commandPool,
            Arena arena) {
        
        DataScale scale = DataScale.fromSize(size, device.physicalDevice());
        BufferStrategySelection selection = BufferStrategySelector.select(cpuWrite, cpuRead, gpuRead, gpuWrite, scale);
        
        return create(selection.memoryStrategy(), selection.secondaryStrategy(), size, usage, device, transferQueue, commandPool, arena);
    }
}