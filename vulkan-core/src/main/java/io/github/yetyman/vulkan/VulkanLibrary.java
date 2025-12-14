package io.github.yetyman.vulkan;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Handles loading of the native Vulkan library (vulkan-1.dll on Windows).
 * Attempts to load from system PATH first, then falls back to VULKAN_SDK environment variable.
 */
public class VulkanLibrary {
    static {
        try {
            System.loadLibrary("vulkan-1");
        } catch (UnsatisfiedLinkError e) {
            String vulkanPath = System.getenv("VULKAN_SDK");
            if (vulkanPath != null) {
                System.load(vulkanPath + "\\Bin\\vulkan-1.dll");
            } else {
                throw new RuntimeException("Vulkan SDK not found", e);
            }
        }
    }
    
    /**
     * Explicitly loads the Vulkan library by triggering the static initializer.
     * Call this early in your application to ensure Vulkan is available.
     */
    public static void load() {
        // Trigger static initializer
    }
}
