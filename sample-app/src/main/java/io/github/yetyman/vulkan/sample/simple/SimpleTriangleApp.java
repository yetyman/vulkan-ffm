package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.VulkanApplication;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.util.Logger;

/**
 * Simplified triangle app using the enhanced VulkanApplication framework.
 * Eliminates ~100 lines of boilerplate compared to the original.
 */
public class SimpleTriangleApp extends VulkanApplication {
    
    public SimpleTriangleApp() {
        super("Simple Triangle", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }
    
    @Override
    protected void initialize() {
        // Context is fully ready - can query device capabilities here
        Logger.info("Context ready for renderer creation");
    }
    
    @Override
    protected Object createRenderer() {
        SimpleRenderer renderer = new SimpleRenderer(vulkanContext().arena(), vulkanContext().device().handle(),
                                        vulkanContext().graphicsQueue(), surface(), 800, 600);
        renderer.init(vulkanContext().physicalDevice(), vulkanContext().graphicsQueueFamily());
        Logger.info("New simple renderer initialized");
        return renderer;
    }
    
    @Override
    protected void render() {
        SimpleRenderer renderer = renderer();
        renderer.drawFrame();
    }
    
    @Override
    protected void onResize(int width, int height) {
        SimpleRenderer renderer = renderer();
        renderer.resize(width, height);
    }
    
    @Override
    protected void shutdown() {
        SimpleRenderer renderer = renderer();
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