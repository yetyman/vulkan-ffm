package io.github.yetyman.vulkan.sample.graph;

import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

/**
 * Sample application demonstrating the render graph system.
 * Renders a triangle to an offscreen buffer, applies edge detection post-processing,
 * and composites to the swapchain. The render graph manages pass ordering and barriers.
 *
 * On startup, prints the compiled render graph DAG to the console.
 */
public class RenderGraphApp extends VulkanApplication {

    private GraphicsLoop loop;
    private RenderGraphFrame frame;

    public RenderGraphApp() {
        super("Render Graph Demo", 800, 600, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        frame = new RenderGraphFrame(
            vulkanContext().arena(), vulkanContext().device(),
            vulkanContext().graphicsVkQueue(), surface(), 800, 600);
        frame.init(vulkanContext().graphicsQueueFamily());

        // Print the compiled render graph on startup
        System.out.println();
        frame.printRenderGraph();
        System.out.println();

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
        try (RenderGraphApp app = new RenderGraphApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
