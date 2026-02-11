package io.github.yetyman.vulkan.sample.complex;

import io.github.yetyman.glfw.enums.GLFWKey;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.input.InputManager;
import io.github.yetyman.vulkan.sample.complex.models.GLTFLoader;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.complex.threading.ThreadedRenderer;
import io.github.yetyman.vulkan.sample.input.SimpleInputHelper;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;

/**
 * Complex triangle app using the enhanced VulkanApplication framework.
 * Eliminates ~150 lines of boilerplate compared to the original.
 */
public class ComplexTriangleApp extends VulkanApplication {
    private ThreadedRenderer renderer;
    private GLTFLoader gltfLoader;
    
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
        renderer.init(vulkanContext().graphicsQueueFamily());

        gltfLoader = new GLTFLoader(Arena.ofShared(), vulkanContext().device());
        loadSampleModels();
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

        // Clean up GLTF loader
        if (gltfLoader != null)
            gltfLoader.shutdown();
    }
    
    @Override
    protected void configureInput(InputManager inputManager) {
        SimpleInputHelper input = new SimpleInputHelper(inputManager);

        // Camera controls
        input.onKeyHold(GLFWKey.GLFW_KEY_W, () -> renderer.getCamera().move(0, 0, -0.5f));
        input.onKeyRelease(GLFWKey.GLFW_KEY_W, () -> renderer.getCamera().stopZ());
        input.onKeyHold(GLFWKey.GLFW_KEY_S, () -> renderer.getCamera().move(0, 0, 0.5f));
        input.onKeyRelease(GLFWKey.GLFW_KEY_S, () -> renderer.getCamera().stopZ());
        
        input.onKeyHold(GLFWKey.GLFW_KEY_A, () -> renderer.getCamera().move(-0.5f, 0, 0));
        input.onKeyRelease(GLFWKey.GLFW_KEY_A, () -> renderer.getCamera().stopX());
        input.onKeyHold(GLFWKey.GLFW_KEY_D, () -> renderer.getCamera().move(0.5f, 0, 0));
        input.onKeyRelease(GLFWKey.GLFW_KEY_D, () -> renderer.getCamera().stopX());
        
        input.onKeyHold(GLFWKey.GLFW_KEY_Q, () -> renderer.getCamera().move(0, -0.5f, 0));
        input.onKeyRelease(GLFWKey.GLFW_KEY_Q, () -> renderer.getCamera().stopY());
        input.onKeyHold(GLFWKey.GLFW_KEY_E, () -> renderer.getCamera().move(0, 0.5f, 0));
        input.onKeyRelease(GLFWKey.GLFW_KEY_E, () -> renderer.getCamera().stopY());

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

    }
    
    @Override
    protected void onFPSUpdate(int fps) {
        float[] camPos = renderer.getCamera().getPosition();
        float camDist = (float)Math.sqrt(camPos[0]*camPos[0] + camPos[1]*camPos[1] + camPos[2]*camPos[2]);
        Logger.info(String.format("FPS: %d | Cam Dist: %.1fm | Threads: %d | Frame: %.2fms | AA: %s | Triangles: %d", 
            fps, camDist, renderer.getActiveThreads(), renderer.getAverageFrameTime(),
            renderer.isAdaptiveAAEnabled() ? "ON" : "OFF", renderer.getActiveTriangleCount()));
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
        Logger.info("");
        
        try (ComplexTriangleApp app = new ComplexTriangleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Load sample models at adjacent positions for testing
     */
    public void loadSampleModels() {
        Logger.info("Starting to load sample models...");

        // Load Box
        gltfLoader.loadModel("/sample-models/Box/glTF/Box.gltf")
                .thenAccept(mesh -> {
                    Logger.info("Box model loaded");
                    renderer.addMesh(mesh);
                })
                .exceptionally(throwable -> {
                    Logger.error("Failed to load Box: " + throwable.getMessage());
                    return null;
                });

        // Load Duck
        gltfLoader.loadModel("/sample-models/Duck/glTF/Duck.gltf")
                .thenAccept(mesh -> {
                    Logger.info("Duck model loaded");
                    renderer.addMesh(mesh);
                })
                .exceptionally(throwable -> {
                    Logger.error("Failed to load Duck: " + throwable.getMessage());
                    return null;
                });

        // Load Suzanne
        gltfLoader.loadModel("/sample-models/Suzanne/glTF/Suzanne.gltf")
                .thenAccept(mesh -> {
                    Logger.info("Suzanne model loaded");
                    renderer.addMesh(mesh);
                })
                .exceptionally(throwable -> {
                    Logger.error("Failed to load Suzanne: " + throwable.getMessage());
                    return null;
                });
    }

}