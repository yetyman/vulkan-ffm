package io.github.yetyman.vulkan.sample.ui;

import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.foundation.ui.UIComposite;
import io.github.yetyman.vulkan.foundation.ui.UIContext;
import io.github.yetyman.vulkan.foundation.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.foundation.ui.assets.FontRegistry;
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
 * Minimal example app for GPUDrivenTextLayer: renders a line of text using a system font,
 * proving the FontRegistry (stb_truetype rasterization + atlas packing + GPU upload) and
 * TextRenderer (instanced glyph quad rendering) pipeline end-to-end.
 *
 * Loads a TrueType font directly from the OS font directory rather than bundling one, since
 * this is a minimal proof-of-pipeline example, not a distributable asset. Adjust FONT_PATH
 * below if the default path does not exist on your system.
 */
public class TextExampleApp extends VulkanApplication {

    private static final String FONT_ID = "default";
    private static final String[] CANDIDATE_FONT_PATHS = {
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf"
    };

    private GraphicsLoop loop;
    private TextExampleFrame frame;
    private UIComposite composite;
    private AssetRegistry assets;
    private VkCommandPool textUploadPool;
    private Arena uiArena;

    public TextExampleApp() {
        super("GPU-Driven Text Example", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
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

        // UIContext.applicationArena() must be usable from the render thread that drives
        // GPUDrivenTextLayer.render() (via TextRenderer), which is not the thread that runs
        // initialize() - so it must be a shared arena, not vulkanContext().arena() (confined
        // to the constructing thread).
        uiArena = Arena.ofShared();

        UIContext uiContext = UIContext.builder()
            .vulkan(vulkanContext())
            .assets(assets)
            .dimensions(800, 600)
            .applicationArena(uiArena)
            .build();

        GPUDrivenTextLayer textLayer = new GPUDrivenTextLayer(FONT_ID);
        textLayer.setFrameCallback(batch -> {
            batch.drawText(FONT_ID, "Hello, VulkanFFM!", 40, 100, 48, 1.0f, 1.0f, 1.0f, 1.0f);
            batch.drawText(FONT_ID, "GPU-driven text via FontRegistry + stb_truetype", 40, 160, 24,
                0.7f, 0.85f, 1.0f, 1.0f);
        });

        composite = UIComposite.builder()
            .context(uiContext)
            .layer(textLayer)
            .build();

        frame = new TextExampleFrame(
            vulkanContext().arena(), vulkanContext().device(),
            vulkanContext().graphicsVkQueue(), surface(), 800, 600, composite);
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
            + " - edit TextExampleApp.CANDIDATE_FONT_PATHS to point at a .ttf file on your system");
    }

    public static void main(String[] args) {
        try (TextExampleApp app = new TextExampleApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
