package io.github.yetyman.vulkan.sample.complex.threading;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.highlevel.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.sample.complex.postprocessing.AdaptiveAA;

import java.lang.foreign.*;
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
    private ThreadManager threadManager;
    private Mode mode = Mode.ADAPTIVE;
    
    // Performance tracking
    private long lastFrameTime = System.nanoTime();
    private final ArrayDeque<Long> frameTimes = new ArrayDeque<>();
    private final int FRAME_HISTORY = 60;
    
    // Cached layouts to avoid FFM overhead
    private MemorySegment cachedViewport;
    private MemorySegment cachedScissor;
    
    public ThreadedRenderer(Arena arena, MemorySegment device, MemorySegment queue, 
                           MemorySegment surface, int width, int height) {
        this.arena = arena;
        this.device = device;
        this.queue = queue;
        this.surface = surface;
        this.width = width;
        this.height = height;
        
        // Initialize thread manager
        threadManager = new ThreadManager();
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
            .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT, 0, 4)
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
        
        // Pre-allocate cached layouts
        cachedViewport = io.github.yetyman.vulkan.VkViewport.builder()
            .position(0, 0)
            .size(width, height)
            .depthRange(0.0f, 1.0f)
            .build(arena);
        cachedScissor = io.github.yetyman.vulkan.VkRect2D.builder()
            .offset(0, 0)
            .extent(width, height)
            .build(arena);
    }
    
    public void drawFrame() {
        long frameStart = System.nanoTime();
        
        // Use main arena instead of creating new one each frame
        Arena frameArena = arena;
            VulkanSyncManager.FrameSync frameSync = syncManager.acquireFrame();
            
            int imgIdx = VkSwapchainOps.acquireNextImage(device, swapchainManager.swapchain().handle())
                .semaphore(frameSync.imageAvailable.handle())
                .execute(frameArena);
            
            if (threadManager.getActiveThreads() == 1) {
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
        
        // Track performance and adjust threads
        trackPerformance(frameStart);
        adjustThreadCount();
    }
    
    private void recordCommandBufferThreaded(MemorySegment primaryCommandBuffer, int imageIndex, Arena frameArena) {
        int threadsToUse = threadManager.getActiveThreads();
        
        if (threadsToUse == 1) {
            // Single-threaded path for efficiency
            recordSingleThreaded(primaryCommandBuffer, imageIndex, frameArena);
            return;
        }
        
        // Use VulkanThreadManager for threaded execution
        MemorySegment[] threadCommandBuffers = new MemorySegment[threadsToUse];
        
        threadManager.executeThreaded(TRIANGLES_COUNT, triangleIndex -> {
            int threadId = (int) Thread.currentThread().getId() % threadsToUse;
            if (threadCommandBuffers[threadId] == null) {
                Arena threadArena = Arena.ofShared();
                MemorySegment threadCmd = commandManager.allocateBuffer(threadArena);
                threadCommandBuffers[threadId] = threadCmd;
                
                VkCommandBuffer.begin(threadCmd).execute(threadArena);
                VkCommandBuffer.beginRenderPass(threadCmd, renderPass.handle(), swapchainManager.framebuffers()[imageIndex].handle())
                    .renderArea(0, 0, width, height)
                    .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                    .execute(threadArena);
                VulkanExtensions.cmdBindPipeline(threadCmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
                VulkanExtensions.cmdSetViewport(threadCmd, 0, 1, cachedViewport);
                VulkanExtensions.cmdSetScissor(threadCmd, 0, 1, cachedScissor);
                VulkanExtensions.cmdDraw(threadCmd, 3, 1, 0, triangleIndex);
                VulkanExtensions.cmdEndRenderPass(threadCmd);
                VulkanExtensions.endCommandBuffer(threadCmd).check();
            }
        });
        
        // Submit command buffers
        for (MemorySegment cmd : threadCommandBuffers) {
            if (cmd != null) {
                VkSubmit.builder()
                    .commandBuffer(cmd)
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
        
        // Push time constant for rotation
        MemorySegment timeConstant = frameArena.allocate(4);
        timeConstant.set(ValueLayout.JAVA_FLOAT, 0, (float)(System.nanoTime() / 1_000_000_000.0));
        VulkanExtensions.cmdPushConstants(commandBuffer, pipeline.layout(), 
            VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT, 0, 4, timeConstant);
        
        VulkanExtensions.cmdSetViewport(commandBuffer, 0, 1, cachedViewport);
        VulkanExtensions.cmdSetScissor(commandBuffer, 0, 1, cachedScissor);
        
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
        threadManager.adjustThreadCount(avgFrameTime);
    }
    
    public void setThreadCount(int count) {
        threadManager.setThreadCount(count);
    }
    
    public void setMode(ThreadManager.Mode mode) {
        threadManager.setMode(mode);
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
        return threadManager.getActiveThreads();
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
        
        // Update cached layouts with new dimensions
        cachedViewport = io.github.yetyman.vulkan.VkViewport.builder()
            .position(0, 0)
            .size(width, height)
            .depthRange(0.0f, 1.0f)
            .build(arena);
        cachedScissor = io.github.yetyman.vulkan.VkRect2D.builder()
            .offset(0, 0)
            .extent(width, height)
            .build(arena);
        
        System.out.println("[OK] Swapchain and depth buffer recreated for resize");
    }
    
    public void cleanup() {
        // Clean up thread manager
        if (threadManager != null) threadManager.close();
        
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