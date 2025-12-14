package io.github.yetyman.sample;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.highlevel.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

public class ThreadedRenderer {
    public enum Mode { BEST_EFFICIENCY, BEST_PERFORMANCE, ADAPTIVE }
    
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static final int TRIANGLES_COUNT = 1000; // Back to 1000 triangles with depth testing
    
    // AA toggle
    private boolean adaptiveAAEnabled = true;
    private AdaptiveAA adaptiveAA;
    
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment queue;
    private final MemorySegment surface;
    private int width, height;
    
    private VulkanSwapchainManager swapchainManager;
    private VkRenderPass renderPass;
    private VkRenderPass directRenderPass; // For non-AA rendering
    private VkPipeline pipeline;
    private MemorySegment[] commandBuffers;
    private VulkanSyncManager syncManager;
    private VulkanCommandManager commandManager;
    private VulkanRenderTarget depthTarget;
    private VulkanShaderManager shaderManager;
    
    // Threading
    private ThreadPoolExecutor executor;
    private Mode mode = Mode.ADAPTIVE;
    private final AtomicInteger currentThreadCount = new AtomicInteger(1);
    
    // Performance tracking
    private long lastFrameTime = System.nanoTime();
    private final ArrayDeque<Long> frameTimes = new ArrayDeque<>();
    private final int FRAME_HISTORY = 60;
    
    public ThreadedRenderer(Arena arena, MemorySegment device, MemorySegment queue, 
                           MemorySegment surface, int width, int height) {
        this.arena = arena;
        this.device = device;
        this.queue = queue;
        this.surface = surface;
        this.width = width;
        this.height = height;
        
        // Initialize thread pool
        int coreCount = Runtime.getRuntime().availableProcessors();
        executor = new ThreadPoolExecutor(1, coreCount, 60L, TimeUnit.SECONDS, 
            new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "VulkanWorker");
                t.setDaemon(true);
                return t;
            });
    }
    
    public void init(MemorySegment physicalDevice, int queueFamilyIndex) {
        this.physicalDevice = physicalDevice;
        createSwapchainManager();
        createManagers(queueFamilyIndex);
        createDepthTarget();
        createRenderPasses();
        if (adaptiveAAEnabled) {
            adaptiveAA = new AdaptiveAA(arena, device, width, height);
        }
        createGraphicsPipeline();
        createFramebuffers();
        createCommandBuffers();
        System.out.println("[OK] Threaded renderer initialized with " + TRIANGLES_COUNT + " triangles (AA: " + (adaptiveAAEnabled ? "ON" : "OFF") + ")");
    }
    
    private void createSwapchainManager() {
        swapchainManager = VulkanSwapchainManager.builder()
            .arena(arena)
            .device(device)
            .surface(surface)
            .extent(width, height)
            .vsync(true)
            .build();
        System.out.println("[OK] Swapchain manager created with " + swapchainManager.imageCount() + " images");
    }
    
    private MemorySegment physicalDevice;
    
    private void createManagers(int queueFamilyIndex) {
        syncManager = VulkanSyncManager.builder()
            .arena(arena)
            .device(device)
            .framesInFlight(MAX_FRAMES_IN_FLIGHT)
            .build();
        
        commandManager = VulkanCommandManager.builder()
            .arena(arena)
            .device(device)
            .queueFamilyIndex(queueFamilyIndex)
            .threaded(true)
            .build();
        
        shaderManager = VulkanShaderManager.builder()
            .arena(arena)
            .device(device)
            .build();
        
        System.out.println("[OK] Managers created");
    }
    
    private void createDepthTarget() {
        depthTarget = VulkanRenderTarget.builder()
            .arena(arena)
            .device(device)
            .physicalDevice(physicalDevice)
            .format(VkFormat.VK_FORMAT_D24_UNORM_S8_UINT)
            .extent(width, height)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT)
            .build();
        System.out.println("[OK] Depth target created");
    }
    
    private void createRenderPasses() {
        // Direct rendering to swapchain (no AA)
        directRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .depthAttachment(VkFormat.VK_FORMAT_D24_UNORM_S8_UINT, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
            .build(arena);
        
        // Set active render pass based on AA setting
        renderPass = directRenderPass;
        System.out.println("[OK] Render passes created");
    }
    
    private void createGraphicsPipeline() {
        VulkanShaderManager.ShaderSet shaders = shaderManager.createShaderSet()
            .vertex("/shaders/triangle.vert")
            .fragment("/shaders/triangle.frag")
            .build();
        
        // Use appropriate render pass based on AA setting
        MemorySegment targetRenderPass = adaptiveAAEnabled ? 
            adaptiveAA.getSceneRenderPass().handle() : directRenderPass.handle();
        
        pipeline = VkPipeline.builder()
            .device(device)
            .renderPass(targetRenderPass)
            .viewport(0, 0, width, height)
            .vertexShader(shaders.vertex())
            .fragmentShader(shaders.fragment())
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .build(arena);
        System.out.println("[OK] Graphics pipeline created");
    }
    
    private void createFramebuffers() {
        swapchainManager.createFramebuffers(directRenderPass.handle(), depthTarget.imageView().handle());
        System.out.println("[OK] Framebuffers created");
    }
    
    private void createCommandBuffers() {
        commandBuffers = commandManager.allocateBuffers(MAX_FRAMES_IN_FLIGHT, arena);
        System.out.println("[OK] Command buffers allocated");
    }
    
    private int findMemoryType(int typeFilter, int properties, MemorySegment memProps) {
        int memoryTypeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(memProps);
        
        for (int i = 0; i < memoryTypeCount; i++) {
            if ((typeFilter & (1 << i)) != 0) {
                try {
                    MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(memProps, i);
                    int flags = VkMemoryType.propertyFlags(memType);
                    if ((flags & properties) == properties) {
                        return i;
                    }
                } catch (Exception e) {
                    // Skip this memory type if we can't read it
                    continue;
                }
            }
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }
    
    public void drawFrame() {
        long frameStart = System.nanoTime();
        
        try (Arena frameArena = Arena.ofConfined()) {
            VulkanSyncManager.FrameSync frameSync = syncManager.acquireFrame(frameArena);
            
            int imgIdx = VkSwapchainOps.acquireNextImage(device, swapchainManager.swapchain().handle())
                .semaphore(frameSync.imageAvailable.handle())
                .execute(frameArena);
            
            if (currentThreadCount.get() == 1) {
                // Single-threaded: use normal submission
                recordCommandBufferThreaded(commandBuffers[frameSync.frameIndex], imgIdx, frameArena);
                
                VkSubmit.builder()
                    .waitSemaphore(frameSync.imageAvailable.handle(), VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .commandBuffer(commandBuffers[frameSync.frameIndex])
                    .signalSemaphore(frameSync.renderFinished.handle())
                    .submit(queue, frameSync.inFlight.handle(), frameArena).check();
            } else {
                // Multi-threaded: command buffers are submitted inside recordCommandBufferThreaded
                recordCommandBufferThreaded(commandBuffers[frameSync.frameIndex], imgIdx, frameArena);
            }
            
            VkPresent.builder()
                .waitSemaphore(frameSync.renderFinished.handle())
                .swapchain(swapchainManager.swapchain().handle(), imgIdx)
                .present(queue, frameArena);
            
            syncManager.nextFrame();
        }
        
        // Track performance and adjust threads
        trackPerformance(frameStart);
        adjustThreadCount();
    }
    
    private void recordCommandBufferThreaded(MemorySegment primaryCommandBuffer, int imageIndex, Arena frameArena) {
        int threadsToUse = currentThreadCount.get();
        
        if (threadsToUse == 1) {
            // Single-threaded path for efficiency
            recordSingleThreaded(primaryCommandBuffer, imageIndex, frameArena);
            return;
        }
        
        // Multi-threaded path: each thread records separate primary command buffers
        int trianglesPerThread = TRIANGLES_COUNT / threadsToUse;
        MemorySegment[] threadCommandBuffers = new MemorySegment[threadsToUse];
        CountDownLatch latch = new CountDownLatch(threadsToUse);
        
        // Allocate command buffers for each thread from their own pools
        for (int t = 0; t < threadsToUse; t++) {
            final int threadIndex = t;
            final int startTriangle = t * trianglesPerThread;
            final int endTriangle = (t == threadsToUse - 1) ? TRIANGLES_COUNT : (t + 1) * trianglesPerThread;
            
            executor.submit(() -> {
                try {
                    Arena threadArena = Arena.ofShared();
                    MemorySegment threadCmd = commandManager.allocateBuffer(threadArena);
                    threadCommandBuffers[threadIndex] = threadCmd;
                    
                    // Record commands for this thread's triangles
                    VkCommandBuffer.begin(threadCmd).execute(threadArena);
                    
                    VkCommandBuffer.beginRenderPass(threadCmd, renderPass.handle(), swapchainManager.framebuffers()[imageIndex].handle())
                        .renderArea(0, 0, width, height)
                        .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                        .execute(threadArena);
                    
                    VulkanExtensions.cmdBindPipeline(threadCmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
                    
                    MemorySegment viewport = io.github.yetyman.vulkan.VkViewport.builder()
                        .position(0, 0)
                        .size(width, height)
                        .depthRange(0.0f, 1.0f)
                        .build(threadArena);
                    VulkanExtensions.cmdSetViewport(threadCmd, 0, 1, viewport);
                    
                    MemorySegment scissor = io.github.yetyman.vulkan.VkRect2D.builder()
                        .offset(0, 0)
                        .extent(width, height)
                        .build(threadArena);
                    VulkanExtensions.cmdSetScissor(threadCmd, 0, 1, scissor);
                    
                    // Use instanced rendering instead of individual draws
                    int triangleCount = endTriangle - startTriangle;
                    VulkanExtensions.cmdDraw(threadCmd, 3, triangleCount, 0, startTriangle);
                    
                    VulkanExtensions.cmdEndRenderPass(threadCmd);
                    VulkanExtensions.endCommandBuffer(threadCmd).check();
                    
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to finish
        try {
            latch.await(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Submit all command buffers to queue (this must be single-threaded)
        for (int i = 0; i < threadsToUse; i++) {
            if (threadCommandBuffers[i] != null) {
                VkSubmit.builder()
                    .commandBuffer(threadCommandBuffers[i])
                    .submit(queue, MemorySegment.NULL, frameArena).check();
            }
        }
    }
    
    private void recordSingleThreaded(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        
        if (adaptiveAAEnabled) {
            // Render scene to AA targets
            VkCommandBuffer.beginRenderPass(commandBuffer, adaptiveAA.getSceneRenderPass().handle(), adaptiveAA.getSceneFramebuffer().handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            VulkanExtensions.cmdEndRenderPass(commandBuffer);
            
            // Perform adaptive AA and output to swapchain
            adaptiveAA.performAA(commandBuffer, swapchainManager.framebuffers()[imageIndex], frameArena);
        } else {
            // Direct rendering to swapchain
            VkCommandBuffer.beginRenderPass(commandBuffer, directRenderPass.handle(), swapchainManager.framebuffers()[imageIndex].handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            VulkanExtensions.cmdEndRenderPass(commandBuffer);
        }
        
        VulkanExtensions.endCommandBuffer(commandBuffer).check();
    }
    
    private void renderScene(MemorySegment commandBuffer, Arena frameArena) {
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
        
        MemorySegment viewport = io.github.yetyman.vulkan.VkViewport.builder()
            .position(0, 0)
            .size(width, height)
            .depthRange(0.0f, 1.0f)
            .build(frameArena);
        VulkanExtensions.cmdSetViewport(commandBuffer, 0, 1, viewport);
        
        MemorySegment scissor = io.github.yetyman.vulkan.VkRect2D.builder()
            .offset(0, 0)
            .extent(width, height)
            .build(frameArena);
        VulkanExtensions.cmdSetScissor(commandBuffer, 0, 1, scissor);
        
        VulkanExtensions.cmdDraw(commandBuffer, 3, TRIANGLES_COUNT, 0, 0);
    }
    

    
    private void trackPerformance(long frameStart) {
        long frameTime = System.nanoTime() - frameStart;
        frameTimes.addLast(frameTime);
        if (frameTimes.size() > FRAME_HISTORY) {
            frameTimes.removeFirst();
        }
        lastFrameTime = frameTime;
    }
    
    private void adjustThreadCount() {
        if (frameTimes.size() < 10) return; // Need some history
        
        double avgFrameTime = frameTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double targetFrameTime = 16_666_667.0; // 60 FPS in nanoseconds
        
        int currentThreads = currentThreadCount.get();
        int maxThreads = Runtime.getRuntime().availableProcessors();
        
        switch (mode) {
            case BEST_EFFICIENCY:
                // Minimize threads while maintaining 60fps
                if (avgFrameTime < targetFrameTime * 0.8 && currentThreads > 1) {
                    setThreadCount(currentThreads - 1);
                } else if (avgFrameTime > targetFrameTime && currentThreads < maxThreads) {
                    setThreadCount(currentThreads + 1);
                }
                break;
                
            case BEST_PERFORMANCE:
                // Maximize threads for best performance
                if (currentThreads < maxThreads && avgFrameTime > targetFrameTime * 0.5) {
                    setThreadCount(currentThreads + 1);
                }
                break;
                
            case ADAPTIVE:
                // Balance between efficiency and performance
                if (avgFrameTime > targetFrameTime && currentThreads < maxThreads) {
                    setThreadCount(currentThreads + 1);
                } else if (avgFrameTime < targetFrameTime * 0.7 && currentThreads > 1) {
                    setThreadCount(Math.max(1, currentThreads - 1));
                }
                break;
        }
    }
    
    public void setThreadCount(int count) {
        int maxThreads = Runtime.getRuntime().availableProcessors();
        count = Math.max(1, Math.min(count, maxThreads));
        
        executor.setCorePoolSize(count);
        executor.setMaximumPoolSize(count);
        currentThreadCount.set(count);
        
        System.out.println("[THREADS] Adjusted to " + count + " threads");
    }
    
    public void setMode(Mode mode) {
        this.mode = mode;
        System.out.println("[MODE] Switched to " + mode);
    }
    
    public void setAdaptiveAAEnabled(boolean enabled) {
        this.adaptiveAAEnabled = enabled;
        System.out.println("[AA] Adaptive AA " + (enabled ? "enabled" : "disabled"));
        // Note: Requires renderer recreation to take effect
    }
    
    public boolean isAdaptiveAAEnabled() {
        return adaptiveAAEnabled;
    }
    
    public int getActiveThreads() {
        return currentThreadCount.get();
    }
    
    public double getAverageFrameTime() {
        return frameTimes.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000.0; // ms
    }
    
    public void resize(int newWidth, int newHeight) {
        // Clean up depth target
        if (depthTarget != null) {
            depthTarget.close();
        }
        
        // Update dimensions
        width = newWidth;
        height = newHeight;
        
        // Recreate swapchain manager
        swapchainManager.recreate(width, height);
        
        // Recreate depth target
        createDepthTarget();
        if (adaptiveAA != null) {
            adaptiveAA.cleanup();
            adaptiveAA = new AdaptiveAA(arena, device, width, height);
        }
        
        // Recreate framebuffers
        createFramebuffers();
        
        System.out.println("[OK] Swapchain and depth buffer recreated for resize");
    }
    
    public void cleanup() {
        // Shutdown thread pool
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        // Clean up managers
        if (syncManager != null) syncManager.close();
        if (commandManager != null) commandManager.close();
        if (shaderManager != null) shaderManager.close();
        if (depthTarget != null) depthTarget.close();
        
        // Clean up main resources
        pipeline.close();
        renderPass.close();
        if (directRenderPass != null) {
            directRenderPass.close();
        }
        if (adaptiveAA != null) {
            adaptiveAA.cleanup();
        }
        swapchainManager.close();
    }
}