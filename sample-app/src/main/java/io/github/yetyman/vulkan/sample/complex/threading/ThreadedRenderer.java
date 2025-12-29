package io.github.yetyman.vulkan.sample.complex.threading;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.generated.VkFormatProperties;
import io.github.yetyman.vulkan.highlevel.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.sample.complex.postprocessing.AdaptiveAA;
import io.github.yetyman.vulkan.sample.complex.models.*;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ThreadedRenderer extends BaseRenderer {
    public enum Mode { BEST_EFFICIENCY, BEST_PERFORMANCE, ADAPTIVE }
    
    private static final int TRIANGLES_COUNT = 1000;
    
    // AA toggle
    private boolean adaptiveAAEnabled = true;
    private AdaptiveAA adaptiveAA;
    
    private VkRenderPass directRenderPass;
    private VkPipeline pipeline;
    private VkPipeline gltfPipeline;
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
        super(arena, device, queue, surface, width, height, 3);
        
        // Initialize thread manager
        threadManager = new ThreadManager();
        
        // Initialize LOD renderer with max instances
        // Note: physicalDevice will be set in init()
        lodRenderer = null; // Will be created in init() when physicalDevice is available
    }
    
    @Override
    protected void initializeResources(MemorySegment physicalDevice, int queueFamilyIndex) {
        this.physicalDevice = physicalDevice;
        
        // Initialize LOD renderer now that we have physicalDevice
        lodRenderer = new LODRenderer(arena, device, physicalDevice, 10000, 1000);
        mainThreadWork = new MainThreadWorkQueue(60.0); // Target 60 FPS
        lodRenderer.setMainThreadWorkQueue(mainThreadWork);
        
        createManagers(queueFamilyIndex);
        createDepthTarget();
        createDirectRenderPass();
        if (adaptiveAAEnabled) {
            adaptiveAA = new AdaptiveAA(arena, device, physicalDevice, width, height);
        }
        
        // Set Vulkan resources after managers are created
        lodRenderer.setVulkanResources(commandManager.getCommandPool(), directRenderPass.handle());
        createGraphicsPipeline();
        Logger.info("Threaded renderer initialized with " + TRIANGLES_COUNT + " triangles (AA: " + (adaptiveAAEnabled ? "ON" : "OFF") + ")");
    }
    

    
    private MemorySegment physicalDevice;
    
    private void createManagers(int queueFamilyIndex) {
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
        
        Logger.info("Managers created");
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
        Logger.info("Depth target created with format: " + depthFormat);
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
    
    @Override
    protected VkRenderPass createRenderPassImpl() {
        int depthFormat = findSupportedDepthFormat();
        return VkRenderPass.builder()
            .device(device)
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE)
            .depthAttachment(depthFormat, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR, VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
            .build(arena);
    }
    
    private void createDirectRenderPass() {
        directRenderPass = createRenderPassImpl();
        Logger.info("Direct render pass created");
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
        
        Logger.info("Graphics pipelines created (triangle + glTF)");
    }
    
    @Override
    protected VkFramebuffer createFramebufferImpl(int imageIndex) {
        return VkFramebuffer.builder()
            .device(device)
            .renderPass(renderPass.handle())
            .attachment(new VkFramebufferAttachment(swapchainImageViews[imageIndex], VkFramebufferAttachment.AttachmentType.COLOR, 0, 0))
            .attachment(new VkFramebufferAttachment(depthTarget.imageView(), VkFramebufferAttachment.AttachmentType.DEPTH, 0, 1))
            .dimensions(width, height)
            .build(arena);
    }
    
    @Override
    protected void postRenderPassInit() {
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
            Logger.work("Processed " + workProcessed + " main thread tasks");
        }
        
        // Use BaseRenderer's drawFrame implementation
        super.drawFrame();
        
        // Track performance and adjust threads
        trackPerformance(frameStart);
        adjustThreadCount();
    }
    
    @Override
    protected void recordCommandBuffer(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        int threadsToUse = threadManager.getActiveThreads();
        
        if (threadsToUse == 1) {
            recordSingleThreaded(commandBuffer, imageIndex, frameArena);
            return;
        }
        
        // Multi-threaded path
        recordMultiThreaded(commandBuffer, imageIndex, frameArena);
    }
    
    private void recordSingleThreaded(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        
        if (adaptiveAAEnabled) {
            VkCommandBuffer.beginRenderPass(commandBuffer, adaptiveAA.getSceneRenderPass().handle(), adaptiveAA.getSceneFramebuffer().handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            VulkanExtensions.cmdEndRenderPass(commandBuffer);
            
            adaptiveAA.performAA(commandBuffer, framebuffers[imageIndex], frameArena);
        } else {
            VkCommandBuffer.beginRenderPass(commandBuffer, directRenderPass.handle(), framebuffers[imageIndex].handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            VulkanExtensions.cmdEndRenderPass(commandBuffer);
        }
        
        VulkanExtensions.endCommandBuffer(commandBuffer).check();
    }
    
    private void recordMultiThreaded(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        
        if (adaptiveAAEnabled) {
            VkCommandBuffer.beginRenderPass(commandBuffer, adaptiveAA.getSceneRenderPass().handle(), adaptiveAA.getSceneFramebuffer().handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            VulkanExtensions.cmdEndRenderPass(commandBuffer);
            
            adaptiveAA.performAA(commandBuffer, framebuffers[imageIndex], frameArena);
        } else {
            VkCommandBuffer.beginRenderPass(commandBuffer, directRenderPass.handle(), framebuffers[imageIndex].handle())
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
        Logger.debug("Rendering glTF test triangle with vertex buffers");
        
        // Bind both vertex and instance buffers
//        lodRenderer.renderTestTriangle(commandBuffer, frameArena);
//        VulkanExtensions.cmdDraw(commandBuffer, 3, 1, 0, 0);
        
        // Render LOD models if any exist
        int instanceCount = lodRenderer.getInstanceCount();
        if (instanceCount > 0) {
            Logger.debug("Attempting model rendering with debug logging");
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
        Logger.aa("Adaptive AA " + (enabled ? "enabled" : "disabled"));
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
        Logger.load("Starting to load sample models...");
        
        // Load only Box for debugging
        loadGLTFModel("/sample-models/Box/glTF/Box.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(-5.0f, 0.0f, -2.0f); // Move closer to camera
                transform.setScale(2.0f, 2.0f, 2.0f); // Make it bigger
                int instanceId = addLODInstance(modelData);
                Logger.load("Box loaded at (-5, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
            })
            .exceptionally(throwable -> {
                Logger.error("Failed to load Box: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
        loadGLTFModel("/sample-models/Duck/glTF/Duck.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(0.0f, 0.0f, -2.0f); // Move closer to camera
                transform.setScale(1.0f, 1.0f, 1.0f); // Make it bigger
                int instanceId = addLODInstance(modelData);
                Logger.load("Duck loaded at (0, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
            })
            .exceptionally(throwable -> {
                Logger.error("Failed to load Duck: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
        loadGLTFModel("/sample-models/Suzanne/glTF/Suzanne.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(5.0f, 0.0f, -2.0f); // Move closer to camera
                transform.setScale(2.0f, 2.0f, 2.0f); // Make it bigger
                int instanceId = addLODInstance(modelData);
                Logger.load("Suzanne loaded at (5, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
            })
            .exceptionally(throwable -> {
                Logger.error("Failed to load Suzanne: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
    }
    
    @Override
    protected void onResize(int width, int height) {
        // Update dimensions first
        this.width = width;
        this.height = height;
        
        // Clean up depth target
        if (depthTarget != null) {
            depthTarget.close();
        }
        
        // Recreate depth target with new dimensions
        createDepthTarget();
        if (adaptiveAA != null) {
            adaptiveAA.cleanup();
            adaptiveAA = new AdaptiveAA(arena, device, physicalDevice, width, height);
        }
        
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
        
        Logger.info("Depth buffer recreated for resize");
    }
    
    @Override
    protected void cleanupResources() {
        // Clean up thread manager
        if (threadManager != null) threadManager.close();
        
        // Clean up LOD renderer first (it has GPU resources)
        if (lodRenderer != null) lodRenderer.cleanup();
        
        // Clean up managers
        if (commandManager != null) commandManager.close();
        if (shaderManager != null) shaderManager.close();
        if (depthTarget != null) depthTarget.close();
        
        // Clean up main resources
        if (pipeline != null) pipeline.close();
        if (gltfPipeline != null) gltfPipeline.close();
        
        if (directRenderPass != null) {
            directRenderPass.close();
        }
        
        if (adaptiveAA != null) {
            adaptiveAA.cleanup();
        }
        
        Logger.info("Threaded renderer cleanup complete");
    }
    
    public void cleanup() {
        close();
    }
}