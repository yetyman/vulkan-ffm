package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.sample.complex.culling.FrustumCuller;
import io.github.yetyman.vulkan.sample.complex.threading.MainThreadWorkQueue;
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
    
    public void setFrustumCullingEnabled(boolean enabled) {
        this.frustumCullingEnabled = enabled;
    }
    
    public void updateFrustum(float[] viewProjMatrix) {
        frustumCuller.updateFromMatrix(viewProjMatrix);
    }
    
    public void renderModels(MemorySegment commandBuffer, float[] cameraPosition, Arena frameArena, 
                            MemorySegment gltfPipeline, InstanceData instanceData, ModelData[] modelDataArray) {
        Vulkan.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), gltfPipeline);
        Logger.debug("Using glTF pipeline: " + gltfPipeline);
        
        if (staticBatches.isEmpty()) return;
        
        batchValidateAndUpload(cameraPosition, instanceData, modelDataArray);
        
        int totalTriangles = 0;
        for (StaticBatch batch : staticBatches) {
            boolean hasEnabled = hasEnabledInstancesInBatch(batch);
            boolean hasBuffers = batch.getLodLevel().hasGPUBuffers();
            
            if (hasEnabled && hasBuffers) {
                if (!batch.getCommandBuffer().equals(MemorySegment.NULL)) {
                    Vulkan.cmdExecuteCommands(commandBuffer, 1, batch.getCommandBuffer());
                } else {
                    renderBatchDirectly(commandBuffer, batch, frameArena, instanceData);
                }
                totalTriangles += batch.getLodLevel().triangleCount() * getEnabledInstanceCount(batch);
            }
        }
        
        Logger.debug("Executed batches, " + totalTriangles + " triangles");
    }
    
    private void renderBatchDirectly(MemorySegment commandBuffer, StaticBatch batch, Arena frameArena, InstanceData instanceData) {
        LODLevel lodLevel = batch.getLodLevel();
        
        if (!lodLevel.hasGPUBuffers()) {
            Logger.debug("LODLevel has no GPU buffers, skipping");
            return;
        }
        
        BufferHandle vertexHandle = lodLevel.getVertexBufferHandle();
        BufferHandle indexHandle = lodLevel.getIndexBufferHandle();
        
        Logger.debug("Got BufferHandles - vertex id=" + (vertexHandle != null ? vertexHandle.getHandleId() : "null") + ", index id=" + (indexHandle != null ? indexHandle.getHandleId() : "null"));
        
        if (vertexHandle == null || indexHandle == null || !vertexHandle.isReady() || !indexHandle.isReady()) {
            Logger.debug("Buffers not ready - vertex ready=" + (vertexHandle != null ? vertexHandle.isReady() : "null") + ", index ready=" + (indexHandle != null ? indexHandle.isReady() : "null"));
            return;
        }
        
        Logger.debug("Getting handles from BufferHandle objects:");
        MemorySegment encodedVertexBuffer = vertexHandle.handle();
        MemorySegment encodedIndexBuffer = indexHandle.handle();
        Logger.debug("Final encoded handles - vertex: 0x" + Long.toHexString(encodedVertexBuffer.address()) + ", index: 0x" + Long.toHexString(encodedIndexBuffer.address()));
        
        VkVertexBufferBinding.create()
            .buffer(MemorySegment.ofAddress(encodedVertexBuffer.address()))
            .bind(commandBuffer, 0, frameArena);
        
        MemorySegment matrixBuffer = instanceData.getMatricesBuffer();
        Logger.debug("Matrix buffer handle: 0x" + Long.toHexString(matrixBuffer.address()) + " (now encoded VkBuffer handle)");
        
        VkVertexBufferBinding.create()
            .buffer(matrixBuffer)
            .bind(commandBuffer, 1, frameArena);
        
        Vulkan.cmdBindIndexBuffer(commandBuffer, encodedIndexBuffer, 0, VkIndexType.VK_INDEX_TYPE_UINT32.value());
        
        int enabledCount = getEnabledInstanceCount(batch);
        Logger.debug("About to draw - enabledCount: " + enabledCount + ", indexCount: " + lodLevel.indexCount());
        if (enabledCount > 0) {
            Logger.debug("Calling cmdDrawIndexed with indexCount=" + lodLevel.indexCount() + ", instanceCount=" + enabledCount);
            Logger.debug("Vertex buffer: 0x" + Long.toHexString(encodedVertexBuffer.address()) + ", Index buffer: 0x" + Long.toHexString(encodedIndexBuffer.address()));
            Logger.debug("Matrix buffer: 0x" + Long.toHexString(matrixBuffer.address()));
            Vulkan.cmdDrawIndexed(commandBuffer, lodLevel.indexCount(), enabledCount, 0, 0, 0);
            Logger.debug("Draw call completed");
        } else {
            Logger.debug("No enabled instances to draw");
        }
    }
    
    private void batchValidateAndUpload(float[] cameraPosition, InstanceData instanceData, ModelData[] modelDataArray) {
        culledCount = 0;
        for (int i = 0; i < instanceData.getCount(); i++) {
            if (instanceData.isActive(i)) {
                ModelData modelData = instanceData.getModelData(i, modelDataArray);
                boolean gpuResident = modelData != null && modelData.isGPUResident();
                boolean visible = true;
                
                if (gpuResident && frustumCullingEnabled && modelData.isLoaded()) {
                    float[] pos = modelData.getTransform().getPosition();
                    float[] scale = modelData.getTransform().getScale();
                    float maxScale = Math.max(Math.max(scale[0], scale[1]), scale[2]);
                    float radius = modelData.getBoundingRadius() * maxScale;
                    visible = frustumCuller.testSphere(pos[0], pos[1], pos[2], radius);
                    if (!visible) {
                        culledCount++;
                        Logger.debug("Culled instance " + i + " at (" + pos[0] + "," + pos[1] + "," + pos[2] + ") radius=" + radius);
                    } else {
                        Logger.debug("Visible instance " + i + " at (" + pos[0] + "," + pos[1] + "," + pos[2] + ") radius=" + radius);
                    }
                }
                
                boolean enabled = gpuResident && visible;
                batchState.markInstanceEnabled(i, enabled);
                if (enabled) {
                    batchState.markMatrixDirty(i);
                }
            }
        }
        
        batchState.buildBatches();
        
        int[] dirtyInstances = batchState.getDirtyInstances();
        for (int i = 0; i < batchState.getDirtyCount(); i++) {
            int instanceId = dirtyInstances[i];
            instanceData.syncMatrixFromModelData(instanceId, modelDataArray);
            
            MemorySegment matrix = instanceData.getMatrix(instanceId);
            float m00 = matrix.get(ValueLayout.JAVA_FLOAT, 0);
            float m11 = matrix.get(ValueLayout.JAVA_FLOAT, 5 * 4);
            float m22 = matrix.get(ValueLayout.JAVA_FLOAT, 10 * 4);
            float m03 = matrix.get(ValueLayout.JAVA_FLOAT, 3 * 4);
            float m13 = matrix.get(ValueLayout.JAVA_FLOAT, 7 * 4);
            float m23 = matrix.get(ValueLayout.JAVA_FLOAT, 11 * 4);
            Logger.debug("Instance " + instanceId + " scale=[" + m00 + "," + m11 + "," + m22 + "] pos=[" + m03 + "," + m13 + "," + m23 + "]");
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
            
            boolean batchExists = staticBatches.stream()
                .anyMatch(batch -> batch.getLodLevel() == lodLevel);
            
            if (!batchExists) {
                if (mainThreadWorkQueue != null) {
                    final int lodIndex = i;
                    mainThreadWorkQueue.enqueue(() -> createCommandBufferForLOD(lodLevel))
                        .thenAccept(commandBuffer -> {
                            addStaticBatch(lodLevel, commandBuffer, new int[]{instanceId});
                            Logger.debug("Created static batch for LOD level " + lodIndex + " of model " + modelData.getModelId());
                        });
                } else {
                    addStaticBatch(lodLevel, MemorySegment.NULL, new int[]{instanceId});
                    Logger.debug("Created placeholder batch for LOD level " + i + " of model " + modelData.getModelId());
                }
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
