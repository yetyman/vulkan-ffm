package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan buffer (VkBuffer) with automatic resource management.
 * Buffers store data accessible to shaders and other GPU operations.
 */
public class VkBuffer implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment memory;
    private final VkDevice device;
    private final long size;
    
    private VkBuffer(MemorySegment handle, MemorySegment memory, VkDevice device, long size) {
        this.handle = handle;
        this.memory = memory;
        this.device = device;
        this.size = size;
    }
    
    /** @return a new builder for configuring buffer creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkBuffer handle */
    public MemorySegment handle() { return handle; }
    
    /** @return the VkDeviceMemory handle */
    public MemorySegment memory() { return memory; }
    
    /** @return the buffer size in bytes */
    public long size() { return size; }
    
    /** @return the device handle */
    public VkDevice device() { return device; }
    
    /**
     * Maps buffer memory for CPU access
     * @param arena Arena for memory allocation
     * @return Mapped memory segment
     */
    public MemorySegment map(Arena arena) {
        MemorySegment mappedPtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.mapMemory(device.handle(), memory, 0, size, 0, mappedPtr).check();
        MemorySegment mappedAddress = mappedPtr.get(ValueLayout.ADDRESS, 0);
        return mappedAddress.reinterpret(size, arena, null);
    }
    
    /**
     * Unmaps buffer memory
     */
    public void unmap() {
        Vulkan.unmapMemory(device.handle(), memory);
    }
    
    @Override
    public void close() {
        if (memory != null && !memory.equals(MemorySegment.NULL)) {
            Vulkan.freeMemory(device.handle(), memory);
        }
        Vulkan.destroyBuffer(device.handle(), handle);
    }
    
    /**
     * Builder for buffer creation with memory allocation.
     */
    public static class Builder {
        private VkDevice device;
        private VkPhysicalDevice physicalDevice;
        private long size;
        private int usage;
        private int sharingMode = VkSharingMode.VK_SHARING_MODE_EXCLUSIVE;
        private int[] queueFamilyIndices = null;
        private int memoryProperties = VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
        private int flags = 0;
        private MemorySegment initialData = null;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        /** Sets the physical device (needed for memory allocation) */
        public Builder physicalDevice(VkPhysicalDevice physicalDevice) {
            this.physicalDevice = physicalDevice;
            return this;
        }
        
        /** Sets buffer size in bytes */
        public Builder size(long size) {
            this.size = size;
            return this;
        }
        
        /** Sets buffer usage flags */
        public Builder usage(int usage) {
            this.usage = usage;
            return this;
        }
        
        /** Configures as vertex buffer */
        public Builder vertexBuffer() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            return this;
        }
        
        /** Configures as index buffer */
        public Builder indexBuffer() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            return this;
        }
        
        /** Configures as uniform buffer */
        public Builder uniformBuffer() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            return this;
        }
        
        /** Configures as storage buffer */
        public Builder storageBuffer() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            return this;
        }
        
        /** Configures as transfer source */
        public Builder transferSrc() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            return this;
        }
        
        /** Configures as transfer destination */
        public Builder transferDst() {
            this.usage |= VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            return this;
        }
        
        /** Sets memory properties */
        public Builder memoryProperties(int properties) {
            this.memoryProperties = properties;
            return this;
        }
        
        /** Configures memory as host visible and coherent (CPU accessible) */
        public Builder hostVisible() {
            this.memoryProperties = VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | 
                                   VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            return this;
        }
        
        /** Configures memory as device local (GPU only) */
        public Builder deviceLocal() {
            this.memoryProperties = VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            return this;
        }
        
        /** Sets sharing mode */
        public Builder sharingMode(int mode) {
            this.sharingMode = mode;
            return this;
        }
        
        /** Sets queue family indices for concurrent sharing */
        public Builder queueFamilyIndices(int... indices) {
            this.queueFamilyIndices = indices;
            if (indices.length > 1) {
                this.sharingMode = VkSharingMode.VK_SHARING_MODE_CONCURRENT;
            }
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Sets initial data to upload to buffer */
        public Builder initialData(MemorySegment data) {
            this.initialData = data;
            return this;
        }
        
        /** Creates the buffer with allocated memory */
        public VkBuffer build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (physicalDevice == null) throw new IllegalStateException("physicalDevice not set");
            if (size <= 0) throw new IllegalStateException("invalid size");
            if (usage == 0) throw new IllegalStateException("usage not set");
            
            // Create buffer
            MemorySegment bufferInfo = VkBufferCreateInfo.allocate(arena);
            VkBufferCreateInfo.sType(bufferInfo, VkStructureType.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            VkBufferCreateInfo.pNext(bufferInfo, MemorySegment.NULL);
            VkBufferCreateInfo.flags(bufferInfo, flags);
            VkBufferCreateInfo.size(bufferInfo, size);
            VkBufferCreateInfo.usage(bufferInfo, usage);
            VkBufferCreateInfo.sharingMode(bufferInfo, sharingMode);
            
            if (queueFamilyIndices != null && queueFamilyIndices.length > 0) {
                MemorySegment indicesArray = arena.allocate(ValueLayout.JAVA_INT, queueFamilyIndices.length);
                for (int i = 0; i < queueFamilyIndices.length; i++) {
                    indicesArray.setAtIndex(ValueLayout.JAVA_INT, i, queueFamilyIndices[i]);
                }
                VkBufferCreateInfo.queueFamilyIndexCount(bufferInfo, queueFamilyIndices.length);
                VkBufferCreateInfo.pQueueFamilyIndices(bufferInfo, indicesArray);
            } else {
                VkBufferCreateInfo.queueFamilyIndexCount(bufferInfo, 0);
                VkBufferCreateInfo.pQueueFamilyIndices(bufferInfo, MemorySegment.NULL);
            }
            
            MemorySegment bufferPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createBuffer(device.handle(), bufferInfo, bufferPtr).check();
            MemorySegment buffer = bufferPtr.get(ValueLayout.ADDRESS, 0);
            
            // Get memory requirements
            MemorySegment memRequirements = VkMemoryRequirements.allocate(arena);
            Vulkan.getBufferMemoryRequirements(device.handle(), buffer, memRequirements);
            long memSize = VkMemoryRequirements.size(memRequirements);
            int memTypeBits = VkMemoryRequirements.memoryTypeBits(memRequirements);
            
            // Find suitable memory type
            MemorySegment memProperties = VkPhysicalDeviceMemoryProperties.allocate(arena);
            Vulkan.getPhysicalDeviceMemoryProperties(physicalDevice.handle(), memProperties);
            
            int memoryTypeIndex = -1;
            int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(memProperties);
            for (int i = 0; i < typeCount; i++) {
                if ((memTypeBits & (1 << i)) != 0) {
                    MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(memProperties, i);
                    int props = VkMemoryType.propertyFlags(memType);
                    if ((props & memoryProperties) == memoryProperties) {
                        memoryTypeIndex = i;
                        break;
                    }
                }
            }
            
            if (memoryTypeIndex == -1) {
                throw new RuntimeException("Failed to find suitable memory type");
            }
            
            // Allocate memory
            MemorySegment allocInfo = VkMemoryAllocateInfo.allocate(arena);
            VkMemoryAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            VkMemoryAllocateInfo.pNext(allocInfo, MemorySegment.NULL);
            VkMemoryAllocateInfo.allocationSize(allocInfo, memSize);
            VkMemoryAllocateInfo.memoryTypeIndex(allocInfo, memoryTypeIndex);
            
            MemorySegment memoryPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.allocateMemory(device.handle(), allocInfo, memoryPtr).check();
            MemorySegment memory = memoryPtr.get(ValueLayout.ADDRESS, 0);
            
            // Bind buffer memory
            Vulkan.bindBufferMemory(device.handle(), buffer, memory, 0).check();
            
            // Upload initial data if provided
            if (initialData != null && !initialData.equals(MemorySegment.NULL) && initialData.byteSize() > 0) {
                // Map memory, copy data, unmap
                MemorySegment mappedPtr = arena.allocate(ValueLayout.ADDRESS);
                Vulkan.mapMemory(device.handle(), memory, 0, size, 0, mappedPtr).check();
                MemorySegment mapped = mappedPtr.get(ValueLayout.ADDRESS, 0);
                
                long copySize = Math.min(size, initialData.byteSize());
                MemorySegment.copy(initialData, 0, mapped, 0, copySize);
                
                Vulkan.unmapMemory(device.handle(), memory);
            }
            
            return new VkBuffer(buffer, memory, device, size);
        }
    }
}