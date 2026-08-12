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
 * Sample application demonstrating the LOD selection system in real time.
 *
 * <p>A sphere mesh is built at 4 detail levels using QEM simplification. The LOD selector
 * picks the appropriate level based on a simulated camera distance that oscillates
 * automatically. The top row shows all 4 LOD levels for reference (with the active one
 * highlighted). The bottom shows the selected mesh large. A text overlay displays
 * real-time selection info: distance, active LOD, triangle count, error, and transition state.
 */
public class LodDemoApp extends VulkanApplication {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 800;
    private static final String FONT_ID = "default";
    private static final String[] CANDIDATE_FONT_PATHS = {
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf"
    };

    private GraphicsLoop loop;
    private QemSimplifierDemoFrame frame; // reuse the same frame class (handles depth + dynamic rendering)
    private UIComposite composite;
    private Arena uiArena;
    private FontRegistry fontRegistry;
    private VkCommandPool textUploadPool;

    public LodDemoApp() {
        super("LOD Selection Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
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

        // The LOD demo layer
        LodDemoLayer lodLayer = new LodDemoLayer();

        // Text overlay showing LOD status
        GPUDrivenTextLayer textLayer = new GPUDrivenTextLayer(FONT_ID);
        textLayer.setFrameCallback(batch -> {
            int w = uiContext.width();
            int h = uiContext.height();

            // Title
            batch.drawText(FONT_ID, "LOD Selection Demo - Screen-Error Selector", 20, 24, 18,
                1.0f, 1.0f, 1.0f, 1.0f);

            // Status line from the LOD layer
            String status = lodLayer.statusLine();
            if (status != null && !status.isEmpty()) {
                batch.drawText(FONT_ID, status, 20, h - 20, 14,
                    0.8f, 1.0f, 0.7f, 1.0f);
            }

            // Column labels for the reference row
            int levels = lodLayer.levelCount();
            long[] tris = lodLayer.triCounts();
            float[] errors = lodLayer.errorBounds();
            if (tris != null && levels > 0) {
                float spacing = (float) w / levels;
                for (int i = 0; i < levels; i++) {
                    float x = spacing * (i + 0.5f) - 30;
                    String label = i == 0 ? "Full" : String.format("%.0f%%", (1.0f - (float) i / levels) * 100);
                    float r = (i == lodLayer.currentSelectedNode()) ? 1.0f : 0.6f;
                    float g = (i == lodLayer.currentSelectedNode()) ? 1.0f : 0.7f;
                    batch.drawText(FONT_ID, label, x, 50, 13, r, g, 0.6f, 1.0f);
                    batch.drawText(FONT_ID, tris[i] + " tri", x, 66, 11, 0.6f, 0.7f, 0.8f, 1.0f);
                }
            }

            // Transition indicator
            var transition = lodLayer.activeTransition();
            if (transition != null) {
                String tInfo = String.format("TRANSITIONING: %d -> %d (%.0f%%)",
                        transition.fromNodeIndex(), transition.toNodeIndex(),
                        transition.factor() * 100);
                batch.drawText(FONT_ID, tInfo, 20, h - 40, 12, 1.0f, 0.8f, 0.3f, 1.0f);
            }
        });

        composite = UIComposite.builder()
            .context(uiContext)
            .layer(lodLayer)
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
        new LodDemoApp().run();
    }
}
