package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkPhysicalDeviceDataGraphOperationTypeARM
 * Generated from jextract bindings
 */
public record VkPhysicalDeviceDataGraphOperationTypeARM(int value) {

    public static final VkPhysicalDeviceDataGraphOperationTypeARM VK_PHYSICAL_DEVICE_DATA_GRAPH_OPERATION_TYPE_BUILTIN_MODEL_QCOM = new VkPhysicalDeviceDataGraphOperationTypeARM(1000629001);
    public static final VkPhysicalDeviceDataGraphOperationTypeARM VK_PHYSICAL_DEVICE_DATA_GRAPH_OPERATION_TYPE_MAX_ENUM_ARM = new VkPhysicalDeviceDataGraphOperationTypeARM(2147483647);
    public static final VkPhysicalDeviceDataGraphOperationTypeARM VK_PHYSICAL_DEVICE_DATA_GRAPH_OPERATION_TYPE_NEURAL_MODEL_QCOM = new VkPhysicalDeviceDataGraphOperationTypeARM(1000629000);
    public static final VkPhysicalDeviceDataGraphOperationTypeARM VK_PHYSICAL_DEVICE_DATA_GRAPH_OPERATION_TYPE_SPIRV_EXTENDED_INSTRUCTION_SET_ARM = new VkPhysicalDeviceDataGraphOperationTypeARM(0);

    public static VkPhysicalDeviceDataGraphOperationTypeARM fromValue(int value) {
        return switch (value) {
            case 1000629001 -> VK_PHYSICAL_DEVICE_DATA_GRAPH_OPERATION_TYPE_BUILTIN_MODEL_QCOM;
            case 2147483647 -> VK_PHYSICAL_DEVICE_DATA_GRAPH_OPERATION_TYPE_MAX_ENUM_ARM;
            case 1000629000 -> VK_PHYSICAL_DEVICE_DATA_GRAPH_OPERATION_TYPE_NEURAL_MODEL_QCOM;
            case 0 -> VK_PHYSICAL_DEVICE_DATA_GRAPH_OPERATION_TYPE_SPIRV_EXTENDED_INSTRUCTION_SET_ARM;
            default -> new VkPhysicalDeviceDataGraphOperationTypeARM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
