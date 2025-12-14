package io.github.yetyman.sample;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.win32.VkWin32Surface;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.glfw.GLFW;

import java.lang.foreign.*;

public class TriangleApp {
    static { VulkanLibrary.load(); }
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    private MemorySegment window;
    private Arena arena;
    private MemorySegment instance;
    private MemorySegment physicalDevice;
    private MemorySegment device;
    private MemorySegment queue;
    private int queueFamilyIndex;
    
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
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        
        window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "Vulkan Triangle");
        if (window.equals(MemorySegment.NULL)) {
            throw new RuntimeException("Failed to create window");
        }
        
        System.out.println("[OK] Window created");
    }
    
    private MemorySegment surface;
    
    private VkInstance vkInstance;
    private VkDevice vkDevice;
    
    private void initVulkan() {
        arena = Arena.ofConfined();
        
        String[] extensions = GLFW.glfwGetRequiredInstanceExtensions(arena);
        if (extensions == null) {
            throw new RuntimeException("Failed to get GLFW extensions");
        }
        
        System.out.println("Extensions: " + String.join(", ", extensions));
        
        vkInstance = VkInstance.builder()
            .applicationName("Triangle App")
            .applicationVersion(1)
            .engineName("NoEngine")
            .engineVersion(0)
            .extensions(extensions)
            .build(arena);
        instance = vkInstance.handle();
        System.out.println("[OK] Vulkan instance created");

        
        physicalDevice = VkPhysicalDeviceOps.enumerate(instance).first(arena);
        System.out.println("[OK] Physical device selected");
        
        queueFamilyIndex = VkQueueFamily.findGraphics(physicalDevice, arena);
        String[] deviceExtensions = {"VK_KHR_swapchain"};
        
        vkDevice = VkDevice.builder()
            .physicalDevice(physicalDevice)
            .queueFamily(queueFamilyIndex)
            .extensions(deviceExtensions)
            .build(arena);
        device = vkDevice.handle();
        System.out.println("[OK] Logical device created");
        
        queue = vkDevice.getQueue(queueFamilyIndex, 0);
        System.out.println("[OK] Queue retrieved");
        
        surface = VkSurface.createPlatformSurface(instance, window, arena);
        System.out.println("[OK] Surface created");
    }
    
    private Renderer renderer;
    
    private void mainLoop() {
        renderer = new Renderer(arena, device, queue, surface, WIDTH, HEIGHT);
        renderer.init(physicalDevice, queueFamilyIndex);
        
        System.out.println("[OK] Rendering enabled with per-frame Arena");
        
        while (!GLFW.glfwWindowShouldClose(window)) {
            GLFW.glfwPollEvents();
            renderer.drawFrame();
        }
    }
    
    private void cleanup() {
        if (renderer != null) {
            renderer.cleanup();
        }
        
        if (vkDevice != null) {
            Vulkan.deviceWaitIdle(device).check();
            vkDevice.close();
            System.out.println("[OK] Device destroyed");
        }
        
        if (surface != null && !surface.equals(MemorySegment.NULL)) {
            VulkanSurface.destroySurface(instance, surface);
            System.out.println("[OK] Surface destroyed");
        }
        
        if (vkInstance != null) {
            vkInstance.close();
            System.out.println("[OK] Instance destroyed");
        }
        
        if (arena != null) {
            arena.close();
        }
        
        if (window != null && !window.equals(MemorySegment.NULL)) {
            GLFW.glfwDestroyWindow(window);
            System.out.println("[OK] Window destroyed");
        }
        
        GLFW.glfwTerminate();
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
