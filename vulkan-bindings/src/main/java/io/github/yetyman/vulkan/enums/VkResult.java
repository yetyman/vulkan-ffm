package io.github.yetyman.vulkan.enums;

import java.util.*;

/**
 * Type-safe constants for VkResult
 * Generated from jextract bindings
 */
public record VkResult(int value) {

    public static final VkResult VK_ERROR_COMPRESSION_EXHAUSTED_EXT = new VkResult(-1000338000);
    public static final VkResult VK_ERROR_DEVICE_LOST = new VkResult(-4);
    public static final VkResult VK_ERROR_EXTENSION_NOT_PRESENT = new VkResult(-7);
    public static final VkResult VK_ERROR_FEATURE_NOT_PRESENT = new VkResult(-8);
    public static final VkResult VK_ERROR_FORMAT_NOT_SUPPORTED = new VkResult(-11);
    public static final VkResult VK_ERROR_FRAGMENTATION = new VkResult(-1000161000);
    public static final VkResult VK_ERROR_FRAGMENTATION_EXT = VK_ERROR_FRAGMENTATION;
    public static final VkResult VK_ERROR_FRAGMENTED_POOL = new VkResult(-12);
    public static final VkResult VK_ERROR_FULL_SCREEN_EXCLUSIVE_MODE_LOST_EXT = new VkResult(-1000255000);
    public static final VkResult VK_ERROR_IMAGE_USAGE_NOT_SUPPORTED_KHR = new VkResult(-1000023000);
    public static final VkResult VK_ERROR_INCOMPATIBLE_DISPLAY_KHR = new VkResult(-1000003001);
    public static final VkResult VK_ERROR_INCOMPATIBLE_DRIVER = new VkResult(-9);
    public static final VkResult VK_ERROR_INCOMPATIBLE_SHADER_BINARY_EXT = new VkResult(1000482000);
    public static final VkResult VK_ERROR_INITIALIZATION_FAILED = new VkResult(-3);
    public static final VkResult VK_ERROR_INVALID_DEVICE_ADDRESS_EXT = new VkResult(-1000257000);
    public static final VkResult VK_ERROR_INVALID_DRM_FORMAT_MODIFIER_PLANE_LAYOUT_EXT = new VkResult(-1000158000);
    public static final VkResult VK_ERROR_INVALID_EXTERNAL_HANDLE = new VkResult(-1000072003);
    public static final VkResult VK_ERROR_INVALID_EXTERNAL_HANDLE_KHR = VK_ERROR_INVALID_EXTERNAL_HANDLE;
    public static final VkResult VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS = VK_ERROR_INVALID_DEVICE_ADDRESS_EXT;
    public static final VkResult VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS_KHR = VK_ERROR_INVALID_DEVICE_ADDRESS_EXT;
    public static final VkResult VK_ERROR_INVALID_SHADER_NV = new VkResult(-1000012000);
    public static final VkResult VK_ERROR_INVALID_VIDEO_STD_PARAMETERS_KHR = new VkResult(-1000299000);
    public static final VkResult VK_ERROR_LAYER_NOT_PRESENT = new VkResult(-6);
    public static final VkResult VK_ERROR_MEMORY_MAP_FAILED = new VkResult(-5);
    public static final VkResult VK_ERROR_NATIVE_WINDOW_IN_USE_KHR = new VkResult(-1000000001);
    public static final VkResult VK_ERROR_NOT_ENOUGH_SPACE_KHR = new VkResult(-1000483000);
    public static final VkResult VK_ERROR_NOT_PERMITTED = new VkResult(-1000174001);
    public static final VkResult VK_ERROR_NOT_PERMITTED_EXT = VK_ERROR_NOT_PERMITTED;
    public static final VkResult VK_ERROR_NOT_PERMITTED_KHR = VK_ERROR_NOT_PERMITTED;
    public static final VkResult VK_ERROR_OUT_OF_DATE_KHR = new VkResult(-1000001004);
    public static final VkResult VK_ERROR_OUT_OF_DEVICE_MEMORY = new VkResult(-2);
    public static final VkResult VK_ERROR_OUT_OF_HOST_MEMORY = new VkResult(-1);
    public static final VkResult VK_ERROR_OUT_OF_POOL_MEMORY = new VkResult(-1000069000);
    public static final VkResult VK_ERROR_OUT_OF_POOL_MEMORY_KHR = VK_ERROR_OUT_OF_POOL_MEMORY;
    public static final VkResult VK_ERROR_PIPELINE_COMPILE_REQUIRED_EXT = new VkResult(1000297000);
    public static final VkResult VK_ERROR_PRESENT_TIMING_QUEUE_FULL_EXT = new VkResult(-1000208000);
    public static final VkResult VK_ERROR_SURFACE_LOST_KHR = new VkResult(-1000000000);
    public static final VkResult VK_ERROR_TOO_MANY_OBJECTS = new VkResult(-10);
    public static final VkResult VK_ERROR_UNKNOWN = new VkResult(-13);
    public static final VkResult VK_ERROR_VALIDATION_FAILED = new VkResult(-1000011001);
    public static final VkResult VK_ERROR_VALIDATION_FAILED_EXT = VK_ERROR_VALIDATION_FAILED;
    public static final VkResult VK_ERROR_VIDEO_PICTURE_LAYOUT_NOT_SUPPORTED_KHR = new VkResult(-1000023001);
    public static final VkResult VK_ERROR_VIDEO_PROFILE_CODEC_NOT_SUPPORTED_KHR = new VkResult(-1000023004);
    public static final VkResult VK_ERROR_VIDEO_PROFILE_FORMAT_NOT_SUPPORTED_KHR = new VkResult(-1000023003);
    public static final VkResult VK_ERROR_VIDEO_PROFILE_OPERATION_NOT_SUPPORTED_KHR = new VkResult(-1000023002);
    public static final VkResult VK_ERROR_VIDEO_STD_VERSION_NOT_SUPPORTED_KHR = new VkResult(-1000023005);
    public static final VkResult VK_EVENT_RESET = new VkResult(4);
    public static final VkResult VK_EVENT_SET = new VkResult(3);
    public static final VkResult VK_INCOMPATIBLE_SHADER_BINARY_EXT = VK_ERROR_INCOMPATIBLE_SHADER_BINARY_EXT;
    public static final VkResult VK_INCOMPLETE = new VkResult(5);
    public static final VkResult VK_NOT_READY = new VkResult(1);
    public static final VkResult VK_OPERATION_DEFERRED_KHR = new VkResult(1000268002);
    public static final VkResult VK_OPERATION_NOT_DEFERRED_KHR = new VkResult(1000268003);
    public static final VkResult VK_PIPELINE_BINARY_MISSING_KHR = new VkResult(1000483000);
    public static final VkResult VK_PIPELINE_COMPILE_REQUIRED = VK_ERROR_PIPELINE_COMPILE_REQUIRED_EXT;
    public static final VkResult VK_PIPELINE_COMPILE_REQUIRED_EXT = VK_ERROR_PIPELINE_COMPILE_REQUIRED_EXT;
    public static final VkResult VK_RESULT_MAX_ENUM = new VkResult(2147483647);
    public static final VkResult VK_SUBOPTIMAL_KHR = new VkResult(1000001003);
    public static final VkResult VK_SUCCESS = new VkResult(0);
    public static final VkResult VK_THREAD_DONE_KHR = new VkResult(1000268001);
    public static final VkResult VK_THREAD_IDLE_KHR = new VkResult(1000268000);
    public static final VkResult VK_TIMEOUT = new VkResult(2);

    public static VkResult fromValue(int value) {
        return switch (value) {
            case -1000338000 -> VK_ERROR_COMPRESSION_EXHAUSTED_EXT;
            case -4 -> VK_ERROR_DEVICE_LOST;
            case -7 -> VK_ERROR_EXTENSION_NOT_PRESENT;
            case -8 -> VK_ERROR_FEATURE_NOT_PRESENT;
            case -11 -> VK_ERROR_FORMAT_NOT_SUPPORTED;
            case -1000161000 -> VK_ERROR_FRAGMENTATION;
            case -12 -> VK_ERROR_FRAGMENTED_POOL;
            case -1000255000 -> VK_ERROR_FULL_SCREEN_EXCLUSIVE_MODE_LOST_EXT;
            case -1000023000 -> VK_ERROR_IMAGE_USAGE_NOT_SUPPORTED_KHR;
            case -1000003001 -> VK_ERROR_INCOMPATIBLE_DISPLAY_KHR;
            case -9 -> VK_ERROR_INCOMPATIBLE_DRIVER;
            case 1000482000 -> VK_INCOMPATIBLE_SHADER_BINARY_EXT;
            case -3 -> VK_ERROR_INITIALIZATION_FAILED;
            case -1000257000 -> VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS;
            case -1000158000 -> VK_ERROR_INVALID_DRM_FORMAT_MODIFIER_PLANE_LAYOUT_EXT;
            case -1000072003 -> VK_ERROR_INVALID_EXTERNAL_HANDLE;
            case -1000012000 -> VK_ERROR_INVALID_SHADER_NV;
            case -1000299000 -> VK_ERROR_INVALID_VIDEO_STD_PARAMETERS_KHR;
            case -6 -> VK_ERROR_LAYER_NOT_PRESENT;
            case -5 -> VK_ERROR_MEMORY_MAP_FAILED;
            case -1000000001 -> VK_ERROR_NATIVE_WINDOW_IN_USE_KHR;
            case -1000483000 -> VK_ERROR_NOT_ENOUGH_SPACE_KHR;
            case -1000174001 -> VK_ERROR_NOT_PERMITTED;
            case -1000001004 -> VK_ERROR_OUT_OF_DATE_KHR;
            case -2 -> VK_ERROR_OUT_OF_DEVICE_MEMORY;
            case -1 -> VK_ERROR_OUT_OF_HOST_MEMORY;
            case -1000069000 -> VK_ERROR_OUT_OF_POOL_MEMORY;
            case 1000297000 -> VK_PIPELINE_COMPILE_REQUIRED;
            case -1000208000 -> VK_ERROR_PRESENT_TIMING_QUEUE_FULL_EXT;
            case -1000000000 -> VK_ERROR_SURFACE_LOST_KHR;
            case -10 -> VK_ERROR_TOO_MANY_OBJECTS;
            case -13 -> VK_ERROR_UNKNOWN;
            case -1000011001 -> VK_ERROR_VALIDATION_FAILED;
            case -1000023001 -> VK_ERROR_VIDEO_PICTURE_LAYOUT_NOT_SUPPORTED_KHR;
            case -1000023004 -> VK_ERROR_VIDEO_PROFILE_CODEC_NOT_SUPPORTED_KHR;
            case -1000023003 -> VK_ERROR_VIDEO_PROFILE_FORMAT_NOT_SUPPORTED_KHR;
            case -1000023002 -> VK_ERROR_VIDEO_PROFILE_OPERATION_NOT_SUPPORTED_KHR;
            case -1000023005 -> VK_ERROR_VIDEO_STD_VERSION_NOT_SUPPORTED_KHR;
            case 4 -> VK_EVENT_RESET;
            case 3 -> VK_EVENT_SET;
            case 5 -> VK_INCOMPLETE;
            case 1 -> VK_NOT_READY;
            case 1000268002 -> VK_OPERATION_DEFERRED_KHR;
            case 1000268003 -> VK_OPERATION_NOT_DEFERRED_KHR;
            case 1000483000 -> VK_PIPELINE_BINARY_MISSING_KHR;
            case 2147483647 -> VK_RESULT_MAX_ENUM;
            case 1000001003 -> VK_SUBOPTIMAL_KHR;
            case 0 -> VK_SUCCESS;
            case 1000268001 -> VK_THREAD_DONE_KHR;
            case 1000268000 -> VK_THREAD_IDLE_KHR;
            case 2 -> VK_TIMEOUT;
            default -> new VkResult(value);
        };
    }

    private static boolean hasVendorSuffix(String name) {
        return name.endsWith("_KHR") || name.endsWith("_EXT") || name.endsWith("_NV") || 
               name.endsWith("_AMD") || name.endsWith("_INTEL") || name.endsWith("_ARM");
    }
}
