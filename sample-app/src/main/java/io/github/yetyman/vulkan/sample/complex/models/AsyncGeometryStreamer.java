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
        for (ModelData modelData : modelDataArray) {
            if (modelData == null || !modelData.isLoaded()) continue;
            
            float[] pos = modelData.getTransform().getPosition();
            float distance = calculateDistance(cameraPosition, pos);
            
            if (distance < 100.0f && !modelData.isGPUResident()) {
                loadQueue.offer(modelData);
            } else if (distance > 200.0f && modelData.isGPUResident()) {
                unloadQueue.offer(modelData);
            }
        }
    }
    
    private void processQueues() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Process unloads first to free memory
                ModelData toUnload = unloadQueue.poll();
                if (toUnload != null) {
                    unloadFromGPU(toUnload);
                }
                
                // Process loads if we have budget
                ModelData toLoad = loadQueue.poll();
                if (toLoad != null && currentGPUUsage.get() < GPU_MEMORY_BUDGET) {
                    loadToGPU(toLoad);
                }
                
                Thread.sleep(16); // ~60fps processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void loadToGPU(ModelData modelData) {
        if (modelData.isGPUResident()) return;
        
        try {
            LODModel lodModel = modelData.getLodModel();
            if (lodModel == null) return;
            
            // Create GPU buffers for each LOD level
            for (int i = 0; i < lodModel.getLODCount(); i++) {
                LODLevel lodLevel = lodModel.getLOD(i);
                
                // Create vertex buffer
                VkBuffer vertexBuffer = VkBuffer.builder()
                    .device(device)
                    .physicalDevice(physicalDevice)
                    .size(1024 * 1024) // 1MB estimate
                    .vertexBuffer()
                    .deviceLocal()
                    .build(arena);
                
                // Create index buffer
                VkBuffer indexBuffer = VkBuffer.builder()
                    .device(device)
                    .physicalDevice(physicalDevice)
                    .size(512 * 1024) // 512KB estimate
                    .indexBuffer()
                    .deviceLocal()
                    .build(arena);
                
                // Update LODLevel with GPU buffers
                lodLevel.setGPUBuffers(vertexBuffer.handle(), indexBuffer.handle());
            }
            
            long bufferSize = estimateBufferSize(modelData);
            currentGPUUsage.addAndGet(bufferSize);
            modelData.setGPUResident(true);
            modelData.setLastAccessTime(System.nanoTime());
            
        } catch (Exception e) {
            System.err.println("Failed to load geometry to GPU: " + e.getMessage());
        }
    }
    
    private void unloadFromGPU(ModelData modelData) {
        if (!modelData.isGPUResident()) return;
        
        try {
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
            System.err.println("Failed to unload geometry from GPU: " + e.getMessage());
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