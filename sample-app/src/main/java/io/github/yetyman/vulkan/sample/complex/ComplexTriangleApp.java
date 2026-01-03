package io.github.yetyman.vulkan.sample.complex;

import io.github.yetyman.glfw.enums.GLFWAction;
import io.github.yetyman.glfw.enums.GLFWKey;
import io.github.yetyman.glfw.enums.GLFWMouseButton;
import io.github.yetyman.vulkan.VulkanApplication;
import io.github.yetyman.vulkan.input.InputManager;
import io.github.yetyman.vulkan.input.events.KeyEvent;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.complex.threading.ThreadedRenderer;
import io.github.yetyman.vulkan.util.Logger;

/**
 * Complex triangle app using the enhanced VulkanApplication framework.
 * Eliminates ~150 lines of boilerplate compared to the original.
 */
public class ComplexTriangleApp extends VulkanApplication {
    
    public ComplexTriangleApp() {
        super("Complex Triangle", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
        
        // Configure logging levels - disable verbose debug output by default
        Logger.setLevels(Logger.Level.INFO, Logger.Level.INPUT, Logger.Level.AA, Logger.Level.LOAD);
    }
    
    @Override
    protected void initialize() {
        // Context is fully ready - can query device capabilities, cache resources, etc.
        Logger.info("Context ready for complex renderer creation");
    }
    
    @Override
    protected Object createRenderer() {
        ThreadedRenderer renderer = new ThreadedRenderer(vulkanContext().arena(), vulkanContext().device(),
                                                         vulkanContext().graphicsQueue(), surface(), 800, 600);
        renderer.init(vulkanContext().physicalDevice(), vulkanContext().graphicsQueueFamily());
        renderer.loadSampleModels();
        Logger.info("Threaded renderer initialized");
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

        // Toggle adaptive AA with spacebar
        inputManager.registerHandler(
            event -> event instanceof KeyEvent ke && ke.key() == GLFWKey.GLFW_KEY_SPACE && ke.action() == GLFWAction.GLFW_PRESS,
            () -> {
                ThreadedRenderer renderer = renderer();
                renderer.setAdaptiveAAEnabled(!renderer.isAdaptiveAAEnabled());
                Logger.input("AA toggled: " + (renderer.isAdaptiveAAEnabled() ? "ON" : "OFF"));
            }
        );
        
        // Adjust thread count with number keys
        for (int i = 1; i <= 8; i++) {
            final int threadCount = i;
            int finalI = i;
            inputManager.registerHandler(
                event -> event instanceof KeyEvent ke && ke.key() == (GLFWKey.GLFW_KEY_1 + finalI - 1) && ke.action() == GLFWAction.GLFW_PRESS,
                () -> {
                    ThreadedRenderer renderer = renderer();
                    renderer.setThreadCount(threadCount);
                    Logger.input("Thread count set to: " + threadCount);
                }
            );
        }
        
        // Toggle debug logging with D key
        inputManager.registerHandler(
            event -> event instanceof KeyEvent ke && ke.key() == GLFWKey.GLFW_KEY_D && ke.action() == GLFWAction.GLFW_PRESS,
            () -> {
                if (Logger.isEnabled(Logger.Level.DEBUG)) {
                    Logger.disable(Logger.Level.DEBUG);
                    Logger.input("Debug logging disabled");
                } else {
                    Logger.enable(Logger.Level.DEBUG);
                    Logger.input("Debug logging enabled");
                }
            }
        );
        
        // Toggle LOD logging with L key
        inputManager.registerHandler(
            event -> event instanceof KeyEvent ke && ke.key() == GLFWKey.GLFW_KEY_L && ke.action() == GLFWAction.GLFW_PRESS,
            () -> {
                if (Logger.isEnabled(Logger.Level.LOD)) {
                    Logger.disable(Logger.Level.LOD, Logger.Level.RENDER, Logger.Level.DRAW, Logger.Level.MATRIX);
                    Logger.input("LOD/Render logging disabled");
                } else {
                    Logger.enable(Logger.Level.LOD, Logger.Level.RENDER, Logger.Level.DRAW, Logger.Level.MATRIX);
                    Logger.input("LOD/Render logging enabled");
                }
            }
        );
    }
    
    @Override
    protected void onFPSUpdate(int fps) {
        ThreadedRenderer renderer = renderer();
        Logger.info(String.format("FPS: %d | Threads: %d | Avg Frame Time: %.2fms | AA: %s", 
            fps, renderer.getActiveThreads(), renderer.getAverageFrameTime(),
            renderer.isAdaptiveAAEnabled() ? "ON" : "OFF"));
    }
    
    public static void main(String[] args) {
        Logger.info("Complex Triangle App Controls:");
        Logger.info("  SPACE - Toggle Anti-Aliasing");
        Logger.info("  1-8   - Set thread count");
        Logger.info("  D     - Toggle debug logging");
        Logger.info("  L     - Toggle LOD/render logging");
        Logger.info("");
        
        try (ComplexTriangleApp app = new ComplexTriangleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}