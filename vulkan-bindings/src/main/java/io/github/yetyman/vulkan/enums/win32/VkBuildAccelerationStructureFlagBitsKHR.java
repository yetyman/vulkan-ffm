package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkBuildAccelerationStructureFlagBitsKHR
 * Generated from jextract bindings
 */
public record VkBuildAccelerationStructureFlagBitsKHR(int value) {

    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_CLUSTER_OPACITY_MICROMAPS_BIT_NV = new VkBuildAccelerationStructureFlagBitsKHR(4096);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_COMPACTION_BIT_KHR = new VkBuildAccelerationStructureFlagBitsKHR(2);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_COMPACTION_BIT_NV = new VkBuildAccelerationStructureFlagBitsKHR(2);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DATA_ACCESS_BIT_KHR = new VkBuildAccelerationStructureFlagBitsKHR(2048);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DATA_ACCESS_KHR = new VkBuildAccelerationStructureFlagBitsKHR(2048);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DISABLE_OPACITY_MICROMAPS_BIT_EXT = new VkBuildAccelerationStructureFlagBitsKHR(128);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DISABLE_OPACITY_MICROMAPS_EXT = new VkBuildAccelerationStructureFlagBitsKHR(128);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_DATA_UPDATE_BIT_EXT = new VkBuildAccelerationStructureFlagBitsKHR(256);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_DATA_UPDATE_EXT = new VkBuildAccelerationStructureFlagBitsKHR(256);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_UPDATE_BIT_EXT = new VkBuildAccelerationStructureFlagBitsKHR(64);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_UPDATE_EXT = new VkBuildAccelerationStructureFlagBitsKHR(64);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_UPDATE_BIT_KHR = new VkBuildAccelerationStructureFlagBitsKHR(1);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_UPDATE_BIT_NV = new VkBuildAccelerationStructureFlagBitsKHR(1);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_FLAG_BITS_MAX_ENUM_KHR = new VkBuildAccelerationStructureFlagBitsKHR(2147483647);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_LOW_MEMORY_BIT_KHR = new VkBuildAccelerationStructureFlagBitsKHR(16);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_LOW_MEMORY_BIT_NV = new VkBuildAccelerationStructureFlagBitsKHR(16);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_MOTION_BIT_NV = new VkBuildAccelerationStructureFlagBitsKHR(32);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_BUILD_BIT_KHR = new VkBuildAccelerationStructureFlagBitsKHR(8);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_BUILD_BIT_NV = new VkBuildAccelerationStructureFlagBitsKHR(8);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR = new VkBuildAccelerationStructureFlagBitsKHR(4);
    public static final VkBuildAccelerationStructureFlagBitsKHR VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_NV = new VkBuildAccelerationStructureFlagBitsKHR(4);

    public static VkBuildAccelerationStructureFlagBitsKHR fromValue(int value) {
        return switch (value) {
            case 4096 -> VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_CLUSTER_OPACITY_MICROMAPS_BIT_NV;
            case 2 -> VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_COMPACTION_BIT_NV;
            case 2048 -> VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DATA_ACCESS_KHR;
            case 128 -> VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DISABLE_OPACITY_MICROMAPS_EXT;
            case 256 -> VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_DATA_UPDATE_EXT;
            case 64 -> VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_UPDATE_EXT;
            case 1 -> VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_UPDATE_BIT_NV;
            case 2147483647 -> VK_BUILD_ACCELERATION_STRUCTURE_FLAG_BITS_MAX_ENUM_KHR;
            case 16 -> VK_BUILD_ACCELERATION_STRUCTURE_LOW_MEMORY_BIT_NV;
            case 32 -> VK_BUILD_ACCELERATION_STRUCTURE_MOTION_BIT_NV;
            case 8 -> VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_BUILD_BIT_NV;
            case 4 -> VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_NV;
            default -> new VkBuildAccelerationStructureFlagBitsKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
