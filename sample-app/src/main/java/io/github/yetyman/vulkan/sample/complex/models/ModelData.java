package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.util.Logger;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actual model data that can be loaded/freed asynchronously
 */
public class ModelData {
    private final int modelId;
    private volatile LODModel lodModel;
    private volatile TransformationMatrix transform;
    private volatile float[][] lodVertices = new float[5][];  // Per-LOD geometry
    private volatile int[][] lodIndices = new int[5][];       // Per-LOD geometry
    private volatile float boundingRadius = 1.0f;
    private final AtomicBoolean isLoaded = new AtomicBoolean(false);
    private final AtomicBoolean isFreed = new AtomicBoolean(false);
    private final boolean[] lodResident = new boolean[5];     // Per-LOD GPU residency
    private volatile long lastAccessTime = 0;
    private final AtomicBoolean pendingGPULoad = new AtomicBoolean(false);
    private final AtomicBoolean pendingGPUUnload = new AtomicBoolean(false);
    
    public ModelData(int modelId) {
        this.modelId = modelId;
    }
    
    // Async loading - must be called from thread with Vulkan context
    public void loadModel(LODModel model, TransformationMatrix initialTransform, float[][] lodVertices, int[][] lodIndices) {
        this.lodModel = model;
        this.transform = initialTransform;
        this.lodVertices = lodVertices;
        this.lodIndices = lodIndices;
        this.boundingRadius = calculateBoundingRadius(lodVertices[0]);
        isLoaded.set(true);
    }
    
    private float calculateBoundingRadius(float[] vertices) {
        if (vertices == null || vertices.length < 3) return 1.0f;
        float maxDistSq = 0;
        for (int i = 0; i < vertices.length; i += 8) { // stride 8 (pos + normal + texcoord)
            float x = vertices[i];
            float y = vertices[i+1];
            float z = vertices[i+2];
            float distSq = x*x + y*y + z*z;
            if (distSq > maxDistSq) maxDistSq = distSq;
        }
        return (float)Math.sqrt(maxDistSq);
    }
    
    // Async freeing
    public void freeModel(VkDevice device) {
        isFreed.set(true);
        // Schedule GPU resource cleanup on render thread
        // Actual buffer destruction must happen on Vulkan context thread
    }
    
    public boolean isLoaded() { return isLoaded.get() && !isFreed.get(); }
    public boolean isGPUResident() { 
        // Model is GPU resident if at least one LOD is resident
        for (boolean resident : lodResident) {
            if (resident) return true;
        }
        return false;
    }
    public boolean isLODResident(int lodIndex) { 
        boolean flagSet = lodResident[lodIndex];
        boolean hasBuffers = lodModel.getLOD(lodIndex).hasGPUBuffers();
        if (flagSet != hasBuffers) {
            Logger.info("[RESIDENT MISMATCH] Model " + modelId + " LOD" + lodIndex + " flag=" + flagSet + " hasBuffers=" + hasBuffers);
        }
        return flagSet && hasBuffers;
    }
    public LODModel getLodModel() { return lodModel; }
    public TransformationMatrix getTransform() { return transform; }
    public int getModelId() { return modelId; }
    
    public void setLODResident(int lodIndex, boolean resident) { 
        lodResident[lodIndex] = resident; 
    }
    public void setLastAccessTime(long time) { this.lastAccessTime = time; }
    public long getLastAccessTime() { return lastAccessTime; }
    
    public boolean setPendingGPULoad(boolean pending) { return pendingGPULoad.getAndSet(pending); }
    public boolean setPendingGPUUnload(boolean pending) { return pendingGPUUnload.getAndSet(pending); }
    public boolean isPendingGPULoad() { return pendingGPULoad.get(); }
    public boolean isPendingGPUUnload() { return pendingGPUUnload.get(); }
    
    public float[] getLodVertices(int lodIndex) { return lodVertices[lodIndex]; }
    public int[] getLodIndices(int lodIndex) { return lodIndices[lodIndex]; }
    public float getBoundingRadius() { return boundingRadius; }
}