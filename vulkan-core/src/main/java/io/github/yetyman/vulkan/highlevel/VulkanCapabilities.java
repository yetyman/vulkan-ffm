package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.generated.VkExtensionProperties;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashSet;
import java.util.Set;

/**
 * Central registry for Vulkan extension capabilities.
 * Checks extension availability once at startup and provides fast runtime access.
 */
public class VulkanCapabilities {
    
    // Extension availability flags - fastest possible runtime checks
    public static boolean multiDraw = false;
    public static boolean meshShaders = false;
    public static boolean rayTracing = false;
    public static boolean indirectCount = false;
    public static boolean drawIndirectByteCount = false;
    public static boolean conditionalRendering = false;
    public static boolean transformFeedback = false;
    public static boolean reBar = false;
    
    private static boolean initialized = false;
    
    /**
     * Initialize capabilities for the given physical device.
     * Should be called once during application startup.
     */
    public static void initialize(VkPhysicalDevice physicalDevice) {
        if (initialized) {
            Logger.info("VulkanCapabilities already initialized");
            return;
        }
        
        Set<String> availableExtensions = getAvailableExtensions(physicalDevice);
        
        // Check each extension we care about
        reBar = physicalDevice.supportsReBar();
        multiDraw = availableExtensions.contains("VK_EXT_multi_draw");
        meshShaders = availableExtensions.contains("VK_EXT_mesh_shader") || 
                     availableExtensions.contains("VK_NV_mesh_shader");
        rayTracing = availableExtensions.contains("VK_KHR_ray_tracing_pipeline");
        indirectCount = availableExtensions.contains("VK_KHR_draw_indirect_count") ||
                       availableExtensions.contains("VK_AMD_draw_indirect_count");
        drawIndirectByteCount = availableExtensions.contains("VK_EXT_transform_feedback");
        conditionalRendering = availableExtensions.contains("VK_EXT_conditional_rendering");
        transformFeedback = availableExtensions.contains("VK_EXT_transform_feedback");
        
        initialized = true;
        logCapabilities();
    }
    
    /**
     * Get all available device extensions.
     */
    private static Set<String> getAvailableExtensions(VkPhysicalDevice physicalDevice) {
        Set<String> extensions = new HashSet<>();
        
        try (Arena arena = Arena.ofConfined()) {
            // Get extension count
            MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);
            Vulkan.enumerateDeviceExtensionProperties(physicalDevice.handle(), MemorySegment.NULL, countPtr, MemorySegment.NULL).check();
            int count = countPtr.get(ValueLayout.JAVA_INT, 0);
            
            if (count > 0) {
                // Get extensions
                MemorySegment extensionsArray = arena.allocate(VkExtensionProperties.layout(), count);
                Vulkan.enumerateDeviceExtensionProperties(physicalDevice.handle(), MemorySegment.NULL, countPtr, extensionsArray).check();
                
                // Extract extension names
                for (int i = 0; i < count; i++) {
                    MemorySegment extension = extensionsArray.asSlice(i * VkExtensionProperties.layout().byteSize(), VkExtensionProperties.layout());
                    String name = VkExtensionProperties.extensionName(extension).getString(0);
                    extensions.add(name);
                }
            }
        }
        
        return extensions;
    }
    
    /**
     * Log detected capabilities for debugging.
     */
    private static void logCapabilities() {
        Logger.info("Vulkan Capabilities Detected:");
        Logger.info("  Multi-draw: " + multiDraw);
        Logger.info("  Mesh shaders: " + meshShaders);
        Logger.info("  Ray tracing: " + rayTracing);
        Logger.info("  Indirect count: " + indirectCount);
        Logger.info("  Draw indirect byte count: " + drawIndirectByteCount);
        Logger.info("  Conditional rendering: " + conditionalRendering);
        Logger.info("  Transform feedback: " + transformFeedback);
        Logger.info("  Resizable BAR (ReBAR): " + reBar);
    }
    
    /**
     * Get list of extensions that should be enabled during device creation.
     * Only returns extensions that are both available and useful.
     */
    public static String[] getRequiredExtensions() {
        if (!initialized) {
            throw new IllegalStateException("VulkanCapabilities not initialized");
        }
        
        java.util.List<String> required = new java.util.ArrayList<>();
        
        if (multiDraw) required.add("VK_EXT_multi_draw");
        if (meshShaders) {
            required.add("VK_EXT_mesh_shader");
            // Mesh shaders require these dependencies
            required.add("VK_KHR_spirv_1_4");
            required.add("VK_KHR_shader_float_controls");
        }
        if (rayTracing) {
            required.add("VK_KHR_ray_tracing_pipeline");
            required.add("VK_KHR_acceleration_structure");
            required.add("VK_KHR_ray_query");
            required.add("VK_KHR_deferred_host_operations");
        }
        if (indirectCount) {
            required.add("VK_KHR_draw_indirect_count");
        }
        if (conditionalRendering) {
            required.add("VK_EXT_conditional_rendering");
        }
        if (transformFeedback) {
            required.add("VK_EXT_transform_feedback");
        }
        
        return required.toArray(new String[0]);
    }
    
    /**
     * Check if capabilities have been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Reset capabilities (for testing).
     */
    public static void reset() {
        initialized = false;
        multiDraw = false;
        meshShaders = false;
        rayTracing = false;
        indirectCount = false;
        drawIndirectByteCount = false;
        conditionalRendering = false;
        transformFeedback = false;
        reBar = false;
    }
}