package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.BufferHandle;
import io.github.yetyman.vulkan.enums.VkIndexType;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.sample.complex.threading.MainThreadWorkQueue;
import io.github.yetyman.vulkan.util.Logger;

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
    private final VkDevice device; // For GPU resource management
    private VkCommandPool commandPool;
    private VkRenderPass renderPass;
    private final AsyncGeometryStreamer geometryStreamer;
    private final GLTFLoader gltfLoader;
    private MainThreadWorkQueue mainThreadWorkQueue;
    
    public LODRenderer(Arena arena, VkDevice device, VkPhysicalDevice physicalDevice, MemorySegment queue, int maxInstances, int maxModelData) {
        // Use shared arena for instance data to allow multi-thread access
        Arena sharedArena = Arena.ofShared();
        this.instanceData = new InstanceData(sharedArena, maxInstances, device, physicalDevice);
        this.modelDataArray = new ModelData[maxModelData];
        this.batchState = new BatchState(maxInstances);

        this.device = device;
        this.geometryStreamer = new AsyncGeometryStreamer(arena, device, physicalDevice, queue);
        this.gltfLoader = new GLTFLoader(arena);
        this.mainThreadWorkQueue = null; // Will be set by ThreadedRenderer
        this.commandPool = null;
        this.renderPass = null;
    }
    
    /**
     * Execute pre-defined static batches with batch validation and uploads
     */
    public void renderModels(MemorySegment commandBuffer, float[] cameraPosition, Arena frameArena, MemorySegment gltfPipeline) {
        // Bind glTF pipeline
        Vulkan.cmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, gltfPipeline);
        Logger.lod("Using glTF pipeline: " + gltfPipeline);
        if (staticBatches.isEmpty()) return;
        
        // Update geometry streaming (transparent to user)
        geometryStreamer.updateStreaming(modelDataArray, cameraPosition);
        
        // Process pending staging copies on main thread
        geometryStreamer.processPendingCopies(2, modelDataArray); // Process up to 2 copies per frame
        
        // Batch process all validation and uploads
        batchValidateAndUpload(cameraPosition);
        
        // Execute pre-recorded batch commands for enabled instances only
        int totalTriangles = 0;
        for (StaticBatch batch : staticBatches) {
            boolean hasEnabled = hasEnabledInstancesInBatch(batch);
            boolean hasBuffers = batch.getLodLevel().hasGPUBuffers();
            
            if (hasEnabled && hasBuffers) {
                if (!batch.getCommandBuffer().equals(MemorySegment.NULL)) {
                    Vulkan.cmdExecuteCommands(commandBuffer, 1, batch.getCommandBuffer());
                } else {
                    // Fallback to direct rendering for this batch
                    renderBatchDirectly(commandBuffer, batch, frameArena);
                }
                totalTriangles += batch.getLodLevel().triangleCount() * getEnabledInstanceCount(batch);
            }
        }
        
        Logger.lod("Executed batches, " + totalTriangles + " triangles");
    }
    
    private void renderBatchDirectly(MemorySegment commandBuffer, StaticBatch batch, Arena frameArena) {
        LODLevel lodLevel = batch.getLodLevel();
        
        // Check if buffers are valid before binding
        if (!lodLevel.hasGPUBuffers()) {
            Logger.debug("LODLevel has no GPU buffers, skipping");
            return; // Skip silently - buffers not ready yet
        }
        
        // Get buffer handles - these should be BufferHandle objects
        BufferHandle vertexHandle = lodLevel.getVertexBufferHandle();
        BufferHandle indexHandle = lodLevel.getIndexBufferHandle();
        
        Logger.render("Got BufferHandles - vertex id=" + (vertexHandle != null ? vertexHandle.getHandleId() : "null") + ", index id=" + (indexHandle != null ? indexHandle.getHandleId() : "null"));
        
        if (vertexHandle == null || indexHandle == null || !vertexHandle.isReady() || !indexHandle.isReady()) {
            Logger.render("Buffers not ready - vertex ready=" + (vertexHandle != null ? vertexHandle.isReady() : "null") + ", index ready=" + (indexHandle != null ? indexHandle.isReady() : "null"));
            return; // Buffers not ready yet
        }
        
        // Use encoded handles directly
        Logger.render("Getting handles from BufferHandle objects:");
        MemorySegment encodedVertexBuffer = vertexHandle.handle();
        MemorySegment encodedIndexBuffer = indexHandle.handle();
        Logger.render("Final encoded handles - vertex: 0x" + Long.toHexString(encodedVertexBuffer.address()) + ", index: 0x" + Long.toHexString(encodedIndexBuffer.address()));
        
        // Bind vertex buffer (binding 0) - use VkVertexBufferBinding abstraction
        VkVertexBufferBinding.create()
            .buffer(MemorySegment.ofAddress(encodedVertexBuffer.address()))
            .bind(commandBuffer, 0, frameArena);
        
        // Bind instance data buffer (binding 1) - now it's a proper VkBuffer
        MemorySegment matrixBuffer = instanceData.getMatricesBuffer();
        Logger.debug("Matrix buffer handle: 0x" + Long.toHexString(matrixBuffer.address()) + " (now encoded VkBuffer handle)");
        
        VkVertexBufferBinding.create()
            .buffer(matrixBuffer)
            .bind(commandBuffer, 1, frameArena);
        
        // Bind index buffer with encoded handle
        Vulkan.cmdBindIndexBuffer(commandBuffer, encodedIndexBuffer, 0, VkIndexType.VK_INDEX_TYPE_UINT32);
        
        // Draw indexed for each enabled instance in this batch
        int enabledCount = getEnabledInstanceCount(batch);
        Logger.draw("About to draw - enabledCount: " + enabledCount + ", indexCount: " + lodLevel.indexCount());
        if (enabledCount > 0) {
            // Get instance positions for debugging
            int[] batchInstances = batch.getInstanceIds();
            for (int instanceId : batchInstances) {
                if (instanceId < modelDataArray.length && modelDataArray[instanceId] != null) {
                    float[] pos = modelDataArray[instanceId].getTransform().getPosition();
                    Logger.lod("Instance " + instanceId + " at position: (" + pos[0] + ", " + pos[1] + ", " + pos[2] + ")");
                }
            }
            Logger.draw("Calling cmdDrawIndexed with indexCount=" + lodLevel.indexCount() + ", instanceCount=" + enabledCount);
            Logger.draw("Vertex buffer: 0x" + Long.toHexString(encodedVertexBuffer.address()) + ", Index buffer: 0x" + Long.toHexString(encodedIndexBuffer.address()));
            Logger.draw("Matrix buffer: 0x" + Long.toHexString(matrixBuffer.address()));
            Vulkan.cmdDrawIndexed(commandBuffer, lodLevel.indexCount(), enabledCount, 0, 0, 0);
            Logger.draw("Draw call completed");
        } else {
            Logger.draw("No enabled instances to draw");
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
            
            // Debug: Print matrix values
            MemorySegment matrix = instanceData.getMatrix(instanceId);
            float m00 = matrix.get(ValueLayout.JAVA_FLOAT, 0);
            float m11 = matrix.get(ValueLayout.JAVA_FLOAT, 5 * 4);
            float m22 = matrix.get(ValueLayout.JAVA_FLOAT, 10 * 4);
            float m03 = matrix.get(ValueLayout.JAVA_FLOAT, 3 * 4); // translation X
            float m13 = matrix.get(ValueLayout.JAVA_FLOAT, 7 * 4); // translation Y
            float m23 = matrix.get(ValueLayout.JAVA_FLOAT, 11 * 4); // translation Z
            Logger.matrix("Instance " + instanceId + " scale=[" + m00 + "," + m11 + "," + m22 + "] pos=[" + m03 + "," + m13 + "," + m23 + "]");
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
                            Logger.lod("Created static batch for LOD level " + lodIndex + " of model " + modelData.getModelId());
                        });
                } else {
                    // Fallback to placeholder
                    addStaticBatch(lodLevel, MemorySegment.NULL, new int[]{instanceId});
                    Logger.lod("Created placeholder batch for LOD level " + i + " of model " + modelData.getModelId());
                }
            }
        }
    }
    
    private MemorySegment createCommandBufferForLOD(LODLevel lodLevel) {
        // TODO: Fix Arena threading issues and re-enable command buffer pre-recording
        /*
        // This runs on main thread and can access Vulkan resources
        if (!lodLevel.hasGPUBuffers()) {
            Logger.lod("LOD level has no GPU buffers, skipping command buffer creation");
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
            Vulkan.cmdBindVertexBuffers(commandBuffer, 0, 1, vertexBuffers, offsets);
            
            // Bind index buffer
            Vulkan.cmdBindIndexBuffer(commandBuffer, lodLevel.indexBuffer(), 0, VkIndexType.VK_INDEX_TYPE_UINT32);
            
            // Draw indexed (instance count will be set during execution)
            Vulkan.cmdDrawIndexed(commandBuffer, lodLevel.indexCount(), 1, 0, 0, 0);
            
            Vulkan.endCommandBuffer(commandBuffer).check();
            
            Logger.lod("Created command buffer for LOD level with " + lodLevel.indexCount() + " indices");
            return commandBuffer;
            
        } catch (Exception e) {
            System.err.println("[LOD] Failed to create command buffer: " + e.getMessage());
            return MemorySegment.NULL;
        }
        */
        
        // Temporary: Skip command buffer creation, use direct rendering fallback
        Logger.lod("Skipping command buffer creation, using direct rendering");
        return MemorySegment.NULL;
    }
    
    public void setMainThreadWorkQueue(MainThreadWorkQueue workQueue) {
        this.mainThreadWorkQueue = workQueue;
    }
    
    public void setVulkanResources(VkCommandPool commandPool, VkRenderPass renderPass) {
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
    

    public void cleanup() {
        // Wait for device to be idle before cleanup
        if (device != null && !device.handle().equals(MemorySegment.NULL)) {
            Vulkan.deviceWaitIdle(device.handle()).check();
            Logger.info("Device idle - starting LODRenderer cleanup");
        }
        
        // Clean up geometry streamer and GLTF loader
        geometryStreamer.shutdown();
        gltfLoader.shutdown();
        
        // Clean up instance data
        if (instanceData != null) {
            instanceData.cleanup();
        }
        
        Logger.info("LODRenderer cleanup complete");
    }
    
    public void shutdown() {
        cleanup();
    }
    
    /**
     * Load glTF model from file path - can be called from any thread
     * @param filePath Path to .gltf or .glb file
     * @return CompletableFuture that resolves to ModelData when loaded
     */
    public CompletableFuture<ModelData> loadGLTFModel(String filePath) {
        return gltfLoader.loadModel(filePath);
    }
    
    private static MemorySegment staticVertexBuffer = MemorySegment.NULL;
    private static MemorySegment staticInstanceBuffer = MemorySegment.NULL;
    private static boolean buffersNeedReset = true;
    private static int resetCounter = 0;
    
    /**
     * Render a test triangle using static buffers (created once)
     */
    public void renderTestTriangle(MemorySegment commandBuffer, Arena frameArena) {
        // Create static buffers only once
        if (staticVertexBuffer.equals(MemorySegment.NULL)) {
            Logger.debug("Creating new glTF test buffers");
            float[] vertices = {
                -0.8f, -0.8f, 0.2f,  0.0f, 0.0f, 1.0f,  0.0f, 0.0f, // vertex 0
                 0.8f, -0.8f, 0.2f,  0.0f, 0.0f, 1.0f,  1.0f, 0.0f, // vertex 1
                 0.0f,  0.8f, 0.2f,  0.0f, 0.0f, 1.0f,  0.5f, 1.0f  // vertex 2
            };
            
            float[] matrix = {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
            };
            
            staticVertexBuffer = geometryStreamer.createTestBuffer(vertices);
            staticInstanceBuffer = geometryStreamer.createTestBuffer(matrix);
        }
        
        if (!staticVertexBuffer.equals(MemorySegment.NULL) && !staticInstanceBuffer.equals(MemorySegment.NULL)) {
            // Bind static buffers using VkVertexBufferBinding abstraction
            VkVertexBufferBinding.create()
                .buffer(staticVertexBuffer)
                .buffer(staticInstanceBuffer)
                .bind(commandBuffer, 0, frameArena);
            
            Vulkan.cmdDraw(commandBuffer, 3, 1, 0, 0);
            Logger.debug("Drew glTF test triangle with buffers");
        } else {
            Logger.debug("glTF test triangle buffers are NULL");
        }
    }
    
    /**
     * Bind only the instance buffer for testing
     */
    public void bindInstanceBufferOnly(MemorySegment commandBuffer, Arena frameArena) {
        if (staticInstanceBuffer.equals(MemorySegment.NULL)) {
            float[] matrix = {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
            };
            Logger.debug("Matrix data: [" + matrix[0] + ", " + matrix[1] + ", " + matrix[2] + ", " + matrix[3] + "]");
            Logger.debug("Matrix data: [" + matrix[4] + ", " + matrix[5] + ", " + matrix[6] + ", " + matrix[7] + "]");
            Logger.debug("Matrix data: [" + matrix[8] + ", " + matrix[9] + ", " + matrix[10] + ", " + matrix[11] + "]");
            Logger.debug("Matrix data: [" + matrix[12] + ", " + matrix[13] + ", " + matrix[14] + ", " + matrix[15] + "]");
            staticInstanceBuffer = geometryStreamer.createTestBuffer(matrix);
        }
        
        if (!staticInstanceBuffer.equals(MemorySegment.NULL)) {
            VkVertexBufferBinding.create()
                .buffer(staticInstanceBuffer)
                .bind(commandBuffer, 1, frameArena);
            
            Logger.debug("Bound instance buffer at binding 1");
        } else {
            Logger.debug("Instance buffer is NULL");
        }
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