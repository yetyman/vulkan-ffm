package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VulkanExtensions;
import io.github.yetyman.vulkan.enums.VkIndexType;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;
import java.util.stream.IntStream;

/**
 * High-performance LOD renderer with distance culling and batch optimization
 */
public class LODRenderer {
    private final List<LODModel> models = new ArrayList<>();
    private InstanceData instanceData;
    private final List<StaticBatch> staticBatches = new ArrayList<>();
    private final ModelData[] modelDataArray; // Global array for pointer resolution
    private final BatchState batchState;
    private final MemorySegment device; // For GPU resource management
    private final AsyncGeometryStreamer geometryStreamer;
    private final GLTFLoader gltfLoader;
    
    public LODRenderer(Arena arena, MemorySegment device, MemorySegment physicalDevice, int maxInstances, int maxModelData) {
        this.instanceData = new InstanceData(arena, maxInstances);
        this.modelDataArray = new ModelData[maxModelData];
        this.batchState = new BatchState(maxInstances);
        this.device = device;
        this.geometryStreamer = new AsyncGeometryStreamer(arena, device, physicalDevice);
        this.gltfLoader = new GLTFLoader(arena);
    }
    
    /**
     * Execute pre-defined static batches with batch validation and uploads
     */
    public void renderModels(MemorySegment commandBuffer, float[] cameraPosition, Arena frameArena) {
        if (staticBatches.isEmpty()) return;
        
        // Update geometry streaming (transparent to user)
        geometryStreamer.updateStreaming(modelDataArray, cameraPosition);
        
        // Batch process all validation and uploads
        batchValidateAndUpload(cameraPosition);
        
        // Execute pre-recorded batch commands for enabled instances only
        int totalTriangles = 0;
        for (StaticBatch batch : staticBatches) {
            if (hasEnabledInstancesInBatch(batch) && batch.getLodLevel().hasGPUBuffers()) {
                VulkanExtensions.cmdExecuteCommands(commandBuffer, 1, batch.getCommandBuffer());
                totalTriangles += batch.getLodLevel().triangleCount() * getEnabledInstanceCount(batch);
            }
        }
        
        System.out.println("[LOD] Executed batches, " + totalTriangles + " triangles");
    }
    
    private void batchValidateAndUpload(float[] cameraPosition) {
        // Batch validate all instances
        for (int i = 0; i < instanceData.getCount(); i++) {
            if (instanceData.isActive(i)) {
                ModelData modelData = instanceData.getModelData(i, modelDataArray);
                boolean enabled = modelData != null && modelData.isGPUResident();
                batchState.markInstanceEnabled(i, enabled);
                
                if (enabled) {
                    // Check if matrix needs update
                    batchState.markMatrixDirty(i);
                }
            }
        }
        
        // Build batch lists
        batchState.buildBatches();
        
        // Batch upload dirty matrices
        int[] dirtyInstances = batchState.getDirtyInstances();
        for (int i = 0; i < batchState.getDirtyCount(); i++) {
            int instanceId = dirtyInstances[i];
            instanceData.syncMatrixFromModelData(instanceId, modelDataArray);
        }
    }
    
    private boolean hasEnabledInstancesInBatch(StaticBatch batch) {
        // Check if any instances in this batch are enabled
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
    
    private float calculateDistance(float[] cameraPos, float[] objectPos) {
        float dx = cameraPos[0] - objectPos[0];
        float dy = cameraPos[1] - objectPos[1];
        float dz = cameraPos[2] - objectPos[2];
        return (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
    

    
    public int addModel(LODModel model) {
        models.add(model);
        return models.size() - 1;
    }
    
    public int addInstance(ModelData modelData) {
        // Store ModelData in global array
        modelDataArray[modelData.getModelId()] = modelData;
        return instanceData.addInstance(modelData);
    }
    
    public void removeInstance(int instanceId) {
        instanceData.removeInstance(instanceId);
    }
    
    public void updateInstance(int instanceId, ModelData newModelData) {
        modelDataArray[newModelData.getModelId()] = newModelData;
        instanceData.updateInstance(instanceId, newModelData);
    }
    
    public int getInstanceCount() {
        return instanceData.getCount();
    }
    
    public void addStaticBatch(LODLevel lodLevel, MemorySegment preRecordedCommandBuffer, int[] instanceIds) {
        staticBatches.add(new StaticBatch(lodLevel, preRecordedCommandBuffer, instanceIds));
    }
    
    public void clearStaticBatches() {
        staticBatches.clear();
    }
    
    public void shutdown() {
        geometryStreamer.shutdown();
        gltfLoader.shutdown();
    }
    
    /**
     * Load glTF model from file path - can be called from any thread
     * @param filePath Path to .gltf or .glb file
     * @return CompletableFuture that resolves to ModelData when loaded
     */
    public CompletableFuture<ModelData> loadGLTFModel(String filePath) {
        return gltfLoader.loadModel(filePath);
    }
    
    /**
     * Get total triangle count being rendered (for performance monitoring)
     */
    public int getActiveTriangleCount(float[] cameraPosition) {
        int total = 0;
        float[] position = new float[3];
        float[] scale = new float[3];
        
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
}