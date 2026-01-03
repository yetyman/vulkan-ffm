package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkAccelerationStructureMemoryRequirementsTypeNV
 * Generated from jextract bindings
 */
public record VkAccelerationStructureMemoryRequirementsTypeNV(int value) {

    public static final VkAccelerationStructureMemoryRequirementsTypeNV VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_BUILD_SCRATCH_NV = new VkAccelerationStructureMemoryRequirementsTypeNV(1);
    public static final VkAccelerationStructureMemoryRequirementsTypeNV VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_MAX_ENUM_NV = new VkAccelerationStructureMemoryRequirementsTypeNV(2147483647);
    public static final VkAccelerationStructureMemoryRequirementsTypeNV VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_OBJECT_NV = new VkAccelerationStructureMemoryRequirementsTypeNV(0);
    public static final VkAccelerationStructureMemoryRequirementsTypeNV VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_UPDATE_SCRATCH_NV = new VkAccelerationStructureMemoryRequirementsTypeNV(2);

    public static VkAccelerationStructureMemoryRequirementsTypeNV fromValue(int value) {
        return switch (value) {
            case 1 -> VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_BUILD_SCRATCH_NV;
            case 2147483647 -> VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_MAX_ENUM_NV;
            case 0 -> VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_OBJECT_NV;
            case 2 -> VK_ACCELERATION_STRUCTURE_MEMORY_REQUIREMENTS_TYPE_UPDATE_SCRATCH_NV;
            default -> new VkAccelerationStructureMemoryRequirementsTypeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
