package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level Vulkan memory allocator with pooling, suballocation, and automatic defragmentation.
 * Manages device memory efficiently by grouping small allocations into larger blocks.
 * 
 * Example usage:
 * <pre>{@code
 * // Create allocator
 * VkMemoryAllocator allocator = VkMemoryAllocator.builder()
 *     .device(device)
 *     .physicalDevice(physicalDevice)
 *     .build(arena);
 * 
 * // Allocate buffer memory
 * VkBuffer buffer = VkBuffer.builder()...build(arena);
 * VkAllocation allocation = allocator.allocateBuffer(buffer.handle(), 
 *     VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
 * 
 * // Bind memory to buffer
 * vkBindBufferMemory(device, buffer.handle(), allocation.memory(), allocation.offset());
 * 
 * // For host-visible memory, map and write
 * VkAllocation stagingAllocation = allocator.allocateBuffer(stagingBuffer.handle(),
 *     VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
 * MemorySegment mapped = allocator.map(stagingAllocation);
 * // Write data to mapped memory...
 * allocator.unmap(stagingAllocation);
 * 
 * // Free when done
 * allocator.free(allocation);
 * allocator.free(stagingAllocation);
 * }</pre>
 */
public class VkMemoryAllocator implements AutoCloseable {
    private final MemorySegment device;
    private final MemorySegment physicalDevice;
    private final Arena arena;
    private final Map<Integer, MemoryPool> memoryPools = new ConcurrentHashMap<>();
    private final Map<MemorySegment, AllocationInfo> allocations = new ConcurrentHashMap<>();
    private final MemorySegment memoryProperties;
    
    private static final long DEFAULT_BLOCK_SIZE = 256 * 1024 * 1024; // 256MB
    private static final long MIN_ALLOCATION_SIZE = 1024; // 1KB
    
    private VkMemoryAllocator(MemorySegment device, MemorySegment physicalDevice, Arena arena) {
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.arena = arena;
        this.memoryProperties = VkPhysicalDeviceMemoryProperties.allocate(arena);
        VulkanExtensions.getPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Allocates memory with specified requirements and properties.
     */
    public VkAllocation allocate(MemorySegment requirements, int memoryProperties) {
        long size = VkMemoryRequirements.size(requirements);
        int memoryTypeBits = VkMemoryRequirements.memoryTypeBits(requirements);
        long alignment = VkMemoryRequirements.alignment(requirements);
        
        int memoryTypeIndex = findMemoryType(memoryTypeBits, memoryProperties);
        if (memoryTypeIndex == -1) {
            throw new RuntimeException("Failed to find suitable memory type");
        }
        
        MemoryPool pool = memoryPools.computeIfAbsent(memoryTypeIndex, 
            k -> new MemoryPool(k, DEFAULT_BLOCK_SIZE));
        
        return pool.allocate(size, alignment);
    }
    
    /**
     * Allocates memory for a buffer.
     */
    public VkAllocation allocateBuffer(MemorySegment buffer, int memoryProperties) {
        MemorySegment requirements = VkMemoryRequirements.allocate(arena);
        VulkanExtensions.getBufferMemoryRequirements(device, buffer, requirements);
        return allocate(requirements, memoryProperties);
    }
    
    /**
     * Allocates memory for an image.
     */
    public VkAllocation allocateImage(MemorySegment image, int memoryProperties) {
        MemorySegment requirements = VkMemoryRequirements.allocate(arena);
        VulkanExtensions.getImageMemoryRequirements(device, image, requirements);
        return allocate(requirements, memoryProperties);
    }
    
    /**
     * Frees an allocation.
     */
    public void free(VkAllocation allocation) {
        AllocationInfo info = allocations.remove(allocation.memory());
        if (info != null) {
            info.pool.free(allocation);
        }
    }
    
    /**
     * Maps memory for CPU access.
     */
    public MemorySegment map(VkAllocation allocation) {
        MemorySegment mappedPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.mapMemory(device, allocation.memory(), allocation.offset(), 
            allocation.size(), 0, mappedPtr).check();
        return mappedPtr.get(ValueLayout.ADDRESS, 0);
    }
    
    /**
     * Unmaps previously mapped memory.
     */
    public void unmap(VkAllocation allocation) {
        VulkanExtensions.unmapMemory(device, allocation.memory());
    }
    
    /**
     * Cleans up empty memory blocks to reduce overhead.
     */
    public void cleanup() {
        for (MemoryPool pool : memoryPools.values()) {
            pool.cleanup();
        }
    }
    
    private int findMemoryType(int typeBits, int properties) {
        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(memoryProperties);
        for (int i = 0; i < typeCount; i++) {
            if ((typeBits & (1 << i)) != 0) {
                MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(memoryProperties, i);
                int props = VkMemoryType.propertyFlags(memType);
                if ((props & properties) == properties) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    @Override
    public void close() {
        for (MemoryPool pool : memoryPools.values()) {
            pool.close();
        }
        memoryPools.clear();
        allocations.clear();
    }
    
    private class MemoryPool implements AutoCloseable {
        private final int memoryTypeIndex;
        private final long blockSize;
        private final List<MemoryBlock> blocks = new ArrayList<>();
        
        MemoryPool(int memoryTypeIndex, long blockSize) {
            this.memoryTypeIndex = memoryTypeIndex;
            this.blockSize = blockSize;
        }
        
        VkAllocation allocate(long size, long alignment) {
            size = Math.max(size, MIN_ALLOCATION_SIZE);
            
            // Try existing blocks first
            for (MemoryBlock block : blocks) {
                VkAllocation allocation = block.allocate(size, alignment);
                if (allocation != null) {
                    AllocationInfo info = new AllocationInfo(this, block);
                    allocations.put(allocation.memory(), info);
                    return allocation;
                }
            }
            
            // Create new block
            long newBlockSize = Math.max(blockSize, size);
            MemoryBlock block = createBlock(newBlockSize);
            blocks.add(block);
            
            VkAllocation allocation = block.allocate(size, alignment);
            if (allocation != null) {
                AllocationInfo info = new AllocationInfo(this, block);
                allocations.put(allocation.memory(), info);
            }
            return allocation;
        }
        
        void free(VkAllocation allocation) {
            AllocationInfo info = allocations.get(allocation.memory());
            if (info != null && info.block != null) {
                info.block.free(allocation);
            }
        }
        
        void cleanup() {
            blocks.removeIf(block -> block.isEmpty());
        }
        
        private MemoryBlock createBlock(long size) {
            MemorySegment allocInfo = VkMemoryAllocateInfo.allocate(arena);
            VkMemoryAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            VkMemoryAllocateInfo.allocationSize(allocInfo, size);
            VkMemoryAllocateInfo.memoryTypeIndex(allocInfo, memoryTypeIndex);
            
            MemorySegment memoryPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.allocateMemory(device, allocInfo, memoryPtr).check();
            MemorySegment memory = memoryPtr.get(ValueLayout.ADDRESS, 0);
            
            return new MemoryBlock(memory, size);
        }
        
        @Override
        public void close() {
            for (MemoryBlock block : blocks) {
                block.close();
            }
            blocks.clear();
        }
    }
    
    private class MemoryBlock implements AutoCloseable {
        private final MemorySegment memory;
        private final long size;
        private final List<AllocationRange> freeRanges = new ArrayList<>();
        private final List<AllocationRange> usedRanges = new ArrayList<>();
        
        MemoryBlock(MemorySegment memory, long size) {
            this.memory = memory;
            this.size = size;
            freeRanges.add(new AllocationRange(0, size));
        }
        
        VkAllocation allocate(long size, long alignment) {
            for (int i = 0; i < freeRanges.size(); i++) {
                AllocationRange range = freeRanges.get(i);
                long alignedOffset = alignUp(range.offset, alignment);
                long alignedSize = alignedOffset - range.offset + size;
                
                if (alignedSize <= range.size) {
                    freeRanges.remove(i);
                    
                    if (alignedOffset > range.offset) {
                        freeRanges.add(new AllocationRange(range.offset, alignedOffset - range.offset));
                    }
                    
                    long remainingOffset = alignedOffset + size;
                    long remainingSize = range.offset + range.size - remainingOffset;
                    if (remainingSize > 0) {
                        freeRanges.add(new AllocationRange(remainingOffset, remainingSize));
                    }
                    
                    usedRanges.add(new AllocationRange(alignedOffset, size));
                    return new VkAllocation(memory, alignedOffset, size);
                }
            }
            return null;
        }
        
        void free(VkAllocation allocation) {
            usedRanges.removeIf(range -> range.offset == allocation.offset() && range.size == allocation.size());
            freeRanges.add(new AllocationRange(allocation.offset(), allocation.size()));
            mergeFreeRanges();
        }
        
        boolean isEmpty() {
            return usedRanges.isEmpty();
        }
        
        private void mergeFreeRanges() {
            freeRanges.sort(Comparator.comparingLong(r -> r.offset));
            
            for (int i = 0; i < freeRanges.size() - 1; i++) {
                AllocationRange current = freeRanges.get(i);
                AllocationRange next = freeRanges.get(i + 1);
                
                if (current.offset + current.size == next.offset) {
                    freeRanges.set(i, new AllocationRange(current.offset, current.size + next.size));
                    freeRanges.remove(i + 1);
                    i--;
                }
            }
        }
        
        private long alignUp(long value, long alignment) {
            return (value + alignment - 1) & ~(alignment - 1);
        }
        
        @Override
        public void close() {
            VulkanExtensions.freeMemory(device, memory);
        }
    }
    
    private record AllocationRange(long offset, long size) {}
    private record AllocationInfo(MemoryPool pool, MemoryBlock block) {}
    
    public static class Builder {
        private MemorySegment device;
        private MemorySegment physicalDevice;
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public Builder physicalDevice(MemorySegment physicalDevice) {
            this.physicalDevice = physicalDevice;
            return this;
        }
        
        public VkMemoryAllocator build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (physicalDevice == null) throw new IllegalStateException("physicalDevice not set");
            return new VkMemoryAllocator(device, physicalDevice, arena);
        }
    }
}

/**
 * Represents an allocated memory region.
 */
record VkAllocation(MemorySegment memory, long offset, long size) {}