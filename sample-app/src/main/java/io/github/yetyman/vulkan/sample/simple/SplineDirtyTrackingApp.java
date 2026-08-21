package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

/**
 * Animated spline renderer demonstrating deferred dirty tracking with multi-region GPU upload.
 *
 * <p>A large buffer of control points (~8192, 192KB) is stored as {@code DEVICE_LOCAL_MIRRORED}
 * in deferred mode. Each frame, several scattered groups of control points are animated on the CPU
 * (sine waves propagating at different speeds through different segments). Only the changed points
 * are tracked by the {@link io.github.yetyman.vulkan.buffers.DirtyStrategy} and flushed to the
 * GPU in a single multi-region {@code vkCmdCopyBuffer} call.
 *
 * <p>This is the pattern dirty tracking is designed for: a large buffer where only scattered
 * partial regions change per frame, and the system coalesces those changes into minimal GPU
 * transfer work without the application manually tracking which ranges changed.
 *
 * <p>Run with:
 * <pre>
 * mvn exec:java -pl sample-app -Dexec.mainClass="io.github.yetyman.vulkan.sample.simple.SplineDirtyTrackingApp"
 * </pre>
 */
public class SplineDirtyTrackingApp extends VulkanApplication {

    private GraphicsLoop loop;
    private SplineDirtyTrackingFrame frame;

    public SplineDirtyTrackingApp() {
        super("Spline Dirty Tracking", 1200, 800, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        frame = new SplineDirtyTrackingFrame(
                vulkanContext().arena(), vulkanContext().device(),
                vulkanContext().graphicsVkQueue(), surface(), 1200, 800);
        frame.init(vulkanContext().graphicsQueueFamily());

        loop = GraphicsLoop.builder()
                .renderer(frame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> frame.resize(dims[0], dims[1]))
                .onFpsUpdate(m -> {
                    String stats = frame.getLastFlushStats();
                    Logger.info(m.summary() + (stats.isEmpty() ? "" : " | " + stats));
                })
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
    }

    public static void main(String[] args) {
        try (SplineDirtyTrackingApp app = new SplineDirtyTrackingApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
