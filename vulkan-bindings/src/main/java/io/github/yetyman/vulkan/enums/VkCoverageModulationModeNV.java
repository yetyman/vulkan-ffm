package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkCoverageModulationModeNV
 * Generated from jextract bindings
 */
public record VkCoverageModulationModeNV(int value) {

    public static final VkCoverageModulationModeNV VK_COVERAGE_MODULATION_MODE_ALPHA_NV = new VkCoverageModulationModeNV(2);
    public static final VkCoverageModulationModeNV VK_COVERAGE_MODULATION_MODE_MAX_ENUM_NV = new VkCoverageModulationModeNV(2147483647);
    public static final VkCoverageModulationModeNV VK_COVERAGE_MODULATION_MODE_NONE_NV = new VkCoverageModulationModeNV(0);
    public static final VkCoverageModulationModeNV VK_COVERAGE_MODULATION_MODE_RGBA_NV = new VkCoverageModulationModeNV(3);
    public static final VkCoverageModulationModeNV VK_COVERAGE_MODULATION_MODE_RGB_NV = new VkCoverageModulationModeNV(1);

    public static VkCoverageModulationModeNV fromValue(int value) {
        return switch (value) {
            case 2 -> VK_COVERAGE_MODULATION_MODE_ALPHA_NV;
            case 2147483647 -> VK_COVERAGE_MODULATION_MODE_MAX_ENUM_NV;
            case 0 -> VK_COVERAGE_MODULATION_MODE_NONE_NV;
            case 3 -> VK_COVERAGE_MODULATION_MODE_RGBA_NV;
            case 1 -> VK_COVERAGE_MODULATION_MODE_RGB_NV;
            default -> new VkCoverageModulationModeNV(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
