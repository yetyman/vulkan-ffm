package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkCubicFilterWeightsQCOM
 * Generated from jextract bindings
 */
public record VkCubicFilterWeightsQCOM(int value) {

    public static final VkCubicFilterWeightsQCOM VK_CUBIC_FILTER_WEIGHTS_B_SPLINE_QCOM = new VkCubicFilterWeightsQCOM(2);
    public static final VkCubicFilterWeightsQCOM VK_CUBIC_FILTER_WEIGHTS_CATMULL_ROM_QCOM = new VkCubicFilterWeightsQCOM(0);
    public static final VkCubicFilterWeightsQCOM VK_CUBIC_FILTER_WEIGHTS_MAX_ENUM_QCOM = new VkCubicFilterWeightsQCOM(2147483647);
    public static final VkCubicFilterWeightsQCOM VK_CUBIC_FILTER_WEIGHTS_MITCHELL_NETRAVALI_QCOM = new VkCubicFilterWeightsQCOM(3);
    public static final VkCubicFilterWeightsQCOM VK_CUBIC_FILTER_WEIGHTS_ZERO_TANGENT_CARDINAL_QCOM = new VkCubicFilterWeightsQCOM(1);

    public static VkCubicFilterWeightsQCOM fromValue(int value) {
        return switch (value) {
            case 2 -> VK_CUBIC_FILTER_WEIGHTS_B_SPLINE_QCOM;
            case 0 -> VK_CUBIC_FILTER_WEIGHTS_CATMULL_ROM_QCOM;
            case 2147483647 -> VK_CUBIC_FILTER_WEIGHTS_MAX_ENUM_QCOM;
            case 3 -> VK_CUBIC_FILTER_WEIGHTS_MITCHELL_NETRAVALI_QCOM;
            case 1 -> VK_CUBIC_FILTER_WEIGHTS_ZERO_TANGENT_CARDINAL_QCOM;
            default -> new VkCubicFilterWeightsQCOM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
