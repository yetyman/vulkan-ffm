package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkDebugReportObjectTypeEXT
 * Generated from jextract bindings
 */
public record VkDebugReportObjectTypeEXT(int value) {

    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_ACCELERATION_STRUCTURE_KHR_EXT = new VkDebugReportObjectTypeEXT(1000150000);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_ACCELERATION_STRUCTURE_NV_EXT = new VkDebugReportObjectTypeEXT(1000165000);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_BUFFER_COLLECTION_FUCHSIA_EXT = new VkDebugReportObjectTypeEXT(1000366000);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_BUFFER_EXT = new VkDebugReportObjectTypeEXT(9);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_BUFFER_VIEW_EXT = new VkDebugReportObjectTypeEXT(13);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_COMMAND_BUFFER_EXT = new VkDebugReportObjectTypeEXT(6);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_COMMAND_POOL_EXT = new VkDebugReportObjectTypeEXT(25);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_CUDA_FUNCTION_NV_EXT = new VkDebugReportObjectTypeEXT(1000307001);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_CUDA_MODULE_NV_EXT = new VkDebugReportObjectTypeEXT(1000307000);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_CU_FUNCTION_NVX_EXT = new VkDebugReportObjectTypeEXT(1000029001);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_CU_MODULE_NVX_EXT = new VkDebugReportObjectTypeEXT(1000029000);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DEBUG_REPORT_CALLBACK_EXT_EXT = new VkDebugReportObjectTypeEXT(28);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DEBUG_REPORT_EXT = VK_DEBUG_REPORT_OBJECT_TYPE_DEBUG_REPORT_CALLBACK_EXT_EXT;
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_POOL_EXT = new VkDebugReportObjectTypeEXT(22);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_SET_EXT = new VkDebugReportObjectTypeEXT(23);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT_EXT = new VkDebugReportObjectTypeEXT(20);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_EXT = new VkDebugReportObjectTypeEXT(1000085000);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_KHR_EXT = VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_EXT;
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_EXT = new VkDebugReportObjectTypeEXT(3);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_MEMORY_EXT = new VkDebugReportObjectTypeEXT(8);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DISPLAY_KHR_EXT = new VkDebugReportObjectTypeEXT(29);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_DISPLAY_MODE_KHR_EXT = new VkDebugReportObjectTypeEXT(30);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_EVENT_EXT = new VkDebugReportObjectTypeEXT(11);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_FENCE_EXT = new VkDebugReportObjectTypeEXT(7);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_FRAMEBUFFER_EXT = new VkDebugReportObjectTypeEXT(24);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_EXT = new VkDebugReportObjectTypeEXT(10);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT = new VkDebugReportObjectTypeEXT(14);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_INSTANCE_EXT = new VkDebugReportObjectTypeEXT(1);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_MAX_ENUM_EXT = new VkDebugReportObjectTypeEXT(2147483647);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_PHYSICAL_DEVICE_EXT = new VkDebugReportObjectTypeEXT(2);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_PIPELINE_CACHE_EXT = new VkDebugReportObjectTypeEXT(16);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_PIPELINE_EXT = new VkDebugReportObjectTypeEXT(19);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_PIPELINE_LAYOUT_EXT = new VkDebugReportObjectTypeEXT(17);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_QUERY_POOL_EXT = new VkDebugReportObjectTypeEXT(12);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_QUEUE_EXT = new VkDebugReportObjectTypeEXT(4);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_RENDER_PASS_EXT = new VkDebugReportObjectTypeEXT(18);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_SAMPLER_EXT = new VkDebugReportObjectTypeEXT(21);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION_EXT = new VkDebugReportObjectTypeEXT(1000156000);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION_KHR_EXT = VK_DEBUG_REPORT_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION_EXT;
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_SEMAPHORE_EXT = new VkDebugReportObjectTypeEXT(5);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_SHADER_MODULE_EXT = new VkDebugReportObjectTypeEXT(15);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_SURFACE_KHR_EXT = new VkDebugReportObjectTypeEXT(26);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_SWAPCHAIN_KHR_EXT = new VkDebugReportObjectTypeEXT(27);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_UNKNOWN_EXT = new VkDebugReportObjectTypeEXT(0);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_VALIDATION_CACHE_EXT = new VkDebugReportObjectTypeEXT(33);
    public static final VkDebugReportObjectTypeEXT VK_DEBUG_REPORT_OBJECT_TYPE_VALIDATION_CACHE_EXT_EXT = VK_DEBUG_REPORT_OBJECT_TYPE_VALIDATION_CACHE_EXT;

    public static VkDebugReportObjectTypeEXT fromValue(int value) {
        return switch (value) {
            case 1000150000 -> VK_DEBUG_REPORT_OBJECT_TYPE_ACCELERATION_STRUCTURE_KHR_EXT;
            case 1000165000 -> VK_DEBUG_REPORT_OBJECT_TYPE_ACCELERATION_STRUCTURE_NV_EXT;
            case 1000366000 -> VK_DEBUG_REPORT_OBJECT_TYPE_BUFFER_COLLECTION_FUCHSIA_EXT;
            case 9 -> VK_DEBUG_REPORT_OBJECT_TYPE_BUFFER_EXT;
            case 13 -> VK_DEBUG_REPORT_OBJECT_TYPE_BUFFER_VIEW_EXT;
            case 6 -> VK_DEBUG_REPORT_OBJECT_TYPE_COMMAND_BUFFER_EXT;
            case 25 -> VK_DEBUG_REPORT_OBJECT_TYPE_COMMAND_POOL_EXT;
            case 1000307001 -> VK_DEBUG_REPORT_OBJECT_TYPE_CUDA_FUNCTION_NV_EXT;
            case 1000307000 -> VK_DEBUG_REPORT_OBJECT_TYPE_CUDA_MODULE_NV_EXT;
            case 1000029001 -> VK_DEBUG_REPORT_OBJECT_TYPE_CU_FUNCTION_NVX_EXT;
            case 1000029000 -> VK_DEBUG_REPORT_OBJECT_TYPE_CU_MODULE_NVX_EXT;
            case 28 -> VK_DEBUG_REPORT_OBJECT_TYPE_DEBUG_REPORT_EXT;
            case 22 -> VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_POOL_EXT;
            case 23 -> VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_SET_EXT;
            case 20 -> VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT_EXT;
            case 1000085000 -> VK_DEBUG_REPORT_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_EXT;
            case 3 -> VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_EXT;
            case 8 -> VK_DEBUG_REPORT_OBJECT_TYPE_DEVICE_MEMORY_EXT;
            case 29 -> VK_DEBUG_REPORT_OBJECT_TYPE_DISPLAY_KHR_EXT;
            case 30 -> VK_DEBUG_REPORT_OBJECT_TYPE_DISPLAY_MODE_KHR_EXT;
            case 11 -> VK_DEBUG_REPORT_OBJECT_TYPE_EVENT_EXT;
            case 7 -> VK_DEBUG_REPORT_OBJECT_TYPE_FENCE_EXT;
            case 24 -> VK_DEBUG_REPORT_OBJECT_TYPE_FRAMEBUFFER_EXT;
            case 10 -> VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_EXT;
            case 14 -> VK_DEBUG_REPORT_OBJECT_TYPE_IMAGE_VIEW_EXT;
            case 1 -> VK_DEBUG_REPORT_OBJECT_TYPE_INSTANCE_EXT;
            case 2147483647 -> VK_DEBUG_REPORT_OBJECT_TYPE_MAX_ENUM_EXT;
            case 2 -> VK_DEBUG_REPORT_OBJECT_TYPE_PHYSICAL_DEVICE_EXT;
            case 16 -> VK_DEBUG_REPORT_OBJECT_TYPE_PIPELINE_CACHE_EXT;
            case 19 -> VK_DEBUG_REPORT_OBJECT_TYPE_PIPELINE_EXT;
            case 17 -> VK_DEBUG_REPORT_OBJECT_TYPE_PIPELINE_LAYOUT_EXT;
            case 12 -> VK_DEBUG_REPORT_OBJECT_TYPE_QUERY_POOL_EXT;
            case 4 -> VK_DEBUG_REPORT_OBJECT_TYPE_QUEUE_EXT;
            case 18 -> VK_DEBUG_REPORT_OBJECT_TYPE_RENDER_PASS_EXT;
            case 21 -> VK_DEBUG_REPORT_OBJECT_TYPE_SAMPLER_EXT;
            case 1000156000 -> VK_DEBUG_REPORT_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION_EXT;
            case 5 -> VK_DEBUG_REPORT_OBJECT_TYPE_SEMAPHORE_EXT;
            case 15 -> VK_DEBUG_REPORT_OBJECT_TYPE_SHADER_MODULE_EXT;
            case 26 -> VK_DEBUG_REPORT_OBJECT_TYPE_SURFACE_KHR_EXT;
            case 27 -> VK_DEBUG_REPORT_OBJECT_TYPE_SWAPCHAIN_KHR_EXT;
            case 0 -> VK_DEBUG_REPORT_OBJECT_TYPE_UNKNOWN_EXT;
            case 33 -> VK_DEBUG_REPORT_OBJECT_TYPE_VALIDATION_CACHE_EXT;
            default -> new VkDebugReportObjectTypeEXT(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
