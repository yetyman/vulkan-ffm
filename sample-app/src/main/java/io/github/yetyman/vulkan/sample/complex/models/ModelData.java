package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actual model data that can be loaded/freed asynchronously
 */
public class ModelData {
    private final int modelId;
    private volatile LODModel lodModel;
    private volatile TransformationMatrix transform;
    private volatile float[] vertices;
    private volatile int[] indices;
    private volatile float boundingRadius = 1.0f; // Conservative default
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
    public void loadModel(LODModel model, TransformationMatrix initialTransform, float[] vertices, int[] indices) {
        this.lodModel = model;
        this.transform = initialTransform;
        this.vertices = vertices;
        this.indices = indices;
        this.boundingRadius = calculateBoundingRadius(vertices);
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
    
    public float[] getVertices() { return vertices; }
    public int[] getIndices() { return indices; }
    public float getBoundingRadius() { return boundingRadius; }
}