package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.util.Logger;

/**
 * Simplified triangle app using the enhanced VulkanApplication framework.
 * Eliminates ~100 lines of boilerplate compared to the original.
 */
public class SimpleTriangleApp extends VulkanApplication {
    private SimpleRenderer renderer;
    
    public SimpleTriangleApp() {
        super("Simple Triangle", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }
    
    @Override
    protected void initialize() {
        // Initialize Vulkan capabilities first
        Logger.info("Initializing VulkanCapabilities...");
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        
        // Context is fully ready - can query device capabilities here
        Logger.info("Context ready for renderer creation");
        
        renderer = new SimpleRenderer(vulkanContext().arena(), vulkanContext().device(),
                                        vulkanContext().graphicsQueue(), surface(), 800, 600);
        renderer.init(vulkanContext().physicalDevice(), vulkanContext().graphicsQueueFamily());
        Logger.info("New simple renderer initialized");
    }
    
    @Override
    protected void render() {
        renderer.drawFrame();
    }
    
    @Override
    protected void onResize(int width, int height) {
        renderer.resize(width, height);
    }
    
    @Override
    protected void shutdown() {
        if (renderer != null) {
            renderer.close();
        }
    }
    
    @Override
    protected void onFPSUpdate(int fps) {
        Logger.info(String.format("FPS: %d", fps));
    }
    
    public static void main(String[] args) {
        try (SimpleTriangleApp app = new SimpleTriangleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}