package io.github.yetyman.vulkan.sample.complex;

import io.github.yetyman.glfw.enums.GLFWKey;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.input.InputManager;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.complex.threading.ThreadedRenderer;
import io.github.yetyman.vulkan.sample.input.SimpleInputHelper;
import io.github.yetyman.vulkan.util.Logger;

/**
 * Complex triangle app using the enhanced VulkanApplication framework.
 * Eliminates ~150 lines of boilerplate compared to the original.
 */
public class ComplexTriangleApp extends VulkanApplication {
    private ThreadedRenderer renderer;
    
    public ComplexTriangleApp() {
        super("Complex Triangle", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }
    
    @Override
    protected void initialize() {
        // Initialize Vulkan capabilities first
        Logger.info("Initializing VulkanCapabilities...");
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        
        // Context is fully ready - can query device capabilities, cache resources, etc.
        Logger.info("Context ready for complex renderer creation");
        
        renderer = new ThreadedRenderer(vulkanContext().arena(), vulkanContext().device(),
                                                         vulkanContext().graphicsQueue(), surface(), 800, 600);
        renderer.init(vulkanContext().physicalDevice(), vulkanContext().graphicsQueueFamily());
        renderer.loadSampleModels();
        Logger.info("Threaded renderer initialized");
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
            renderer.cleanup();
        }
    }
    
    @Override
    protected void configureInput(InputManager inputManager) {
        SimpleInputHelper input = new SimpleInputHelper(inputManager);

        // Toggle adaptive AA with spacebar
        input.onKeyPress(GLFWKey.GLFW_KEY_SPACE, () -> {
            renderer.setAdaptiveAAEnabled(!renderer.isAdaptiveAAEnabled());
            Logger.info("AA toggled: " + (renderer.isAdaptiveAAEnabled() ? "ON" : "OFF"));
        });
        
        // Increase AA Mode with M key
        input.onKeyPress(GLFWKey.GLFW_KEY_M, () -> {
            renderer.cycleAAModeKey();
            Logger.info("AA Mode: " + renderer.getAAMode());
        });
        
        // Increase MSAA samples with + key
        input.onKeyPress(GLFWKey.GLFW_KEY_EQUAL, () -> {
            renderer.increaseMSAAKey();
        });
        
        // Decrease MSAA samples with - key
        input.onKeyPress(GLFWKey.GLFW_KEY_MINUS, () -> {
            renderer.decreaseMSAAKey();
        });
        
        // Adjust thread count with number keys
        for (int i = 1; i <= 8; i++) {
            final int threadCount = i;
            GLFWKey key = GLFWKey.fromValue(GLFWKey.GLFW_KEY_1.value() + i - 1);
            input.onKeyPress(key, () -> {
                renderer.setThreadCount(threadCount);
                Logger.info("Thread count set to: " + threadCount);
            });
        }
        
        // Toggle debug logging with D key
        input.onKeyPress(GLFWKey.GLFW_KEY_D, () -> {
            if (Logger.isEnabled(Logger.Level.DEBUG)) {
                Logger.disable(Logger.Level.DEBUG);
                Logger.info("Debug logging disabled");
            } else {
                Logger.enable(Logger.Level.DEBUG);
                Logger.info("Debug logging enabled");
            }
        });

    }
    
    @Override
    protected void onFPSUpdate(int fps) {
        int culled = renderer.getCulledInstanceCount();
        Logger.info(String.format("FPS: %d | Threads: %d | Avg Frame Time: %.2fms | AA: %s | Culled: %d", 
            fps, renderer.getActiveThreads(), renderer.getAverageFrameTime(),
            renderer.isAdaptiveAAEnabled() ? "ON" : "OFF", culled));
    }
    
    public static void main(String[] args) {
        Logger.info("Complex Triangle App Controls:");
        Logger.info("  SPACE - Toggle Anti-Aliasing");
        Logger.info("  M     - Cycle AA Mode (NONE/MSAA/POST_PROCESS)");
        Logger.info("  +/-   - Increase/Decrease MSAA samples (when in MSAA mode)");
        Logger.info("  1-8   - Set thread count");
        Logger.info("  D     - Toggle debug logging");
        Logger.info("");
        
        try (ComplexTriangleApp app = new ComplexTriangleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}