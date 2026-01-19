package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.BufferHandle;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.MemorySegment;

/**
 * Represents a single LOD level with its geometry and distance threshold
 */
public class LODLevel {
    private volatile MemorySegment vertexBuffer;
    private volatile MemorySegment indexBuffer;
    private volatile BufferHandle vertexBufferHandle;
    private volatile BufferHandle indexBufferHandle;
    private final int indexCount;
    private final float maxDistance;
    private final int triangleCount;
    private final float detailFactor; // 1.0 = full detail, 0.5 = 50%, etc.
    
    public LODLevel(MemorySegment vertexBuffer, MemorySegment indexBuffer, int indexCount, float maxDistance, int triangleCount, float detailFactor) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.indexCount = indexCount;
        this.maxDistance = maxDistance;
        this.triangleCount = triangleCount;
        this.detailFactor = detailFactor;
        this.vertexBufferHandle = new BufferHandle(System.identityHashCode(this) * 2);
        this.indexBufferHandle = new BufferHandle(System.identityHashCode(this) * 2 + 1);
        
        // Set the buffers in the handles
        this.vertexBufferHandle.setVkBuffer(vertexBuffer);
        this.indexBufferHandle.setVkBuffer(indexBuffer);
    }
    
    public boolean isValidForDistance(float distance) {
        return distance <= maxDistance;
    }
    
    public MemorySegment vertexBuffer() { return vertexBuffer; }
    public MemorySegment indexBuffer() { return indexBuffer; }
    public int indexCount() { return indexCount; }
    public float maxDistance() { return maxDistance; }
    public int triangleCount() { return triangleCount; }
    public float detailFactor() { return detailFactor; }
    
    public boolean hasGPUBuffers() {
        return vertexBuffer != null && indexBuffer != null && 
               !vertexBuffer.equals(MemorySegment.NULL) && !indexBuffer.equals(MemorySegment.NULL);
    }
    
    public void setGPUBuffers(MemorySegment vertexBuf, MemorySegment indexBuf) {
        this.vertexBuffer = vertexBuf;
        this.indexBuffer = indexBuf;
        this.vertexBufferHandle.setVkBuffer(vertexBuf);
        this.indexBufferHandle.setVkBuffer(indexBuf);
    }
    
    public void clearGPUBuffers() {
        this.vertexBuffer = MemorySegment.NULL;
        this.indexBuffer = MemorySegment.NULL;
        this.vertexBufferHandle.setVkBuffer(MemorySegment.NULL);
        this.indexBufferHandle.setVkBuffer(MemorySegment.NULL);
    }
    
    public BufferHandle getVertexBufferHandle() { return vertexBufferHandle; }
    public BufferHandle getIndexBufferHandle() { return indexBufferHandle; }
}