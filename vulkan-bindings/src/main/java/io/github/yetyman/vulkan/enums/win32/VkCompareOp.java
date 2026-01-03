package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkCompareOp
 * Generated from jextract bindings
 */
public record VkCompareOp(int value) {

    public static final VkCompareOp VK_COMPARE_OP_ALWAYS = new VkCompareOp(7);
    public static final VkCompareOp VK_COMPARE_OP_EQUAL = new VkCompareOp(2);
    public static final VkCompareOp VK_COMPARE_OP_GREATER = new VkCompareOp(4);
    public static final VkCompareOp VK_COMPARE_OP_GREATER_OR_EQUAL = new VkCompareOp(6);
    public static final VkCompareOp VK_COMPARE_OP_LESS = new VkCompareOp(1);
    public static final VkCompareOp VK_COMPARE_OP_LESS_OR_EQUAL = new VkCompareOp(3);
    public static final VkCompareOp VK_COMPARE_OP_MAX_ENUM = new VkCompareOp(2147483647);
    public static final VkCompareOp VK_COMPARE_OP_NEVER = new VkCompareOp(0);
    public static final VkCompareOp VK_COMPARE_OP_NOT_EQUAL = new VkCompareOp(5);

    public static VkCompareOp fromValue(int value) {
        return switch (value) {
            case 7 -> VK_COMPARE_OP_ALWAYS;
            case 2 -> VK_COMPARE_OP_EQUAL;
            case 4 -> VK_COMPARE_OP_GREATER;
            case 6 -> VK_COMPARE_OP_GREATER_OR_EQUAL;
            case 1 -> VK_COMPARE_OP_LESS;
            case 3 -> VK_COMPARE_OP_LESS_OR_EQUAL;
            case 2147483647 -> VK_COMPARE_OP_MAX_ENUM;
            case 0 -> VK_COMPARE_OP_NEVER;
            case 5 -> VK_COMPARE_OP_NOT_EQUAL;
            default -> new VkCompareOp(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
