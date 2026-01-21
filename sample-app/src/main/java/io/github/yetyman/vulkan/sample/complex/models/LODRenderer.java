package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.sample.complex.threading.MainThreadWorkQueue;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance LOD renderer - delegates to specialized components
 */
public class LODRenderer {
    private final ModelRegistry modelRegistry;
    private final GeometryManager geometryManager;
    private final BatchRenderer batchRenderer;
    private final VkDevice device;
    private float[] cameraPosition = {0.0f, 0.0f, 5.0f};
    
    public LODRenderer(Arena arena, VkDevice device, VkPhysicalDevice physicalDevice, MemorySegment queue, int maxInstances, int maxModelData) {
        this.device = device;
        this.modelRegistry = new ModelRegistry(arena, maxInstances, maxModelData, device, physicalDevice);
        this.geometryManager = new GeometryManager(arena, device, physicalDevice, queue);
        this.batchRenderer = new BatchRenderer(device, maxInstances);
    }
    
    public void renderModels(MemorySegment commandBuffer, float[] cameraPosition, Arena frameArena, MemorySegment gltfPipeline, MemorySegment pipelineLayout) {
        geometryManager.updateStreaming(modelRegistry.getModelDataArray(), cameraPosition);
        geometryManager.processPendingCopies(2, modelRegistry.getModelDataArray());
        batchRenderer.renderModels(commandBuffer, cameraPosition, frameArena, gltfPipeline, pipelineLayout,
                                  modelRegistry.getInstanceData(), modelRegistry.getModelDataArray());
    }
    
    public int addModel(LODModel model) {
        return modelRegistry.addModel(model);
    }
    
    public int addInstance(ModelData modelData) {
        int instanceId = modelRegistry.addInstance(modelData);
        
        // Mark as GPU resident immediately since geometry is in LODLevel
        if (modelData.isLoaded()) {
            modelData.setGPUResident(true);
            Logger.info("Marked instance " + instanceId + " as GPU resident");
        }
        
        batchRenderer.createStaticBatchesForModel(modelData, instanceId);
        return instanceId;
    }
    
    public void removeInstance(int instanceId) {
        modelRegistry.removeInstance(instanceId);
    }
    
    public void updateInstance(int instanceId, ModelData newModelData) {
        modelRegistry.updateInstance(instanceId, newModelData);
    }
    
    public int getInstanceCount() {
        return modelRegistry.getInstanceCount();
    }
    
    public void setMainThreadWorkQueue(MainThreadWorkQueue workQueue) {
        batchRenderer.setMainThreadWorkQueue(workQueue);
    }
    
    public void setVulkanResources(VkCommandPool commandPool, VkRenderPass renderPass) {
        batchRenderer.setVulkanResources(commandPool, renderPass);
    }
    
    public void setCameraPosition(float x, float y, float z) {
        cameraPosition[0] = x;
        cameraPosition[1] = y;
        cameraPosition[2] = z;
    }
    
    public void updateFrustum(float[] viewProjMatrix) {
        batchRenderer.updateFrustum(viewProjMatrix);
    }
    
    public void setFrustumCullingEnabled(boolean enabled) {
        batchRenderer.setFrustumCullingEnabled(enabled);
    }
    
    public int getCulledCount() {
        return batchRenderer.getCulledCount();
    }
    
    public int getActiveTriangleCount(float[] cameraPosition) {
        return batchRenderer.getActiveTriangleCount(cameraPosition, modelRegistry.getInstanceData(), 
                                                   modelRegistry.getModelDataArray());
    }
    
    public CompletableFuture<ModelData> loadGLTFModel(String filePath) {
        return modelRegistry.loadGLTFModel(filePath);
    }
    
    public void addStaticBatch(LODLevel lodLevel, MemorySegment preRecordedCommandBuffer, int[] instanceIds) {
        batchRenderer.addStaticBatch(lodLevel, preRecordedCommandBuffer, instanceIds);
    }
    
    public void clearStaticBatches() {
        batchRenderer.clearStaticBatches();
    }
    
    public void renderTestTriangle(MemorySegment commandBuffer, Arena frameArena) {
        MemorySegment vertexBuffer = geometryManager.getStaticVertexBuffer();
        MemorySegment instanceBuffer = geometryManager.getStaticInstanceBuffer();
        
        if (!vertexBuffer.equals(MemorySegment.NULL) && !instanceBuffer.equals(MemorySegment.NULL)) {
            VkVertexBufferBinding.create()
                .buffer(vertexBuffer)
                .buffer(instanceBuffer)
                .bind(commandBuffer, 0, frameArena);
            
            Vulkan.cmdDraw(commandBuffer, 3, 1, 0, 0);
            Logger.debug("Drew glTF test triangle with buffers");
        }
    }
    
    public void bindInstanceBufferOnly(MemorySegment commandBuffer, Arena frameArena) {
        MemorySegment instanceBuffer = geometryManager.getStaticInstanceBuffer();
        
        if (!instanceBuffer.equals(MemorySegment.NULL)) {
            VkVertexBufferBinding.create()
                .buffer(instanceBuffer)
                .bind(commandBuffer, 1, frameArena);
            Logger.debug("Bound instance buffer at binding 1");
        }
    }
    
    public void cleanup() {
        if (device != null && !device.handle().equals(MemorySegment.NULL)) {
            Vulkan.deviceWaitIdle(device.handle()).check();
        }
        
        geometryManager.cleanup();
        modelRegistry.cleanup();
        batchRenderer.cleanup();
        
        Logger.info("LODRenderer cleanup complete");
    }
    
    public void shutdown() {
        cleanup();
    }
}
