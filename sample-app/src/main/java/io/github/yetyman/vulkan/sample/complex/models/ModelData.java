package io.github.yetyman.vulkan.sample.complex.models;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actual model data that can be loaded/freed asynchronously
 */
public class ModelData {
    private final int modelId;
    private volatile LODModel lodModel;
    private volatile TransformationMatrix transform;
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);
    private final AtomicBoolean isFreed = new AtomicBoolean(false);
    private volatile boolean gpuResident = false;
    private volatile long lastAccessTime = 0;
    private final AtomicBoolean pendingGPULoad = new AtomicBoolean(false);
    private final AtomicBoolean pendingGPUUnload = new AtomicBoolean(false);
    
    public ModelData(int modelId) {
        this.modelId = modelId;
    }
    
    // Async loading - must be called from thread with Vulkan context
    public void loadModel(LODModel model, TransformationMatrix initialTransform) {
        // GPU resources must be created on Vulkan context thread
        // LODModel should already have GPU buffers allocated
        this.lodModel = model;
        this.transform = initialTransform;
        isLoaded.set(true);
    }
    
    // Async freeing
    public void freeModel(MemorySegment device) {
        isFreed.set(true);
        // Schedule GPU resource cleanup on render thread
        // Actual buffer destruction must happen on Vulkan context thread
    }
    
    public boolean isLoaded() { return isLoaded.get() && !isFreed.get(); }
    public boolean isGPUResident() { return gpuResident && isLoaded(); }
    public LODModel getLodModel() { return lodModel; }
    public TransformationMatrix getTransform() { return transform; }
    public int getModelId() { return modelId; }
    
    public void setGPUResident(boolean resident) { this.gpuResident = resident; }
    public void setLastAccessTime(long time) { this.lastAccessTime = time; }
    public long getLastAccessTime() { return lastAccessTime; }
    
    public boolean setPendingGPULoad(boolean pending) { return pendingGPULoad.getAndSet(pending); }
    public boolean setPendingGPUUnload(boolean pending) { return pendingGPUUnload.getAndSet(pending); }
    public boolean isPendingGPULoad() { return pendingGPULoad.get(); }
    public boolean isPendingGPUUnload() { return pendingGPUUnload.get(); }
}