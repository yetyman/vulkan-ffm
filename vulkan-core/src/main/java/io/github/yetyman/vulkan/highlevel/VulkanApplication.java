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
 * Base class for Vulkan applications. Manages the window, Vulkan context, input, and main loop.
 *
 * <p>Subclasses implement {@link #initialize()}, {@link #render()}, {@link #onResize}, and
 * {@link #shutdown()} for application lifecycle.
 *
 * <h3>Background GPU work and lifecycle coordination</h3>
 * Use {@link #registerLifecycleDependency(ILifecycle)} for managed components (stop/start handled
 * automatically) or {@link #addLifecycleListener(ILifecycleListener)} for components that manage
 * their own sequencing. Both are notified before {@code vkDeviceWaitIdle} during resize and
 * shutdown, and after the swapchain is rebuilt.
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
    private MemorySegment callbackStub;
    private boolean framebufferResized = false;
    private InputManager inputManager;
    private final java.util.List<ILifecycle> lifecycleDependencies = new java.util.ArrayList<>();
    private final java.util.List<ILifecycleListener> lifecycleListeners = new java.util.ArrayList<>();

    /**
     * Registers a lifecycle dependency that will be stopped before deviceWaitIdle
     * during resize and shutdown, and restarted after resize completes.
     * Dependencies are stopped in registration order and restarted in reverse order.
     */
    protected void registerLifecycleDependency(ILifecycle dep) {
        lifecycleDependencies.add(dep);
    }

    /**
     * Registers a lifecycle listener that will be notified of lifecycle events.
     * If the listener also implements {@link ILifecycle}, prefer
     * {@link #registerLifecycleDependency(ILifecycle)} which provides managed stop/start sequencing.
     */
    protected void addLifecycleListener(ILifecycleListener listener) {
        lifecycleListeners.add(listener);
    }
    
    protected VulkanApplication(String title, int width, int height, WindowSystem windowSystem, io.github.yetyman.vulkan.input.InputSystem inputSystem) {
        this(title, width, height, Config.development(windowSystem, inputSystem));
    }
    
    protected VulkanApplication(String title, int width, int height, Config config) {
        if (config.windowSystem == null) {
            throw new IllegalArgumentException("WindowSystem is required");
        }
        if (config.enableInput && config.inputSystem == null) {
            throw new IllegalArgumentException("InputSystem is required when input is enabled");
        }
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
        mainLoop();
        cleanup();
    }
    
    private void initWindow() {
        config.windowSystem.setResizable(config.resizable);
        window = config.windowSystem.createWindow(width, height, title);
        if (window.equals(MemorySegment.NULL)) {
            throw new RuntimeException("Failed to create window");
        }
        
        config.windowSystem.setResizeCallback(window, this::onFramebufferResize, Arena.global());
        log("Window created");
    }
    
    private void initVulkan() {
        try (Arena tempArena = Arena.ofConfined()) {
            String[] extensions = config.windowSystem.getRequiredVulkanExtensions(tempArena);
            if (extensions == null) {
                throw new RuntimeException("Failed to get required extensions");
            }
            
            VulkanContext.Builder builder = VulkanContext.builder()
                .applicationName(title)
                .applicationVersion(1)
                .instanceExtensions(extensions);
            
            if (config.validationLayers.length > 0) {
                builder.validationLayers(config.validationLayers);
            }
            
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
    
    private void mainLoop() {
        long lastTime = System.nanoTime();
        int frameCount = 0;
        
        while (!config.windowSystem.shouldClose(window)) {
            config.windowSystem.pollEvents();
            
            if (framebufferResized) {
                handleResize();
                framebufferResized = false;
            }
            
            if (shouldRender()) {
                render();
                
                frameCount++;
                long currentTime = System.nanoTime();
                if (currentTime - lastTime >= 1_000_000_000L) {
                    onFPSUpdate(frameCount);
                    frameCount = 0;
                    lastTime = currentTime;
                }
            } else {
                try { Thread.sleep(10); } catch (InterruptedException e) {}
            }
        }
    }
    
    private boolean shouldRender() {
        try (Arena tempArena = Arena.ofConfined()) {
            var size = io.github.yetyman.vulkan.util.VkFramebufferSize.query(
                config.windowSystem::getFramebufferSize, window, tempArena);
            return size.isValid();
        }
    }
    
    private void handleResize() {
        try (Arena tempArena = Arena.ofConfined()) {
            for (ILifecycleListener l : lifecycleListeners) l.onBeforeStop();
            for (ILifecycle dep : lifecycleDependencies) { dep.beforeStop(); dep.stop(); dep.awaitStopped(); dep.afterStop(); }
            Vulkan.deviceWaitIdle(vulkanContext.device().handle()).check();
            for (ILifecycleListener l : lifecycleListeners) l.onAfterStop();

            var size = io.github.yetyman.vulkan.util.VkFramebufferSize.query(
                config.windowSystem::getFramebufferSize, window, tempArena);

            if (size.isValid()) {
                for (ILifecycleListener l : lifecycleListeners) l.onBeforeResize(size.width, size.height);
                for (ILifecycle dep : lifecycleDependencies) dep.beforeResize(size.width, size.height);
                onResize(size.width, size.height);
                for (int i = lifecycleDependencies.size() - 1; i >= 0; i--) lifecycleDependencies.get(i).afterResize(size.width, size.height);
                for (ILifecycleListener l : lifecycleListeners) l.onAfterResize(size.width, size.height);
                log("Resized to " + size.width + "x" + size.height);
            }
            for (int i = lifecycleDependencies.size() - 1; i >= 0; i--) {
                ILifecycle dep = lifecycleDependencies.get(i);
                dep.beforeStart(); dep.start(); dep.afterStart();
            }
            for (ILifecycleListener l : lifecycleListeners) l.onAfterStart();
        }
    }
    
    private void onFramebufferResize(MemorySegment window, int width, int height) {
        framebufferResized = true;
    }
    
    private void log(String message) {
        if (config.enableLogging) {
            config.logger.accept(message);
        }
    }
    
    private boolean cleanedUp = false;

    @Override
    public void close() {
        cleanup();
    }
    
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
        
        if (inputManager != null) {
            inputManager.close();
        }
        
        if (surface != null && !surface.equals(MemorySegment.NULL)) {
            VkSurface.destroy(vulkanContext.instance().handle(), surface);
        }
        
        if (vulkanContext != null) {
            vulkanContext.close();
        }
        
        if (window != null && !window.equals(MemorySegment.NULL)) {
            config.windowSystem.destroyWindow(window);
        }
        
        config.windowSystem.terminate();
        log("Application cleanup complete");
    }
    
    // Protected accessors
    protected MemorySegment window() { return window; }
    protected VulkanContext vulkanContext() { return vulkanContext; }
    protected MemorySegment surface() { return surface; }
    protected InputManager inputManager() { return inputManager; }
    
    // Abstract methods for subclasses
    protected abstract void initialize();
    protected abstract void render();
    protected abstract void onResize(int width, int height);
    protected abstract void shutdown();
    
    // Optional hooks
    protected void configureInput(InputManager inputManager) {}
    protected void onFPSUpdate(int fps) {}
}