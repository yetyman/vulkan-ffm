package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public interface WindowSystem {
    
    @FunctionalInterface
    interface ResizeCallback {
        void onResize(MemorySegment window, int width, int height);
    }
    
    // Window lifecycle
    MemorySegment createWindow(int width, int height, String title);
    void destroyWindow(MemorySegment window);
    void terminate();
    
    // Vulkan integration
    String[] getRequiredVulkanExtensions(Arena arena);
    MemorySegment createSurface(MemorySegment instance, MemorySegment window, Arena arena);
    
    // Event handling
    boolean shouldClose(MemorySegment window);
    void pollEvents();
    void setResizeCallback(MemorySegment window, ResizeCallback callback, Arena arena);
    
    // Window properties
    void getFramebufferSize(MemorySegment window, MemorySegment widthPtr, MemorySegment heightPtr);
    void setResizable(boolean resizable);
}