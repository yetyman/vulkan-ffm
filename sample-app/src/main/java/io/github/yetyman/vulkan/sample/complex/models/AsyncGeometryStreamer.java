package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;


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

    
    private final Arena arena;
    private final MemorySegment device;
    private final MemorySegment physicalDevice;
    
    public AsyncGeometryStreamer(Arena arena, MemorySegment device, MemorySegment physicalDevice) {
        this.arena = arena;
        this.device = device;
        this.physicalDevice = physicalDevice;
        
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
            System.out.println("[STREAM] Queued " + loadRequests + " loads, " + unloadRequests + " unloads");
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
                    System.out.println("[STREAM] Processing GPU load for model " + toLoad.getModelId());
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
            System.out.println("[STREAM] Model " + modelData.getModelId() + " already GPU resident, skipping");
            return;
        }
        
        // Clear any stale GPU buffer handles and skip GPU buffer creation for now
        LODModel lodModel = modelData.getLodModel();
        if (lodModel != null) {
            for (int i = 0; i < lodModel.getLODCount(); i++) {
                lodModel.getLOD(i).clearGPUBuffers();
            }
        }
        
        System.out.println("[STREAM] Cleared GPU buffers for model " + modelData.getModelId());
        
        long bufferSize = estimateBufferSize(modelData);
        currentGPUUsage.addAndGet(bufferSize);
        modelData.setGPUResident(true); // Mark as resident to prevent re-queuing
        modelData.setLastAccessTime(System.nanoTime());
    }
    
    private void unloadFromGPU(ModelData modelData) {
        if (!modelData.isGPUResident()) return;
        
        try {
            System.out.println("[STREAM] Unloading model ID " + modelData.getModelId() + " from GPU");
            
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
            System.err.println("[STREAM] Failed to unload geometry from GPU: " + e.getMessage());
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
    
    public void shutdown() {
        loadingThread.shutdown();
    }
}