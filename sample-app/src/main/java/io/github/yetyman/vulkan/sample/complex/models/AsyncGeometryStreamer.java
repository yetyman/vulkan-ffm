package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
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
    private final ConcurrentLinkedQueue<LODLoadRequest> loadQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<LODUnloadRequest> unloadQueue = new ConcurrentLinkedQueue<>();
    private final StagingSystem stagingSystem;
    private final Map<String, Integer> lodToRequestMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lodUnloadTimestamp = new ConcurrentHashMap<>();
    private static final long UNLOAD_DELAY_MS = 500; // Wait 500ms before unloading

    
    private final Arena arena;
    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;
    
    public AsyncGeometryStreamer(Arena arena, VkDevice device, VkPhysicalDevice physicalDevice, MemorySegment queue) {
        this.arena = arena;
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.stagingSystem = new StagingSystem(arena, device, physicalDevice, queue);
        
        // Start background processing
        loadingThread.submit(this::processQueues);
    }
    
    public void updateStreaming(ModelData[] modelDataArray, float[] cameraPosition) {
        for (ModelData modelData : modelDataArray) {
            if (modelData == null || !modelData.isLoaded()) continue;
            
            float[] pos = modelData.getTransform().getPosition();
            float distance = calculateDistance(cameraPosition, pos);
            
            // Use LODModel's selectLOD which has hysteresis to prevent thrashing
            LODLevel selectedLOD = modelData.getLodModel().selectLOD(distance);
            int neededLOD = modelData.getLodModel().getLODIndex(selectedLOD);
            int nextLOD = Math.min(neededLOD + 1, 4);
            
            // Load needed LODs if not already resident or pending
            String neededKey = modelData.getModelId() + ":" + neededLOD;
            String nextKey = modelData.getModelId() + ":" + nextLOD;
            
            if (!modelData.isLODResident(neededLOD) && !lodToRequestMap.containsKey(neededKey)) {
                Logger.info("[QUEUE LOAD] Model " + modelData.getModelId() + " LOD" + neededLOD + " - resident=" + modelData.isLODResident(neededLOD) + " pending=" + lodToRequestMap.containsKey(neededKey));
                loadQueue.offer(new LODLoadRequest(modelData, neededLOD));
            }
            if (!modelData.isLODResident(nextLOD) && !lodToRequestMap.containsKey(nextKey)) {
                Logger.info("[QUEUE LOAD] Model " + modelData.getModelId() + " LOD" + nextLOD + " - resident=" + modelData.isLODResident(nextLOD) + " pending=" + lodToRequestMap.containsKey(nextKey));
                loadQueue.offer(new LODLoadRequest(modelData, nextLOD));
            }
            
            // Mark LODs for delayed unload
            long now = System.currentTimeMillis();
            for (int i = 0; i < 5; i++) {
                String lodKey = modelData.getModelId() + ":" + i;
                if (i != neededLOD && i != nextLOD && modelData.isLODResident(i)) {
                    // Only unload if replacement is resident AND delay has passed
                    if (modelData.isLODResident(neededLOD)) {
                        Long timestamp = lodUnloadTimestamp.get(lodKey);
                        if (timestamp == null) {
                            // First time marking for unload
                            lodUnloadTimestamp.put(lodKey, now);
                            Logger.info("[UNLOAD MARK] Model " + modelData.getModelId() + " LOD" + i + " marked for unload in 500ms");
                        } else if (now - timestamp > UNLOAD_DELAY_MS) {
                            // Delay passed, queue unload
                            unloadQueue.offer(new LODUnloadRequest(modelData, i));
                            lodUnloadTimestamp.remove(lodKey);
                            Logger.info("[UNLOAD QUEUE] Model " + modelData.getModelId() + " LOD" + i + " queued for unload");
                        }
                    }
                } else {
                    // This LOD is needed, cancel any pending unload
                    lodUnloadTimestamp.remove(lodKey);
                }
            }
        }
    }
    
    private int selectLODIndex(float distance) {
        if (distance <= 10.0f) return 0;
        if (distance <= 25.0f) return 1;
        if (distance <= 50.0f) return 2;
        if (distance <= 100.0f) return 3;
        return 4;
    }
    
    private static class LODLoadRequest {
        final ModelData modelData;
        final int lodIndex;
        LODLoadRequest(ModelData m, int i) { modelData = m; lodIndex = i; }
    }
    
    private static class LODUnloadRequest {
        final ModelData modelData;
        final int lodIndex;
        LODUnloadRequest(ModelData m, int i) { modelData = m; lodIndex = i; }
    }
    
    private void processQueues() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Process unloads first to free memory
                LODUnloadRequest toUnload = unloadQueue.poll();
                if (toUnload != null) {
                    unloadLODFromGPU(toUnload.modelData, toUnload.lodIndex);
                }
                
                // Process loads if we have budget
                LODLoadRequest toLoad = loadQueue.poll();
                if (toLoad != null && currentGPUUsage.get() < GPU_MEMORY_BUDGET) {
                    loadLODToGPU(toLoad.modelData, toLoad.lodIndex);
                }
                
                Thread.sleep(16); // ~60fps processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void loadLODToGPU(ModelData modelData, int lodIndex) {
        if (modelData.isLODResident(lodIndex)) return;
        
        try (Arena tempArena = Arena.ofShared()) {
            float[] vertices = modelData.getLodVertices(lodIndex);
            int[] indices = modelData.getLodIndices(lodIndex);
            
            if (vertices == null || indices == null) {
                Logger.error("LOD" + lodIndex + " geometry is null for model " + modelData.getModelId());
                return;
            }
            
            MemorySegment vertexData = io.github.yetyman.vulkan.util.VkDataCopy.copyFloatArray(vertices, tempArena);
            MemorySegment indexData = io.github.yetyman.vulkan.util.VkDataCopy.copyIntArray(indices, tempArena);
            
            int requestId = stagingSystem.stageVertexData(vertexData, indexData);
            if (requestId != -1) {
                String key = modelData.getModelId() + ":" + lodIndex;
                lodToRequestMap.put(key, requestId);
            }
            
            long bufferSize = (vertices.length * 4L + indices.length * 4L);
            currentGPUUsage.addAndGet(bufferSize);
            modelData.setLastAccessTime(System.nanoTime());
        } catch (Exception e) {
            Logger.error("Failed to load LOD" + lodIndex + ": " + e.getMessage());
        }
    }
    
    private void unloadLODFromGPU(ModelData modelData, int lodIndex) {
        if (!modelData.isLODResident(lodIndex)) return;
        
        try {
            Logger.info("[UNLOAD EXECUTE] Model " + modelData.getModelId() + " LOD" + lodIndex + " unloading from GPU");
            
            LODLevel lodLevel = modelData.getLodModel().getLOD(lodIndex);
            lodLevel.clearGPUBuffers();
            modelData.setLODResident(lodIndex, false);
            
            float[] vertices = modelData.getLodVertices(lodIndex);
            int[] indices = modelData.getLodIndices(lodIndex);
            long bufferSize = (vertices.length * 4L + indices.length * 4L);
            currentGPUUsage.addAndGet(-bufferSize);
        } catch (Exception e) {
            Logger.error("Failed to unload LOD" + lodIndex + ": " + e.getMessage());
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
        
        // Check for completed staging requests and update specific LOD levels
        lodToRequestMap.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            int requestId = entry.getValue();
            
            String[] parts = key.split(":");
            int modelId = Integer.parseInt(parts[0]);
            int lodIndex = Integer.parseInt(parts[1]);
            
            StagingSystem.CopyRequest request = stagingSystem.getCompletedRequest(requestId);
            if (request != null && request.completed) {
                updateLODGPUBuffers(modelId, lodIndex, request, modelDataArray);
                Logger.debug("Completed GPU load for model " + modelId + " LOD" + lodIndex);
                return true; // Remove from map
            }
            return false; // Keep in map
        });
    }
    
    private void updateLODGPUBuffers(int modelId, int lodIndex, StagingSystem.CopyRequest request, ModelData[] modelDataArray) {
        ModelData modelData = modelDataArray[modelId];
        if (modelData != null && modelData.getLodModel() != null) {
            LODLevel lodLevel = modelData.getLodModel().getLOD(lodIndex);
            MemorySegment vertexBuffer = request.deviceVertexBuffer.handle();
            MemorySegment indexBuffer = request.deviceIndexBuffer.handle();
            
            Logger.info("[GPU UPLOAD] Model " + modelId + " LOD" + lodIndex + 
                       " - VB=" + vertexBuffer.address() + " IB=" + indexBuffer.address());
            
            lodLevel.setGPUBuffers(vertexBuffer, indexBuffer);
            modelData.setLODResident(lodIndex, true);
            
            Logger.info("[RESIDENT] Model " + modelId + " LOD" + lodIndex + " marked resident. hasGPUBuffers=" + lodLevel.hasGPUBuffers());
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
        if (device != null && !device.handle().equals(MemorySegment.NULL)) {
            io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(device.handle()).check();
            Logger.info("Device idle - starting AsyncGeometryStreamer cleanup");
        }
        
        loadingThread.shutdown();
        stagingSystem.cleanup();
        
        Logger.info("AsyncGeometryStreamer cleanup complete");
    }
}