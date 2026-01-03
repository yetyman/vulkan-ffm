package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkLogicOp
 * Generated from jextract bindings
 */
public record VkLogicOp(int value) {

    public static final VkLogicOp VK_LOGIC_OP_AND = new VkLogicOp(1);
    public static final VkLogicOp VK_LOGIC_OP_AND_INVERTED = new VkLogicOp(4);
    public static final VkLogicOp VK_LOGIC_OP_AND_REVERSE = new VkLogicOp(2);
    public static final VkLogicOp VK_LOGIC_OP_CLEAR = new VkLogicOp(0);
    public static final VkLogicOp VK_LOGIC_OP_COPY = new VkLogicOp(3);
    public static final VkLogicOp VK_LOGIC_OP_COPY_INVERTED = new VkLogicOp(12);
    public static final VkLogicOp VK_LOGIC_OP_EQUIVALENT = new VkLogicOp(9);
    public static final VkLogicOp VK_LOGIC_OP_INVERT = new VkLogicOp(10);
    public static final VkLogicOp VK_LOGIC_OP_MAX_ENUM = new VkLogicOp(2147483647);
    public static final VkLogicOp VK_LOGIC_OP_NAND = new VkLogicOp(14);
    public static final VkLogicOp VK_LOGIC_OP_NOR = new VkLogicOp(8);
    public static final VkLogicOp VK_LOGIC_OP_NO_OP = new VkLogicOp(5);
    public static final VkLogicOp VK_LOGIC_OP_OR = new VkLogicOp(7);
    public static final VkLogicOp VK_LOGIC_OP_OR_INVERTED = new VkLogicOp(13);
    public static final VkLogicOp VK_LOGIC_OP_OR_REVERSE = new VkLogicOp(11);
    public static final VkLogicOp VK_LOGIC_OP_SET = new VkLogicOp(15);
    public static final VkLogicOp VK_LOGIC_OP_XOR = new VkLogicOp(6);

    public static VkLogicOp fromValue(int value) {
        return switch (value) {
            case 1 -> VK_LOGIC_OP_AND;
            case 4 -> VK_LOGIC_OP_AND_INVERTED;
            case 2 -> VK_LOGIC_OP_AND_REVERSE;
            case 0 -> VK_LOGIC_OP_CLEAR;
            case 3 -> VK_LOGIC_OP_COPY;
            case 12 -> VK_LOGIC_OP_COPY_INVERTED;
            case 9 -> VK_LOGIC_OP_EQUIVALENT;
            case 10 -> VK_LOGIC_OP_INVERT;
            case 2147483647 -> VK_LOGIC_OP_MAX_ENUM;
            case 14 -> VK_LOGIC_OP_NAND;
            case 8 -> VK_LOGIC_OP_NOR;
            case 5 -> VK_LOGIC_OP_NO_OP;
            case 7 -> VK_LOGIC_OP_OR;
            case 13 -> VK_LOGIC_OP_OR_INVERTED;
            case 11 -> VK_LOGIC_OP_OR_REVERSE;
            case 15 -> VK_LOGIC_OP_SET;
            case 6 -> VK_LOGIC_OP_XOR;
            default -> new VkLogicOp(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
