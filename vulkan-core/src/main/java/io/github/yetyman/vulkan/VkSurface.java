package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.win32.VkWin32Surface;
import java.lang.foreign.*;

public class VkSurface {
    
    public static MemorySegment createPlatformSurface(MemorySegment instance, MemorySegment window, Arena arena) {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return createWin32Surface(instance, window, arena);
        } else if (os.contains("linux")) {
            throw new UnsupportedOperationException("Linux surface creation not implemented yet");
        } else if (os.contains("mac")) {
            throw new UnsupportedOperationException("macOS surface creation not implemented yet");
        } else {
            throw new UnsupportedOperationException("Unsupported platform: " + os);
        }
    }
    
    public static void destroy(MemorySegment instance, MemorySegment surface) {
        Vulkan.destroySurfaceKHR(instance, surface);
    }
    
    private static MemorySegment createWin32Surface(MemorySegment instance, MemorySegment window, Arena arena) {
        return VkWin32Surface.builder(instance)
            .hwnd(getWindowHandle(window))
            .build(arena);
    }
    
    private static long getWindowHandle(MemorySegment window) {
        // Platform-specific window handle extraction
        try {
            var glfwClass = Class.forName("io.github.yetyman.glfw.GLFW");
            var method = glfwClass.getMethod("glfwGetWin32Window", MemorySegment.class);
            return (Long) method.invoke(null, window);
        } catch (Exception e) {
            throw new VulkanException.SurfaceException("Failed to get window handle: " + e.getMessage());
        }
    }
}