package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCoverageReductionModeNV
 * Generated from jextract bindings
 */
public record VkCoverageReductionModeNV(int value) {

    public static final VkCoverageReductionModeNV VK_COVERAGE_REDUCTION_MODE_MAX_ENUM_NV = new VkCoverageReductionModeNV(2147483647);
    public static final VkCoverageReductionModeNV VK_COVERAGE_REDUCTION_MODE_MERGE_NV = new VkCoverageReductionModeNV(0);
    public static final VkCoverageReductionModeNV VK_COVERAGE_REDUCTION_MODE_TRUNCATE_NV = new VkCoverageReductionModeNV(1);

    public static VkCoverageReductionModeNV fromValue(int value) {
        return switch (value) {
            case 2147483647 -> VK_COVERAGE_REDUCTION_MODE_MAX_ENUM_NV;
            case 0 -> VK_COVERAGE_REDUCTION_MODE_MERGE_NV;
            case 1 -> VK_COVERAGE_REDUCTION_MODE_TRUNCATE_NV;
            default -> new VkCoverageReductionModeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
