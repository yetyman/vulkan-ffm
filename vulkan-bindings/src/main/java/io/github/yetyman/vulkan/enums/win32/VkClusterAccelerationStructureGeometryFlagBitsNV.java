package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkClusterAccelerationStructureGeometryFlagBitsNV
 * Generated from jextract bindings
 */
public record VkClusterAccelerationStructureGeometryFlagBitsNV(int value) {

    public static final VkClusterAccelerationStructureGeometryFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_GEOMETRY_CULL_DISABLE_BIT_NV = new VkClusterAccelerationStructureGeometryFlagBitsNV(1);
    public static final VkClusterAccelerationStructureGeometryFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_GEOMETRY_FLAG_BITS_MAX_ENUM_NV = new VkClusterAccelerationStructureGeometryFlagBitsNV(2147483647);
    public static final VkClusterAccelerationStructureGeometryFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_GEOMETRY_NO_DUPLICATE_ANYHIT_INVOCATION_BIT_NV = new VkClusterAccelerationStructureGeometryFlagBitsNV(2);
    public static final VkClusterAccelerationStructureGeometryFlagBitsNV VK_CLUSTER_ACCELERATION_STRUCTURE_GEOMETRY_OPAQUE_BIT_NV = new VkClusterAccelerationStructureGeometryFlagBitsNV(4);

    public static VkClusterAccelerationStructureGeometryFlagBitsNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_CLUSTER_ACCELERATION_STRUCTURE_GEOMETRY_CULL_DISABLE_BIT_NV;
            case 2147483647 -> VK_CLUSTER_ACCELERATION_STRUCTURE_GEOMETRY_FLAG_BITS_MAX_ENUM_NV;
            case 2 -> VK_CLUSTER_ACCELERATION_STRUCTURE_GEOMETRY_NO_DUPLICATE_ANYHIT_INVOCATION_BIT_NV;
            case 4 -> VK_CLUSTER_ACCELERATION_STRUCTURE_GEOMETRY_OPAQUE_BIT_NV;
            default -> new VkClusterAccelerationStructureGeometryFlagBitsNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
