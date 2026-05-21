package io.github.yetyman.vulkan.sample.graph;

import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

/**
 * Render-graph-driven temporal trail demo.
 *
 * Same visual effect as TemporalTrailApp (orbiting circles with motion trails),
 * but this version uses the full render graph pipeline:
 * - Declares temporal edges (readsTemporalPrevious / writesTemporalCurrent)
 * - Graph compiler validates the temporal cycle (completeness, no non-temporal cycles)
 * - Graph compiler validates starting point (InitialState.Clear.BLACK defined)
 * - Compiled graph determines execution order from resource dependencies
 * - Nodes execute in the graph-determined order
 *
 * On startup, prints the compiled DAG showing:
 *   trail-compute --> trail-display --> present
 *
 * Run: mvn exec:java -pl sample-app -Dexec.mainClass="io.github.yetyman.vulkan.sample.graph.TemporalTrailGraphApp"
 */
public class TemporalTrailGraphApp extends VulkanApplication {

    private GraphicsLoop loop;
    private TemporalTrailGraphFrame frame;

    public TemporalTrailGraphApp() {
        super("Temporal Trail (Graph)", 800, 800, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        frame = new TemporalTrailGraphFrame(
            vulkanContext().arena(), vulkanContext().device(),
            vulkanContext().graphicsVkQueue(), surface(), 800, 800);
        frame.init(vulkanContext().graphicsQueueFamily());

        // Print the compiled render graph on startup
        System.out.println();
        System.out.println("=== Temporal Trail - Render Graph Demo ===");
        System.out.println("Graph compiled with temporal cycle validation.");
        System.out.println("Compiled DAG:");
        frame.printRenderGraph();
        System.out.println();

        loop = GraphicsLoop.builder()
            .renderer(frame)
            .driver(LoopDriver.uncapped())
            .shouldClose(() -> windowSystem().shouldClose(window()))
            .onResize(dims -> frame.resize(dims[0], dims[1]))
            .onFpsUpdate(fps -> Logger.info("FPS: " + fps))
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
        try (TemporalTrailGraphApp app = new TemporalTrailGraphApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
