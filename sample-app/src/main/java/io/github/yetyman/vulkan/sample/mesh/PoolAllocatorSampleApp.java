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
 * Runs the {@link PoolAllocatorSampleLayer} falsification test from
 * {@code plans/mesh/09-roadmap.md} Phase 5: renders a large number of small meshes through one
 * {@code PoolAllocator}, bound once per frame, and reports CPU command-recording time on screen so
 * the "must be flat as mesh count grows" claim is directly observable.
 *
 * <p>To check the claim, change {@code PoolAllocatorSampleLayer.MESH_COUNT} (e.g. 100, 2000,
 * 20000) and compare the reported recording time between runs. Per Invariant 1 in
 * {@code plans/mesh/07-performance-invariants.md}, it should not grow with mesh count; only the
 * GPU-side draw cost should.
 */
public class PoolAllocatorSampleApp extends VulkanApplication {

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
    private MutationDemoFrame frame;
    private UIComposite composite;
    private Arena uiArena;
    private FontRegistry fontRegistry;
    private VkCommandPool textUploadPool;

    public PoolAllocatorSampleApp() {
        super("Pool Allocator Sample - Phase 5 Falsification Test", WIDTH, HEIGHT,
                new GLFWWindowSystem(), new GLFWInputSystem());
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

        PoolAllocatorSampleLayer poolLayer = new PoolAllocatorSampleLayer();

        GPUDrivenTextLayer textLayer = new GPUDrivenTextLayer(FONT_ID);
        textLayer.setFrameCallback(batch -> {
            batch.drawText(FONT_ID, "Pool Allocator Sample - single bind, many meshes", 20, 20, 20,
                1.0f, 1.0f, 1.0f, 1.0f);
            batch.drawText(FONT_ID, poolLayer.statusText(), 20, 50, 14,
                0.7f, 0.9f, 0.7f, 1.0f);
        });

        composite = UIComposite.builder()
            .context(uiContext)
            .layer(poolLayer)
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
        new PoolAllocatorSampleApp().run();
    }
}
