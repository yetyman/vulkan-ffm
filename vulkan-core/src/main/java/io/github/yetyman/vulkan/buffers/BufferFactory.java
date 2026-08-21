package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;

import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.*;

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
     * @param strategy          primary memory strategy
     * @param secondaryStrategy underlying strategy for composite buffers (ignored for simple buffers)
     * @param size              buffer size in bytes
     * @param usage             buffer usage flags (UNIFORM, STORAGE, VERTEX, etc.)
     * @param device            Vulkan logical device
     * @param transferQueue     queue for transfer operations
     * @return managed buffer instance
     */
    public static IBuffer create(
            MemoryStrategy strategy,
            MemoryStrategy secondaryStrategy,
            long size,
            BufferUsage usage,
            VkDevice device,
            VkQueue transferQueue) {

        return switch (strategy) {
            case MAPPED -> managedBuffer(device, size, usage,
                    new DirectAllocationStrategy(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value() | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value(), true),
                    new MappedTransferStrategy(true),
                    new InherentObservability(),
                    DirtyStrategy.forSize(size));
            case MAPPED_CACHED -> managedBuffer(device, size, usage,
                    new DirectAllocationStrategy(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value() | VK_MEMORY_PROPERTY_HOST_CACHED_BIT.value(), true),
                    new MappedTransferStrategy(false),
                    new InherentObservability(),
                    DirtyStrategy.forSize(size));
            case DEVICE_LOCAL -> managedBuffer(device, size, usage,
                    new DirectAllocationStrategy(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value(), false),
                    new StagingTransferStrategy(false, transferQueue),
                    NoneObservability.INSTANCE,
                    DirtyStrategy.forSize(size));
            case DEVICE_LOCAL_MIRRORED -> managedBuffer(device, size, usage,
                    new DirectAllocationStrategy(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value(), false),
                    new StagingTransferStrategy(false, transferQueue),
                    new MirroredObservability(),
                    DirtyStrategy.forSize(size));
            case STAGING -> managedBuffer(device, size, usage,
                    new DirectAllocationStrategy(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value(), false),
                    new StagingTransferStrategy(true, transferQueue),
                    NoneObservability.INSTANCE,
                    DirtyStrategy.forSize(size));
            case REBAR -> managedBuffer(device, size, usage,
                    new ReBarAllocationStrategy(),
                    new DirectTransferStrategy(),
                    new InherentObservability(),
                    DirtyStrategy.forSize(size));
            case RING_BUFFER -> {
                boolean singleOffset = switch (secondaryStrategy) {
                    case MAPPED, MAPPED_CACHED, REBAR -> true;
                    case DEVICE_LOCAL, DEVICE_LOCAL_MIRRORED ->
                            io.github.yetyman.vulkan.highlevel.VulkanCapabilities.unifiedMemory;
                    default -> false;
                };
                yield new RingBuffer(device, size, usage, secondaryStrategy, 3, transferQueue, singleOffset);
            }
            case SPARSE -> {
                int pageMemoryProperties = switch (secondaryStrategy) {
                    case MAPPED, MAPPED_CACHED ->
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value() | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value();
                    case DEVICE_LOCAL -> VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value();
                    default ->
                            throw new IllegalArgumentException("Unsupported underlying strategy for sparse buffer: " + secondaryStrategy);
                };
                yield managedBuffer(device, size, usage,
                        new SparseAllocationStrategy(pageMemoryProperties, transferQueue),
                        new SparseTransferStrategy(transferQueue),
                        NoneObservability.INSTANCE,
                        DirtyStrategy.forSize(size));
            }
            case SUBALLOCATOR ->
                    throw new IllegalArgumentException("Use BufferFactory.createSlab() for SUBALLOCATOR — slotSize is required");
        };
    }

    private static ManagedBuffer managedBuffer(VkDevice device, long size, BufferUsage usage,
                                               AllocationStrategy allocation, TransferStrategy transfer,
                                               CpuObservability observability, DirtyStrategy dirtyStrategy) {
        return ManagedBuffer.builder()
                .device(device).size(size).usage(usage)
                .allocation(allocation).transfer(transfer)
                .observability(observability).dirtyStrategy(dirtyStrategy)
                .build();
    }

    /** Kept for internal compatibility during migration. Prefer the 7-arg overload. */
    private static ManagedBuffer managedBuffer(VkDevice device, long size, BufferUsage usage,
                                               AllocationStrategy allocation, TransferStrategy transfer) {
        return managedBuffer(device, size, usage, allocation, transfer,
                NoneObservability.INSTANCE, DirtyStrategy.forSize(size));
    }

    /**
     * Creates a fixed-size slab suballocator.
     *
     * @param totalSize       total buffer size in bytes
     * @param slotSize        size of each fixed slot in bytes (aligned up to device requirements)
     * @param usage           buffer usage
     * @param backingStrategy memory strategy for the backing buffer
     */
    public static SuballocatorBuffer createSlab(
            long totalSize,
            long slotSize,
            BufferUsage usage,
            MemoryStrategy backingStrategy,
            VkDevice device,
            VkQueue transferQueue) {
        return new SuballocatorBuffer(device, totalSize, usage, slotSize, backingStrategy, transferQueue);
    }

    /**
     * Creates a sparse buffer with the given underlying memory type (MAPPED/MAPPED_CACHED for
     * host-visible pages, DEVICE_LOCAL for GPU-only pages backed by staged transfers).
     * The returned {@link ManagedBuffer} also implements {@link SparseCapable} for page
     * commit/decommit control.
     *
     * <p>Note: sparse binding is issued on {@code sparseQueue}; staging transfers for
     * device-local underlying pages are issued on {@code transferQueue}. Pass the same queue
     * for both when the device does not expose a separate sparse-binding queue family.
     *
     * @param sparseQueue   queue with VK_QUEUE_SPARSE_BINDING_BIT support, for vkQueueBindSparse
     * @param transferQueue queue for staging transfers (device-local pages only)
     */
    public static ManagedBuffer createSparse(
            long size,
            BufferUsage usage,
            MemoryStrategy underlyingStrategy,
            VkDevice device,
            VkQueue sparseQueue,
            VkQueue transferQueue) {
        int pageMemoryProperties = switch (underlyingStrategy) {
            case MAPPED, MAPPED_CACHED ->
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value() | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value();
            case DEVICE_LOCAL -> VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value();
            default ->
                    throw new IllegalArgumentException("Unsupported underlying strategy for sparse buffer: " + underlyingStrategy);
        };
        return managedBuffer(device, size, usage,
                new SparseAllocationStrategy(pageMemoryProperties, sparseQueue),
                new SparseTransferStrategy(transferQueue));
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
     * @param cpuWrite      how often CPU writes to buffer
     * @param cpuRead       how often CPU reads from buffer
     * @param gpuRead       how often GPU reads from buffer
     * @param gpuWrite      how often GPU writes to buffer
     * @param size          buffer size in bytes
     * @param usage         buffer usage flags
     * @param device        Vulkan logical device
     * @param transferQueue queue for transfer operations
     * @return optimally configured managed buffer
     */
    public static IBuffer createAutomatic(
            AccessFrequency cpuWrite,
            AccessFrequency cpuRead,
            AccessFrequency gpuRead,
            AccessFrequency gpuWrite,
            long size,
            BufferUsage usage,
            VkDevice device,
            VkQueue transferQueue) {

        DataScale scale = DataScale.fromSize(size, device.physicalDevice());
        BufferStrategySelection selection = BufferStrategySelector.select(cpuWrite, cpuRead, gpuRead, gpuWrite, scale);

        return create(selection.memoryStrategy(), selection.secondaryStrategy(), size, usage, device, transferQueue);
    }
}
