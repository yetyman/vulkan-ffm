package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkVideoEncodeRgbChromaOffsetFlagBitsVALVE
 * Generated from jextract bindings
 */
public record VkVideoEncodeRgbChromaOffsetFlagBitsVALVE(int value) {

    public static final VkVideoEncodeRgbChromaOffsetFlagBitsVALVE VK_VIDEO_ENCODE_RGB_CHROMA_OFFSET_COSITED_EVEN_BIT_VALVE = new VkVideoEncodeRgbChromaOffsetFlagBitsVALVE(1);
    public static final VkVideoEncodeRgbChromaOffsetFlagBitsVALVE VK_VIDEO_ENCODE_RGB_CHROMA_OFFSET_FLAG_BITS_MAX_ENUM_VALVE = new VkVideoEncodeRgbChromaOffsetFlagBitsVALVE(2147483647);
    public static final VkVideoEncodeRgbChromaOffsetFlagBitsVALVE VK_VIDEO_ENCODE_RGB_CHROMA_OFFSET_MIDPOINT_BIT_VALVE = new VkVideoEncodeRgbChromaOffsetFlagBitsVALVE(2);

    public static VkVideoEncodeRgbChromaOffsetFlagBitsVALVE fromValue(int value) {
        return switch (value) {
            case 1 -> VK_VIDEO_ENCODE_RGB_CHROMA_OFFSET_COSITED_EVEN_BIT_VALVE;
            case 2147483647 -> VK_VIDEO_ENCODE_RGB_CHROMA_OFFSET_FLAG_BITS_MAX_ENUM_VALVE;
            case 2 -> VK_VIDEO_ENCODE_RGB_CHROMA_OFFSET_MIDPOINT_BIT_VALVE;
            default -> new VkVideoEncodeRgbChromaOffsetFlagBitsVALVE(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
