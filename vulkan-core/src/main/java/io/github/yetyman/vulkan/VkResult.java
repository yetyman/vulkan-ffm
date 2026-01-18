package io.github.yetyman.vulkan;

/**
 * Vulkan result codes representing success or various error conditions.
 * Positive values indicate success, negative values indicate errors.
 */
public enum VkResult {
    VK_SUCCESS(0),
    VK_NOT_READY(1),
    VK_TIMEOUT(2),
    VK_EVENT_SET(3),
    VK_EVENT_RESET(4),
    VK_INCOMPLETE(5),
    VK_ERROR_OUT_OF_HOST_MEMORY(-1),
    VK_ERROR_OUT_OF_DEVICE_MEMORY(-2),
    VK_ERROR_INITIALIZATION_FAILED(-3),
    VK_ERROR_DEVICE_LOST(-4),
    VK_ERROR_MEMORY_MAP_FAILED(-5),
    VK_ERROR_LAYER_NOT_PRESENT(-6),
    VK_ERROR_EXTENSION_NOT_PRESENT(-7),
    VK_ERROR_FEATURE_NOT_PRESENT(-8),
    VK_ERROR_INCOMPATIBLE_DRIVER(-9);
    
    public final int value;
    
    VkResult(int value) {
        this.value = value;
    }
    
    /**
     * Converts an integer result code to a VkResult enum value.
     * @param value the integer result code from Vulkan
     * @return the corresponding VkResult, or VK_ERROR_INITIALIZATION_FAILED if unknown
     */
    public static VkResult fromInt(int value) {
        for (VkResult result : values()) {
            if (result.value == value) return result;
        }
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    
    /**
     * Throws a VulkanException if this result indicates an error (value < 0).
     * @throws VulkanException if the result is an error
     */
    public void check() {
        if (value < 0) {
            throw new VulkanException("Vulkan error: " + this);
        }
    }
}
