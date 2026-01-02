package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Async geometry streamer - loads/unloads GPU buffers on background threads
 */
public class AsyncGeometryStreamer {
    private final long GPU_MEMORY_BUDGET = 512 * 1024 * 1024; // 512MB
    private final AtomicLong currentGPUUsage = new AtomicLong(0);
    private final ExecutorService loadingThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GeometryLoader");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentLinkedQueue<ModelData> loadQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ModelData> unloadQueue = new ConcurrentLinkedQueue<>();
    private final StagingSystem stagingSystem;
    private final Map<Integer, Integer> modelToRequestMap = new ConcurrentHashMap<>();

    
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment physicalDevice;
    
    public AsyncGeometryStreamer(Arena arena, MemorySegment device, MemorySegment physicalDevice, MemorySegment queue) {
        this.arena = arena;
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.stagingSystem = new StagingSystem(arena, device, physicalDevice, queue);
        
        // Start background processing
        loadingThread.submit(this::processQueues);
    }
    
    public void updateStreaming(ModelData[] modelDataArray, float[] cameraPosition) {
        int loadRequests = 0, unloadRequests = 0;
        
        for (ModelData modelData : modelDataArray) {
            if (modelData == null || !modelData.isLoaded()) continue;
            
            float[] pos = modelData.getTransform().getPosition();
            float distance = calculateDistance(cameraPosition, pos);
            
            if (distance < 100.0f && !modelData.isGPUResident() && !modelData.setPendingGPULoad(true)) {
                loadQueue.offer(modelData);
                loadRequests++;
            } else if (distance > 200.0f && modelData.isGPUResident() && !modelData.setPendingGPUUnload(true)) {
                unloadQueue.offer(modelData);
                unloadRequests++;
            }
        }
        
        if (loadRequests > 0 || unloadRequests > 0) {
            Logger.load("Queued " + loadRequests + " loads, " + unloadRequests + " unloads");
        }
    }
    
    private void processQueues() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Process unloads first to free memory
                ModelData toUnload = unloadQueue.poll();
                if (toUnload != null) {
                    unloadFromGPU(toUnload);
                    toUnload.setPendingGPUUnload(false);
                }
                
                // Process loads if we have budget
                ModelData toLoad = loadQueue.poll();
                if (toLoad != null && currentGPUUsage.get() < GPU_MEMORY_BUDGET) {
                    Logger.load("Processing GPU load for model " + toLoad.getModelId());
                    loadToGPU(toLoad);
                    toLoad.setPendingGPULoad(false);
                }
                
                Thread.sleep(16); // ~60fps processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void loadToGPU(ModelData modelData) {
        if (modelData.isGPUResident()) {
            return;
        }
        
        LODModel lodModel = modelData.getLodModel();
        if (lodModel == null) return;
        
        // Create actual vertex data from the loaded model
        try {
            Arena tempArena = Arena.ofShared();
            
            // Get actual geometry data from the loaded glTF model
            Logger.load("Using actual glTF geometry data for model " + modelData.getModelId());
            
            // Get vertex and index data from the model
            float[] vertices = modelData.getVertices();
            int[] indices = modelData.getIndices();
            
            if (vertices == null || indices == null) {
                Logger.error("Model geometry data is null! vertices=" + vertices + ", indices=" + indices);
                return;
            }
            
            Logger.load("Model has " + vertices.length + " vertex floats, " + indices.length + " indices");
            Logger.load("First few vertices: [" + vertices[0] + ", " + vertices[1] + ", " + vertices[2] + "]");
            Logger.load("First few indices: [" + indices[0] + ", " + indices[1] + ", " + indices[2] + "]");
            
            // Create vertex data buffer
            MemorySegment vertexData = tempArena.allocate(vertices.length * 4); // 4 bytes per float
            for (int i = 0; i < vertices.length; i++) {
                vertexData.setAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, i, vertices[i]);
            }
            
            // Create index data buffer
            MemorySegment indexData = tempArena.allocate(indices.length * 4); // 4 bytes per int
            for (int i = 0; i < indices.length; i++) {
                indexData.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i, indices[i]);
            }
            
            // Stage the data
            int requestId = stagingSystem.stageVertexData(vertexData, indexData);
            if (requestId != -1) {
                modelToRequestMap.put(modelData.getModelId(), requestId);
                Logger.load("Staged model " + modelData.getModelId() + " with request " + requestId);
            }
            
        } catch (Exception e) {
            Logger.error("Failed to stage model data: " + e.getMessage());
        }
        
        long bufferSize = estimateBufferSize(modelData);
        currentGPUUsage.addAndGet(bufferSize);
        modelData.setGPUResident(true);
        modelData.setLastAccessTime(System.nanoTime());
    }
    
    private void unloadFromGPU(ModelData modelData) {
        if (!modelData.isGPUResident()) return;
        
        try {
            Logger.load("Unloading model ID " + modelData.getModelId() + " from GPU");
            
            LODModel lodModel = modelData.getLodModel();
            if (lodModel != null) {
                // Clean up GPU buffers for each LOD level
                for (int i = 0; i < lodModel.getLODCount(); i++) {
                    LODLevel lodLevel = lodModel.getLOD(i);
                    lodLevel.clearGPUBuffers();
                }
            }
            
            long bufferSize = estimateBufferSize(modelData);
            currentGPUUsage.addAndGet(-bufferSize);
            modelData.setGPUResident(false);
            
        } catch (Exception e) {
            Logger.error("Failed to unload geometry from GPU: " + e.getMessage());
        }
    }
    
    private long estimateBufferSize(ModelData modelData) {
        return 1024 * 1024; // 1MB estimate per model
    }
    
    private float calculateDistance(float[] cameraPos, float[] objectPos) {
        float dx = cameraPos[0] - objectPos[0];
        float dy = cameraPos[1] - objectPos[1];
        float dz = cameraPos[2] - objectPos[2];
        return (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
    
    /**
     * Process pending copies on main thread
     */
    public void processPendingCopies(int maxCopies, ModelData[] modelDataArray) {
        stagingSystem.processPendingCopies(maxCopies);
        
        // Check for completed staging requests and update LOD levels
        modelToRequestMap.entrySet().removeIf(entry -> {
            int modelId = entry.getKey();
            int requestId = entry.getValue();
            
            StagingSystem.CopyRequest request = stagingSystem.getCompletedRequest(requestId);
            if (request != null && request.completed) {
                updateModelGPUBuffers(modelId, request, modelDataArray);
                Logger.load("Completed GPU load for model " + modelId);
                return true; // Remove from map
            }
            return false; // Keep in map
        });
    }
    
    private void updateModelGPUBuffers(int modelId, StagingSystem.CopyRequest request, ModelData[] modelDataArray) {
        ModelData modelData = modelDataArray[modelId];
        if (modelData != null && modelData.getLodModel() != null) {
            Logger.load("Getting handles from VkBuffer objects:");
            Logger.load("request.deviceVertexBuffer: " + request.deviceVertexBuffer);
            Logger.load("request.deviceIndexBuffer: " + request.deviceIndexBuffer);
            
            MemorySegment vertexBuffer = request.deviceVertexBuffer.handle();
            MemorySegment indexBuffer = request.deviceIndexBuffer.handle();
            Logger.load("VkBuffer.handle() results - vertex: 0x" + Long.toHexString(vertexBuffer.address()) + ", index: 0x" + Long.toHexString(indexBuffer.address()));
            
            // Set GPU buffers on ALL LOD levels (they all use the same geometry for now)
            LODModel lodModel = modelData.getLodModel();
            for (int i = 0; i < lodModel.getLODCount(); i++) {
                LODLevel lodLevel = lodModel.getLOD(i);
                Logger.load("Calling setGPUBuffers on LOD level " + i);
                lodLevel.setGPUBuffers(vertexBuffer, indexBuffer);
            }
            Logger.load("Set GPU buffers on all " + lodModel.getLODCount() + " LOD levels for model " + modelId);
        }
    }
    
    /**
     * Create a test buffer for immediate use (for testing glTF pipeline)
     */
    public MemorySegment createTestBuffer(float[] data) {
        try {
            VkBuffer buffer = stagingSystem.createImmediateBuffer(data);
            return buffer != null ? buffer.handle() : MemorySegment.NULL;
        } catch (Exception e) {
            Logger.error("Failed to create test buffer: " + e.getMessage());
            return MemorySegment.NULL;
        }
    }
    
    public void shutdown() {
        // Wait for device to be idle before cleanup
        if (device != null && !device.equals(MemorySegment.NULL)) {
            io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(device).check();
            Logger.info("Device idle - starting AsyncGeometryStreamer cleanup");
        }
        
        loadingThread.shutdown();
        stagingSystem.cleanup();
        
        Logger.info("AsyncGeometryStreamer cleanup complete");
    }
}