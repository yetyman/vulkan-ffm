package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.ui.UIComposite;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.assets.FontRegistry;
import io.github.yetyman.vulkan.layers.text.GPUDrivenTextLayer;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sample application demonstrating QEM mesh simplification visually.
 *
 * <p>Renders two rows of meshes (sphere on top, torus on bottom), each row showing the original
 * mesh alongside progressively decimated versions at 50%, 25%, 10%, and 5% of the original
 * triangle count. All meshes rotate slowly to show the 3D geometry.</p>
 *
 * <p>A text overlay labels each column with the decimation ratio and triangle count, allowing
 * easy visual confirmation that the simplification preserves overall shape while reducing
 * geometric detail.</p>
 *
 * <p>Uses the existing {@code mesh_sample} shaders with normal-based lighting for clear
 * visualization of surface quality at each LOD level.</p>
 */
public class QemSimplifierDemoApp extends VulkanApplication {

    private static final int WIDTH = 1400;
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

    public QemSimplifierDemoApp() {
        super("QEM Simplifier Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
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

        // The simplifier demo layer renders all LOD levels side by side
        QemSimplifierDemoLayer simplifierLayer = new QemSimplifierDemoLayer();

        // Text layer for labels
        GPUDrivenTextLayer textLayer = new GPUDrivenTextLayer(FONT_ID);
        textLayer.setFrameCallback(batch -> {
            int w = uiContext.width();
            int h = uiContext.height();

            // Title
            batch.drawText(FONT_ID, "QEM Mesh Simplification - Visual Comparison", 20, 24, 20,
                1.0f, 1.0f, 1.0f, 1.0f);

            // Column labels
            float[] ratios = simplifierLayer.ratios();
            String[] triCounts = simplifierLayer.triCountLabels();
            int columns = ratios.length + 1; // original + decimated
            float spacing = (float) w / columns;

            // Original label
            float x0 = spacing * 0.5f - 40;
            batch.drawText(FONT_ID, "Original", x0, 50, 14, 0.9f, 1.0f, 0.7f, 1.0f);
            if (triCounts.length > 0) {
                batch.drawText(FONT_ID, triCounts[0], x0, 68, 12, 0.7f, 0.8f, 0.6f, 1.0f);
            }

            // Decimated labels
            for (int i = 0; i < ratios.length; i++) {
                float x = spacing * (i + 1.5f) - 40;
                String label = String.format("%.0f%%", ratios[i] * 100);
                batch.drawText(FONT_ID, label, x, 50, 14, 0.8f, 0.9f, 1.0f, 1.0f);
                if (triCounts.length > i + 1) {
                    batch.drawText(FONT_ID, triCounts[i + 1], x, 68, 12, 0.7f, 0.8f, 0.6f, 1.0f);
                }
            }

            // Row labels
            float midY = h * 0.35f;
            batch.drawText(FONT_ID, "Sphere", 5, midY, 14, 0.6f, 0.9f, 0.6f, 1.0f);
            batch.drawText(FONT_ID, "Torus", 5, h * 0.7f, 14, 0.6f, 0.9f, 0.6f, 1.0f);

            // Error info
            String errorInfo = simplifierLayer.errorInfo();
            if (errorInfo != null && !errorInfo.isEmpty()) {
                batch.drawText(FONT_ID, errorInfo, 20, h - 20, 12,
                    0.6f, 0.7f, 0.8f, 1.0f);
            }
        });

        composite = UIComposite.builder()
            .context(uiContext)
            .layer(simplifierLayer)
            .layer(textLayer)
            .build();

        frame = new QemSimplifierDemoFrame(
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
            windowSystem().waitEventsTimeout(0.004);
        }
        loop.stop();
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
        new QemSimplifierDemoApp().run();
    }
}
