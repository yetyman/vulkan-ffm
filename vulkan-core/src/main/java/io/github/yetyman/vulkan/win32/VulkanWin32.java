package io.github.yetyman.vulkan.win32;

import io.github.yetyman.vulkan.VkResult;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Windows-specific Vulkan surface creation functions.
 * Provides access to vkCreateWin32SurfaceKHR for creating Vulkan surfaces from Win32 windows.
 */
public class VulkanWin32 {
    private static final MethodHandle vkCreateWin32SurfaceKHR;
    
    static {
        try {
            var vulkanLib = SymbolLookup.loaderLookup();
            var createSurfaceFunc = vulkanLib.find("vkCreateWin32SurfaceKHR").orElseThrow();
            
            var linker = Linker.nativeLinker();
            vkCreateWin32SurfaceKHR = linker.downcallHandle(
                createSurfaceFunc,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                )
            );
        } catch (Throwable e) {
            throw new RuntimeException("Failed to load Win32 surface functions", e);
        }
    }
    
    /**
     * Creates a Vulkan surface for a Win32 window.
     * @param instance the VkInstance handle
     * @param createInfo pointer to VkWin32SurfaceCreateInfoKHR structure
     * @param surfacePtr pointer to store the created VkSurfaceKHR handle
     * @return VkResult indicating success or failure
     */
    public static VkResult createWin32Surface(MemorySegment instance, MemorySegment createInfo,
                                              MemorySegment surfacePtr) {
        try {
            int result = (int) vkCreateWin32SurfaceKHR.invoke(instance, createInfo, MemorySegment.NULL, surfacePtr);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create Win32 surface", e);
        }
    }
}
