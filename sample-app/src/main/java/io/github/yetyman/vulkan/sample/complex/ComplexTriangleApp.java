package io.github.yetyman.vulkan.sample.complex;

import io.github.yetyman.vulkan.VulkanApplication;
import io.github.yetyman.vulkan.input.InputManager;
import io.github.yetyman.vulkan.input.events.KeyEvent;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.complex.threading.ThreadedRenderer;
import io.github.yetyman.glfw.GLFW;

/**
 * Complex triangle app using the enhanced VulkanApplication framework.
 * Eliminates ~150 lines of boilerplate compared to the original.
 */
public class ComplexTriangleApp extends VulkanApplication {
    
    public ComplexTriangleApp() {
        super("Complex Triangle", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }
    
    @Override
    protected void initialize() {
        // Context is fully ready - can query device capabilities, cache resources, etc.
        System.out.println("[OK] Context ready for complex renderer creation");
    }
    
    @Override
    protected Object createRenderer() {
        ThreadedRenderer renderer = new ThreadedRenderer(vulkanContext().arena(), vulkanContext().device().handle(),
                                                         vulkanContext().graphicsQueue(), surface(), 800, 600);
        renderer.init(vulkanContext().physicalDevice(), vulkanContext().graphicsQueueFamily());
        renderer.loadSampleModels();
        System.out.println("[OK] Threaded renderer initialized");
        return renderer;
    }
    
    @Override
    protected void render() {
        ThreadedRenderer renderer = renderer();
        renderer.drawFrame();
    }
    
    @Override
    protected void onResize(int width, int height) {
        ThreadedRenderer renderer = renderer();
        renderer.resize(width, height);
    }
    
    @Override
    protected void shutdown() {
        ThreadedRenderer renderer = renderer();
        if (renderer != null) {
            renderer.cleanup();
        }
    }
    
    @Override
    protected void configureInput(InputManager inputManager) {
        ThreadedRenderer renderer = renderer();
        
        // Toggle adaptive AA with spacebar
        inputManager.registerHandler(
            event -> event instanceof KeyEvent ke && ke.key() == GLFW.GLFW_KEY_SPACE && ke.action() == GLFW.GLFW_PRESS,
            () -> {
                renderer.setAdaptiveAAEnabled(!renderer.isAdaptiveAAEnabled());
                System.out.println("[INPUT] AA toggled: " + (renderer.isAdaptiveAAEnabled() ? "ON" : "OFF"));
            }
        );
        
        // Adjust thread count with number keys
        for (int i = 1; i <= 8; i++) {
            final int threadCount = i;
            int finalI = i;
            inputManager.registerHandler(
                event -> event instanceof KeyEvent ke && ke.key() == (GLFW.GLFW_KEY_1 + finalI - 1) && ke.action() == GLFW.GLFW_PRESS,
                () -> {
                    renderer.setThreadCount(threadCount);
                    System.out.println("[INPUT] Thread count set to: " + threadCount);
                }
            );
        }
    }
    
    @Override
    protected void onFPSUpdate(int fps) {
        ThreadedRenderer renderer = renderer();
        System.out.printf("FPS: %d | Threads: %d | Avg Frame Time: %.2fms | AA: %s%n", 
            fps, renderer.getActiveThreads(), renderer.getAverageFrameTime(),
            renderer.isAdaptiveAAEnabled() ? "ON" : "OFF");
    }
    
    public static void main(String[] args) {
        try (ComplexTriangleApp app = new ComplexTriangleApp()) {
            app.run();
        } catch (Exception e) {
            System.err.println("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}