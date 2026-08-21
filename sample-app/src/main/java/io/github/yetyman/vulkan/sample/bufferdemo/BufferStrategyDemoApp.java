package io.github.yetyman.vulkan.sample.bufferdemo;

import io.github.yetyman.glfw.GLFWCallbacks;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.assets.FontRegistry;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.layers.gizmo.Gizmo;
import io.github.yetyman.vulkan.layers.gizmo.GizmoOverlayLayer;
import io.github.yetyman.vulkan.layers.text.GPUDrivenTextLayer;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.ui.UIComposite;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.assets.AssetRegistry;
import io.github.yetyman.vulkan.ui.input.types.PointerInputData;
import io.github.yetyman.vulkan.util.Logger;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Buffer Strategy Demo Application.
 *
 * <p>Demonstrates multiple buffer memory strategies side-by-side, all performing the same
 * operation: a grid of quads deformed by mouse position. Each grid uses a different
 * {@link io.github.yetyman.vulkan.buffers.MemoryStrategy}:
 * <ol>
 *   <li>DEVICE_LOCAL_MIRRORED - GPU-resident, CPU reads from mirror</li>
 *   <li>MAPPED (coherent) - HOST_VISIBLE|HOST_COHERENT, inherent observability</li>
 *   <li>MAPPED_CACHED - HOST_VISIBLE|HOST_CACHED, inherent observability</li>
 *   <li>REBAR - Direct CPU-to-VRAM (falls back to MAPPED if unavailable)</li>
 * </ol>
 *
 * <p>All writes and reads occur through the ManagedBuffer API. The gizmo overlay reads
 * back vertex positions through the buffer's observability path and renders wireframe
 * quad outlines with a cycling rainbow color.
 *
 * <p>Architecture:
 * <ul>
 *   <li>{@link MouseInputLayer} - captures mouse position</li>
 *   <li>{@link QuadGridMeshLayer} - renders 4 deformed quad grids via vertex pulling</li>
 *   <li>{@link GizmoOverlayLayer} - renders gizmo outlines from buffer readback</li>
 *   <li>{@link GPUDrivenTextLayer} - renders strategy labels above each grid column</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>
 * mvn exec:java -pl sample-app -Dexec.mainClass="io.github.yetyman.vulkan.sample.bufferdemo.BufferStrategyDemoApp"
 * </pre>
 */
public class BufferStrategyDemoApp extends VulkanApplication {

    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    private static final String FONT_ID = "default";
    private static final String[] CANDIDATE_FONT_PATHS = {
            "C:/Windows/Fonts/segoeui.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf"
    };

    private GraphicsLoop loop;
    private BufferStrategyDemoFrame frame;
    private UIComposite composite;
    private AssetRegistry assets;
    private VkCommandPool textUploadPool;
    private Arena uiArena;

    // Cached gizmo list (reused across frames, not rebuilt)
    private List<Gizmo> cachedGizmos;
    private QuadGridMeshLayer quadGridLayer;

    // Time-based exponential moving average for stats display
    // tau = 0.3 (~0.3s to reach 63% of target, settles in ~1s)
    private static final double EMA_TAU = 0.1;
    private final double[] emaWrittenBytes = new double[QuadGridMeshLayer.GRID_COUNT];
    private final double[] emaTransferBytes = new double[QuadGridMeshLayer.GRID_COUNT];
    private final double[] emaTransferRegions = new double[QuadGridMeshLayer.GRID_COUNT];
    private long lastUpdateNanos = System.nanoTime();

    public BufferStrategyDemoApp() {
        super("Buffer Strategy Demo", WIDTH, HEIGHT, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        // Font setup
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

        // Create layers
        MouseInputLayer mouseLayer = new MouseInputLayer();
        quadGridLayer = new QuadGridMeshLayer(mouseLayer);
        GizmoOverlayLayer gizmoLayer = new GizmoOverlayLayer();
        GPUDrivenTextLayer textLayer = new GPUDrivenTextLayer(FONT_ID);

        // Configure gizmo layer to supply gizmos (built once, reused every frame)
        gizmoLayer.setGizmoSupplier(() -> {
            if (cachedGizmos == null) {
                cachedGizmos = buildGizmos(quadGridLayer);
            }
            return cachedGizmos;
        });

        // Configure text layer to render strategy labels and dirty/transfer stats below each grid
        textLayer.setFrameCallback(batch -> {
            // Update EMA
            long now = System.nanoTime();
            double dt = (now - lastUpdateNanos) / 1_000_000_000.0;
            lastUpdateNanos = now;
            double alpha = 1.0 - Math.exp(-dt / EMA_TAU);

            for (int i = 0; i < QuadGridMeshLayer.GRID_COUNT; i++) {
                float[] bounds = quadGridLayer.gridBounds(i);
                String label = quadGridLayer.strategyLabel(i);

                // Title label above grid
                float labelX = bounds[0] + bounds[2] * 0.02f;
                float labelY = bounds[1] - 5.0f;
                batch.drawText(FONT_ID, label, labelX, labelY, 14, 0.9f, 0.9f, 0.9f, 1.0f);

                // Update EMAs
                long writtenBytes = quadGridLayer.lastWrittenBytes(i);
                long transferBytes = quadGridLayer.lastTransferBytes(i);
                int transferRegions = quadGridLayer.lastTransferRegions(i);

                emaWrittenBytes[i] += alpha * (writtenBytes - emaWrittenBytes[i]);
                emaTransferBytes[i] += alpha * (transferBytes - emaTransferBytes[i]);
                emaTransferRegions[i] += alpha * (transferRegions - emaTransferRegions[i]);

                // Written line (CPU writes) - shown in green
                float statsY = bounds[1] + bounds[3] + 14.0f;
                String writtenStats;
                if (emaWrittenBytes[i] < 1.0) {
                    writtenStats = "written: 0 B";
                } else {
                    writtenStats = String.format("written: %d B",
                            (long) emaWrittenBytes[i]);
                }
                batch.drawText(FONT_ID, writtenStats, labelX, statsY, 12, 0.6f, 0.9f, 0.6f, 1.0f);

                // Transfer line (GPU upload via vkCmdCopy) - shown in yellow/orange
                float transferY = statsY + 14.0f;
                String transferStats;
                if (emaTransferBytes[i] < 1.0) {
                    transferStats = "flush: 0 B (direct)";
                } else {
                    transferStats = String.format("flush: %d B / %d rgn",
                            (long) emaTransferBytes[i], (int) Math.round(emaTransferRegions[i]));
                }
                batch.drawText(FONT_ID, transferStats, labelX, transferY, 12, 0.9f, 0.7f, 0.3f, 1.0f);
            }
        });

        // Build composite
        composite = UIComposite.builder()
                .context(uiContext)
                .layer(mouseLayer)
                .layer(quadGridLayer)
                .layer(gizmoLayer)
                .layer(textLayer)
                .build();

        // Create frame
        frame = new BufferStrategyDemoFrame(
                vulkanContext().arena(), vulkanContext().device(),
                vulkanContext().graphicsVkQueue(), surface(), WIDTH, HEIGHT, composite);
        frame.init(vulkanContext().graphicsQueueFamily());

        // Create render loop
        loop = GraphicsLoop.builder()
                .renderer(frame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> frame.resize(dims[0], dims[1]))
                .onFpsUpdate(m -> Logger.info(m.summary()))
                .build();

        loop.start();

        // Wire GLFW cursor position callback to dispatch mouse events into the composite
        wireInput();

        // Main thread: poll events
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

    /**
     * Wires GLFW mouse callbacks to dispatch input events into the composite.
     */
    private void wireInput() {
        Arena callbackArena = Arena.global();
        double[] lastPos = {0, 0};
        GLFWCallbacks.setCursorPosCallback(window(), (w, x, y) -> {
            float dx = (float) (x - lastPos[0]);
            float dy = (float) (y - lastPos[1]);
            lastPos[0] = x;
            lastPos[1] = y;
            composite.dispatchInput(PointerInputData.mouseMove((float) x, (float) y, dx, dy));
        }, callbackArena);
    }

    /**
     * Builds gizmos for each grid from its buffer readback.
     */
    private List<Gizmo> buildGizmos(QuadGridMeshLayer quadGridLayer) {
        List<Gizmo> gizmos = new ArrayList<>();
        for (int i = 0; i < QuadGridMeshLayer.GRID_COUNT; i++) {
            float[] bounds = quadGridLayer.gridBounds(i);
            gizmos.add(new MeshOutlineGizmo(
                    quadGridLayer.strategyLabel(i),
                    quadGridLayer.vertexBuffer(i),
                    vulkanContext().graphicsVkQueue(),
                    bounds,
                    QuadGridMeshLayer.QUAD_COLS,
                    QuadGridMeshLayer.QUAD_ROWS));
        }
        return gizmos;
    }

    @Override
    protected void onWindowResize(int w, int h) {
        if (loop != null) loop.signalResize(w, h);
    }

    @Override
    protected void shutdown() {
        if (frame != null) frame.close();
        if (textUploadPool != null) textUploadPool.close();
        if (assets != null) assets.close();
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
                        + " - edit BufferStrategyDemoApp.CANDIDATE_FONT_PATHS to point at a .ttf file on your system");
    }

    public static void main(String[] args) {
        try (BufferStrategyDemoApp app = new BufferStrategyDemoApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
