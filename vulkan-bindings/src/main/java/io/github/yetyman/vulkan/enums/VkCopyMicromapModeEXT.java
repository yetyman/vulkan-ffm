package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkCopyMicromapModeEXT
 * Generated from jextract bindings
 */
public record VkCopyMicromapModeEXT(int value) {

    public static final VkCopyMicromapModeEXT VK_COPY_MICROMAP_MODE_CLONE_EXT = new VkCopyMicromapModeEXT(0);
    public static final VkCopyMicromapModeEXT VK_COPY_MICROMAP_MODE_COMPACT_EXT = new VkCopyMicromapModeEXT(3);
    public static final VkCopyMicromapModeEXT VK_COPY_MICROMAP_MODE_DESERIALIZE_EXT = new VkCopyMicromapModeEXT(2);
    public static final VkCopyMicromapModeEXT VK_COPY_MICROMAP_MODE_MAX_ENUM_EXT = new VkCopyMicromapModeEXT(2147483647);
    public static final VkCopyMicromapModeEXT VK_COPY_MICROMAP_MODE_SERIALIZE_EXT = new VkCopyMicromapModeEXT(1);

    public static VkCopyMicromapModeEXT fromValue(int value) {
        return switch (value) {
            case 0 -> VK_COPY_MICROMAP_MODE_CLONE_EXT;
            case 3 -> VK_COPY_MICROMAP_MODE_COMPACT_EXT;
            case 2 -> VK_COPY_MICROMAP_MODE_DESERIALIZE_EXT;
            case 2147483647 -> VK_COPY_MICROMAP_MODE_MAX_ENUM_EXT;
            case 1 -> VK_COPY_MICROMAP_MODE_SERIALIZE_EXT;
            default -> new VkCopyMicromapModeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
