package io.github.yetyman.vulkan.sample.ui;

import io.github.yetyman.vulkan.ui.UIComposite;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.layers.scene3d.DepthMode;
import io.github.yetyman.vulkan.layers.scene3d.Scene3DOverlayLayer;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;

/**
 * Minimal example app for Scene3DOverlayLayer: a rotating camera orbits a scene containing a
 * debug grid, a wire box, and an axis triad gizmo, proving the pure-Vulkan line/triangle
 * overlay rendering pipeline end-to-end. No new native bindings involved.
 */
public class Scene3DOverlayExampleApp extends VulkanApplication {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private GraphicsLoop loop;
    private Scene3DOverlayExampleFrame frame;
    private UIComposite composite;
    private AssetRegistry assets;
    private Arena uiArena;

    private final long startNanos = System.nanoTime();

    public Scene3DOverlayExampleApp() {
        super("Scene3D Overlay Example", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        assets = new AssetRegistry();

        // UIContext.applicationArena() must be usable from the render thread, not confined to
        // this (main/init) thread - see TextExampleApp for the same requirement/explanation.
        uiArena = Arena.ofShared();

        UIContext uiContext = UIContext.builder()
            .vulkan(vulkanContext())
            .assets(assets)
            .dimensions(WIDTH, HEIGHT)
            .applicationArena(uiArena)
            .build();

        Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
        overlay.setFrameCallback(drawList -> {
            drawList.addGrid(new float[]{0, 0, 0}, 10f, 10, new float[]{0.35f, 0.35f, 0.4f, 1f});
            drawList.addWireBox(new float[]{-1, 0, -1}, new float[]{1, 2, 1}, new float[]{1f, 0.6f, 0.1f, 1f}, DepthMode.ALWAYS_ON_TOP);
            drawList.addAxis(identity(), 1.5f);
            drawList.addWireSphere(new float[]{3, 1, 0}, 1f, new float[]{0.2f, 0.8f, 1f, 1f}, 24, DepthMode.ALWAYS_ON_TOP);
        });

        composite = UIComposite.builder()
            .context(uiContext)
            .layer(overlay)
            .build();

        frame = new Scene3DOverlayExampleFrame(
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
            windowSystem().pollEvents();

            double t = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            float radius = 12.0f;
            float eyeX = (float) (Math.cos(t * 0.3) * radius);
            float eyeZ = (float) (Math.sin(t * 0.3) * radius);
            float[] view = lookAt(
                new float[]{eyeX, 5.0f, eyeZ},
                new float[]{1.5f, 0.5f, 0},
                new float[]{0, 1, 0});
            float[] proj = perspective((float) Math.toRadians(60), (float) WIDTH / HEIGHT, 0.1f, 100f);
            overlay.setCamera(view, proj);

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        loop.stop();
    }

    @Override
    protected void onWindowResize(int w, int h) {
        if (loop != null) loop.signalResize(w, h);
    }

    @Override
    protected void shutdown() {
        if (frame != null) frame.close();
        if (assets != null) assets.close();
        if (uiArena != null) uiArena.close();
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

    /** Column-major right-handed perspective projection matrix with the Vulkan clip-space
     *  Y-flip applied (Vulkan NDC has +Y pointing down, unlike OpenGL) - without this, anything
     *  "up" in world/view space renders at the bottom of the screen and vice versa. */
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
        try (Scene3DOverlayExampleApp app = new Scene3DOverlayExampleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
