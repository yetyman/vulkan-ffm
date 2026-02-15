package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkPhysicalDevice;

/**
 * Scale of data size for buffer allocation.
 * Thresholds are device-specific based on memory heap sizes.
 */
public enum DataScale {
    TRIVIAL,
    SMALL,
    MEDIUM,
    LARGE;
    
    /**
     * Determines the scale category for a given size in bytes.
     * Uses device-specific thresholds based on total device-local memory and sparse page size.
     */
    public static DataScale fromSize(long bytes, VkPhysicalDevice physicalDevice) {
        long deviceMemory = physicalDevice.getDeviceLocalMemorySize();
        long pageSize = physicalDevice.getSparsePageSize();
        
        // Trivial: < 1KB or < single sparse page
        if (bytes < 1024 || bytes < pageSize) return TRIVIAL;
        
        // Small: < 1MB or < 0.1% of device memory
        if (bytes < 1024 * 1024 || bytes < deviceMemory / 1000) return SMALL;
        
        // Medium: < 100MB or < 10% of device memory
        if (bytes < 100 * 1024 * 1024 || bytes < deviceMemory / 10) return MEDIUM;
        
        return LARGE;
    }
    
    /**
     * Fallback for when physical device is unavailable.
     * Uses fixed thresholds.
     */
    public static DataScale fromSize(long bytes) {
        if (bytes < 1024) return TRIVIAL;
        if (bytes < 1024 * 1024) return SMALL;
        if (bytes < 1024L * 1024 * 1024) return MEDIUM;
        return LARGE;
    }
}
