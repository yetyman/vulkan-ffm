package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.spatial.isosurface.MarchingCubes;
import io.github.yetyman.helpers.math.spatial.isosurface.MeshOutput;
import io.github.yetyman.helpers.math.spatial.isosurface.ScalarField3D;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.assets.FontRegistry;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.layers.scene3d.DepthMode;
import io.github.yetyman.vulkan.layers.scene3d.Scene3DOverlayLayer;
import io.github.yetyman.vulkan.layers.text.GPUDrivenTextLayer;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.ui.Scene3DOverlayExampleFrame;
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

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Marching Cubes Demo — shows scalar field sample points alongside the extracted mesh.
 *
 * Controls:
 *   Left-drag: orbit
 *   Scroll: zoom
 *   1-4: switch scalar field
 *   +/-: change resolution
 *   I: toggle iso-level animation
 *   F: toggle field point visualization
 */
public class MarchingCubesApp extends VulkanApplication {

    private static final int WIDTH = 1280, HEIGHT = 720;
    private static final String FONT_ID = "default";
    private static final String[] FONT_PATHS = {
            "C:/Windows/Fonts/consola.ttf", "C:/Windows/Fonts/arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", "/System/Library/Fonts/Menlo.ttc"
    };

    private final OrbitCamera camera = new OrbitCamera();
    private GraphicsLoop loop;
    private Scene3DOverlayLayer overlay;
    private GPUDrivenTextLayer textLayer;

    private int resolution = 20;
    private float isoLevel = 0f;
    private boolean animateIso = true;
    private boolean showFieldPoints = true;
    private int fieldIndex = 0;
    private MeshOutput currentMesh;
    private final long startNanos = System.nanoTime();

    private static final ScalarField3D[] FIELDS = {
            // 0: Sphere
            (x, y, z) -> 1f - (float) Math.sqrt(x * x + y * y + z * z),
            // 1: Two metaballs
            (x, y, z) -> {
                float d1 = (float) Math.sqrt((x - 0.5f) * (x - 0.5f) + y * y + z * z);
                float d2 = (float) Math.sqrt((x + 0.5f) * (x + 0.5f) + y * y + z * z);
                return 0.5f / (d1 + 0.01f) + 0.5f / (d2 + 0.01f) - 2f;
            },
            // 2: Torus-like
            (x, y, z) -> {
                float r = 0.7f;
                float R = (float) Math.sqrt(x * x + z * z) - r;
                return 0.3f - (float) Math.sqrt(R * R + y * y);
            },
            // 3: Sine waves
            (x, y, z) -> (float) (Math.sin(x * 4) + Math.sin(y * 4) + Math.sin(z * 4)) / 3f
    };

    private static final String[] FIELD_NAMES = {"Sphere", "Metaballs", "Torus", "Sine Waves"};

    public MarchingCubesApp() {
        super("Marching Cubes Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        camera.setDistance(5f);
        camera.setAspect((float) WIDTH / HEIGHT);

        AssetRegistry assets = new AssetRegistry();
        Arena uiArena = Arena.ofShared();

        VkCommandPool uploadPool = VkCommandPool.builder()
                .device(vulkanContext().device())
                .queueFamilyIndex(vulkanContext().graphicsQueueFamily())
                .build(vulkanContext().arena());
        FontRegistry fonts = new FontRegistry(vulkanContext().device(), vulkanContext().graphicsVkQueue(), uploadPool);
        fonts.loadFont(FONT_ID, loadFont(), 1024, 1024);
        assets.register(FontRegistry.class, fonts);

        UIContext uiContext = UIContext.builder()
                .vulkan(vulkanContext())
                .assets(assets)
                .dimensions(WIDTH, HEIGHT)
                .applicationArena(uiArena)
                .build();

        overlay = new Scene3DOverlayLayer();
        textLayer = new GPUDrivenTextLayer(FONT_ID);

        overlay.setFrameCallback(this::renderScene);
        textLayer.setFrameCallback(batch -> {
            batch.drawText(FONT_ID, "Marching Cubes: " + FIELD_NAMES[fieldIndex], 20, 30, 22, 1f, 1f, 1f, 1f);
            batch.drawText(FONT_ID, "Resolution: " + resolution + "  Verts: " + (currentMesh != null ? currentMesh.vertexCount() : 0) + "  Tris: " + (currentMesh != null ? currentMesh.indexCount() / 3 : 0), 20, 58, 16, 0.7f, 0.8f, 0.7f, 1f);
            batch.drawText(FONT_ID, "Iso: " + String.format("%.2f", isoLevel) + (animateIso ? " (animated)" : " (static)"), 20, 80, 16, 0.7f, 0.8f, 0.7f, 1f);
            batch.drawText(FONT_ID, "1-4=field  +/-=res  I=animate  F=points  Drag=orbit  Scroll=zoom", 20, 110, 14, 0.5f, 0.5f, 0.5f, 1f);
        });

        OrbitCameraLayer inputLayer = new OrbitCameraLayer(camera);
        inputLayer.onPlus(() -> { resolution = Math.min(50, resolution + 5); rebuildMesh(); });
        inputLayer.onMinus(() -> { resolution = Math.max(5, resolution - 5); rebuildMesh(); });

        UIComposite composite = UIComposite.builder()
                .context(uiContext)
                .layer(overlay)
                .layer(textLayer)
                .layer(inputLayer)
                .build();

        // Wire extra keys via direct GLFW (numbers + I + F)
        wireInput(composite);

        Scene3DOverlayExampleFrame frame = new Scene3DOverlayExampleFrame(
                vulkanContext().arena(), vulkanContext().device(),
                vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, composite);
        frame.init(vulkanContext().graphicsQueueFamily());

        loop = GraphicsLoop.builder()
                .renderer(frame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> { frame.resize(dims[0], dims[1]); camera.setAspect((float) dims[0] / dims[1]); })
                .onFpsUpdate(m -> Logger.info(m.summary()))
                .build();

        rebuildMesh();
        loop.start();

        while (!windowSystem().shouldClose(window())) {
            windowSystem().pollEvents();
            if (animateIso) {
                float t = (System.nanoTime() - startNanos) / 1_000_000_000f;
                isoLevel = (float) Math.sin(t * 0.5) * 0.5f;
                rebuildMesh();
            }
            overlay.setCamera(camera.viewArray(), camera.projArray());
            try { Thread.sleep(1); } catch (InterruptedException e) { break; }
        }
        loop.stop();
    }

    private void rebuildMesh() {
        currentMesh = MarchingCubes.extract(FIELDS[fieldIndex],
                new Vec3(-1.5f, -1.5f, -1.5f), new Vec3(1.5f, 1.5f, 1.5f),
                resolution, resolution, resolution, isoLevel);
    }

    private void renderScene(io.github.yetyman.vulkan.layers.scene3d.OverlayDrawList drawList) {
        // Draw extracted mesh as wireframe triangles
        if (currentMesh != null) {
            List<Vec3> verts = currentMesh.vertices();
            List<Integer> indices = currentMesh.indices();
            float[] meshColor = {0.3f, 0.9f, 0.4f, 0.8f};
            for (int i = 0; i < indices.size(); i += 3) {
                Vec3 a = verts.get(indices.get(i));
                Vec3 b = verts.get(indices.get(i + 1));
                Vec3 c = verts.get(indices.get(i + 2));
                drawList.addLine(toArr(a), toArr(b), meshColor, DepthMode.DEPTH_TESTED);
                drawList.addLine(toArr(b), toArr(c), meshColor, DepthMode.DEPTH_TESTED);
                drawList.addLine(toArr(c), toArr(a), meshColor, DepthMode.DEPTH_TESTED);
            }
        }

        // Draw field sample points (small crosses at grid positions, colored by value)
        if (showFieldPoints) {
            float step = 3f / resolution;
            float crossSize = step * 0.15f;
            ScalarField3D field = FIELDS[fieldIndex];
            // Sample at lower resolution to avoid flooding with points
            int sampleRes = Math.min(resolution, 12);
            float sampleStep = 3f / sampleRes;
            for (int x = 0; x <= sampleRes; x++) {
                for (int y = 0; y <= sampleRes; y++) {
                    for (int z = 0; z <= sampleRes; z++) {
                        float px = -1.5f + x * sampleStep;
                        float py = -1.5f + y * sampleStep;
                        float pz = -1.5f + z * sampleStep;
                        float val = field.sample(px, py, pz);
                        // Color: red = below iso (inside), blue = above iso (outside)
                        float[] ptColor;
                        if (val > isoLevel) {
                            ptColor = new float[]{0.2f, 0.3f, 0.9f, 0.4f}; // outside = blue
                        } else {
                            ptColor = new float[]{0.9f, 0.2f, 0.2f, 0.5f}; // inside = red
                        }
                        // Draw as tiny cross
                        drawList.addLine(new float[]{px - crossSize, py, pz}, new float[]{px + crossSize, py, pz}, ptColor, DepthMode.DEPTH_TESTED);
                        drawList.addLine(new float[]{px, py - crossSize, pz}, new float[]{px, py + crossSize, pz}, ptColor, DepthMode.DEPTH_TESTED);
                    }
                }
            }
        }

        // Bounding box
        drawList.addWireBox(new float[]{-1.5f, -1.5f, -1.5f}, new float[]{1.5f, 1.5f, 1.5f},
                new float[]{0.3f, 0.3f, 0.3f, 0.3f}, DepthMode.DEPTH_TESTED);
    }

    private void wireInput(UIComposite composite) {
        Arena callbackArena = Arena.global();
        double[] lastMousePos = {0, 0};

        GLFWCallbacks.setCursorPosCallback(window(), (w, x, y) -> {
            float dx = (float) (x - lastMousePos[0]);
            float dy = (float) (y - lastMousePos[1]);
            lastMousePos[0] = x; lastMousePos[1] = y;
            composite.dispatchInput(PointerInputData.mouseMove((float) x, (float) y, dx, dy));
        }, callbackArena);

        GLFWCallbacks.setMouseButtonCallback(window(), (w, button, action, mods) -> {
            boolean pressed = action != 0;
            composite.dispatchInput(pressed
                    ? PointerInputData.mousePress(button, (float) lastMousePos[0], (float) lastMousePos[1])
                    : PointerInputData.mouseRelease(button, (float) lastMousePos[0], (float) lastMousePos[1]));
        }, callbackArena);

        GLFWCallbacks.setScrollCallback(window(), (w, dx, dy) ->
                composite.dispatchInput(ScrollInputData.scroll((float) lastMousePos[0], (float) lastMousePos[1], (float) dx, (float) dy)),
                callbackArena);

        GLFWCallbacks.setKeyCallback(window(), (w, key, scancode, action, mods) -> {
            if (action == 1) { // PRESS only
                switch (key) {
                    case 49 -> { fieldIndex = 0; rebuildMesh(); } // 1
                    case 50 -> { fieldIndex = 1; rebuildMesh(); } // 2
                    case 51 -> { fieldIndex = 2; rebuildMesh(); } // 3
                    case 52 -> { fieldIndex = 3; rebuildMesh(); } // 4
                    case 73 -> animateIso = !animateIso; // I
                    case 70 -> showFieldPoints = !showFieldPoints; // F
                }
            }
            InputEvent event = switch (action) {
                case 1 -> KeyInputData.press(key, scancode, mods);
                case 2 -> KeyInputData.repeat(key, scancode, mods);
                default -> KeyInputData.release(key, scancode, mods);
            };
            composite.dispatchInput(event);
        }, callbackArena);
    }

    @Override protected void onWindowResize(int w, int h) { if (loop != null) loop.signalResize(w, h); }
    @Override protected void shutdown() { if (loop != null) loop.stop(); }

    private static float[] toArr(Vec3 v) { return new float[]{v.x, v.y, v.z}; }

    private static byte[] loadFont() {
        for (String p : FONT_PATHS) { Path path = Path.of(p); if (Files.exists(path)) try { return Files.readAllBytes(path); } catch (IOException e) {} }
        throw new IllegalStateException("No font found");
    }

    public static void main(String[] args) { new MarchingCubesApp().run(); }
}
