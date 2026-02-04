package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.sample.complex.culling.FrustumCuller;
import io.github.yetyman.vulkan.sample.complex.threading.MainThreadWorkQueue;
import io.github.yetyman.vulkan.sample.complex.debug.LODVisualizer;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;

/**
 * Handles batch rendering with LOD selection and frustum culling
 */
public class BatchRenderer {
    private final List<StaticBatch> staticBatches = new ArrayList<>();
    private final BatchState batchState;
    private final VkDevice device;
    private VkCommandPool commandPool;
    private VkRenderPass renderPass;
    private MainThreadWorkQueue mainThreadWorkQueue;
    private final FrustumCuller frustumCuller = new FrustumCuller();
    private boolean frustumCullingEnabled = true;
    private int culledCount = 0;
    private LODVisualizer lodVisualizer;
    
    public BatchRenderer(VkDevice device, int maxInstances) {
        this.device = device;
        this.batchState = new BatchState(maxInstances);
    }
    
    public void setVulkanResources(VkCommandPool commandPool, VkRenderPass renderPass) {
        this.commandPool = commandPool;
        this.renderPass = renderPass;
    }
    
    public void setMainThreadWorkQueue(MainThreadWorkQueue workQueue) {
        this.mainThreadWorkQueue = workQueue;
    }
    
    public void setLODVisualizer(LODVisualizer visualizer) {
        this.lodVisualizer = visualizer;
    }
    
    public void setFrustumCullingEnabled(boolean enabled) {
        this.frustumCullingEnabled = enabled;
    }
    
    public void updateFrustum(float[] viewProjMatrix) {
        frustumCuller.updateFromMatrix(viewProjMatrix);
    }
    
    public void renderModels(MemorySegment commandBuffer, float[] cameraPosition, Arena frameArena, 
                            MemorySegment gltfPipeline, MemorySegment pipelineLayout, InstanceData instanceData, ModelData[] modelDataArray) {
        Vulkan.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), gltfPipeline);
        Logger.debug("BatchRenderer.renderModels called with " + instanceData.getCount() + " instances");
        
        // Sync matrices first
        for (int i = 0; i < instanceData.getCount(); i++) {
            if (instanceData.isActive(i)) {
                instanceData.syncMatrixFromModelData(i, modelDataArray);
            }
        }
        
        // Dynamic LOD selection per instance
        culledCount = 0;
        int rendered = 0;
        for (int i = 0; i < instanceData.getCount(); i++) {
            if (!instanceData.isActive(i)) continue;
            
            ModelData modelData = instanceData.getModelData(i, modelDataArray);
            if (modelData == null || !modelData.isLoaded() || !modelData.isGPUResident()) {
                Logger.debug("Skipping instance " + i + ": loaded=" + (modelData != null && modelData.isLoaded()) + 
                           ", gpuResident=" + (modelData != null && modelData.isGPUResident()));
                continue;
            }
            
            // Calculate distance and select LOD with blend
            float[] pos = modelData.getTransform().getPosition();
            float distance = calculateDistance(cameraPosition, pos);
            LODModel.LODSelection selection = modelData.getLodModel().selectLODWithBlend(distance);
            LODLevel selectedLOD = selection.level();
            float blendFactor = selection.blendFactor();
            
            // Frustum culling
            boolean visible = true;
            if (frustumCullingEnabled) {
                float[] scale = modelData.getTransform().getScale();
                float maxScale = Math.max(Math.max(scale[0], scale[1]), scale[2]);
                float radius = modelData.getBoundingRadius() * maxScale;
                visible = frustumCuller.testSphere(pos[0], pos[1], pos[2], radius);
                if (!visible) {
                    culledCount++;
                    continue;
                }
            }
            
            // Render this instance with selected LOD
            if (selectedLOD.hasGPUBuffers()) {
                int lodIndex = modelData.getLodModel().getLODIndex(selectedLOD);
                if (lodIndex == -1) {
                    Logger.error("Failed to find LOD index for selected LOD!");
                    lodIndex = 0;
                }
                Logger.debug("Rendering instance " + i + " with LOD " + lodIndex + " (distance: " + String.format("%.1f", distance) + ")");
                renderInstance(commandBuffer, selectedLOD, i, blendFactor, pipelineLayout, frameArena, instanceData, lodIndex);
                rendered++;
            }
        }
        
        Logger.debug("Rendered " + rendered + " instances, culled " + culledCount);
    }
    
    private void renderInstance(MemorySegment commandBuffer, LODLevel lodLevel, int instanceId,
                               float blendFactor, MemorySegment pipelineLayout, Arena frameArena, InstanceData instanceData, int lodIndex) {
        BufferHandle vertexHandle = lodLevel.getVertexBufferHandle();
        BufferHandle indexHandle = lodLevel.getIndexBufferHandle();
        
        if (vertexHandle == null || indexHandle == null || !vertexHandle.isReady() || !indexHandle.isReady()) {
            return;
        }
        
        // Push constants for visualization
        if (lodVisualizer != null) {
            try (Arena pushArena = Arena.ofConfined()) {
                MemorySegment pushData = pushArena.allocate(16); // 4 ints
                int vizMode = lodVisualizer.isColorCodingEnabled() ? 2 : 
                             (lodVisualizer.isSplitScreenEnabled() ? 3 : 0);
                pushData.setAtIndex(ValueLayout.JAVA_INT, 0, vizMode);
                pushData.setAtIndex(ValueLayout.JAVA_INT, 1, lodIndex);
                pushData.setAtIndex(ValueLayout.JAVA_FLOAT, 2, lodVisualizer.getSplitScreenOffset(lodIndex));
                pushData.setAtIndex(ValueLayout.JAVA_INT, 3, 0); // padding
                
                Vulkan.cmdPushConstants(commandBuffer, pipelineLayout, 
                    VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value() | VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value(),
                    0, 16, pushData);
            }
        }
        
        VkVertexBufferBinding.create()
            .buffer(vertexHandle.handle())
            .bind(commandBuffer, 0, frameArena);
        
        VkVertexBufferBinding.create()
            .buffer(instanceData.getMatricesBuffer())
            .bind(commandBuffer, 1, frameArena);
        
        Vulkan.cmdBindIndexBuffer(commandBuffer, indexHandle.handle(), 0, VkIndexType.VK_INDEX_TYPE_UINT32.value());
        
        Vulkan.cmdDrawIndexed(commandBuffer, lodLevel.indexCount(), 1, 0, 0, instanceId);
    }
    
    private void renderBatchDirectly(MemorySegment commandBuffer, StaticBatch batch, Arena frameArena, InstanceData instanceData) {
        LODLevel lodLevel = batch.getLodLevel();
        
        if (!lodLevel.hasGPUBuffers()) {
            return;
        }
        
        BufferHandle vertexHandle = lodLevel.getVertexBufferHandle();
        BufferHandle indexHandle = lodLevel.getIndexBufferHandle();
        
        if (vertexHandle == null || indexHandle == null || !vertexHandle.isReady() || !indexHandle.isReady()) {
            return;
        }
        
        MemorySegment encodedVertexBuffer = vertexHandle.handle();
        MemorySegment encodedIndexBuffer = indexHandle.handle();
        
        VkVertexBufferBinding.create()
            .buffer(MemorySegment.ofAddress(encodedVertexBuffer.address()))
            .bind(commandBuffer, 0, frameArena);
        
        MemorySegment matrixBuffer = instanceData.getMatricesBuffer();
        
        VkVertexBufferBinding.create()
            .buffer(matrixBuffer)
            .bind(commandBuffer, 1, frameArena);
        
        Vulkan.cmdBindIndexBuffer(commandBuffer, encodedIndexBuffer, 0, VkIndexType.VK_INDEX_TYPE_UINT32.value());
        
        int enabledCount = getEnabledInstanceCount(batch);
        if (enabledCount > 0) {
            int[] batchInstances = batch.getInstanceIds();
            int[] enabledInstances = batchState.getEnabledInstances();
            int firstInstanceId = -1;
            for (int batchInstance : batchInstances) {
                for (int j = 0; j < batchState.getEnabledCount(); j++) {
                    if (enabledInstances[j] == batchInstance) {
                        firstInstanceId = batchInstance;
                        break;
                    }
                }
                if (firstInstanceId != -1) break;
            }
            
            Vulkan.cmdDrawIndexed(commandBuffer, lodLevel.indexCount(), enabledCount, 0, 0, firstInstanceId);
        }
    }
    

    
    private void batchValidateAndUpload(float[] cameraPosition, InstanceData instanceData, ModelData[] modelDataArray) {
        culledCount = 0;
        int gpuResidentCount = 0;
        for (int i = 0; i < instanceData.getCount(); i++) {
            if (instanceData.isActive(i)) {
                ModelData modelData = instanceData.getModelData(i, modelDataArray);
                boolean gpuResident = modelData != null && modelData.isGPUResident();
                if (gpuResident) gpuResidentCount++;
                boolean visible = true;
                
                if (gpuResident && frustumCullingEnabled && modelData.isLoaded()) {
                    float[] pos = modelData.getTransform().getPosition();
                    float[] scale = modelData.getTransform().getScale();
                    float maxScale = Math.max(Math.max(scale[0], scale[1]), scale[2]);
                    float radius = modelData.getBoundingRadius() * maxScale;
                    visible = frustumCuller.testSphere(pos[0], pos[1], pos[2], radius);
                    if (!visible) {
                        culledCount++;
                    }
                }
                
                boolean enabled = gpuResident && visible;
                batchState.markInstanceEnabled(i, enabled);
                if (enabled) {
                    batchState.markMatrixDirty(i);
                }
            }
        }
        
        if (gpuResidentCount == 0) {
            Logger.info("WARNING: No GPU resident models found!");
        }
        
        batchState.buildBatches();
        
        // Compact matrices: copy enabled instances to consecutive buffer positions
        int[] enabledInstances = batchState.getEnabledInstances();
        int enabledCount = batchState.getEnabledCount();
        
        for (int compactIndex = 0; compactIndex < enabledCount; compactIndex++) {
            int originalInstanceId = enabledInstances[compactIndex];
            instanceData.syncMatrixFromModelData(originalInstanceId, modelDataArray);
            
            // Copy to compact position
            if (originalInstanceId != compactIndex) {
                instanceData.copyMatrix(originalInstanceId, compactIndex);
            }
        }
    }
    
    private boolean hasEnabledInstancesInBatch(StaticBatch batch) {
        int[] batchInstances = batch.getInstanceIds();
        int[] enabledInstances = batchState.getEnabledInstances();
        
        for (int batchInstance : batchInstances) {
            for (int j = 0; j < batchState.getEnabledCount(); j++) {
                if (enabledInstances[j] == batchInstance) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private int getEnabledInstanceCount(StaticBatch batch) {
        int count = 0;
        int[] batchInstances = batch.getInstanceIds();
        int[] enabledInstances = batchState.getEnabledInstances();
        
        for (int batchInstance : batchInstances) {
            for (int j = 0; j < batchState.getEnabledCount(); j++) {
                if (enabledInstances[j] == batchInstance) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }
    
    public void addStaticBatch(LODLevel lodLevel, MemorySegment preRecordedCommandBuffer, int[] instanceIds) {
        staticBatches.add(new StaticBatch(lodLevel, preRecordedCommandBuffer, instanceIds));
    }
    
    public void createStaticBatchesForModel(ModelData modelData, int instanceId) {
        if (!modelData.isLoaded()) return;
        
        LODModel lodModel = modelData.getLodModel();
        for (int i = 0; i < lodModel.getLODCount(); i++) {
            LODLevel lodLevel = lodModel.getLOD(i);
            
            // Find existing batch for this LODLevel
            StaticBatch existingBatch = staticBatches.stream()
                .filter(batch -> batch.getLodLevel() == lodLevel)
                .findFirst()
                .orElse(null);
            
            if (existingBatch != null) {
                // Add instance to existing batch
                int[] oldIds = existingBatch.getInstanceIds();
                int[] newIds = Arrays.copyOf(oldIds, oldIds.length + 1);
                newIds[oldIds.length] = instanceId;
                staticBatches.remove(existingBatch);
                addStaticBatch(lodLevel, existingBatch.getCommandBuffer(), newIds);
                Logger.debug("Added instance " + instanceId + " to existing batch for LOD level " + i);
            } else {
                // Create new batch
                addStaticBatch(lodLevel, MemorySegment.NULL, new int[]{instanceId});
                Logger.debug("Created new batch for LOD level " + i + " with instance " + instanceId);
            }
        }
    }
    
    private MemorySegment createCommandBufferForLOD(LODLevel lodLevel) {
        Logger.debug("Skipping command buffer creation, using direct rendering");
        return MemorySegment.NULL;
    }
    
    public void clearStaticBatches() {
        staticBatches.clear();
    }
    
    public int getActiveTriangleCount(float[] cameraPosition, InstanceData instanceData, ModelData[] modelDataArray) {
        int total = 0;
        float[] position = new float[3];
        
        for (int i = 0; i < instanceData.getCount(); i++) {
            if (!instanceData.isActive(i)) continue;
            
            ModelData modelData = instanceData.getModelData(i, modelDataArray);
            if (modelData != null && modelData.isLoaded()) {
                instanceData.getPosition(i, position, modelDataArray);
                float distance = calculateDistance(cameraPosition, position);
                LODLevel selectedLOD = modelData.getLodModel().selectLOD(distance);
                total += selectedLOD.triangleCount();
            }
        }
        return total;
    }
    
    private float calculateDistance(float[] cameraPos, float[] objectPos) {
        float dx = cameraPos[0] - objectPos[0];
        float dy = cameraPos[1] - objectPos[1];
        float dz = cameraPos[2] - objectPos[2];
        return (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
    
    public void cleanup() {
        Logger.info("BatchRenderer cleanup complete");
    }
    
    public int getCulledCount() {
        return culledCount;
    }
}
