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
 * Sample application demonstrating all six mesh mutation categories:
 *
 * <ol>
 *   <li><b>Attribute Paint</b> - in-place attribute re-upload via MutableGeometrySource dirty tracking</li>
 *   <li><b>Growing Stroke</b> - count grows within capacity via setLiveCounts</li>
 *   <li><b>Unbounded Growth</b> - reallocation when capacity is exceeded</li>
 *   <li><b>Topology Swap</b> - replace mesh topology with RetireQueue deferred free</li>
 *   <li><b>Compute Deform</b> - GPU writes geometry via compute shader, mesh wraps external allocation</li>
 *   <li><b>Ring Buffered</b> - N-buffered write-while-reading with RingAllocator</li>
 * </ol>
 *
 * <p>All six demonstrations are rendered side-by-side in a single {@link MutationDemoLayer},
 * labeled by a {@link GPUDrivenTextLayer} overlay. The app uses dynamic rendering (no render pass)
 * and an uncapped loop.
 */
public class MeshMutationDemoApp extends VulkanApplication {

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 700;
    private static final String FONT_ID = "default";
    private static final String[] CANDIDATE_FONT_PATHS = {
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf"
    };

    private GraphicsLoop loop;
    private MutationDemoFrame frame;
    private UIComposite composite;
    private Arena uiArena;
    private FontRegistry fontRegistry;
    private VkCommandPool textUploadPool;

    public MeshMutationDemoApp() {
        super("Mesh Mutation Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
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

        // The mutation demo layer renders all 6 mutation types side by side
        MutationDemoLayer mutationLayer = new MutationDemoLayer();

        // Text layer for labels
        GPUDrivenTextLayer textLayer = new GPUDrivenTextLayer(FONT_ID);
        textLayer.setFrameCallback(batch -> {
            // Use dynamic dimensions from the UI context
            int w = uiContext.width();
            int h = uiContext.height();
            float y = 20;
            batch.drawText(FONT_ID, "Mesh Mutation Demo - 6 Categories", 20, y, 20,
                1.0f, 1.0f, 1.0f, 1.0f);

            // Labels for each demonstration, positioned above the meshes
            float spacing = w / 6.0f;
            String[] labels = {
                "1: Attr Paint",
                "2: Grow Count",
                "3: Realloc",
                "4: Topo Swap",
                "5: Compute",
                "6: Ring Buf"
            };
            for (int i = 0; i < labels.length; i++) {
                float x = spacing * i + spacing * 0.1f;
                batch.drawText(FONT_ID, labels[i], x, h - 30, 14,
                    0.8f, 0.9f, 1.0f, 1.0f);
            }

            // Status line
            String status = mutationLayer.statusText();
            if (status != null && !status.isEmpty()) {
                batch.drawText(FONT_ID, status, 20, h - 60, 12,
                    0.6f, 0.8f, 0.6f, 1.0f);
            }
        });

        composite = UIComposite.builder()
            .context(uiContext)
            .layer(mutationLayer)
            .layer(textLayer)
            .build();

        frame = new MutationDemoFrame(
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
        // Stop the loop first - this ensures no more frames are being rendered
        if (loop != null) loop.stop();

        // Wait for GPU to finish all pending work
        io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(vulkanContext().device().handle()).check();

        // Close frame (destroys swapchain, image views, semaphores) before surface/device
        if (frame != null) {
            try { frame.close(); } catch (Exception e) { Logger.info("frame.close() failed: " + e); }
        }

        // Close layers (text renderer, mutation layer)
        if (composite != null) {
            try { composite.close(); } catch (Exception e) { Logger.info("composite.close() failed: " + e); }
        }

        // Close font registry (atlas images/views/memory)
        if (fontRegistry != null) {
            try { fontRegistry.close(); } catch (Exception e) { Logger.info("fontRegistry.close() failed: " + e); }
        }

        // Close command pool used for font uploads
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
        new MeshMutationDemoApp().run();
    }
}
