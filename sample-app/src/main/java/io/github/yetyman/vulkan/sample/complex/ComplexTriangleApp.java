package io.github.yetyman.vulkan.sample.complex;

import io.github.yetyman.glfw.enums.GLFWKey;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.input.InputManager;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.complex.threading.ThreadedRenderer;
import io.github.yetyman.vulkan.sample.input.SimpleInputHelper;
import io.github.yetyman.vulkan.sample.complex.debug.LODVisualizer;
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

        // Camera controls
        input.onKeyHold(GLFWKey.GLFW_KEY_W, () -> renderer.moveCameraForward(1.0f));
        input.onKeyHold(GLFWKey.GLFW_KEY_S, () -> renderer.moveCameraForward(-1.0f));
        input.onKeyHold(GLFWKey.GLFW_KEY_A, () -> renderer.moveCameraRight(-1.0f));
        input.onKeyHold(GLFWKey.GLFW_KEY_D, () -> renderer.moveCameraRight(1.0f));
        input.onKeyHold(GLFWKey.GLFW_KEY_Q, () -> renderer.moveCameraUp(-1.0f));
        input.onKeyHold(GLFWKey.GLFW_KEY_E, () -> renderer.moveCameraUp(1.0f));

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
        
        // Toggle debug logging with L key
        input.onKeyPress(GLFWKey.GLFW_KEY_L, () -> {
            if (Logger.isEnabled(Logger.Level.DEBUG)) {
                Logger.disable(Logger.Level.DEBUG);
                Logger.info("Debug logging disabled");
            } else {
                Logger.enable(Logger.Level.DEBUG);
                Logger.info("Debug logging enabled");
            }
        });
        
        // LOD Visualization controls
        input.onKeyPress(GLFWKey.GLFW_KEY_V, () -> {
            renderer.getLODVisualizer().toggleWireframe();
            Logger.info("Wireframe: " + (renderer.getLODVisualizer().isWireframeEnabled() ? "ON" : "OFF"));
        });
        
        input.onKeyPress(GLFWKey.GLFW_KEY_C, () -> {
            renderer.getLODVisualizer().toggleColorCoding();
            Logger.info("LOD Color-Coding: " + (renderer.getLODVisualizer().isColorCodingEnabled() ? "ON" : "OFF"));
        });
        
        input.onKeyPress(GLFWKey.GLFW_KEY_B, () -> {
            renderer.getLODVisualizer().toggleSplitScreen();
            Logger.info("Split-Screen: " + (renderer.getLODVisualizer().isSplitScreenEnabled() ? "ON" : "OFF"));
        });

    }
    
    @Override
    protected void onFPSUpdate(int fps) {
        int culled = renderer.getCulledInstanceCount();
        float[] camPos = renderer.getCamera().getPosition();
        float camDist = (float)Math.sqrt(camPos[0]*camPos[0] + camPos[1]*camPos[1] + camPos[2]*camPos[2]);
        Logger.info(String.format("FPS: %d | Cam Dist: %.1fm | Threads: %d | Frame: %.2fms | AA: %s | Culled: %d", 
            fps, camDist, renderer.getActiveThreads(), renderer.getAverageFrameTime(),
            renderer.isAdaptiveAAEnabled() ? "ON" : "OFF", culled));
    }
    
    public static void main(String[] args) {
        Logger.info("Complex Triangle App Controls:");
        Logger.info("  W/S   - Move camera forward/backward (hold for smooth movement)");
        Logger.info("  A/D   - Move camera left/right (hold for smooth movement)");
        Logger.info("  Q/E   - Move camera down/up (hold for smooth movement)");
        Logger.info("  SPACE - Toggle Anti-Aliasing");
        Logger.info("  M     - Cycle AA Mode (NONE/MSAA/POST_PROCESS)");
        Logger.info("  +/-   - Increase/Decrease MSAA samples (when in MSAA mode)");
        Logger.info("  1-8   - Set thread count");
        Logger.info("  L     - Toggle debug logging");
        Logger.info("  V     - Toggle wireframe mode");
        Logger.info("  C     - Toggle LOD color-coding");
        Logger.info("  B     - Toggle split-screen LOD comparison");
        Logger.info("");
        Logger.info("Move the camera to test LOD transitions!");
        Logger.info("");
        
        try (ComplexTriangleApp app = new ComplexTriangleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}