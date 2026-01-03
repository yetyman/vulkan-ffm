package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkComponentTypeKHR
 * Generated from jextract bindings
 */
public record VkComponentTypeKHR(int value) {

    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_BFLOAT16_KHR = new VkComponentTypeKHR(1000141000);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT16_KHR = new VkComponentTypeKHR(0);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT16_NV = VK_COMPONENT_TYPE_FLOAT16_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT32_KHR = new VkComponentTypeKHR(1);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT32_NV = VK_COMPONENT_TYPE_FLOAT32_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT64_KHR = new VkComponentTypeKHR(2);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT64_NV = VK_COMPONENT_TYPE_FLOAT64_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT8_E4M3_EXT = new VkComponentTypeKHR(1000491002);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT8_E5M2_EXT = new VkComponentTypeKHR(1000491003);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT_E4M3_NV = VK_COMPONENT_TYPE_FLOAT8_E4M3_EXT;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_FLOAT_E5M2_NV = VK_COMPONENT_TYPE_FLOAT8_E5M2_EXT;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_MAX_ENUM_KHR = new VkComponentTypeKHR(2147483647);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT16_KHR = new VkComponentTypeKHR(4);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT16_NV = VK_COMPONENT_TYPE_SINT16_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT32_KHR = new VkComponentTypeKHR(5);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT32_NV = VK_COMPONENT_TYPE_SINT32_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT64_KHR = new VkComponentTypeKHR(6);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT64_NV = VK_COMPONENT_TYPE_SINT64_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT8_KHR = new VkComponentTypeKHR(3);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT8_NV = VK_COMPONENT_TYPE_SINT8_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_SINT8_PACKED_NV = new VkComponentTypeKHR(1000491000);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT16_KHR = new VkComponentTypeKHR(8);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT16_NV = VK_COMPONENT_TYPE_UINT16_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT32_KHR = new VkComponentTypeKHR(9);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT32_NV = VK_COMPONENT_TYPE_UINT32_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT64_KHR = new VkComponentTypeKHR(10);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT64_NV = VK_COMPONENT_TYPE_UINT64_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT8_KHR = new VkComponentTypeKHR(7);
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT8_NV = VK_COMPONENT_TYPE_UINT8_KHR;
    public static final VkComponentTypeKHR VK_COMPONENT_TYPE_UINT8_PACKED_NV = new VkComponentTypeKHR(1000491001);

    public static VkComponentTypeKHR fromValue(int value) {
        return switch (value) {
            case 1000141000 -> VK_COMPONENT_TYPE_BFLOAT16_KHR;
            case 0 -> VK_COMPONENT_TYPE_FLOAT16_NV;
            case 1 -> VK_COMPONENT_TYPE_FLOAT32_NV;
            case 2 -> VK_COMPONENT_TYPE_FLOAT64_NV;
            case 1000491002 -> VK_COMPONENT_TYPE_FLOAT_E4M3_NV;
            case 1000491003 -> VK_COMPONENT_TYPE_FLOAT_E5M2_NV;
            case 2147483647 -> VK_COMPONENT_TYPE_MAX_ENUM_KHR;
            case 4 -> VK_COMPONENT_TYPE_SINT16_NV;
            case 5 -> VK_COMPONENT_TYPE_SINT32_NV;
            case 6 -> VK_COMPONENT_TYPE_SINT64_NV;
            case 3 -> VK_COMPONENT_TYPE_SINT8_NV;
            case 1000491000 -> VK_COMPONENT_TYPE_SINT8_PACKED_NV;
            case 8 -> VK_COMPONENT_TYPE_UINT16_NV;
            case 9 -> VK_COMPONENT_TYPE_UINT32_NV;
            case 10 -> VK_COMPONENT_TYPE_UINT64_NV;
            case 7 -> VK_COMPONENT_TYPE_UINT8_NV;
            case 1000491001 -> VK_COMPONENT_TYPE_UINT8_PACKED_NV;
            default -> new VkComponentTypeKHR(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
