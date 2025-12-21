package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.VulkanApplication;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;

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
        System.out.println("[OK] Context ready for renderer creation");
    }
    
    @Override
    protected Object createRenderer() {
        SimpleRenderer renderer = new SimpleRenderer(vulkanContext().arena(), vulkanContext().device().handle(),
                                        vulkanContext().graphicsQueue(), surface(), 800, 600);
        renderer.init(vulkanContext().physicalDevice(), vulkanContext().graphicsQueueFamily());
        System.out.println("[OK] New simple renderer initialized");
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
        System.out.printf("FPS: %d%n", fps);
    }
    
    public static void main(String[] args) {
        try (SimpleTriangleApp app = new SimpleTriangleApp()) {
            app.run();
        } catch (Exception e) {
            System.err.println("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}