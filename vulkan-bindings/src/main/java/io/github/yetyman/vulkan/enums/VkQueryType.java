package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkQueryType
 * Generated from jextract bindings
 */
public record VkQueryType(int value) {

    public static final VkQueryType VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR = new VkQueryType(1000150000);
    public static final VkQueryType VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_NV = new VkQueryType(1000165000);
    public static final VkQueryType VK_QUERY_TYPE_ACCELERATION_STRUCTURE_SERIALIZATION_BOTTOM_LEVEL_POINTERS_KHR = new VkQueryType(1000386000);
    public static final VkQueryType VK_QUERY_TYPE_ACCELERATION_STRUCTURE_SERIALIZATION_SIZE_KHR = new VkQueryType(1000150001);
    public static final VkQueryType VK_QUERY_TYPE_ACCELERATION_STRUCTURE_SIZE_KHR = new VkQueryType(1000386001);
    public static final VkQueryType VK_QUERY_TYPE_MAX_ENUM = new VkQueryType(2147483647);
    public static final VkQueryType VK_QUERY_TYPE_MESH_PRIMITIVES_GENERATED_EXT = new VkQueryType(1000328000);
    public static final VkQueryType VK_QUERY_TYPE_MICROMAP_COMPACTED_SIZE_EXT = new VkQueryType(1000396001);
    public static final VkQueryType VK_QUERY_TYPE_MICROMAP_SERIALIZATION_SIZE_EXT = new VkQueryType(1000396000);
    public static final VkQueryType VK_QUERY_TYPE_OCCLUSION = new VkQueryType(0);
    public static final VkQueryType VK_QUERY_TYPE_PERFORMANCE_QUERY_INTEL = new VkQueryType(1000210000);
    public static final VkQueryType VK_QUERY_TYPE_PERFORMANCE_QUERY_KHR = new VkQueryType(1000116000);
    public static final VkQueryType VK_QUERY_TYPE_PIPELINE_STATISTICS = new VkQueryType(1);
    public static final VkQueryType VK_QUERY_TYPE_PRIMITIVES_GENERATED_EXT = new VkQueryType(1000382000);
    public static final VkQueryType VK_QUERY_TYPE_RESULT_STATUS_ONLY_KHR = new VkQueryType(1000023000);
    public static final VkQueryType VK_QUERY_TYPE_TIMESTAMP = new VkQueryType(2);
    public static final VkQueryType VK_QUERY_TYPE_TRANSFORM_FEEDBACK_STREAM_EXT = new VkQueryType(1000028004);
    public static final VkQueryType VK_QUERY_TYPE_VIDEO_ENCODE_FEEDBACK_KHR = new VkQueryType(1000299000);

    public static VkQueryType fromValue(int value) {
        return switch (value) {
            case 1000150000 -> VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR;
            case 1000165000 -> VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_NV;
            case 1000386000 -> VK_QUERY_TYPE_ACCELERATION_STRUCTURE_SERIALIZATION_BOTTOM_LEVEL_POINTERS_KHR;
            case 1000150001 -> VK_QUERY_TYPE_ACCELERATION_STRUCTURE_SERIALIZATION_SIZE_KHR;
            case 1000386001 -> VK_QUERY_TYPE_ACCELERATION_STRUCTURE_SIZE_KHR;
            case 2147483647 -> VK_QUERY_TYPE_MAX_ENUM;
            case 1000328000 -> VK_QUERY_TYPE_MESH_PRIMITIVES_GENERATED_EXT;
            case 1000396001 -> VK_QUERY_TYPE_MICROMAP_COMPACTED_SIZE_EXT;
            case 1000396000 -> VK_QUERY_TYPE_MICROMAP_SERIALIZATION_SIZE_EXT;
            case 0 -> VK_QUERY_TYPE_OCCLUSION;
            case 1000210000 -> VK_QUERY_TYPE_PERFORMANCE_QUERY_INTEL;
            case 1000116000 -> VK_QUERY_TYPE_PERFORMANCE_QUERY_KHR;
            case 1 -> VK_QUERY_TYPE_PIPELINE_STATISTICS;
            case 1000382000 -> VK_QUERY_TYPE_PRIMITIVES_GENERATED_EXT;
            case 1000023000 -> VK_QUERY_TYPE_RESULT_STATUS_ONLY_KHR;
            case 2 -> VK_QUERY_TYPE_TIMESTAMP;
            case 1000028004 -> VK_QUERY_TYPE_TRANSFORM_FEEDBACK_STREAM_EXT;
            case 1000299000 -> VK_QUERY_TYPE_VIDEO_ENCODE_FEEDBACK_KHR;
            default -> new VkQueryType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
