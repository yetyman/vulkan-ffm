package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkSubgroupFeatureFlagBits
 * Generated from jextract bindings
 */
public record VkSubgroupFeatureFlagBits(int value) {

    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_ARITHMETIC_BIT = new VkSubgroupFeatureFlagBits(4);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_BALLOT_BIT = new VkSubgroupFeatureFlagBits(8);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_BASIC_BIT = new VkSubgroupFeatureFlagBits(1);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_CLUSTERED_BIT = new VkSubgroupFeatureFlagBits(64);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_FLAG_BITS_MAX_ENUM = new VkSubgroupFeatureFlagBits(2147483647);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_PARTITIONED_BIT_NV = new VkSubgroupFeatureFlagBits(256);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_QUAD_BIT = new VkSubgroupFeatureFlagBits(128);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_ROTATE_BIT = new VkSubgroupFeatureFlagBits(512);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_ROTATE_BIT_KHR = new VkSubgroupFeatureFlagBits(512);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_ROTATE_CLUSTERED_BIT = new VkSubgroupFeatureFlagBits(1024);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_ROTATE_CLUSTERED_BIT_KHR = new VkSubgroupFeatureFlagBits(1024);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_SHUFFLE_BIT = new VkSubgroupFeatureFlagBits(16);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_SHUFFLE_RELATIVE_BIT = new VkSubgroupFeatureFlagBits(32);
    public static final VkSubgroupFeatureFlagBits VK_SUBGROUP_FEATURE_VOTE_BIT = new VkSubgroupFeatureFlagBits(2);

    public static VkSubgroupFeatureFlagBits fromValue(int value) {
        return switch (value) {
            case 4 -> VK_SUBGROUP_FEATURE_ARITHMETIC_BIT;
            case 8 -> VK_SUBGROUP_FEATURE_BALLOT_BIT;
            case 1 -> VK_SUBGROUP_FEATURE_BASIC_BIT;
            case 64 -> VK_SUBGROUP_FEATURE_CLUSTERED_BIT;
            case 2147483647 -> VK_SUBGROUP_FEATURE_FLAG_BITS_MAX_ENUM;
            case 256 -> VK_SUBGROUP_FEATURE_PARTITIONED_BIT_NV;
            case 128 -> VK_SUBGROUP_FEATURE_QUAD_BIT;
            case 512 -> VK_SUBGROUP_FEATURE_ROTATE_BIT;
            case 1024 -> VK_SUBGROUP_FEATURE_ROTATE_CLUSTERED_BIT;
            case 16 -> VK_SUBGROUP_FEATURE_SHUFFLE_BIT;
            case 32 -> VK_SUBGROUP_FEATURE_SHUFFLE_RELATIVE_BIT;
            case 2 -> VK_SUBGROUP_FEATURE_VOTE_BIT;
            default -> new VkSubgroupFeatureFlagBits(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
