package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkImageLayout
 * Generated from jextract bindings
 */
public record VkImageLayout(int value) {

    public static final VkImageLayout VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT = new VkImageLayout(1000339000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL = new VkImageLayout(1000314001);
    public static final VkImageLayout VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR = VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL;
    public static final VkImageLayout VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL = new VkImageLayout(2);
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL = new VkImageLayout(1000241000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL_KHR = VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL;
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_STENCIL_READ_ONLY_OPTIMAL = new VkImageLayout(1000117001);
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_STENCIL_READ_ONLY_OPTIMAL_KHR = VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_STENCIL_READ_ONLY_OPTIMAL;
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL = new VkImageLayout(1000241001);
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL_KHR = VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL;
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_STENCIL_ATTACHMENT_OPTIMAL = new VkImageLayout(1000117000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_STENCIL_ATTACHMENT_OPTIMAL_KHR = VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_STENCIL_ATTACHMENT_OPTIMAL;
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL = new VkImageLayout(3);
    public static final VkImageLayout VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL = new VkImageLayout(4);
    public static final VkImageLayout VK_IMAGE_LAYOUT_FRAGMENT_DENSITY_MAP_OPTIMAL_EXT = new VkImageLayout(1000218000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_FRAGMENT_SHADING_RATE_ATTACHMENT_OPTIMAL_KHR = new VkImageLayout(1000164003);
    public static final VkImageLayout VK_IMAGE_LAYOUT_GENERAL = new VkImageLayout(1);
    public static final VkImageLayout VK_IMAGE_LAYOUT_MAX_ENUM = new VkImageLayout(2147483647);
    public static final VkImageLayout VK_IMAGE_LAYOUT_PREINITIALIZED = new VkImageLayout(8);
    public static final VkImageLayout VK_IMAGE_LAYOUT_PRESENT_SRC_KHR = new VkImageLayout(1000001002);
    public static final VkImageLayout VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL = new VkImageLayout(1000314000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL_KHR = VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL;
    public static final VkImageLayout VK_IMAGE_LAYOUT_RENDERING_LOCAL_READ = new VkImageLayout(1000232000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_RENDERING_LOCAL_READ_KHR = VK_IMAGE_LAYOUT_RENDERING_LOCAL_READ;
    public static final VkImageLayout VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL = new VkImageLayout(5);
    public static final VkImageLayout VK_IMAGE_LAYOUT_SHADING_RATE_OPTIMAL_NV = VK_IMAGE_LAYOUT_FRAGMENT_SHADING_RATE_ATTACHMENT_OPTIMAL_KHR;
    public static final VkImageLayout VK_IMAGE_LAYOUT_SHARED_PRESENT_KHR = new VkImageLayout(1000111000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_STENCIL_ATTACHMENT_OPTIMAL = new VkImageLayout(1000241002);
    public static final VkImageLayout VK_IMAGE_LAYOUT_STENCIL_ATTACHMENT_OPTIMAL_KHR = VK_IMAGE_LAYOUT_STENCIL_ATTACHMENT_OPTIMAL;
    public static final VkImageLayout VK_IMAGE_LAYOUT_STENCIL_READ_ONLY_OPTIMAL = new VkImageLayout(1000241003);
    public static final VkImageLayout VK_IMAGE_LAYOUT_STENCIL_READ_ONLY_OPTIMAL_KHR = VK_IMAGE_LAYOUT_STENCIL_READ_ONLY_OPTIMAL;
    public static final VkImageLayout VK_IMAGE_LAYOUT_TENSOR_ALIASING_ARM = new VkImageLayout(1000460000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL = new VkImageLayout(7);
    public static final VkImageLayout VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL = new VkImageLayout(6);
    public static final VkImageLayout VK_IMAGE_LAYOUT_UNDEFINED = new VkImageLayout(0);
    public static final VkImageLayout VK_IMAGE_LAYOUT_VIDEO_DECODE_DPB_KHR = new VkImageLayout(1000024002);
    public static final VkImageLayout VK_IMAGE_LAYOUT_VIDEO_DECODE_DST_KHR = new VkImageLayout(1000024000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_VIDEO_DECODE_SRC_KHR = new VkImageLayout(1000024001);
    public static final VkImageLayout VK_IMAGE_LAYOUT_VIDEO_ENCODE_DPB_KHR = new VkImageLayout(1000299002);
    public static final VkImageLayout VK_IMAGE_LAYOUT_VIDEO_ENCODE_DST_KHR = new VkImageLayout(1000299000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_VIDEO_ENCODE_QUANTIZATION_MAP_KHR = new VkImageLayout(1000553000);
    public static final VkImageLayout VK_IMAGE_LAYOUT_VIDEO_ENCODE_SRC_KHR = new VkImageLayout(1000299001);
    public static final VkImageLayout VK_IMAGE_LAYOUT_ZERO_INITIALIZED_EXT = new VkImageLayout(1000620000);

    public static VkImageLayout fromValue(int value) {
        return switch (value) {
            case 1000339000 -> VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT;
            case 1000314001 -> VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL;
            case 2 -> VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            case 1000241000 -> VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL;
            case 1000117001 -> VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_STENCIL_READ_ONLY_OPTIMAL;
            case 1000241001 -> VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL;
            case 1000117000 -> VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_STENCIL_ATTACHMENT_OPTIMAL;
            case 3 -> VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
            case 4 -> VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
            case 1000218000 -> VK_IMAGE_LAYOUT_FRAGMENT_DENSITY_MAP_OPTIMAL_EXT;
            case 1000164003 -> VK_IMAGE_LAYOUT_SHADING_RATE_OPTIMAL_NV;
            case 1 -> VK_IMAGE_LAYOUT_GENERAL;
            case 2147483647 -> VK_IMAGE_LAYOUT_MAX_ENUM;
            case 8 -> VK_IMAGE_LAYOUT_PREINITIALIZED;
            case 1000001002 -> VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
            case 1000314000 -> VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL;
            case 1000232000 -> VK_IMAGE_LAYOUT_RENDERING_LOCAL_READ;
            case 5 -> VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            case 1000111000 -> VK_IMAGE_LAYOUT_SHARED_PRESENT_KHR;
            case 1000241002 -> VK_IMAGE_LAYOUT_STENCIL_ATTACHMENT_OPTIMAL;
            case 1000241003 -> VK_IMAGE_LAYOUT_STENCIL_READ_ONLY_OPTIMAL;
            case 1000460000 -> VK_IMAGE_LAYOUT_TENSOR_ALIASING_ARM;
            case 7 -> VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            case 6 -> VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
            case 0 -> VK_IMAGE_LAYOUT_UNDEFINED;
            case 1000024002 -> VK_IMAGE_LAYOUT_VIDEO_DECODE_DPB_KHR;
            case 1000024000 -> VK_IMAGE_LAYOUT_VIDEO_DECODE_DST_KHR;
            case 1000024001 -> VK_IMAGE_LAYOUT_VIDEO_DECODE_SRC_KHR;
            case 1000299002 -> VK_IMAGE_LAYOUT_VIDEO_ENCODE_DPB_KHR;
            case 1000299000 -> VK_IMAGE_LAYOUT_VIDEO_ENCODE_DST_KHR;
            case 1000553000 -> VK_IMAGE_LAYOUT_VIDEO_ENCODE_QUANTIZATION_MAP_KHR;
            case 1000299001 -> VK_IMAGE_LAYOUT_VIDEO_ENCODE_SRC_KHR;
            case 1000620000 -> VK_IMAGE_LAYOUT_ZERO_INITIALIZED_EXT;
            default -> new VkImageLayout(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
