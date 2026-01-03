package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkVendorId
 * Generated from jextract bindings
 */
public record VkVendorId(int value) {

    public static final VkVendorId VK_VENDOR_ID_CODEPLAY = new VkVendorId(65540);
    public static final VkVendorId VK_VENDOR_ID_KAZAN = new VkVendorId(65539);
    public static final VkVendorId VK_VENDOR_ID_KHRONOS = new VkVendorId(65536);
    public static final VkVendorId VK_VENDOR_ID_MAX_ENUM = new VkVendorId(2147483647);
    public static final VkVendorId VK_VENDOR_ID_MESA = new VkVendorId(65541);
    public static final VkVendorId VK_VENDOR_ID_MOBILEYE = new VkVendorId(65543);
    public static final VkVendorId VK_VENDOR_ID_POCL = new VkVendorId(65542);
    public static final VkVendorId VK_VENDOR_ID_VIV = new VkVendorId(65537);
    public static final VkVendorId VK_VENDOR_ID_VSI = new VkVendorId(65538);

    public static VkVendorId fromValue(int value) {
        return switch (value) {
            case 65540 -> VK_VENDOR_ID_CODEPLAY;
            case 65539 -> VK_VENDOR_ID_KAZAN;
            case 65536 -> VK_VENDOR_ID_KHRONOS;
            case 2147483647 -> VK_VENDOR_ID_MAX_ENUM;
            case 65541 -> VK_VENDOR_ID_MESA;
            case 65543 -> VK_VENDOR_ID_MOBILEYE;
            case 65542 -> VK_VENDOR_ID_POCL;
            case 65537 -> VK_VENDOR_ID_VIV;
            case 65538 -> VK_VENDOR_ID_VSI;
            default -> new VkVendorId(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
