package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.assets.FontRegistry;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.layers.lodscene.LodSceneLayer;
import io.github.yetyman.vulkan.layers.lodscene.SceneObject;
import io.github.yetyman.vulkan.layers.text.GPUDrivenTextLayer;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.mesh.source.primitives.BoxSource;
import io.github.yetyman.vulkan.mesh.source.primitives.SphereSource;
import io.github.yetyman.vulkan.sample.spatial.OrbitCamera;
import io.github.yetyman.vulkan.sample.spatial.OrbitCameraLayer;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.ui.UIComposite;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.ui.input.InputEvent;
import io.github.yetyman.vulkan.ui.input.types.KeyInputData;
import io.github.yetyman.vulkan.ui.input.types.PointerInputData;
import io.github.yetyman.vulkan.ui.input.types.ScrollInputData;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Sample application demonstrating the LOD scene system: multiple models placed in a
 * 3D scene, each with a runtime-generated LOD chain, navigated with an interactive
 * orbit camera.
 *
 * <p>Left-drag: orbit. Scroll: zoom. Right-drag: pan.
 *
 * <p>LOD selection is automatic based on screen-space error from the camera distance
 * to each object's bounding box. A text overlay shows per-object LOD state and totals.
 */
public class LodSceneApp extends VulkanApplication {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final String FONT_ID = "default";
    private static final String[] CANDIDATE_FONT_PATHS = {
            "C:/Windows/Fonts/segoeui.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf"
    };

    private GraphicsLoop loop;
    private QemSimplifierDemoFrame frame;
    private UIComposite composite;
    private Arena uiArena;
    private FontRegistry fontRegistry;
    private VkCommandPool textUploadPool;
    private OrbitCamera camera;

    public LodSceneApp() {
        super("LOD Scene Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        byte[] fontBytes = loadSystemFontBytes();
        textUploadPool = vulkanContext().createTransientCommandPool();

        AssetRegistry assets = new AssetRegistry();
        fontRegistry = new FontRegistry(
                vulkanContext().device(), vulkanContext().graphicsVkQueue(), textUploadPool);
        fontRegistry.loadFont(FONT_ID, fontBytes, 1024, 1024);
        assets.register(FontRegistry.class, fontRegistry);

        uiArena = Arena.ofShared();

        UIContext uiContext = UIContext.builder()
                .vulkan(vulkanContext())
                .assets(assets)
                .dimensions(WIDTH, HEIGHT)
                .applicationArena(uiArena)
                .build();

        // Camera
        camera = new OrbitCamera();
        camera.setDistance(15f);
        camera.setAspect((float) WIDTH / HEIGHT);
        OrbitCameraLayer cameraLayer = new OrbitCameraLayer(camera);

        // LOD scene layer
        LodSceneLayer sceneLayer = new LodSceneLayer();
        sceneLayer.setCameraSource(layer -> layer.setCamera(camera.eye(), camera.view(), camera.proj()));
        populateScene(sceneLayer);

        // Text overlay
        GPUDrivenTextLayer textLayer = new GPUDrivenTextLayer(FONT_ID);
        textLayer.setFrameCallback(batch -> {
            int w = uiContext.width();
            int h = uiContext.height();

            batch.drawText(FONT_ID, "LOD Scene Demo - Orbit Camera", 20, 24, 18,
                    1.0f, 1.0f, 1.0f, 1.0f);
            batch.drawText(FONT_ID, "Left-drag: orbit | Scroll: zoom | Right-drag: pan",
                    20, 46, 12, 0.6f, 0.7f, 0.8f, 1.0f);

            // Per-object stats
            List<SceneObject> objects = sceneLayer.objects();
            int y = 70;
            for (int i = 0; i < objects.size(); i++) {
                SceneObject obj = objects.get(i);
                String line = String.format("[%s] LOD %d/%d  Tris: %d",
                        obj.name(), obj.currentLod(), obj.lodLevelCount() - 1,
                        obj.currentTriangleCount());
                float r = 0.8f, g = 1.0f, b = 0.7f;
                if (obj.activeTransition() != null) {
                    line += String.format("  (transitioning %.0f%%)",
                            obj.activeTransition().factor() * 100);
                    r = 1.0f; g = 0.8f; b = 0.3f;
                }
                batch.drawText(FONT_ID, line, 20, y, 13, r, g, b, 1.0f);
                y += 18;
            }

            // Totals
            batch.drawText(FONT_ID,
                    String.format("Total: %d objects, %d triangles",
                            sceneLayer.objectCount(), sceneLayer.totalTrianglesThisFrame()),
                    20, h - 20, 14, 0.7f, 0.9f, 1.0f, 1.0f);
        });

        composite = UIComposite.builder()
                .context(uiContext)
                .layer(cameraLayer)
                .layer(sceneLayer)
                .layer(textLayer)
                .build();

        wireInput(composite);

        frame = new QemSimplifierDemoFrame(
                vulkanContext().arena(), vulkanContext().device(),
                vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, composite);
        frame.init(vulkanContext().graphicsQueueFamily());

        loop = GraphicsLoop.builder()
                .renderer(frame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> {
                    frame.resize(dims[0], dims[1]);
                    camera.setAspect((float) dims[0] / dims[1]);
                })
                .onFpsUpdate(m -> Logger.info(m.summary()))
                .build();

        loop.start();

        while (!windowSystem().shouldClose(window())) {
            windowSystem().waitEventsTimeout(0.004);
        }
        loop.stop();
    }

    private void wireInput(UIComposite composite) {
        Arena callbackArena = Arena.global();
        double[] lastMousePos = {0, 0};

        GLFWCallbacks.setCursorPosCallback(window(), (w, x, y) -> {
            float dx = (float) (x - lastMousePos[0]);
            float dy = (float) (y - lastMousePos[1]);
            lastMousePos[0] = x;
            lastMousePos[1] = y;
            composite.dispatchInput(PointerInputData.mouseMove((float) x, (float) y, dx, dy));
        }, callbackArena);

        GLFWCallbacks.setMouseButtonCallback(window(), (w, button, action, mods) -> {
            boolean pressed = action != 0;
            composite.dispatchInput(pressed
                    ? PointerInputData.mousePress(button, (float) lastMousePos[0], (float) lastMousePos[1])
                    : PointerInputData.mouseRelease(button, (float) lastMousePos[0], (float) lastMousePos[1]));
        }, callbackArena);

        GLFWCallbacks.setScrollCallback(window(), (w, dx, dy) ->
                composite.dispatchInput(ScrollInputData.scroll(
                        (float) lastMousePos[0], (float) lastMousePos[1], (float) dx, (float) dy)),
                callbackArena);

        GLFWCallbacks.setKeyCallback(window(), (w, key, scancode, action, mods) -> {
            InputEvent event = switch (action) {
                case 1 -> KeyInputData.press(key, scancode, mods);
                case 2 -> KeyInputData.repeat(key, scancode, mods);
                default -> KeyInputData.release(key, scancode, mods);
            };
            composite.dispatchInput(event);
        }, callbackArena);
    }

    private void populateScene(LodSceneLayer sceneLayer) {
        Arena sourceArena = Arena.ofShared();

        // Place several spheres at different positions and sizes
        sceneLayer.addModel("Sphere-A",
                new SphereSource(sourceArena, 1.0f, 32, 48),
                sourceArena, Mat4.translation(-4, 0, 0));

        sceneLayer.addModel("Sphere-B",
                new SphereSource(sourceArena, 1.5f, 32, 48),
                sourceArena, Mat4.translation(0, 0, 0));

        sceneLayer.addModel("Sphere-C",
                new SphereSource(sourceArena, 0.8f, 32, 48),
                sourceArena, Mat4.translation(4, 0, 0));

        // Place some boxes at different heights
        sceneLayer.addModel("Box-A",
                new BoxSource(sourceArena, new Vec3(-0.5f, -0.5f, -0.5f), new Vec3(0.5f, 0.5f, 0.5f)),
                sourceArena, Mat4.translation(-2, 3, 0));

        sceneLayer.addModel("Box-B",
                new BoxSource(sourceArena, new Vec3(-1.0f, -0.25f, -0.75f), new Vec3(1.0f, 0.25f, 0.75f)),
                sourceArena, Mat4.translation(2, 3, 0));

        // A distant high-detail sphere (should drop to low LOD quickly)
        sceneLayer.addModel("Far-Sphere",
                new SphereSource(sourceArena, 2.0f, 48, 64),
                sourceArena, Mat4.translation(0, 0, -20));
    }

    @Override
    protected void shutdown() {
        if (loop != null) loop.stop();

        io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(vulkanContext().device().handle()).check();

        if (frame != null) {
            try { frame.close(); } catch (Exception e) { Logger.info("frame.close() failed: " + e); }
        }
        if (composite != null) {
            try { composite.close(); } catch (Exception e) { Logger.info("composite.close() failed: " + e); }
        }
        if (fontRegistry != null) {
            try { fontRegistry.close(); } catch (Exception e) { Logger.info("fontRegistry.close() failed: " + e); }
        }
        if (textUploadPool != null) {
            try { textUploadPool.close(); } catch (Exception e) { Logger.info("textUploadPool.close() failed: " + e); }
        }
        if (uiArena != null) uiArena.close();
    }

    @Override
    protected void onWindowResize(int width, int height) {
        if (loop != null) loop.signalResize(width, height);
    }

    private byte[] loadSystemFontBytes() {
        for (String path : CANDIDATE_FONT_PATHS) {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                try {
                    return Files.readAllBytes(p);
                } catch (Exception e) {
                    // try next
                }
            }
        }
        throw new RuntimeException("No system font found. Tried: " + String.join(", ", CANDIDATE_FONT_PATHS));
    }

    public static void main(String[] args) {
        new LodSceneApp().run();
    }
}
