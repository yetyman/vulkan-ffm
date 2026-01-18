package io.github.yetyman.vulkan.sample.complex.threading;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.generated.VkDescriptorBufferInfo;
import io.github.yetyman.vulkan.generated.VkFormatProperties;
import io.github.yetyman.vulkan.generated.VkWriteDescriptorSet;
import io.github.yetyman.vulkan.highlevel.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.sample.complex.culling.Camera;
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
    
    // Camera uniform buffer
    private VkBuffer cameraUniformBuffer;
    private VkDescriptorSetLayout descriptorSetLayout;
    private VkDescriptorPool descriptorPool;
    private VkDescriptorSet descriptorSet;
    
    // Threading
    private ThreadManager threadManager;
    private Mode mode = Mode.ADAPTIVE;
    
    // Rendering
    private RenderGraph renderGraph;
    
    // Performance tracking
    private long lastFrameTime = System.nanoTime();
    private final ArrayDeque<Long> frameTimes = new ArrayDeque<>();
    private final int FRAME_HISTORY = 60;
    
    // Animation timing
    private final long startTime = System.nanoTime();
    
    // Cached layouts to avoid FFM overhead
    private MemorySegment cachedViewport;
    private MemorySegment cachedScissor;
    
    // LOD rendering
    private LODRenderer lodRenderer;
    private Camera camera;
    private MainThreadWorkQueue mainThreadWork;
    
    public ThreadedRenderer(Arena arena, VkDevice device, MemorySegment queue, 
                           MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
        
        // Initialize thread manager
        threadManager = new ThreadManager();
        
        // Initialize LOD renderer with max instances
        // Note: physicalDevice will be set in init()
        lodRenderer = null; // Will be created in init() when physicalDevice is available
    }
    
    @Override
    protected void initializeResources(VkPhysicalDevice physicalDevice, int queueFamilyIndex) {
        this.physicalDevice = physicalDevice;
        
        // Initialize camera
        camera = new Camera();
        camera.setPosition(0.0f, 0.0f, 10.0f);
        camera.setTarget(0.0f, 0.0f, 0.0f);
        camera.setAspectRatio((float)width / (float)height);
        
        // Initialize LOD renderer now that we have physicalDevice
        lodRenderer = new LODRenderer(arena, device, physicalDevice, queue, 10000, 1000);
        mainThreadWork = new MainThreadWorkQueue(60.0);
        lodRenderer.setMainThreadWorkQueue(mainThreadWork);
        
        createManagers(queueFamilyIndex);
        createDepthTarget();
        createDirectRenderPass();
        if (adaptiveAAEnabled) {
            adaptiveAA = new AdaptiveAA(arena, device, physicalDevice, width, height);
        }
        
        // Set Vulkan resources after managers are created
        lodRenderer.setVulkanResources(commandManager.getCommandPool(), directRenderPass);
        createGraphicsPipeline();
        createRenderGraph();
        Logger.info("Threaded renderer initialized with RenderGraph (AA: " + (adaptiveAAEnabled ? "ON" : "OFF") + ")");
    }
    

    
    private VkPhysicalDevice physicalDevice;
    
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
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT.value())
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value())
            .build();
        Logger.info("Depth target created with format: " + depthFormat);
    }
    
    private int findSupportedDepthFormat() {
        // Try common depth formats in order of preference
        int[] candidates = {
            VkFormat.VK_FORMAT_D32_SFLOAT.value(),
            VkFormat.VK_FORMAT_D32_SFLOAT_S8_UINT.value(),
            VkFormat.VK_FORMAT_D24_UNORM_S8_UINT.value(),
            VkFormat.VK_FORMAT_D16_UNORM.value()
        };
        
        try (Arena tempArena = Arena.ofConfined()) {
            for (int format : candidates) {
                VkPhysicalDevice.VkFormatPropertiesWrapper formatProps = physicalDevice.getFormatProperties(format, tempArena);
                if ((formatProps.optimalTilingFeatures() & VkFormatFeatureFlagBits.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT.value()) != 0) {
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
            .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value(), VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value())
            .depthAttachment(depthFormat, VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(), VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_DONT_CARE.value())
            .subpassDependency(~0, 0, 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value() | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value(), 
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value() | VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value(),
                0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value() | VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value())
            .build(arena);
    }
    
    private void createRenderGraph() {
        renderGraph = RenderGraph.builder()
            .resource("colorTarget", RenderGraph.ResourceDesc.color(width, height, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value()))
            .resource("sceneDepth", RenderGraph.ResourceDesc.depth(width, height, findSupportedDepthFormat()))
            .resource("geometryBuffer", RenderGraph.ResourceDesc.buffer(1024 * 1024))
            
            // Main triangle pass
            .graphicsPass("triangle")
                .write("colorTarget", RenderGraph.ResourceUsage.COLOR_ATTACHMENT)
                .write("sceneDepth", RenderGraph.ResourceUsage.DEPTH_ATTACHMENT)
                .execute((cmd, resources, frameArena) -> {
                    Logger.debug("Executing triangle pass");
                    Vulkan.cmdBindPipeline(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.handle());
                    
                    // Push time constant for rotation
                    float elapsedTime = (System.nanoTime() - startTime) / 1_000_000_000.0f;
                    VkPushConstants.floatValue(elapsedTime, 
                        VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), frameArena)
                        .push(cmd, pipeline.layout());
                    
                    Vulkan.cmdSetViewport(cmd, 0, 1, cachedViewport);
                    Vulkan.cmdSetScissor(cmd, 0, 1, cachedScissor);
                    
                    DrawCommand.direct(3, 1).execute(cmd);
                })
            
            // glTF geometry pass
            .graphicsPass("gltfGeometry")
                .read("geometryBuffer", RenderGraph.ResourceUsage.VERTEX_BUFFER)
                .write("colorTarget", RenderGraph.ResourceUsage.COLOR_ATTACHMENT)
                .write("sceneDepth", RenderGraph.ResourceUsage.DEPTH_ATTACHMENT)
                .execute((cmd, resources, frameArena) -> {
                    Logger.debug("Executing glTF geometry pass");
                    
                    // Update camera uniform buffer
                    float[] vpMatrix = camera.getViewProjectionMatrix();
                    try (Arena mapArena = Arena.ofConfined()) {
                        MemorySegment mapped = cameraUniformBuffer.map(mapArena);
                        for (int i = 0; i < 16; i++) {
                            mapped.setAtIndex(ValueLayout.JAVA_FLOAT, i, vpMatrix[i]);
                        }
                        cameraUniformBuffer.unmap();
                    }
                    
                    Vulkan.cmdBindPipeline(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), gltfPipeline.handle());
                    
                    // Bind descriptor set with camera uniform
                    descriptorSet.bind(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), 
                        gltfPipeline.layout(), 0, frameArena);
                    
                    Vulkan.cmdSetViewport(cmd, 0, 1, cachedViewport);
                    Vulkan.cmdSetScissor(cmd, 0, 1, cachedScissor);
                    
                    // Render LOD models if any exist
                    int instanceCount = lodRenderer.getInstanceCount();
                    if (instanceCount > 0) {
                        lodRenderer.updateFrustum(camera.getViewProjectionMatrix());
                        lodRenderer.setFrustumCullingEnabled(true);
                        
                        // Debug: Log camera and frustum info
                        float[] camPos = camera.getPosition();
                        Logger.debug("Camera at (" + camPos[0] + "," + camPos[1] + "," + camPos[2] + ")");
                        Logger.debug("VP Matrix[0-3]: " + vpMatrix[0] + "," + vpMatrix[1] + "," + vpMatrix[2] + "," + vpMatrix[3]);
                        
                        lodRenderer.renderModels(cmd, camera.getPosition(), frameArena, gltfPipeline.handle());
                    }
                })
            
            .build();
        
        Logger.info("RenderGraph created - replaced manual rendering");
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
        
        // Create camera uniform buffer
        cameraUniformBuffer = VkBuffer.builder()
            .device(device)
            .physicalDevice(physicalDevice)
            .size(64) // mat4 = 16 floats * 4 bytes
            .uniformBuffer()
            .hostVisible()
            .build(arena);
        
        // Create descriptor set layout
        descriptorSetLayout = VkDescriptorSetLayout.builder()
            .device(device)
            .uniformBuffer(0, VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value())
            .build(arena);
        
        // Create descriptor pool
        descriptorPool = VkDescriptorPool.builder()
            .device(device)
            .maxSets(1)
            .uniformBuffers(1)
            .build(arena);
        
        // Allocate descriptor set
        descriptorSet = descriptorPool.allocateDescriptorSet(descriptorSetLayout);
        
        // Update descriptor set to point to uniform buffer
        descriptorSet.updateBuffer(0, VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(), 
            cameraUniformBuffer.handle(), 0, cameraUniformBuffer.size(), arena);
        
        // Use appropriate render pass based on AA setting
        MemorySegment targetRenderPass = adaptiveAAEnabled ? 
            adaptiveAA.getSceneRenderPass().handle() : directRenderPass.handle();
        
        // Original triangle pipeline
        pipeline = VkPipeline.builder()
            .device(device)
            .renderPass(targetRenderPass)
            .vertexShader(triangleShaders.vertex())
            .fragmentShader(triangleShaders.fragment())
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .depthTest(true)
            .depthWrite(true)
            .depthCompareOp(VkCompareOp.VK_COMPARE_OP_LESS.value())
            .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 4)
            .build(arena);
        
        // glTF pipeline with vertex input descriptions and descriptor set layout
        gltfPipeline = VkPipeline.builder()
            .device(device)
            .renderPass(targetRenderPass)
            .vertexShader(gltfShaders.vertex())
            .fragmentShader(gltfShaders.fragment())
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .depthTest(true)
            .depthWrite(true)
            .depthCompareOp(VkCompareOp.VK_COMPARE_OP_LESS.value())
            .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, 4)
            .descriptorSetLayouts(descriptorSetLayout.handle())
            .vertexInput()
                .binding(0, 32, VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX.value()) // 3*4 + 3*4 + 2*4 = 32 bytes
                .attribute(0, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT.value(), 0)  // position
                .attribute(1, 0, VkFormat.VK_FORMAT_R32G32B32_SFLOAT.value(), 12) // normal
                .attribute(2, 0, VkFormat.VK_FORMAT_R32G32_SFLOAT.value(), 24)    // texcoord
                .binding(1, 64, VkVertexInputRate.VK_VERTEX_INPUT_RATE_INSTANCE.value()) // 16*4 = 64 bytes per matrix
                .attribute(3, 1, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT.value(), 0)  // matrix row 0
                .attribute(4, 1, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT.value(), 16) // matrix row 1
                .attribute(5, 1, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT.value(), 32) // matrix row 2
                .attribute(6, 1, VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT.value(), 48) // matrix row 3
                .build()
            .build(arena);
        
        Logger.info("Graphics pipelines created (triangle + glTF with camera UBO)");
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
            Logger.debug("Processed " + workProcessed + " main thread tasks");
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
                .clearColor(0.1f, 0.1f, 0.15f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            Vulkan.cmdEndRenderPass(commandBuffer);
            
            adaptiveAA.performAA(commandBuffer, framebuffers[imageIndex], frameArena);
        } else {
            VkCommandBuffer.beginRenderPass(commandBuffer, directRenderPass.handle(), framebuffers[imageIndex].handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.1f, 0.1f, 0.15f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            Vulkan.cmdEndRenderPass(commandBuffer);
        }
        
        Vulkan.endCommandBuffer(commandBuffer).check();
    }
    
    private void recordMultiThreaded(MemorySegment commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        
        if (adaptiveAAEnabled) {
            VkCommandBuffer.beginRenderPass(commandBuffer, adaptiveAA.getSceneRenderPass().handle(), adaptiveAA.getSceneFramebuffer().handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.1f, 0.1f, 0.15f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            Vulkan.cmdEndRenderPass(commandBuffer);
            
            adaptiveAA.performAA(commandBuffer, framebuffers[imageIndex], frameArena);
        } else {
            VkCommandBuffer.beginRenderPass(commandBuffer, directRenderPass.handle(), framebuffers[imageIndex].handle())
                .renderArea(0, 0, width, height)
                .clearColor(0.1f, 0.1f, 0.15f, 1.0f)
                .clearDepth(1.0f, 0)
                .execute(frameArena);
            
            renderScene(commandBuffer, frameArena);
            Vulkan.cmdEndRenderPass(commandBuffer);
        }
        
        Vulkan.endCommandBuffer(commandBuffer).check();
    }
    
    private void renderScene(MemorySegment commandBuffer, Arena frameArena) {
        // EXECUTE RENDER GRAPH - replaces all manual rendering
        renderGraph.execute(commandBuffer, frameArena);
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
        Logger.info("Adaptive AA " + (enabled ? "enabled" : "disabled"));
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
        camera.setPosition(x, y, z);
    }
    
    public void setCameraTarget(float x, float y, float z) {
        camera.setTarget(x, y, z);
    }
    
    public int getActiveTriangleCount() {
        return lodRenderer.getActiveTriangleCount(camera.getPosition());
    }
    
    public int getCulledInstanceCount() {
        return lodRenderer.getCulledCount();
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
        Logger.info("Starting to load sample models...");
        
        // Load only Box for debugging
        loadGLTFModel("/sample-models/Box/glTF/Box.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(-5.0f, 0.0f, 0.0f);
                transform.setScale(2.0f, 2.0f, 2.0f);
                int instanceId = addLODInstance(modelData);
                Logger.info("Box loaded at (-5, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
            })
            .exceptionally(throwable -> {
                Logger.error("Failed to load Box: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
        loadGLTFModel("/sample-models/Duck/glTF/Duck.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(0.0f, 0.0f, 0.0f);
                transform.setScale(1.0f, 1.0f, 1.0f);
                int instanceId = addLODInstance(modelData);
                Logger.info("Duck loaded at (0, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
            })
            .exceptionally(throwable -> {
                Logger.error("Failed to load Duck: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
        loadGLTFModel("/sample-models/Suzanne/glTF/Suzanne.gltf")
            .thenAcceptAsync(modelData -> {
                TransformationMatrix transform = modelData.getTransform();
                transform.setPosition(5.0f, 0.0f, 0.0f);
                transform.setScale(2.0f, 2.0f, 2.0f);
                int instanceId = addLODInstance(modelData);
                Logger.info("Suzanne loaded at (5, 0, 0), instanceId: " + instanceId + ", total instances: " + lodRenderer.getInstanceCount());
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
        
        // Update camera aspect ratio
        camera.setAspectRatio((float)width / (float)height);
        
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
        
        // Clean up descriptor resources
        if (descriptorPool != null) descriptorPool.close();
        if (descriptorSetLayout != null) descriptorSetLayout.close();
        if (cameraUniformBuffer != null) cameraUniformBuffer.close();
        
        if (directRenderPass != null) {
            directRenderPass.close();
        }
        
        if (adaptiveAA != null) {
            adaptiveAA.cleanup();
        }
        
        // Clean up render graph
        if (renderGraph != null) renderGraph.close();
        
        Logger.info("Threaded renderer cleanup complete");
    }
    
    public void cleanup() {
        close();
    }
}