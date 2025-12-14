package io.github.yetyman.vulkan;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

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
