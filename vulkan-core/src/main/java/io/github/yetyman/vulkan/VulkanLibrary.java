package io.github.yetyman.vulkan;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

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
    
    public static void load() {
        // Trigger static initializer
    }
}
