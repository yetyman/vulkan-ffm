package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.ILifecycle;
import io.github.yetyman.vulkan.ILifecycleListener;
import io.github.yetyman.vulkan.VkSurface;
import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.VulkanLibrary;
import io.github.yetyman.vulkan.WindowSystem;
import io.github.yetyman.vulkan.input.InputManager;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.*;
import java.util.function.Consumer;

/**
 * Base class for Vulkan applications. Manages window, VulkanContext, surface, and input.
 * Subclasses implement {@link #initialize()} and {@link #shutdown()} for application lifecycle.
 * The render loop is the subclass's responsibility — compose a {@link GraphicsLoop} and call
 * {@code loop.runOnCurrentThread()} from {@link #initialize()}.
 *
 * <h3>Window resize</h3>
 * Override {@link #onWindowResize(int, int)} to forward resize events to your loop:
 * <pre>{@code
 * protected void onWindowResize(int w, int h) { loop.signalResize(w, h); }
 * }</pre>
 *
 * <h3>Background GPU work and lifecycle coordination</h3>
 * Use {@link #registerLifecycleDependency(ILifecycle)} for managed components or
 * {@link #addLifecycleListener(ILifecycleListener)} for components that manage their own sequencing.
 */
public abstract class VulkanApplication implements AutoCloseable {

    public static class Config {
        public String[] validationLayers = {};
        public boolean enableInput = true;
        public boolean enableLogging = true;
        public boolean resizable = true;
        public Consumer<String> logger = Logger::info;
        public WindowSystem windowSystem;
        public io.github.yetyman.vulkan.input.InputSystem inputSystem;

        public static Config defaults(WindowSystem windowSystem, io.github.yetyman.vulkan.input.InputSystem inputSystem) {
            Config config = new Config();
            config.windowSystem = windowSystem;
            config.inputSystem = inputSystem;
            return config;
        }

        public static Config development(WindowSystem windowSystem, io.github.yetyman.vulkan.input.InputSystem inputSystem) {
            Config config = new Config();
            config.validationLayers = new String[]{"VK_LAYER_KHRONOS_validation"};
            config.windowSystem = windowSystem;
            config.inputSystem = inputSystem;
            return config;
        }
    }

    protected final int width, height;
    protected final String title;
    protected final Config config;

    private MemorySegment window;
    private VulkanContext vulkanContext;
    private MemorySegment surface;
    private InputManager inputManager;
    private final java.util.List<ILifecycle> lifecycleDependencies = new java.util.ArrayList<>();
    private final java.util.List<ILifecycleListener> lifecycleListeners = new java.util.ArrayList<>();

    /**
     * Registers a lifecycle dependency that will be stopped before deviceWaitIdle
     * during shutdown, and restarted after resize completes.
     */
    protected void registerLifecycleDependency(ILifecycle dep) {
        lifecycleDependencies.add(dep);
    }

    /**
     * Registers a lifecycle listener that will be notified of lifecycle events.
     */
    protected void addLifecycleListener(ILifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    protected VulkanApplication(String title, int width, int height, WindowSystem windowSystem, io.github.yetyman.vulkan.input.InputSystem inputSystem) {
        this(title, width, height, Config.development(windowSystem, inputSystem));
    }

    protected VulkanApplication(String title, int width, int height, Config config) {
        if (config.windowSystem == null) throw new IllegalArgumentException("WindowSystem is required");
        if (config.enableInput && config.inputSystem == null) throw new IllegalArgumentException("InputSystem is required when input is enabled");
        this.title = title;
        this.width = width;
        this.height = height;
        this.config = config;
        VulkanLibrary.load();
    }

    public final void run() {
        initWindow();
        initVulkan();
        if (config.enableInput) initInput();
        initialize();
        cleanup();
    }

    private void initWindow() {
        config.windowSystem.setResizable(config.resizable);
        window = config.windowSystem.createWindow(width, height, title);
        if (window.equals(MemorySegment.NULL)) throw new RuntimeException("Failed to create window");
        config.windowSystem.setResizeCallback(window, (w, nw, nh) -> onWindowResize(nw, nh), Arena.global());
        log("Window created");
    }

    private void initVulkan() {
        try (Arena tempArena = Arena.ofConfined()) {
            String[] extensions = config.windowSystem.getRequiredVulkanExtensions(tempArena);
            if (extensions == null) throw new RuntimeException("Failed to get required extensions");

            VulkanContext.Builder builder = VulkanContext.builder()
                .applicationName(title)
                .applicationVersion(1)
                .instanceExtensions(extensions);

            if (config.validationLayers.length > 0) builder.validationLayers(config.validationLayers);

            vulkanContext = builder.build();
            surface = config.windowSystem.createSurface(vulkanContext.instance().handle(), window, vulkanContext.arena());
            log("Vulkan initialized");
        }
    }

    private void initInput() {
        inputManager = new InputManager(window, config.inputSystem);
        configureInput(inputManager);
        log("Input system initialized");
    }

    private boolean cleanedUp = false;

    @Override
    public void close() { cleanup(); }

    private void cleanup() {
        if (cleanedUp) return;
        cleanedUp = true;

        for (ILifecycleListener l : lifecycleListeners) l.onBeforeStop();
        for (ILifecycle dep : lifecycleDependencies) { dep.beforeStop(); dep.stop(); dep.awaitStopped(); dep.afterStop(); }

        if (vulkanContext != null && vulkanContext.device() != null) {
            Vulkan.deviceWaitIdle(vulkanContext.device().handle()).check();
        }
        for (ILifecycleListener l : lifecycleListeners) l.onAfterStop();
        for (ILifecycleListener l : lifecycleListeners) l.onBeforeShutdown();

        shutdown();

        if (inputManager != null) inputManager.close();

        if (surface != null && !surface.equals(MemorySegment.NULL)) {
            VkSurface.destroy(vulkanContext.instance().handle(), surface);
        }

        if (vulkanContext != null) vulkanContext.close();

        if (window != null && !window.equals(MemorySegment.NULL)) {
            config.windowSystem.destroyWindow(window);
        }

        config.windowSystem.terminate();
        log("Application cleanup complete");
    }

    private void log(String message) {
        if (config.enableLogging) config.logger.accept(message);
    }

    // Protected accessors
    protected MemorySegment window()            { return window; }
    protected VulkanContext vulkanContext()      { return vulkanContext; }
    protected MemorySegment surface()           { return surface; }
    protected InputManager inputManager()       { return inputManager; }
    protected WindowSystem windowSystem()       { return config.windowSystem; }

    // Abstract lifecycle
    protected abstract void initialize();
    protected abstract void shutdown();

    // Optional hooks
    /** Called when the window framebuffer is resized. Forward to your loop: {@code loop.signalResize(w, h)}. */
    protected void onWindowResize(int width, int height) {}
    protected void configureInput(InputManager inputManager) {}
}
