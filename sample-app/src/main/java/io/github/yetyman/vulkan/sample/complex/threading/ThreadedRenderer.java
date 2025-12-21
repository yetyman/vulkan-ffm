package io.github.yetyman.vulkan.sample.complex.threading;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.generated.VkFormatProperties;
import io.github.yetyman.vulkan.highlevel.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.sample.complex.postprocessing.AdaptiveAA;
import io.github.yetyman.vulkan.sample.complex.models.*;
import io.github.yetyman.vulkan.sample.complex.threading.MainThreadWorkQueue;
import io.github.yetyman.vulkan.sample.complex.threading.ThreadManager;

import java.lang.foreign.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    private VkPipeline gltfPipeline;
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
    
    // LOD rendering
    private LODRenderer lodRenderer;
    private float[] cameraPosition = {0.0f, 0.0f, 5.0f};
    private MainThreadWorkQueue mainThreadWork;
    
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
        
        // Initialize LOD renderer with max instances
        // Note: physicalDevice will be set in init()
        lodRenderer = null; // Will be created in init() when physicalDevice is available
    }
    
    public void init(MemorySegment physicalDevice, int queueFamilyIndex) {
        this.physicalDevice = physicalDevice;
        
        // Initialize LOD renderer now that we have physicalDevice
        lodRenderer = new LODRenderer(arena, device, physicalDevice, 10000, 1000);
        mainThreadWork = new MainThreadWorkQueue(60.0); // Target 60 FPS
        lodRenderer.setMainThreadWorkQueue(mainThreadWork);
        
        createSwapchainManager();
        createManagers(queueFamilyIndex);
        createDepthTarget();
        createRenderPasses();
        if (adaptiveAAEnabled) {
            adaptiveAA = new AdaptiveAA(arena, device, physicalDevice, width, height);
        }
        
        // Set Vulkan resources after managers are created
        lodRenderer.setVulkanResources(commandManager.getCommandPool(), renderPass.handle());
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
        // Find supported depth format
        int depthFormat = findSupportedDepthFormat();
        
        depthTarget = VulkanRenderTarget.builder()
            .arena(arena)
            .device(device)
            .physicalDevice(physicalDevice)
            .format(depthFormat)
            .extent(width, height)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT)
            .build();
        System.out.println("[OK] Depth target created with format: " + depthFormat);
    }
    
    private int findSupportedDepthFormat() {
        // Try common depth formats in order of preference
        int[] candidates = {
            VkFormat.VK_FORMAT_D32_SFLOAT,
            VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT,
            VkFormat.VK_FORMAT_D24_UNORM_S8_UINT,
            VkFormat.VK_FORMAT_D16_UNORM
        };
        
        try (Arena tempArena = Arena.ofConfined()) {
            for (int format : candidates) {
                MemorySegment formatProps = tempArena.allocate(VkFormatProperties.sizeof());
                VulkanExtensions.getPhysicalDeviceFormatProperties(physicalDevice, format, formatProps);
                
                int optimalFeatures = VkFormatProperties.optimalTilingFeatures(formatProps);
                if ((optimalFeatures & VkFormatFeatureFlagBits.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                    return format;
                }
            }
        }
        
        throw new RuntimeException("Failed to find supported depth format");
    }
    
    private void createRenderPasses() {
        // Find supported depth format
        int depthFormat = findSupportedDepthFormat();
        
        // Direct rendering to swapchain (no AA)
        directRenderPass = VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .depthAttachment(depthFormat, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE)
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
        // Original triangle pipeline
        VulkanShaderManager.ShaderSet triangleShaders = shaderManager.createShaderSet()
            .vertex("/shaders/triangle.vert")
            .fragment("/shaders/triangle.frag")
            .build();
        
        // glTF pipeline
        VulkanShaderManager.ShaderSet gltfShaders = shaderManager.createShaderSet()
            .vertex("/shaders/gltf.vert")
            .fragment("/shaders/gltf.frag")
            .build();
        
        // Use appropriate render pass based on AA setting
        MemorySegment targetRenderPass = adaptiveAAEnabled ? 
            adaptiveAA.getSceneRenderPass().handle() : directRenderPass.handle();
        
        // Original triangle pipeline
        pipeline = VkPipeline.builder()
            .device(device)
            .renderPass(targetRenderPass)
            .viewport(0, 0, width, height)
            .vertexShader(triangleShaders.vertex())
            .fragmentShader(triangleShaders.fragment())
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .depthTest(true)
            .depthWrite(true)
            .depthCompareOp(VkCompareOp.VK_COMPARE_OP_LESS)
            .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT, 0, 4)
            .build(arena);
        
        // glTF pipeline with vertex input descriptions
        gltfPipeline = VkPipeline.builder()
            .device(device)
            .renderPass(targetRenderPass)
            .viewport(0, 0, width, height)
            .vertexShader(gltfShaders.vertex())
            .fragmentShader(gltfShaders.fragment())
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .depthTest(true)
            .depthWrite(true)
            .depthCompareOp(VkCompareOp.VK_COMPARE_OP_LESS)
            .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT, 0, 4)
            .vertexInput()
                .binding(0, 32, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX) // 3*4 + 3*4 + 2*4 = 32 bytes
                .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 0)  // position
                .attribute(1, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT, 12) // normal
                .attribute(2, 0, VkFormat.VK_FORMAT_R32G32_SFLOAT, 24)    // texcoord
                .binding(1, 64, VkVertexInputRate.VK_VERTEX_INPUT_RATE_INSTANCE) // 16*4 = 64 bytes per matrix
                .attribute(3, 1, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT, 0)  // matrix row 0
                .attribute(4, 1, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT, 16) // matrix row 1
                .attribute(5, 1, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT, 32) // matrix row 2
                .attribute(6, 1, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT, 48) // matrix row 3
                .build()
            .build(arena);
        
        System.out.println("[OK] Graphics pipelines created (triangle + glTF)");
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
        
        // Process main thread work during spare time
        int workProcessed = mainThreadWork.processWork(frameStart);
        if (workProcessed > 0) {
            System.out.println("[WORK] Processed " + workProcessed + " main thread tasks");
        }
        
        Arena frameArena = arena;
        VulkanSyncManager.FrameSync frameSync = syncManager.acquireFrame();
        
        // Acquire image with semaphore
        int imgIdx = VkSwapchainOps.acquireNextImage(device, swapchainManager.swapchain().handle())
            .semaphore(frameSync.imageAvailable.handle())
            .execute(frameArena);
        
        SwapchainImage swapImage = swapchainManager.getImage(imgIdx);
        
        recordCommandBufferThreaded(commandBuffers[frameSync.frameIndex], imgIdx, frameArena);
        
        VkSubmit.builder()
            .waitSemaphore(frameSync.imageAvailable.handle(), VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .commandBuffer(commandBuffers[frameSync.frameIndex])
            .signalSemaphore(frameSync.renderFinished.handle())
            .submit(queue, frameSync.inFlight.handle(), frameArena).check();
        
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
        
        // Multi-threaded path with proper command buffer handling
        VkCommandBuffer.begin(primaryCommandBuffer).execute(frameArena);
        
        if (adaptiveAAEnabled) {
            // Render scene to AA targets
            VkCommandBuffer.beginRenderPass(primaryCommandBuffer, adaptiveAA.getSceneRenderPass().handle(), adaptiveAA.getSceneFramebuffer().handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(primaryCommandBuffer, frameArena);
            VulkanExtensions.cmdEndRenderPass(primaryCommandBuffer);
            
            // Perform adaptive AA and output to swapchain
            adaptiveAA.performAA(primaryCommandBuffer, swapchainManager.framebuffers()[imageIndex], frameArena);
        } else {
            // Direct rendering to swapchain
            VkCommandBuffer.beginRenderPass(primaryCommandBuffer, directRenderPass.handle(), swapchainManager.framebuffers()[imageIndex].handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(primaryCommandBuffer, frameArena);
            VulkanExtensions.cmdEndRenderPass(primaryCommandBuffer);
        }
        
        // Always end the command buffer
        VulkanExtensions.endCommandBuffer(primaryCommandBuffer).check();
    }
    
    private void recordSingleThreaded(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        
        if (adaptiveAAEnabled) {
            // Render scene to AA targets
            VkCommandBuffer.beginRenderPass(commandBuffer, adaptiveAA.getSceneRenderPass().handle(), adaptiveAA.getSceneFramebuffer().handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .clearDepth(1.0f, 0)
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
                .clearDepth(1.0f, 0)
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
        
        // TEST: Manual triangle with simple triangle pipeline (no vertex buffers needed)
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());
        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0);
        
        // Test glTF pipeline with vertex and instance buffers
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, gltfPipeline.handle());
        System.out.println("[DEBUG] Rendering glTF test triangle with vertex buffers");
        
        // Bind both vertex and instance buffers
//        lodRenderer.renderTestTriangle(commandBuffer, frameArena);
//        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0);
        
        // Render LOD models if any exist
        int instanceCount = lodRenderer.getInstanceCount();
        if (instanceCount > 0) {
            System.out.println("[DEBUG] Attempting model rendering with debug logging");
            lodRenderer.renderModels(commandBuffer, cameraPosition, frameArena, gltfPipeline.handle());
        }
        

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
    
    // LOD model management
    public int addLODModel(LODModel model) {
        return lodRenderer.addModel(model);
    }
    
    public int addLODInstance(ModelData modelData) {
        return lodRenderer.addInstance(modelData);
    }
    
    public void removeLODInstance(int instanceId) {
        lodRenderer.removeInstance(instanceId);
    }
    
    public void updateLODInstance(int instanceId, ModelData modelData) {
        lodRenderer.updateInstance(instanceId, modelData);
    }
    
    public void setCameraPosition(float x, float y, float z) {
        cameraPosition[0] = x;
        cameraPosition[1] = y;
        cameraPosition[2] = z;
    }
    
    public int getActiveTriangleCount() {
        return lodRenderer.getActiveTriangleCount(cameraPosition);
    }
    
    /**
     * Load glTF model from file path - can be called from any thread
     * @param filePath Path to .gltf or .glb file
     * @return CompletableFuture that resolves to ModelData when loaded
     */
    public CompletableFuture<ModelData> loadGLTFModel(String filePath) {
        return lodRenderer.loadGLTFModel(filePath);
    }
    
    public MainThreadWorkQueue getMainThreadWorkQueue() {
        return mainThreadWork;
    }
    
    /**
     * Load sample models at adjacent positions for testing
     */
    public void loadSampleModels() {
        System.out.println("[LOAD] Starting to load sample models...");
        
        // Load only Box for debugging
        loadGLTFModel("/sample-models/Box/glTF/Box.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(-5.0f, 0.0f, -2.0f); // Move closer to camera
                transform.setScale(2.0f, 2.0f, 2.0f); // Make it bigger
                int instanceId = addLODInstance(modelData);
                System.out.println("[OK] Box loaded at (-5, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
            })
            .exceptionally(throwable -> {
                System.err.println("[ERROR] Failed to load Suzanne: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
        loadGLTFModel("/sample-models/Duck/glTF/Duck.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(0.0f, 0.0f, -2.0f); // Move closer to camera
                transform.setScale(1.0f, 1.0f, 1.0f); // Make it bigger
                int instanceId = addLODInstance(modelData);
                System.out.println("[OK] Box loaded at (0, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
            })
            .exceptionally(throwable -> {
                System.err.println("[ERROR] Failed to load Suzanne: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
        loadGLTFModel("/sample-models/Suzanne/glTF/Suzanne.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(5.0f, 0.0f, -2.0f); // Move closer to camera
                transform.setScale(2.0f, 2.0f, 2.0f); // Make it bigger
                int instanceId = addLODInstance(modelData);
                System.out.println("[OK] Box loaded at (5, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
            })
            .exceptionally(throwable -> {
                System.err.println("[ERROR] Failed to load Suzanne: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
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
            adaptiveAA = new AdaptiveAA(arena, device, physicalDevice, width, height);
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
        // Wait for device to be idle before cleanup
        if (device != null && !device.equals(MemorySegment.NULL)) {
            Vulkan.deviceWaitIdle(device).check();
            System.out.println("[OK] Device idle - starting renderer cleanup");
        }
        
        // Clean up thread manager
        if (threadManager != null) threadManager.close();
        
        // Clean up LOD renderer first (it has GPU resources)
        if (lodRenderer != null) lodRenderer.cleanup();
        
        // Clean up managers
        if (syncManager != null) syncManager.close();
        if (commandManager != null) commandManager.close();
        if (shaderManager != null) shaderManager.close();
        if (depthTarget != null) depthTarget.close();
        
        // Clean up main resources
        if (pipeline != null) pipeline.close();
        if (gltfPipeline != null) gltfPipeline.close();
        
        // Only destroy render passes if they haven't been destroyed yet
        if (renderPass != null && renderPass != directRenderPass) {
            renderPass.close();
            renderPass = null;
        }
        if (directRenderPass != null) {
            directRenderPass.close();
            directRenderPass = null;
        }
        
        if (adaptiveAA != null) {
            adaptiveAA.cleanup();
        }
        if (swapchainManager != null) swapchainManager.close();
        
        System.out.println("[OK] Renderer cleanup complete");
    }
}