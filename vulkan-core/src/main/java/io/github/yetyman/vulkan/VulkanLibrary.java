package io.github.yetyman.vulkan;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class VulkanLibrary {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup VULKAN_LOOKUP;
    
    static {
        try {
            System.loadLibrary("vulkan-1");
        } catch (UnsatisfiedLinkError e) {
            // Try common Vulkan SDK locations
            String vulkanPath = System.getenv("VULKAN_SDK");
            if (vulkanPath != null) {
                System.load(vulkanPath + "\\Bin\\vulkan-1.dll");
            } else {
                throw new RuntimeException("Vulkan SDK not found. Install from https://vulkan.lunarg.com", e);
            }
        }
        VULKAN_LOOKUP = SymbolLookup.loaderLookup();
    }
    
    public static MethodHandle findFunction(String name, FunctionDescriptor descriptor) {
        return VULKAN_LOOKUP.find(name)
            .map(addr -> LINKER.downcallHandle(addr, descriptor))
            .orElseThrow(() -> new UnsatisfiedLinkError("Function not found: " + name));
    }
}
