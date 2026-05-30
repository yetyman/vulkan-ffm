package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.ILifecycleListener;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.highlevel.GraphicsLoop;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.ComputeLoop;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.util.Logger;

public class NBodyApp extends VulkanApplication implements ILifecycleListener {

    private GraphicsLoop      loop;
    private NBodyGraphicsFrame frame;

    public NBodyApp() {
        super("N-Body Simulation", 900, 900, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());

        VkQueue graphicsQueue = vulkanContext().graphicsVkQueue();
        VkQueue computeQueue  = vulkanContext().computeVkQueue();

        frame = new NBodyGraphicsFrame(
                vulkanContext().arena(), vulkanContext().device(),
                graphicsQueue, surface(), 900, 900);
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
    public void onBeforeShutdown() {
        if (frame != null) frame.close();
    }

    @Override
    protected void shutdown() {
    }

    public static void main(String[] args) {
        try (NBodyApp app = new NBodyApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
