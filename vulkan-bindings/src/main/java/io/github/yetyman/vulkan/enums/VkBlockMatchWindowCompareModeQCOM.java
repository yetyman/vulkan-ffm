package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkBlockMatchWindowCompareModeQCOM
 * Generated from jextract bindings
 */
public record VkBlockMatchWindowCompareModeQCOM(int value) {

    public static final VkBlockMatchWindowCompareModeQCOM VK_BLOCK_MATCH_WINDOW_COMPARE_MODE_MAX_ENUM_QCOM = new VkBlockMatchWindowCompareModeQCOM(2147483647);
    public static final VkBlockMatchWindowCompareModeQCOM VK_BLOCK_MATCH_WINDOW_COMPARE_MODE_MAX_QCOM = new VkBlockMatchWindowCompareModeQCOM(1);
    public static final VkBlockMatchWindowCompareModeQCOM VK_BLOCK_MATCH_WINDOW_COMPARE_MODE_MIN_QCOM = new VkBlockMatchWindowCompareModeQCOM(0);

    public static VkBlockMatchWindowCompareModeQCOM fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_BLOCK_MATCH_WINDOW_COMPARE_MODE_MAX_ENUM_QCOM;
            case 1 -> VK_BLOCK_MATCH_WINDOW_COMPARE_MODE_MAX_QCOM;
            case 0 -> VK_BLOCK_MATCH_WINDOW_COMPARE_MODE_MIN_QCOM;
            default -> new VkBlockMatchWindowCompareModeQCOM(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
