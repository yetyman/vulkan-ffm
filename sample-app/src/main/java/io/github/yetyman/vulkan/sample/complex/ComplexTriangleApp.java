package io.github.yetyman.vulkan.sample.complex;

import io.github.yetyman.glfw.enums.GLFWKey;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.input.InputManager;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.complex.models.GLTFLoader;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.complex.threading.ThreadedRenderer;
import io.github.yetyman.vulkan.sample.input.SimpleInputHelper;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;

public class ComplexTriangleApp extends VulkanApplication {

    private GraphicsLoop loop;
    private ThreadedRenderer renderer;
    private GLTFLoader gltfLoader;

    public ComplexTriangleApp() {
        super("Complex Triangle", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        renderer = new ThreadedRenderer(vulkanContext().arena(), vulkanContext().device(),
            vulkanContext().graphicsVkQueue(), surface(), 800, 600);
        renderer.init(vulkanContext().graphicsQueueFamily());

        gltfLoader = new GLTFLoader(Arena.ofShared(), vulkanContext().device());
        loadSampleModels();

        loop = GraphicsLoop.builder()
            .renderer(renderer)
            .driver(LoopDriver.uncapped())
            .shouldClose(() -> windowSystem().shouldClose(window()))
            .pollEvents(() -> windowSystem().pollEvents())
            .onResize(dims -> renderer.resize(dims[0], dims[1]))
            .onFpsUpdate(fps -> {
                float[] camPos = renderer.getCamera().getPosition();
                float camDist = (float) Math.sqrt(camPos[0]*camPos[0] + camPos[1]*camPos[1] + camPos[2]*camPos[2]);
                Logger.info(String.format("FPS: %d | Cam Dist: %.1fm | Threads: %d | Frame: %.2fms | AA: %s | Triangles: %d",
                    fps, camDist, renderer.getActiveThreads(), renderer.getAverageFrameTime(),
                    renderer.isAdaptiveAAEnabled() ? "ON" : "OFF", renderer.getActiveTriangleCount()));
            })
            .build();

        loop.runOnCurrentThread();
        renderer.close();
    }

    @Override
    protected void onWindowResize(int w, int h) {
        if (loop != null) loop.signalResize(w, h);
    }

    @Override
    protected void configureInput(InputManager inputManager) {
        SimpleInputHelper input = new SimpleInputHelper(inputManager);

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

        input.onKeyPress(GLFWKey.GLFW_KEY_SPACE, () -> {
            renderer.setAdaptiveAAEnabled(!renderer.isAdaptiveAAEnabled());
            Logger.info("AA toggled: " + (renderer.isAdaptiveAAEnabled() ? "ON" : "OFF"));
        });
        input.onKeyPress(GLFWKey.GLFW_KEY_M, () -> {
            renderer.cycleAAModeKey();
            Logger.info("AA Mode: " + renderer.getAAMode());
        });
        input.onKeyPress(GLFWKey.GLFW_KEY_EQUAL, () -> renderer.increaseMSAAKey());
        input.onKeyPress(GLFWKey.GLFW_KEY_MINUS, () -> renderer.decreaseMSAAKey());

        for (int i = 1; i <= 8; i++) {
            final int threadCount = i;
            GLFWKey key = GLFWKey.fromValue(GLFWKey.GLFW_KEY_1.value() + i - 1);
            input.onKeyPress(key, () -> {
                renderer.setThreadCount(threadCount);
                Logger.info("Thread count set to: " + threadCount);
            });
        }

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

    @Override protected void shutdown() {
        if (gltfLoader != null) gltfLoader.shutdown();
    }

    public static void main(String[] args) {
        Logger.info("Complex Triangle App Controls:");
        Logger.info("  W/S   - Move camera forward/backward");
        Logger.info("  A/D   - Move camera left/right");
        Logger.info("  Q/E   - Move camera down/up");
        Logger.info("  SPACE - Toggle Anti-Aliasing");
        Logger.info("  M     - Cycle AA Mode");
        Logger.info("  +/-   - Increase/Decrease MSAA samples");
        Logger.info("  1-8   - Set thread count");
        Logger.info("  L     - Toggle debug logging");

        try (ComplexTriangleApp app = new ComplexTriangleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Application error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void loadSampleModels() {
        gltfLoader.loadModel("/sample-models/Box/glTF/Box.gltf")
            .thenAccept(mesh -> { Logger.info("Box model loaded"); renderer.addMesh(mesh); })
            .exceptionally(t -> { Logger.error("Failed to load Box: " + t.getMessage()); return null; });

        gltfLoader.loadModel("/sample-models/Duck/glTF/Duck.gltf")
            .thenAccept(mesh -> { Logger.info("Duck model loaded"); renderer.addMesh(mesh); })
            .exceptionally(t -> { Logger.error("Failed to load Duck: " + t.getMessage()); return null; });

        gltfLoader.loadModel("/sample-models/Suzanne/glTF/Suzanne.gltf")
            .thenAccept(mesh -> { Logger.info("Suzanne model loaded"); renderer.addMesh(mesh); })
            .exceptionally(t -> { Logger.error("Failed to load Suzanne: " + t.getMessage()); return null; });
    }
}
