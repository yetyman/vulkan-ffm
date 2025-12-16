package io.github.yetyman.vulkan.sample.complex.models;

import java.lang.foreign.MemorySegment;

/**
 * Represents a single LOD level with its geometry and distance threshold
 */
public class LODLevel {
    private volatile MemorySegment vertexBuffer;
    private volatile MemorySegment indexBuffer;
    private final int indexCount;
    private final float maxDistance;
    private final int triangleCount;
    
    public LODLevel(MemorySegment vertexBuffer, MemorySegment indexBuffer, int indexCount, float maxDistance, int triangleCount) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.indexCount = indexCount;
        this.maxDistance = maxDistance;
        this.triangleCount = triangleCount;
    }
    
    public boolean isValidForDistance(float distance) {
        return distance <= maxDistance;
    }
    
    public MemorySegment vertexBuffer() { return vertexBuffer; }
    public MemorySegment indexBuffer() { return indexBuffer; }
    public int indexCount() { return indexCount; }
    public float maxDistance() { return maxDistance; }
    public int triangleCount() { return triangleCount; }
    
    public boolean hasGPUBuffers() {
        return vertexBuffer != null && indexBuffer != null && 
               !vertexBuffer.equals(MemorySegment.NULL) && !indexBuffer.equals(MemorySegment.NULL);
    }
    
    public void setGPUBuffers(MemorySegment vertexBuf, MemorySegment indexBuf) {
        this.vertexBuffer = vertexBuf;
        this.indexBuffer = indexBuf;
    }
    
    public void clearGPUBuffers() {
        this.vertexBuffer = MemorySegment.NULL;
        this.indexBuffer = MemorySegment.NULL;
    }
}