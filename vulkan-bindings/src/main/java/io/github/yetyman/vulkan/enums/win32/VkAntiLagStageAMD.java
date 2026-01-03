package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkAntiLagStageAMD
 * Generated from jextract bindings
 */
public record VkAntiLagStageAMD(int value) {

    public static final VkAntiLagStageAMD VK_ANTI_LAG_STAGE_INPUT_AMD = new VkAntiLagStageAMD(0);
    public static final VkAntiLagStageAMD VK_ANTI_LAG_STAGE_MAX_ENUM_AMD = new VkAntiLagStageAMD(2147483647);
    public static final VkAntiLagStageAMD VK_ANTI_LAG_STAGE_PRESENT_AMD = new VkAntiLagStageAMD(1);

    public static VkAntiLagStageAMD fromValue(int value) {
        return switch (value) {
            case 0 -> VK_ANTI_LAG_STAGE_INPUT_AMD;
            case 2147483647 -> VK_ANTI_LAG_STAGE_MAX_ENUM_AMD;
            case 1 -> VK_ANTI_LAG_STAGE_PRESENT_AMD;
            default -> new VkAntiLagStageAMD(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
