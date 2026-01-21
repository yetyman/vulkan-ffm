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
    private volatile MemorySegment morphTargetBuffer; // NEW: positions for next LOD
    private volatile BufferHandle vertexBufferHandle;
    private volatile BufferHandle indexBufferHandle;
    private volatile BufferHandle morphTargetBufferHandle; // NEW
    private final int indexCount;
    private final float maxDistance;
    private final int triangleCount;
    private final float detailFactor;
    
    public LODLevel(MemorySegment vertexBuffer, MemorySegment indexBuffer, int indexCount, float maxDistance, int triangleCount, float detailFactor) {
        this(vertexBuffer, indexBuffer, MemorySegment.NULL, indexCount, maxDistance, triangleCount, detailFactor);
    }
    
    public LODLevel(MemorySegment vertexBuffer, MemorySegment indexBuffer, MemorySegment morphTargetBuffer,
                   int indexCount, float maxDistance, int triangleCount, float detailFactor) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.morphTargetBuffer = morphTargetBuffer;
        this.indexCount = indexCount;
        this.maxDistance = maxDistance;
        this.triangleCount = triangleCount;
        this.detailFactor = detailFactor;
        this.vertexBufferHandle = new BufferHandle(System.identityHashCode(this) * 3);
        this.indexBufferHandle = new BufferHandle(System.identityHashCode(this) * 3 + 1);
        this.morphTargetBufferHandle = new BufferHandle(System.identityHashCode(this) * 3 + 2);
        
        this.vertexBufferHandle.setVkBuffer(vertexBuffer);
        this.indexBufferHandle.setVkBuffer(indexBuffer);
        this.morphTargetBufferHandle.setVkBuffer(morphTargetBuffer);
    }
    
    public boolean isValidForDistance(float distance) {
        return distance <= maxDistance;
    }
    
    public MemorySegment vertexBuffer() { return vertexBuffer; }
    public MemorySegment indexBuffer() { return indexBuffer; }
    public MemorySegment morphTargetBuffer() { return morphTargetBuffer; }
    public int indexCount() { return indexCount; }
    public float maxDistance() { return maxDistance; }
    public int triangleCount() { return triangleCount; }
    public float detailFactor() { return detailFactor; }
    public boolean hasMorphTargets() { return morphTargetBuffer != null && !morphTargetBuffer.equals(MemorySegment.NULL); }
    
    public boolean hasGPUBuffers() {
        return vertexBuffer != null && indexBuffer != null && 
               !vertexBuffer.equals(MemorySegment.NULL) && !indexBuffer.equals(MemorySegment.NULL);
    }
    
    public void setGPUBuffers(MemorySegment vertexBuf, MemorySegment indexBuf) {
        setGPUBuffers(vertexBuf, indexBuf, MemorySegment.NULL);
    }
    
    public void setGPUBuffers(MemorySegment vertexBuf, MemorySegment indexBuf, MemorySegment morphBuf) {
        this.vertexBuffer = vertexBuf;
        this.indexBuffer = indexBuf;
        this.morphTargetBuffer = morphBuf;
        this.vertexBufferHandle.setVkBuffer(vertexBuf);
        this.indexBufferHandle.setVkBuffer(indexBuf);
        this.morphTargetBufferHandle.setVkBuffer(morphBuf);
    }
    
    public void clearGPUBuffers() {
        this.vertexBuffer = MemorySegment.NULL;
        this.indexBuffer = MemorySegment.NULL;
        this.morphTargetBuffer = MemorySegment.NULL;
        this.vertexBufferHandle.setVkBuffer(MemorySegment.NULL);
        this.indexBufferHandle.setVkBuffer(MemorySegment.NULL);
        this.morphTargetBufferHandle.setVkBuffer(MemorySegment.NULL);
    }
    
    public BufferHandle getVertexBufferHandle() { return vertexBufferHandle; }
    public BufferHandle getIndexBufferHandle() { return indexBufferHandle; }
    public BufferHandle getMorphTargetBufferHandle() { return morphTargetBufferHandle; }
}