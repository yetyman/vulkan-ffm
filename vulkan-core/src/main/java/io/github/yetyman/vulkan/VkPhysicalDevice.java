package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.VkFormatProperties;
import io.github.yetyman.vulkan.generated.VkPhysicalDeviceMemoryProperties;
import io.github.yetyman.vulkan.generated.VkPhysicalDeviceProperties;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Wrapper for VkPhysicalDevice with common operations as instance methods
 */
public class VkPhysicalDevice {
    private final MemorySegment handle;
    private Long cachedSparsePageSize;
    
    private VkPhysicalDevice(MemorySegment handle) {
        this.handle = handle;
    }
    
    public static VkPhysicalDevice wrap(MemorySegment physicalDeviceHandle) {
        return new VkPhysicalDevice(physicalDeviceHandle);
    }
    
    public MemorySegment handle() {
        return handle;
    }
    
    public void getFormatProperties(int format, MemorySegment properties) {
        VulkanFFM.vkGetPhysicalDeviceFormatProperties(handle, format, properties);
    }
    
    public VkFormatPropertiesWrapper getFormatProperties(int format, Arena arena) {
        MemorySegment props = arena.allocate(VkFormatProperties.sizeof());
        getFormatProperties(format, props);
        return new VkFormatPropertiesWrapper(props);
    }
    
    public void getMemoryProperties(MemorySegment properties) {
        VulkanFFM.vkGetPhysicalDeviceMemoryProperties(handle, properties);
    }
    
    public VkPhysicalDeviceMemoryPropertiesWrapper getMemoryProperties(Arena arena) {
        MemorySegment props = VkPhysicalDeviceMemoryProperties.allocate(arena);
        getMemoryProperties(props);
        return new VkPhysicalDeviceMemoryPropertiesWrapper(props);
    }
    
    public int findMemoryType(int typeBits, int properties, Arena arena) {
        MemorySegment memProps = VkPhysicalDeviceMemoryProperties.allocate(arena);
        getMemoryProperties(memProps);
        
        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(memProps);
        for (int i = 0; i < typeCount; i++) {
            if ((typeBits & (1 << i)) != 0) {
                MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(memProps, i);
                int props = io.github.yetyman.vulkan.generated.VkMemoryType.propertyFlags(memType);
                if ((props & properties) == properties) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Returns the sparse buffer page size (bufferImageGranularity).
     * Cached after first query.
     */
    public long getSparsePageSize() {
        if (cachedSparsePageSize == null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment props = VkPhysicalDeviceProperties.allocate(arena);
                VulkanFFM.vkGetPhysicalDeviceProperties(handle, props);
                cachedSparsePageSize = VkPhysicalDeviceProperties.limits.bufferImageGranularity(props);
            }
        }
        return cachedSparsePageSize;
    }
    
    // Wrapper classes for return values
    public static class VkPhysicalDeviceMemoryPropertiesWrapper {
        private final MemorySegment segment;
        
        VkPhysicalDeviceMemoryPropertiesWrapper(MemorySegment segment) {
            this.segment = segment;
        }
        
        public int memoryTypeCount() {
            return VkPhysicalDeviceMemoryProperties.memoryTypeCount(segment);
        }
        
        public MemorySegment memoryTypes(int index) {
            return VkPhysicalDeviceMemoryProperties.memoryTypes(segment, index);
        }
    }
    
    public static class VkFormatPropertiesWrapper {
        private final MemorySegment segment;
        
        VkFormatPropertiesWrapper(MemorySegment segment) {
            this.segment = segment;
        }
        
        public int optimalTilingFeatures() {
            return VkFormatProperties.optimalTilingFeatures(segment);
        }
        
        public int linearTilingFeatures() {
            return VkFormatProperties.linearTilingFeatures(segment);
        }
        
        public int bufferFeatures() {
            return VkFormatProperties.bufferFeatures(segment);
        }
    }
}