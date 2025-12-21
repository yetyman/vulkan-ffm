package io.github.yetyman.vulkan.sample.windowing;

import io.github.yetyman.vulkan.WindowSystem;
import io.github.yetyman.glfw.GLFW;
import io.github.yetyman.glfw.GLFWCallbacks;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class GLFWWindowSystem implements WindowSystem {
    private boolean initialized = false;
    private boolean resizable = true;
    
    @Override
    public MemorySegment createWindow(int width, int height, String title) {
        if (!initialized) {
            if (!GLFW.glfwInit()) {
                throw new RuntimeException("Failed to initialize GLFW");
            }
            initialized = true;
        }
        
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        
        return GLFW.glfwCreateWindow(width, height, title);
    }
    
    @Override
    public void destroyWindow(MemorySegment window) {
        if (!window.equals(MemorySegment.NULL)) {
            GLFW.glfwDestroyWindow(window);
        }
    }
    
    @Override
    public void terminate() {
        if (initialized) {
            GLFW.glfwTerminate();
            initialized = false;
        }
    }
    
    @Override
    public String[] getRequiredVulkanExtensions(Arena arena) {
        return GLFW.glfwGetRequiredInstanceExtensions(arena);
    }
    
    @Override
    public MemorySegment createSurface(MemorySegment instance, MemorySegment window, Arena arena) {
        return io.github.yetyman.vulkan.VkSurface.createPlatformSurface(instance, window, arena);
    }
    
    @Override
    public boolean shouldClose(MemorySegment window) {
        return GLFW.glfwWindowShouldClose(window);
    }
    
    @Override
    public void pollEvents() {
        GLFW.glfwPollEvents();
    }
    
    @Override
    public void setResizeCallback(MemorySegment window, ResizeCallback callback, Arena arena) {
        GLFWCallbacks.setFramebufferSizeCallback(window, callback::onResize, arena);
    }
    
    @Override
    public void getFramebufferSize(MemorySegment window, MemorySegment widthPtr, MemorySegment heightPtr) {
        GLFW.glfwGetFramebufferSize(window, widthPtr, heightPtr);
    }
    
    @Override
    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }
}