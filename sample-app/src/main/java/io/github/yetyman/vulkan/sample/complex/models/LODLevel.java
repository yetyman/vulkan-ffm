package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.BufferHandle;
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
    
    public LODLevel(MemorySegment vertexBuffer, MemorySegment indexBuffer, int indexCount, float maxDistance, int triangleCount) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.indexCount = indexCount;
        this.maxDistance = maxDistance;
        this.triangleCount = triangleCount;
        this.vertexBufferHandle = new BufferHandle(System.identityHashCode(this) * 2);
        this.indexBufferHandle = new BufferHandle(System.identityHashCode(this) * 2 + 1);
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
        System.out.println("[LOD] setGPUBuffers called with vertex: 0x" + Long.toHexString(vertexBuf.address()) + ", index: 0x" + Long.toHexString(indexBuf.address()));
        this.vertexBuffer = vertexBuf;
        this.indexBuffer = indexBuf;
        System.out.println("[LOD] Setting BufferHandle vertex (id=" + vertexBufferHandle.getHandleId() + ") and index (id=" + indexBufferHandle.getHandleId() + ")");
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