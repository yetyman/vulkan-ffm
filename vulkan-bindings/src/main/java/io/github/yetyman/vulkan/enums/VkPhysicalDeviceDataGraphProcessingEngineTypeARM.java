package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkPhysicalDeviceDataGraphProcessingEngineTypeARM
 * Generated from jextract bindings
 */
public record VkPhysicalDeviceDataGraphProcessingEngineTypeARM(int value) {

    public static final VkPhysicalDeviceDataGraphProcessingEngineTypeARM VK_PHYSICAL_DEVICE_DATA_GRAPH_PROCESSING_ENGINE_TYPE_COMPUTE_QCOM = new VkPhysicalDeviceDataGraphProcessingEngineTypeARM(1000629001);
    public static final VkPhysicalDeviceDataGraphProcessingEngineTypeARM VK_PHYSICAL_DEVICE_DATA_GRAPH_PROCESSING_ENGINE_TYPE_DEFAULT_ARM = new VkPhysicalDeviceDataGraphProcessingEngineTypeARM(0);
    public static final VkPhysicalDeviceDataGraphProcessingEngineTypeARM VK_PHYSICAL_DEVICE_DATA_GRAPH_PROCESSING_ENGINE_TYPE_MAX_ENUM_ARM = new VkPhysicalDeviceDataGraphProcessingEngineTypeARM(2147483647);
    public static final VkPhysicalDeviceDataGraphProcessingEngineTypeARM VK_PHYSICAL_DEVICE_DATA_GRAPH_PROCESSING_ENGINE_TYPE_NEURAL_QCOM = new VkPhysicalDeviceDataGraphProcessingEngineTypeARM(1000629000);

    public static VkPhysicalDeviceDataGraphProcessingEngineTypeARM fromValue(int value) {
        return switch (value) {
            case 1000629001 -> VK_PHYSICAL_DEVICE_DATA_GRAPH_PROCESSING_ENGINE_TYPE_COMPUTE_QCOM;
            case 0 -> VK_PHYSICAL_DEVICE_DATA_GRAPH_PROCESSING_ENGINE_TYPE_DEFAULT_ARM;
            case 2147483647 -> VK_PHYSICAL_DEVICE_DATA_GRAPH_PROCESSING_ENGINE_TYPE_MAX_ENUM_ARM;
            case 1000629000 -> VK_PHYSICAL_DEVICE_DATA_GRAPH_PROCESSING_ENGINE_TYPE_NEURAL_QCOM;
            default -> new VkPhysicalDeviceDataGraphProcessingEngineTypeARM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
