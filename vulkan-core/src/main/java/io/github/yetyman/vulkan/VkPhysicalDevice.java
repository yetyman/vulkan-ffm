package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Wrapper for VkPhysicalDevice with common operations as instance methods
 */
public class VkPhysicalDevice {
    private final MemorySegment handle;
    private MemorySegment cachedProperties;
    private MemorySegment cachedFeatures;
    private MemorySegment cachedMemoryProperties;
    
    private VkPhysicalDevice(MemorySegment handle) {
        this.handle = handle;
    }
    
    public static VkPhysicalDevice wrap(MemorySegment physicalDeviceHandle) {
        return new VkPhysicalDevice(physicalDeviceHandle);
    }
    
    public MemorySegment handle() {
        return handle;
    }
    
    private void ensureCached() {
        if (cachedProperties == null) {
            Arena globalArena = Arena.global();
            
            // Cache properties (includes limits, sparse properties, etc.)
            cachedProperties = VkPhysicalDeviceProperties.allocate(globalArena);
            VulkanFFM.vkGetPhysicalDeviceProperties(handle, cachedProperties);
            
            // Cache features (includes sparse residency, etc.)
            cachedFeatures = VkPhysicalDeviceFeatures.allocate(globalArena);
            VulkanFFM.vkGetPhysicalDeviceFeatures(handle, cachedFeatures);
            
            // Cache memory properties (memory types and heaps)
            cachedMemoryProperties = VkPhysicalDeviceMemoryProperties.allocate(globalArena);
            VulkanFFM.vkGetPhysicalDeviceMemoryProperties(handle, cachedMemoryProperties);
        }
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
    
    public VkPhysicalDeviceMemoryPropertiesWrapper getMemoryProperties() {
        ensureCached();
        return new VkPhysicalDeviceMemoryPropertiesWrapper(cachedMemoryProperties);
    }
    
    public int findMemoryType(int typeBits, int properties) {
        ensureCached();
        
        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(cachedMemoryProperties);
        for (int i = 0; i < typeCount; i++) {
            if ((typeBits & (1 << i)) != 0) {
                MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(cachedMemoryProperties, i);
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
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.bufferImageGranularity(limits);
    }
    
    /**
     * Returns whether the device supports sparse buffer residency.
     * When true, accessing uncommitted sparse buffer regions returns zero.
     * When false, accessing uncommitted regions has undefined behavior.
     * Cached after first query.
     */
    public boolean supportsSparseResidencyBuffer() {
        ensureCached();
        return VkPhysicalDeviceFeatures.sparseResidencyBuffer(cachedFeatures) != 0;
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