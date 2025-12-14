package io.github.yetyman.sample;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.win32.VkWin32Surface;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.glfw.GLFW;
import io.github.yetyman.glfw.GLFWCallbacks;

import java.lang.foreign.*;

public class TriangleApp {
    static { VulkanLibrary.load(); }
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    private MemorySegment window;
    private VulkanContext vulkanContext;
    private boolean framebufferResized = false;
    private MemorySegment callbackStub; // Keep reference to prevent GC
    
    public void run() {
        VulkanLibrary.load();
        initWindow();
        initVulkan();
        mainLoop();
        cleanup();
    }
    
    private void initWindow() {
        if (!GLFW.glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        
        window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "Vulkan Triangle");
        if (window.equals(MemorySegment.NULL)) {
            throw new RuntimeException("Failed to create window");
        }
        
        // Set resize callback - keep reference to prevent GC
        callbackStub = GLFWCallbacks.setFramebufferSizeCallback(window, this::framebufferResizeCallback, Arena.global());
        
        System.out.println("[OK] Window created");
    }
    
    private MemorySegment surface;
    
    private void initVulkan() {
        try (Arena tempArena = Arena.ofConfined()) {
            String[] extensions = GLFW.glfwGetRequiredInstanceExtensions(tempArena);
            if (extensions == null) {
                throw new RuntimeException("Failed to get GLFW extensions");
            }
            
            System.out.println("Extensions: " + String.join(", ", extensions));
            
            // Create surface first so VulkanContext can use it for queue family selection
            VkInstance tempInstance = VkInstance.builder()
                .applicationName("Triangle App")
                .applicationVersion(1)
                .extensions(extensions)
                .build(tempArena);
            
            surface = VkSurface.createPlatformSurface(tempInstance.handle(), window, tempArena);
            System.out.println("[OK] Surface created");
            
            // Now create VulkanContext with surface
            vulkanContext = VulkanContext.builder()
                .applicationName("Triangle App")
                .applicationVersion(1)
                .instanceExtensions(extensions)
                .surface(surface)
                .build();
            
            System.out.println("[OK] VulkanContext created");
            
            // Copy surface to context arena for proper lifecycle management
            surface = VkSurface.createPlatformSurface(vulkanContext.instance().handle(), window, vulkanContext.arena());
        }
    }
    
    private ThreadedRenderer renderer;
    
    private void mainLoop() {
        renderer = new ThreadedRenderer(vulkanContext.arena(), vulkanContext.device().handle(), 
                                      vulkanContext.graphicsQueue(), surface, WIDTH, HEIGHT);
        renderer.init(vulkanContext.physicalDevice(), vulkanContext.graphicsQueueFamily());
        
        System.out.println("[OK] Rendering enabled with per-frame Arena");
        
        long lastTime = System.nanoTime();
        int frameCount = 0;
        
        while (!GLFW.glfwWindowShouldClose(window)) {
            GLFW.glfwPollEvents();
            
            if (framebufferResized) {
                handleResize();
                framebufferResized = false;
            }
            
            // Skip rendering when minimized (0x0 size)
            try (Arena tempArena = Arena.ofConfined()) {
                MemorySegment widthPtr = tempArena.allocate(ValueLayout.JAVA_INT);
                MemorySegment heightPtr = tempArena.allocate(ValueLayout.JAVA_INT);
                GLFW.glfwGetFramebufferSize(window, widthPtr, heightPtr);
                int currentWidth = widthPtr.get(ValueLayout.JAVA_INT, 0);
                int currentHeight = heightPtr.get(ValueLayout.JAVA_INT, 0);
                
                if (currentWidth > 0 && currentHeight > 0) {
                    renderer.drawFrame();
                } else {
                    // Window minimized, skip frame and sleep briefly
                    try { Thread.sleep(10); } catch (InterruptedException e) {}
                }
            }
            
            frameCount++;
            long currentTime = System.nanoTime();
            if (currentTime - lastTime >= 1_000_000_000L) { // 1 second
                System.out.printf("FPS: %d | Threads: %d | Avg Frame Time: %.2fms%n", 
                    frameCount, renderer.getActiveThreads(), renderer.getAverageFrameTime());
                frameCount = 0;
                lastTime = currentTime;
            }
        }
    }
    
    private void cleanup() {
        if (renderer != null) {
            renderer.cleanup();
        }
        
        if (vulkanContext != null) {
            vulkanContext.close();
            System.out.println("[OK] VulkanContext destroyed");
        }
        
        if (window != null && !window.equals(MemorySegment.NULL)) {
            GLFW.glfwDestroyWindow(window);
            System.out.println("[OK] Window destroyed");
        }
        
        GLFW.glfwTerminate();
    }
    
    private void framebufferResizeCallback(MemorySegment window, int width, int height) {
        // Only set flag - don't do any Vulkan operations in callback
        framebufferResized = true;
    }
    
    private void handleResize() {
        try (Arena tempArena = Arena.ofConfined()) {
            // Wait for device to be idle before recreating resources
            Vulkan.deviceWaitIdle(vulkanContext.device().handle()).check();
            
            // Get new window size
            MemorySegment widthPtr = tempArena.allocate(ValueLayout.JAVA_INT);
            MemorySegment heightPtr = tempArena.allocate(ValueLayout.JAVA_INT);
            GLFW.glfwGetFramebufferSize(window, widthPtr, heightPtr);
            int newWidth = widthPtr.get(ValueLayout.JAVA_INT, 0);
            int newHeight = heightPtr.get(ValueLayout.JAVA_INT, 0);
            
            if (newWidth > 0 && newHeight > 0) {
                renderer.resize(newWidth, newHeight);
                System.out.println("[OK] Resized to " + newWidth + "x" + newHeight);
            }
        }
    }
    
    public static void main(String[] args) {
        try {
            new TriangleApp().run();
        } catch (Exception e) {
            System.err.println("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
