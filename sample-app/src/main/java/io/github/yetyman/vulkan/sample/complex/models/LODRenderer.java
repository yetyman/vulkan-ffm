package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.sample.complex.threading.MainThreadWorkQueue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private MemorySegment commandPool;
    private MemorySegment renderPass;
    private final AsyncGeometryStreamer geometryStreamer;
    private final GLTFLoader gltfLoader;
    private MainThreadWorkQueue mainThreadWorkQueue;
    
    public LODRenderer(Arena arena, MemorySegment device, MemorySegment physicalDevice, int maxInstances, int maxModelData) {
        // Use shared arena for instance data to allow multi-thread access
        Arena sharedArena = Arena.ofShared();
        this.instanceData = new InstanceData(sharedArena, maxInstances);
        this.modelDataArray = new ModelData[maxModelData];
        this.batchState = new BatchState(maxInstances);

        this.device = device;
        this.geometryStreamer = new AsyncGeometryStreamer(arena, device, physicalDevice);
        this.gltfLoader = new GLTFLoader(arena);
        this.mainThreadWorkQueue = null; // Will be set by ThreadedRenderer
        this.commandPool = MemorySegment.NULL;
        this.renderPass = MemorySegment.NULL;
    }
    
    /**
     * Execute pre-defined static batches with batch validation and uploads
     */
    public void renderModels(MemorySegment commandBuffer, float[] cameraPosition, Arena frameArena, MemorySegment gltfPipeline) {
        // Bind glTF pipeline
        VulkanExtensions.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, gltfPipeline);
        if (staticBatches.isEmpty()) return;
        
        // Update geometry streaming (transparent to user)
        geometryStreamer.updateStreaming(modelDataArray, cameraPosition);
        
        // Batch process all validation and uploads
        batchValidateAndUpload(cameraPosition);
        
        // Execute pre-recorded batch commands for enabled instances only
        int totalTriangles = 0;
        for (StaticBatch batch : staticBatches) {
            if (hasEnabledInstancesInBatch(batch) && batch.getLodLevel().hasGPUBuffers()) {
                if (!batch.getCommandBuffer().equals(MemorySegment.NULL)) {
                    VulkanExtensions.cmdExecuteCommands(commandBuffer, 1, batch.getCommandBuffer());
                } else {
                    // Fallback to direct rendering for this batch
                    renderBatchDirectly(commandBuffer, batch, frameArena);
                }
                totalTriangles += batch.getLodLevel().triangleCount() * getEnabledInstanceCount(batch);
            }
        }
        
        System.out.println("[LOD] Executed batches, " + totalTriangles + " triangles");
    }
    
    private void renderBatchDirectly(MemorySegment commandBuffer, StaticBatch batch, Arena frameArena) {
        LODLevel lodLevel = batch.getLodLevel();
        

        
        // Bind vertex buffer
        MemorySegment vertexBuffers = frameArena.allocate(ValueLayout.ADDRESS);
        vertexBuffers.set(ValueLayout.ADDRESS, 0, lodLevel.vertexBuffer());
        MemorySegment offsets = frameArena.allocate(ValueLayout.JAVA_LONG);
        offsets.set(ValueLayout.JAVA_LONG, 0, 0L);
        VulkanExtensions.cmdBindVertexBuffers(commandBuffer, 0, 1, vertexBuffers, offsets);
        
        // Bind index buffer
        VulkanExtensions.cmdBindIndexBuffer(commandBuffer, lodLevel.indexBuffer(), 0, VkIndexType.VK_INDEX_TYPE_UINT32);
        
        // Draw indexed for each enabled instance in this batch
        int enabledCount = getEnabledInstanceCount(batch);
        if (enabledCount > 0) {
            System.out.println("[LOD] Drawing " + enabledCount + " instances with " + lodLevel.indexCount() + " indices each");
            VulkanExtensions.cmdDrawIndexed(commandBuffer, lodLevel.indexCount(), enabledCount, 0, 0, 0);
        } else {
            System.out.println("[LOD] No enabled instances for this batch");
        }
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
        int instanceId = instanceData.addInstance(modelData);
        
        // Create static batches for this model if needed
        createStaticBatchesForModel(modelData, instanceId);
        
        return instanceId;
    }
    
    private void createStaticBatchesForModel(ModelData modelData, int instanceId) {
        if (!modelData.isLoaded()) return;
        
        LODModel lodModel = modelData.getLodModel();
        for (int i = 0; i < lodModel.getLODCount(); i++) {
            LODLevel lodLevel = lodModel.getLOD(i);
            
            // Check if we already have a batch for this LOD level
            boolean batchExists = staticBatches.stream()
                .anyMatch(batch -> batch.getLodLevel() == lodLevel);
            
            if (!batchExists) {
                // Queue command buffer creation on main thread
                if (mainThreadWorkQueue != null) {
                    final int lodIndex = i;
                    mainThreadWorkQueue.enqueue(() -> createCommandBufferForLOD(lodLevel))
                        .thenAccept(commandBuffer -> {
                            addStaticBatch(lodLevel, commandBuffer, new int[]{instanceId});
                            System.out.println("[LOD] Created static batch for LOD level " + lodIndex + " of model " + modelData.getModelId());
                        });
                } else {
                    // Fallback to placeholder
                    addStaticBatch(lodLevel, MemorySegment.NULL, new int[]{instanceId});
                    System.out.println("[LOD] Created placeholder batch for LOD level " + i + " of model " + modelData.getModelId());
                }
            }
        }
    }
    
    private MemorySegment createCommandBufferForLOD(LODLevel lodLevel) {
        // TODO: Fix Arena threading issues and re-enable command buffer pre-recording
        /*
        // This runs on main thread and can access Vulkan resources
        if (!lodLevel.hasGPUBuffers()) {
            System.out.println("[LOD] LOD level has no GPU buffers, skipping command buffer creation");
            return MemorySegment.NULL;
        }
        
        try {
            // Allocate secondary command buffer
            Arena cmdArena = Arena.ofShared();
            MemorySegment[] commandBuffers = VkCommandBufferAlloc.builder()
                .device(device)
                .commandPool(commandPool)
                .secondary()
                .count(1)
                .allocate(cmdArena);
            MemorySegment commandBuffer = commandBuffers[0];
            
            // Record draw commands
            VkCommandBuffer.begin(commandBuffer)
                .inheritanceInfo(renderPass, 0, MemorySegment.NULL)
                .execute(cmdArena);
            
            // Bind vertex buffer
            MemorySegment vertexBuffers = cmdArena.allocate(ValueLayout.ADDRESS);
            vertexBuffers.set(ValueLayout.ADDRESS, 0, lodLevel.vertexBuffer());
            MemorySegment offsets = cmdArena.allocate(ValueLayout.JAVA_LONG);
            offsets.set(ValueLayout.JAVA_LONG, 0, 0L);
            VulkanExtensions.cmdBindVertexBuffers(commandBuffer, 0, 1, vertexBuffers, offsets);
            
            // Bind index buffer
            VulkanExtensions.cmdBindIndexBuffer(commandBuffer, lodLevel.indexBuffer(), 0, VkIndexType.VK_INDEX_TYPE_UINT32);
            
            // Draw indexed (instance count will be set during execution)
            VulkanExtensions.cmdDrawIndexed(commandBuffer, lodLevel.indexCount(), 1, 0, 0, 0);
            
            VulkanExtensions.endCommandBuffer(commandBuffer).check();
            
            System.out.println("[LOD] Created command buffer for LOD level with " + lodLevel.indexCount() + " indices");
            return commandBuffer;
            
        } catch (Exception e) {
            System.err.println("[LOD] Failed to create command buffer: " + e.getMessage());
            return MemorySegment.NULL;
        }
        */
        
        // Temporary: Skip command buffer creation, use direct rendering fallback
        System.out.println("[LOD] Skipping command buffer creation, using direct rendering");
        return MemorySegment.NULL;
    }
    
    public void setMainThreadWorkQueue(MainThreadWorkQueue workQueue) {
        this.mainThreadWorkQueue = workQueue;
    }
    
    public void setVulkanResources(MemorySegment commandPool, MemorySegment renderPass) {
        this.commandPool = commandPool;
        this.renderPass = renderPass;
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