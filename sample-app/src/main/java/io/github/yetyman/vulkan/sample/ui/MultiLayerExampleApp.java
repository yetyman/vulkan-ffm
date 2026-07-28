package io.github.yetyman.vulkan.sample.ui;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.foundation.ui.UIComposite;
import io.github.yetyman.vulkan.foundation.ui.UIContext;
import io.github.yetyman.vulkan.foundation.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.foundation.ui.assets.FontRegistry;
import io.github.yetyman.vulkan.foundation.ui.input.InputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.types.KeyInputData;
import io.github.yetyman.vulkan.foundation.ui.input.types.MouseInputData;
import io.github.yetyman.vulkan.foundation.ui.input.types.ScrollInputData;
import io.github.yetyman.vulkan.foundation.ui.layers.scene3d.DepthMode;
import io.github.yetyman.vulkan.foundation.ui.layers.scene3d.Scene3DOverlayLayer;
import io.github.yetyman.vulkan.foundation.ui.layers.text.GPUDrivenTextLayer;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Combined example app: layers Scene3DOverlayLayer (order 100, background + input-annotating),
 * GPUDrivenTextLayer (order 900, HUD text), and HoverHighlightLayer (order 950, topmost,
 * input-only) into one UIComposite, driven by one window and one render loop.
 *
 * This exercises UIComposite's layered rendering (multiple layers with independent pipeline
 * state drawing into the same command buffer) and UIInputDispatcher's capture/bubble
 * propagation with real mouse input from GLFW callbacks, driven by TWO input-accepting layers
 * so the cross-layer data flow described in the original plan doc is actually observable:
 *
 *   - CAPTURE (highest order to lowest): HoverHighlightLayer (950) runs first, then
 *     Scene3DOverlayLayer (100) runs second - it unprojects the mouse position into a world-space
 *     ray, tests it against the pick sphere, and on hit annotates propagation.put("hoveredWorldPos", ...).
 *   - BUBBLE (lowest order to highest): Scene3DOverlayLayer (100) runs first (no-op on bubble),
 *     then HoverHighlightLayer (950) runs second and reads "hoveredWorldPos" from the very same
 *     PropagationState object - demonstrating that context written by one layer during CAPTURE
 *     is visible to a different layer during BUBBLE.
 *
 * Move the mouse over the cyan wire sphere to see it turn orange and a small white marker appear
 * at the exact ray-hit point, plus HUD text reporting the world-space hit coordinates. Click
 * anywhere to toggle the highlight (HoverHighlightLayer consumes the click and stops
 * propagation during bubble) and watch the HUD text color change.
 */
public class MultiLayerExampleApp extends VulkanApplication {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final String FONT_ID = "default";
    private static final String[] CANDIDATE_FONT_PATHS = {
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf"
    };

    private GraphicsLoop loop;
    private MultiLayerExampleFrame frame;
    private UIComposite composite;
    private AssetRegistry assets;
    private VkCommandPool textUploadPool;
    private Arena uiArena;

    private final long startNanos = System.nanoTime();

    public MultiLayerExampleApp() {
        super("Multi-Layer UI Example", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        byte[] fontBytes = loadSystemFontBytes();
        textUploadPool = vulkanContext().createTransientCommandPool();

        assets = new AssetRegistry();
        FontRegistry fonts = new FontRegistry(
            vulkanContext().device(), vulkanContext().graphicsVkQueue(), textUploadPool);
        fonts.loadFont(FONT_ID, fontBytes, 1024, 1024);
        assets.register(FontRegistry.class, fonts);

        uiArena = Arena.ofShared();

        UIContext uiContext = UIContext.builder()
            .vulkan(vulkanContext())
            .assets(assets)
            .dimensions(WIDTH, HEIGHT)
            .applicationArena(uiArena)
            .build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        float[] sphereCenter = {2, 1, 0};
        float sphereRadius = 1f;
        overlay.setPickSphere(sphereCenter, sphereRadius);

        GPUDrivenTextLayer textLayer = new GPUDrivenTextLayer(FONT_ID);
        HoverHighlightLayer hoverLayer = new HoverHighlightLayer();

        overlay.setFrameCallback(drawList -> {
            drawList.addGrid(new float[]{0, 0, 0}, 10f, 10, new float[]{0.3f, 0.3f, 0.35f, 1f});
            drawList.addAxis(identity(), 1.5f);
            boolean hovered = hoverLayer.lastHoveredWorldPos() != null;
            float[] sphereColor = hovered
                ? new float[]{1f, 0.8f, 0.2f, 1f}
                : new float[]{0.2f, 0.8f, 1f, 1f};
            drawList.addWireSphere(sphereCenter, sphereRadius, sphereColor, 24, DepthMode.ALWAYS_ON_TOP);
            if (hovered) {
                float[] hit = hoverLayer.lastHoveredWorldPos();
                drawList.addWireSphere(hit, 0.05f, new float[]{1f, 1f, 1f, 1f}, 12, DepthMode.ALWAYS_ON_TOP);
            }
        });

        textLayer.setFrameCallback(batch -> {
            float r = hoverLayer.isHighlighted() ? 1.0f : 0.7f;
            float g = hoverLayer.isHighlighted() ? 0.9f : 0.85f;
            float b = hoverLayer.isHighlighted() ? 0.2f : 1.0f;
            batch.drawText(FONT_ID, "Multi-layer UI example - click to toggle highlight", 20, 30, 22, r, g, b, 1.0f);
            batch.drawText(FONT_ID, "Cursor: (" + (int) hoverLayer.mouseX() + ", " + (int) hoverLayer.mouseY() + ")",
                20, 60, 18, 0.7f, 0.7f, 0.75f, 1.0f);

            float[] hit = hoverLayer.lastHoveredWorldPos();
            String hoverText = hit != null
                ? String.format("Hovering sphere at world (%.2f, %.2f, %.2f)", hit[0], hit[1], hit[2])
                : "Hovering sphere: no";
            batch.drawText(FONT_ID, hoverText, 20, 90, 18, 0.6f, 1.0f, 0.6f, 1.0f);
        });

        composite = UIComposite.builder()
            .context(uiContext)
            .layer(overlay)
            .layer(textLayer)
            .layer(hoverLayer)
            .build();

        wireInput(composite);

        frame = new MultiLayerExampleFrame(
            vulkanContext().arena(), vulkanContext().device(),
            vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, composite);
        frame.init(vulkanContext().graphicsQueueFamily());

        loop = GraphicsLoop.builder()
            .renderer(frame)
            .driver(LoopDriver.uncapped())
            .shouldClose(() -> windowSystem().shouldClose(window()))
            .onResize(dims -> frame.resize(dims[0], dims[1]))
            .onFpsUpdate(m -> Logger.info(m.summary()))
            .build();

        loop.start();

        while (!windowSystem().shouldClose(window())) {
            windowSystem().waitEventsTimeout(0.004); // 250 Hz floor, instant wake on input

            double t = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            float radius = 6.0f;
            float eyeX = (float) (Math.cos(t * 0.3) * radius);
            float eyeZ = (float) (Math.sin(t * 0.3) * radius);
            float[] view = lookAt(
                new float[]{eyeX, 3.0f, eyeZ},
                new float[]{1.0f, 0.5f, 0},
                new float[]{0, 1, 0});
            float[] proj = perspective((float) Math.toRadians(60), (float) WIDTH / HEIGHT, 0.1f, 100f);
            overlay.setCamera(view, proj);
        }
        loop.stop();
    }

    /**
     * Wires raw GLFW input callbacks directly into UIInputEvent construction + dispatch. This is
     * the first place UIComposite.dispatchInput() is actually driven by real input rather than
     * being unit-reasoned-about - each GLFW callback synthesizes exactly one UIInputEvent and
     * dispatches it synchronously through capture then bubble across all three layers.
     */
    private void wireInput(UIComposite composite) {
        Arena callbackArena = Arena.global();
        double[] lastMousePos = {0, 0};

        GLFWCallbacks.setCursorPosCallback(window(), (w, x, y) -> {
            float dx = (float) (x - lastMousePos[0]);
            float dy = (float) (y - lastMousePos[1]);
            lastMousePos[0] = x;
            lastMousePos[1] = y;
            composite.dispatchInput(MouseInputData.move((float) x, (float) y, dx, dy));
        }, callbackArena);

        GLFWCallbacks.setMouseButtonCallback(window(), (w, button, action, mods) -> {
            boolean pressed = action != 0; // GLFW_RELEASE = 0, GLFW_PRESS = 1, GLFW_REPEAT = 2
            composite.dispatchInput(pressed
                ? MouseInputData.buttonPress(button, (float) lastMousePos[0], (float) lastMousePos[1])
                : MouseInputData.buttonRelease(button, (float) lastMousePos[0], (float) lastMousePos[1]));
        }, callbackArena);

        GLFWCallbacks.setScrollCallback(window(), (w, dx, dy) ->
            composite.dispatchInput(ScrollInputData.scroll(
                (float) lastMousePos[0], (float) lastMousePos[1], (float) dx, (float) dy)),
            callbackArena);

        GLFWCallbacks.setKeyCallback(window(), (w, key, scancode, action, mods) -> {
            InputEvent event = switch (action) {
                case 1 -> KeyInputData.press(key, scancode, mods);   // GLFW_PRESS
                case 2 -> KeyInputData.repeat(key, scancode, mods);  // GLFW_REPEAT
                default -> KeyInputData.release(key, scancode, mods); // GLFW_RELEASE
            };
            composite.dispatchInput(event);
        }, callbackArena);
    }

    @Override
    protected void onWindowResize(int w, int h) {
        if (loop != null) loop.signalResize(w, h);
    }

    @Override
    protected void shutdown() {
        if (frame != null) frame.close();
        if (assets != null) assets.close();
        if (textUploadPool != null) textUploadPool.close();
        if (uiArena != null) uiArena.close();
    }

    private static byte[] loadSystemFontBytes() {
        for (String candidate : CANDIDATE_FONT_PATHS) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException e) {
                    Logger.error("Failed to read font at " + candidate + ": " + e.getMessage());
                }
            }
        }
        throw new IllegalStateException(
            "No system font found among candidate paths: " + String.join(", ", CANDIDATE_FONT_PATHS)
            + " - edit MultiLayerExampleApp.CANDIDATE_FONT_PATHS to point at a .ttf file on your system");
    }

    private static float[] identity() {
        return new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
    }

    /** Column-major right-handed look-at view matrix. */
    private static float[] lookAt(float[] eye, float[] center, float[] up) {
        float[] f = normalize(sub(center, eye));
        float[] s = normalize(cross(f, up));
        float[] u = cross(s, f);

        return new float[]{
            s[0], u[0], -f[0], 0,
            s[1], u[1], -f[1], 0,
            s[2], u[2], -f[2], 0,
            -dot(s, eye), -dot(u, eye), dot(f, eye), 1
        };
    }

    /** Column-major right-handed perspective projection matrix with the Vulkan clip-space Y-flip applied. */
    private static float[] perspective(float fovyRadians, float aspect, float near, float far) {
        float f = 1.0f / (float) Math.tan(fovyRadians / 2.0);
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = -f;
        m[10] = far / (near - far);
        m[11] = -1.0f;
        m[14] = (far * near) / (near - far);
        return m;
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-8f) return new float[]{0, 0, 0};
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }

    public static void main(String[] args) {
        try (MultiLayerExampleApp app = new MultiLayerExampleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
