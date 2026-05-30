package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

/**
 * Temporal feedback demo: colorful orbiting circles leave persistent motion trails.
 *
 * Demonstrates the TemporalResource API managing a double-buffered pixel buffer.
 * Each frame, a compute shader reads the previous frame's output and blends it with
 * new animated content, creating a trail effect. The temporal resource handles:
 * - Which physical buffer to read from (previous frame's write)
 * - Which physical buffer to write to (this frame's output)
 * - Automatic flip after each write
 * - Initial black clear on frame 0
 *
 * Run: mvn exec:java -pl sample-app -Dexec.mainClass="io.github.yetyman.vulkan.sample.simple.TemporalTrailApp"
 */
public class TemporalTrailApp extends VulkanApplication {

    private GraphicsLoop loop;
    private TemporalTrailGraphicsFrame frame;

    public TemporalTrailApp() {
        super("Temporal Trail Demo", 800, 800, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        frame = new TemporalTrailGraphicsFrame(
            vulkanContext().arena(), vulkanContext().device(),
            vulkanContext().graphicsVkQueue(), surface(), 800, 800);
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
    }

    public static void main(String[] args) {
        try (TemporalTrailApp app = new TemporalTrailApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
