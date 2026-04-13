package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.ILifecycleListener;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.ComputeLoop;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.queue.DirectSubmitter;
import io.github.yetyman.vulkan.queue.MutexSubmitter;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

public class GameOfLifeApp extends VulkanApplication implements ILifecycleListener {

    private GraphicsLoop loop;
    private GameOfLifeGraphicsFrame frame;

    public GameOfLifeApp() {
        super("Game of Life", 800, 800, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        VkQueue graphicsQueue = vulkanContext().graphicsVkQueue();
        VkQueue computeQueue = vulkanContext().computeVkQueue();

        if (graphicsQueue.handle().equals(computeQueue.handle())) {
            MutexSubmitter sharedSubmitter = new MutexSubmitter(graphicsQueue.handle());
            graphicsQueue.setSubmitter(sharedSubmitter);
            computeQueue.setSubmitter(new MutexSubmitter(computeQueue.handle(), sharedSubmitter.lock()));
            Logger.info("Compute: shared queue (MutexSubmitter)");
        } else {
            computeQueue.setSubmitter(new DirectSubmitter(computeQueue.handle()));
            Logger.info("Compute: dedicated queue (DirectSubmitter)");
        }

        frame = new GameOfLifeGraphicsFrame(
                vulkanContext().arena(), vulkanContext().device(),
                graphicsQueue, surface(), 800, 800);
        frame.init(vulkanContext().graphicsQueueFamily());

        ComputeLoop computeLoop = frame.buildComputeLoop(computeQueue);

        registerLifecycleDependency(computeLoop);
        addLifecycleListener(this);
        computeLoop.start();

        loop = GraphicsLoop.builder()
                .renderer(frame)
                .driver(LoopDriver.uncapped())
                .shouldClose(() -> windowSystem().shouldClose(window()))
                .onResize(dims -> frame.resize(dims[0], dims[1]))
                .lifecycleDependency(computeLoop)
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
    public void onBeforeShutdown() {
        if (frame != null) frame.close();
    }

    @Override
    protected void shutdown() {
    }

    public static void main(String[] args) {
        try (GameOfLifeApp app = new GameOfLifeApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
