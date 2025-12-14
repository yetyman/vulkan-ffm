package io.github.yetyman.vulkan;

import java.lang.foreign.*;

/**
 * Vulkan surface management functions.
 * Handles creation and destruction of VkSurfaceKHR objects for rendering to windows.
 */
public class VulkanSurface {
    
    public static MemorySegment createSurface(MemorySegment instance, long glfwWindow, Arena arena) {
        // Surface creation will be handled by LWJGL in the app layer
        // This is a placeholder that returns a dummy surface
        return MemorySegment.ofAddress(0);
    }
    
    /**
     * Destroys a Vulkan surface.
     * @param instance the VkInstance handle
     * @param surface the VkSurfaceKHR handle to destroy
     */
    public static void destroySurface(MemorySegment instance, MemorySegment surface) {
        try {
            var vulkanLib = SymbolLookup.loaderLookup();
            var destroySurfaceFunc = vulkanLib.find("vkDestroySurfaceKHR").orElseThrow();
            
            var linker = Linker.nativeLinker();
            var destroySurface = linker.downcallHandle(
                destroySurfaceFunc,
                FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                )
            );
            
            destroySurface.invoke(instance, surface, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to destroy surface", e);
        }
    }
}
