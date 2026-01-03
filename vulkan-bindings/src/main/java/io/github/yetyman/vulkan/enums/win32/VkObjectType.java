package io.github.yetyman.vulkan.enums.win32;

import java.util.*;

/**
 * Type-safe constants for VkObjectType
 * Generated from jextract bindings
 */
public record VkObjectType(int value) {

    public static final VkObjectType VK_OBJECT_TYPE_ACCELERATION_STRUCTURE_KHR = new VkObjectType(1000150000);
    public static final VkObjectType VK_OBJECT_TYPE_ACCELERATION_STRUCTURE_NV = new VkObjectType(1000165000);
    public static final VkObjectType VK_OBJECT_TYPE_BUFFER = new VkObjectType(9);
    public static final VkObjectType VK_OBJECT_TYPE_BUFFER_COLLECTION_FUCHSIA = new VkObjectType(1000366000);
    public static final VkObjectType VK_OBJECT_TYPE_BUFFER_VIEW = new VkObjectType(13);
    public static final VkObjectType VK_OBJECT_TYPE_COMMAND_BUFFER = new VkObjectType(6);
    public static final VkObjectType VK_OBJECT_TYPE_COMMAND_POOL = new VkObjectType(25);
    public static final VkObjectType VK_OBJECT_TYPE_CU_FUNCTION_NVX = new VkObjectType(1000029001);
    public static final VkObjectType VK_OBJECT_TYPE_CU_MODULE_NVX = new VkObjectType(1000029000);
    public static final VkObjectType VK_OBJECT_TYPE_DATA_GRAPH_PIPELINE_SESSION_ARM = new VkObjectType(1000507000);
    public static final VkObjectType VK_OBJECT_TYPE_DEBUG_REPORT_CALLBACK_EXT = new VkObjectType(1000011000);
    public static final VkObjectType VK_OBJECT_TYPE_DEBUG_UTILS_MESSENGER_EXT = new VkObjectType(1000128000);
    public static final VkObjectType VK_OBJECT_TYPE_DEFERRED_OPERATION_KHR = new VkObjectType(1000268000);
    public static final VkObjectType VK_OBJECT_TYPE_DESCRIPTOR_POOL = new VkObjectType(22);
    public static final VkObjectType VK_OBJECT_TYPE_DESCRIPTOR_SET = new VkObjectType(23);
    public static final VkObjectType VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT = new VkObjectType(20);
    public static final VkObjectType VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE = new VkObjectType(1000085000);
    public static final VkObjectType VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_KHR = new VkObjectType(1000085000);
    public static final VkObjectType VK_OBJECT_TYPE_DEVICE = new VkObjectType(3);
    public static final VkObjectType VK_OBJECT_TYPE_DEVICE_MEMORY = new VkObjectType(8);
    public static final VkObjectType VK_OBJECT_TYPE_DISPLAY_KHR = new VkObjectType(1000002000);
    public static final VkObjectType VK_OBJECT_TYPE_DISPLAY_MODE_KHR = new VkObjectType(1000002001);
    public static final VkObjectType VK_OBJECT_TYPE_EVENT = new VkObjectType(11);
    public static final VkObjectType VK_OBJECT_TYPE_EXTERNAL_COMPUTE_QUEUE_NV = new VkObjectType(1000556000);
    public static final VkObjectType VK_OBJECT_TYPE_FENCE = new VkObjectType(7);
    public static final VkObjectType VK_OBJECT_TYPE_FRAMEBUFFER = new VkObjectType(24);
    public static final VkObjectType VK_OBJECT_TYPE_IMAGE = new VkObjectType(10);
    public static final VkObjectType VK_OBJECT_TYPE_IMAGE_VIEW = new VkObjectType(14);
    public static final VkObjectType VK_OBJECT_TYPE_INDIRECT_COMMANDS_LAYOUT_EXT = new VkObjectType(1000572000);
    public static final VkObjectType VK_OBJECT_TYPE_INDIRECT_COMMANDS_LAYOUT_NV = new VkObjectType(1000277000);
    public static final VkObjectType VK_OBJECT_TYPE_INDIRECT_EXECUTION_SET_EXT = new VkObjectType(1000572001);
    public static final VkObjectType VK_OBJECT_TYPE_INSTANCE = new VkObjectType(1);
    public static final VkObjectType VK_OBJECT_TYPE_MAX_ENUM = new VkObjectType(2147483647);
    public static final VkObjectType VK_OBJECT_TYPE_MICROMAP_EXT = new VkObjectType(1000396000);
    public static final VkObjectType VK_OBJECT_TYPE_OPTICAL_FLOW_SESSION_NV = new VkObjectType(1000464000);
    public static final VkObjectType VK_OBJECT_TYPE_PERFORMANCE_CONFIGURATION_INTEL = new VkObjectType(1000210000);
    public static final VkObjectType VK_OBJECT_TYPE_PHYSICAL_DEVICE = new VkObjectType(2);
    public static final VkObjectType VK_OBJECT_TYPE_PIPELINE = new VkObjectType(19);
    public static final VkObjectType VK_OBJECT_TYPE_PIPELINE_BINARY_KHR = new VkObjectType(1000483000);
    public static final VkObjectType VK_OBJECT_TYPE_PIPELINE_CACHE = new VkObjectType(16);
    public static final VkObjectType VK_OBJECT_TYPE_PIPELINE_LAYOUT = new VkObjectType(17);
    public static final VkObjectType VK_OBJECT_TYPE_PRIVATE_DATA_SLOT = new VkObjectType(1000295000);
    public static final VkObjectType VK_OBJECT_TYPE_PRIVATE_DATA_SLOT_EXT = new VkObjectType(1000295000);
    public static final VkObjectType VK_OBJECT_TYPE_QUERY_POOL = new VkObjectType(12);
    public static final VkObjectType VK_OBJECT_TYPE_QUEUE = new VkObjectType(4);
    public static final VkObjectType VK_OBJECT_TYPE_RENDER_PASS = new VkObjectType(18);
    public static final VkObjectType VK_OBJECT_TYPE_SAMPLER = new VkObjectType(21);
    public static final VkObjectType VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION = new VkObjectType(1000156000);
    public static final VkObjectType VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION_KHR = new VkObjectType(1000156000);
    public static final VkObjectType VK_OBJECT_TYPE_SEMAPHORE = new VkObjectType(5);
    public static final VkObjectType VK_OBJECT_TYPE_SHADER_EXT = new VkObjectType(1000482000);
    public static final VkObjectType VK_OBJECT_TYPE_SHADER_MODULE = new VkObjectType(15);
    public static final VkObjectType VK_OBJECT_TYPE_SURFACE_KHR = new VkObjectType(1000000000);
    public static final VkObjectType VK_OBJECT_TYPE_SWAPCHAIN_KHR = new VkObjectType(1000001000);
    public static final VkObjectType VK_OBJECT_TYPE_TENSOR_ARM = new VkObjectType(1000460000);
    public static final VkObjectType VK_OBJECT_TYPE_TENSOR_VIEW_ARM = new VkObjectType(1000460001);
    public static final VkObjectType VK_OBJECT_TYPE_UNKNOWN = new VkObjectType(0);
    public static final VkObjectType VK_OBJECT_TYPE_VALIDATION_CACHE_EXT = new VkObjectType(1000160000);
    public static final VkObjectType VK_OBJECT_TYPE_VIDEO_SESSION_KHR = new VkObjectType(1000023000);
    public static final VkObjectType VK_OBJECT_TYPE_VIDEO_SESSION_PARAMETERS_KHR = new VkObjectType(1000023001);

    public static VkObjectType fromValue(int value) {
        return switch (value) {
            case 1000150000 -> VK_OBJECT_TYPE_ACCELERATION_STRUCTURE_KHR;
            case 1000165000 -> VK_OBJECT_TYPE_ACCELERATION_STRUCTURE_NV;
            case 9 -> VK_OBJECT_TYPE_BUFFER;
            case 1000366000 -> VK_OBJECT_TYPE_BUFFER_COLLECTION_FUCHSIA;
            case 13 -> VK_OBJECT_TYPE_BUFFER_VIEW;
            case 6 -> VK_OBJECT_TYPE_COMMAND_BUFFER;
            case 25 -> VK_OBJECT_TYPE_COMMAND_POOL;
            case 1000029001 -> VK_OBJECT_TYPE_CU_FUNCTION_NVX;
            case 1000029000 -> VK_OBJECT_TYPE_CU_MODULE_NVX;
            case 1000507000 -> VK_OBJECT_TYPE_DATA_GRAPH_PIPELINE_SESSION_ARM;
            case 1000011000 -> VK_OBJECT_TYPE_DEBUG_REPORT_CALLBACK_EXT;
            case 1000128000 -> VK_OBJECT_TYPE_DEBUG_UTILS_MESSENGER_EXT;
            case 1000268000 -> VK_OBJECT_TYPE_DEFERRED_OPERATION_KHR;
            case 22 -> VK_OBJECT_TYPE_DESCRIPTOR_POOL;
            case 23 -> VK_OBJECT_TYPE_DESCRIPTOR_SET;
            case 20 -> VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT;
            case 1000085000 -> VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE;
            case 3 -> VK_OBJECT_TYPE_DEVICE;
            case 8 -> VK_OBJECT_TYPE_DEVICE_MEMORY;
            case 1000002000 -> VK_OBJECT_TYPE_DISPLAY_KHR;
            case 1000002001 -> VK_OBJECT_TYPE_DISPLAY_MODE_KHR;
            case 11 -> VK_OBJECT_TYPE_EVENT;
            case 1000556000 -> VK_OBJECT_TYPE_EXTERNAL_COMPUTE_QUEUE_NV;
            case 7 -> VK_OBJECT_TYPE_FENCE;
            case 24 -> VK_OBJECT_TYPE_FRAMEBUFFER;
            case 10 -> VK_OBJECT_TYPE_IMAGE;
            case 14 -> VK_OBJECT_TYPE_IMAGE_VIEW;
            case 1000572000 -> VK_OBJECT_TYPE_INDIRECT_COMMANDS_LAYOUT_EXT;
            case 1000277000 -> VK_OBJECT_TYPE_INDIRECT_COMMANDS_LAYOUT_NV;
            case 1000572001 -> VK_OBJECT_TYPE_INDIRECT_EXECUTION_SET_EXT;
            case 1 -> VK_OBJECT_TYPE_INSTANCE;
            case 2147483647 -> VK_OBJECT_TYPE_MAX_ENUM;
            case 1000396000 -> VK_OBJECT_TYPE_MICROMAP_EXT;
            case 1000464000 -> VK_OBJECT_TYPE_OPTICAL_FLOW_SESSION_NV;
            case 1000210000 -> VK_OBJECT_TYPE_PERFORMANCE_CONFIGURATION_INTEL;
            case 2 -> VK_OBJECT_TYPE_PHYSICAL_DEVICE;
            case 19 -> VK_OBJECT_TYPE_PIPELINE;
            case 1000483000 -> VK_OBJECT_TYPE_PIPELINE_BINARY_KHR;
            case 16 -> VK_OBJECT_TYPE_PIPELINE_CACHE;
            case 17 -> VK_OBJECT_TYPE_PIPELINE_LAYOUT;
            case 1000295000 -> VK_OBJECT_TYPE_PRIVATE_DATA_SLOT;
            case 12 -> VK_OBJECT_TYPE_QUERY_POOL;
            case 4 -> VK_OBJECT_TYPE_QUEUE;
            case 18 -> VK_OBJECT_TYPE_RENDER_PASS;
            case 21 -> VK_OBJECT_TYPE_SAMPLER;
            case 1000156000 -> VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION;
            case 5 -> VK_OBJECT_TYPE_SEMAPHORE;
            case 1000482000 -> VK_OBJECT_TYPE_SHADER_EXT;
            case 15 -> VK_OBJECT_TYPE_SHADER_MODULE;
            case 1000000000 -> VK_OBJECT_TYPE_SURFACE_KHR;
            case 1000001000 -> VK_OBJECT_TYPE_SWAPCHAIN_KHR;
            case 1000460000 -> VK_OBJECT_TYPE_TENSOR_ARM;
            case 1000460001 -> VK_OBJECT_TYPE_TENSOR_VIEW_ARM;
            case 0 -> VK_OBJECT_TYPE_UNKNOWN;
            case 1000160000 -> VK_OBJECT_TYPE_VALIDATION_CACHE_EXT;
            case 1000023000 -> VK_OBJECT_TYPE_VIDEO_SESSION_KHR;
            case 1000023001 -> VK_OBJECT_TYPE_VIDEO_SESSION_PARAMETERS_KHR;
            default -> new VkObjectType(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
