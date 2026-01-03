package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkDriverId
 * Generated from jextract bindings
 */
public record VkDriverId(int value) {

    public static final VkDriverId VK_DRIVER_ID_AMD_OPEN_SOURCE = new VkDriverId(2);
    public static final VkDriverId VK_DRIVER_ID_AMD_OPEN_SOURCE_KHR = new VkDriverId(2);
    public static final VkDriverId VK_DRIVER_ID_AMD_PROPRIETARY = new VkDriverId(1);
    public static final VkDriverId VK_DRIVER_ID_AMD_PROPRIETARY_KHR = new VkDriverId(1);
    public static final VkDriverId VK_DRIVER_ID_ARM_PROPRIETARY = new VkDriverId(9);
    public static final VkDriverId VK_DRIVER_ID_ARM_PROPRIETARY_KHR = new VkDriverId(9);
    public static final VkDriverId VK_DRIVER_ID_BROADCOM_PROPRIETARY = new VkDriverId(12);
    public static final VkDriverId VK_DRIVER_ID_BROADCOM_PROPRIETARY_KHR = new VkDriverId(12);
    public static final VkDriverId VK_DRIVER_ID_COREAVI_PROPRIETARY = new VkDriverId(15);
    public static final VkDriverId VK_DRIVER_ID_GGP_PROPRIETARY = new VkDriverId(11);
    public static final VkDriverId VK_DRIVER_ID_GGP_PROPRIETARY_KHR = new VkDriverId(11);
    public static final VkDriverId VK_DRIVER_ID_GOOGLE_SWIFTSHADER = new VkDriverId(10);
    public static final VkDriverId VK_DRIVER_ID_GOOGLE_SWIFTSHADER_KHR = new VkDriverId(10);
    public static final VkDriverId VK_DRIVER_ID_IMAGINATION_OPEN_SOURCE_MESA = new VkDriverId(25);
    public static final VkDriverId VK_DRIVER_ID_IMAGINATION_PROPRIETARY = new VkDriverId(7);
    public static final VkDriverId VK_DRIVER_ID_IMAGINATION_PROPRIETARY_KHR = new VkDriverId(7);
    public static final VkDriverId VK_DRIVER_ID_INTEL_OPEN_SOURCE_MESA = new VkDriverId(6);
    public static final VkDriverId VK_DRIVER_ID_INTEL_OPEN_SOURCE_MESA_KHR = new VkDriverId(6);
    public static final VkDriverId VK_DRIVER_ID_INTEL_PROPRIETARY_WINDOWS = new VkDriverId(5);
    public static final VkDriverId VK_DRIVER_ID_INTEL_PROPRIETARY_WINDOWS_KHR = new VkDriverId(5);
    public static final VkDriverId VK_DRIVER_ID_JUICE_PROPRIETARY = new VkDriverId(16);
    public static final VkDriverId VK_DRIVER_ID_MAX_ENUM = new VkDriverId(2147483647);
    public static final VkDriverId VK_DRIVER_ID_MESA_DOZEN = new VkDriverId(23);
    public static final VkDriverId VK_DRIVER_ID_MESA_HONEYKRISP = new VkDriverId(26);
    public static final VkDriverId VK_DRIVER_ID_MESA_KOSMICKRISP = new VkDriverId(28);
    public static final VkDriverId VK_DRIVER_ID_MESA_LLVMPIPE = new VkDriverId(13);
    public static final VkDriverId VK_DRIVER_ID_MESA_NVK = new VkDriverId(24);
    public static final VkDriverId VK_DRIVER_ID_MESA_PANVK = new VkDriverId(20);
    public static final VkDriverId VK_DRIVER_ID_MESA_RADV = new VkDriverId(3);
    public static final VkDriverId VK_DRIVER_ID_MESA_RADV_KHR = new VkDriverId(3);
    public static final VkDriverId VK_DRIVER_ID_MESA_TURNIP = new VkDriverId(18);
    public static final VkDriverId VK_DRIVER_ID_MESA_V3DV = new VkDriverId(19);
    public static final VkDriverId VK_DRIVER_ID_MESA_VENUS = new VkDriverId(22);
    public static final VkDriverId VK_DRIVER_ID_MOLTENVK = new VkDriverId(14);
    public static final VkDriverId VK_DRIVER_ID_NVIDIA_PROPRIETARY = new VkDriverId(4);
    public static final VkDriverId VK_DRIVER_ID_NVIDIA_PROPRIETARY_KHR = new VkDriverId(4);
    public static final VkDriverId VK_DRIVER_ID_QUALCOMM_PROPRIETARY = new VkDriverId(8);
    public static final VkDriverId VK_DRIVER_ID_QUALCOMM_PROPRIETARY_KHR = new VkDriverId(8);
    public static final VkDriverId VK_DRIVER_ID_SAMSUNG_PROPRIETARY = new VkDriverId(21);
    public static final VkDriverId VK_DRIVER_ID_VERISILICON_PROPRIETARY = new VkDriverId(17);
    public static final VkDriverId VK_DRIVER_ID_VULKAN_SC_EMULATION_ON_VULKAN = new VkDriverId(27);

    public static VkDriverId fromValue(int value) {
        return switch (value) {
            case 2 -> VK_DRIVER_ID_AMD_OPEN_SOURCE;
            case 1 -> VK_DRIVER_ID_AMD_PROPRIETARY;
            case 9 -> VK_DRIVER_ID_ARM_PROPRIETARY;
            case 12 -> VK_DRIVER_ID_BROADCOM_PROPRIETARY;
            case 15 -> VK_DRIVER_ID_COREAVI_PROPRIETARY;
            case 11 -> VK_DRIVER_ID_GGP_PROPRIETARY;
            case 10 -> VK_DRIVER_ID_GOOGLE_SWIFTSHADER;
            case 25 -> VK_DRIVER_ID_IMAGINATION_OPEN_SOURCE_MESA;
            case 7 -> VK_DRIVER_ID_IMAGINATION_PROPRIETARY;
            case 6 -> VK_DRIVER_ID_INTEL_OPEN_SOURCE_MESA;
            case 5 -> VK_DRIVER_ID_INTEL_PROPRIETARY_WINDOWS;
            case 16 -> VK_DRIVER_ID_JUICE_PROPRIETARY;
            case 2147483647 -> VK_DRIVER_ID_MAX_ENUM;
            case 23 -> VK_DRIVER_ID_MESA_DOZEN;
            case 26 -> VK_DRIVER_ID_MESA_HONEYKRISP;
            case 28 -> VK_DRIVER_ID_MESA_KOSMICKRISP;
            case 13 -> VK_DRIVER_ID_MESA_LLVMPIPE;
            case 24 -> VK_DRIVER_ID_MESA_NVK;
            case 20 -> VK_DRIVER_ID_MESA_PANVK;
            case 3 -> VK_DRIVER_ID_MESA_RADV;
            case 18 -> VK_DRIVER_ID_MESA_TURNIP;
            case 19 -> VK_DRIVER_ID_MESA_V3DV;
            case 22 -> VK_DRIVER_ID_MESA_VENUS;
            case 14 -> VK_DRIVER_ID_MOLTENVK;
            case 4 -> VK_DRIVER_ID_NVIDIA_PROPRIETARY;
            case 8 -> VK_DRIVER_ID_QUALCOMM_PROPRIETARY;
            case 21 -> VK_DRIVER_ID_SAMSUNG_PROPRIETARY;
            case 17 -> VK_DRIVER_ID_VERISILICON_PROPRIETARY;
            case 27 -> VK_DRIVER_ID_VULKAN_SC_EMULATION_ON_VULKAN;
            default -> new VkDriverId(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
