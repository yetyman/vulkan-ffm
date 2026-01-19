package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts base geometry to LOD models with automatic LOD generation
 */
public class LODConverter {
    private final Arena arena;
    private VkDevice device;
    private VkPhysicalDevice physicalDevice;
    
    public LODConverter(Arena arena) {
        this.arena = arena;
    }
    
    public void setVulkanDevice(VkDevice device, VkPhysicalDevice physicalDevice) {
        this.device = device;
        this.physicalDevice = physicalDevice;
    }
    
    public LODModel generateLODModel(float[] vertices, int[] indices) {
        List<LODLevel> lodLevels = new ArrayList<>();
        
        LODLevel lod0 = createLODLevel(vertices, indices, 10.0f, 1.0f);
        lodLevels.add(lod0);
        
        LODLevel lod1 = createDecimatedLOD(vertices, indices, 0.90f, 25.0f, lod0);
        lodLevels.add(lod1);
        
        LODLevel lod2 = createDecimatedLOD(vertices, indices, 0.70f, 50.0f, lod1);
        lodLevels.add(lod2);
        
        LODLevel lod3 = createDecimatedLOD(vertices, indices, 0.50f, 100.0f, lod2);
        lodLevels.add(lod3);
        
        LODLevel lod4 = createDecimatedLOD(vertices, indices, 0.30f, Float.MAX_VALUE, lod3);
        lodLevels.add(lod4);
        
        return new LODModel(arena, lodLevels);
    }
    
    private LODLevel createDecimatedLOD(float[] vertices, int[] indices, float detailFactor, float maxDistance, LODLevel fallback) {
        float[] decimatedVertices = decimateVertices(vertices, detailFactor);
        int[] decimatedIndices = decimateIndices(indices, decimatedVertices.length / 8);
        
        if (decimatedVertices.length == 0 || decimatedIndices.length == 0) {
            return new LODLevel(fallback.vertexBuffer(), fallback.indexBuffer(), 
                              fallback.indexCount(), maxDistance, fallback.triangleCount(), detailFactor);
        }
        
        return createLODLevel(decimatedVertices, decimatedIndices, maxDistance, detailFactor);
    }
    
    private LODLevel createLODLevel(float[] vertices, int[] indices, float maxDistance, float detailFactor) {
        if (device == null || physicalDevice == null || vertices.length == 0 || indices.length == 0) {
            return new LODLevel(MemorySegment.NULL, MemorySegment.NULL, indices.length, maxDistance, indices.length / 3, detailFactor);
        }
        
        VkBuffer vertexBuffer = VkBuffer.builder()
            .device(device)
            .physicalDevice(physicalDevice)
            .size(vertices.length * Float.BYTES)
            .vertexBuffer()
            .hostVisible()
            .build(arena);
        
        VkBuffer indexBuffer = VkBuffer.builder()
            .device(device)
            .physicalDevice(physicalDevice)
            .size(indices.length * Integer.BYTES)
            .indexBuffer()
            .hostVisible()
            .build(arena);
        
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment vertexMapped = vertexBuffer.map(tempArena);
            for (int i = 0; i < vertices.length; i++) {
                vertexMapped.setAtIndex(ValueLayout.JAVA_FLOAT, i, vertices[i]);
            }
            vertexBuffer.unmap();
            
            MemorySegment indexMapped = indexBuffer.map(tempArena);
            for (int i = 0; i < indices.length; i++) {
                indexMapped.setAtIndex(ValueLayout.JAVA_INT, i, indices[i]);
            }
            indexBuffer.unmap();
        }
        
        return new LODLevel(vertexBuffer.handle(), indexBuffer.handle(), indices.length, maxDistance, indices.length / 3, detailFactor);
    }
    
    /**
     * Simple vertex decimation - removes every nth vertex based on detail factor
     */
    private float[] decimateVertices(float[] vertices, float detailFactor) {
        int originalVertexCount = vertices.length / 8; // 8 components per vertex
        int targetVertexCount = (int)(originalVertexCount * detailFactor);
        
        if (targetVertexCount >= originalVertexCount) {
            return vertices.clone();
        }
        
        float[] decimated = new float[targetVertexCount * 8];
        float step = (float)originalVertexCount / targetVertexCount;
        
        for (int i = 0; i < targetVertexCount; i++) {
            int sourceIndex = (int)(i * step) * 8;
            for (int j = 0; j < 8; j++) {
                decimated[i * 8 + j] = vertices[sourceIndex + j];
            }
        }
        
        return decimated;
    }
    
    /**
     * Simple index decimation - adjusts indices for decimated vertex count
     */
    private int[] decimateIndices(int[] indices, int newVertexCount) {
        List<Integer> validIndices = new ArrayList<>();
        
        for (int i = 0; i < indices.length; i += 3) {
            // Check if all three vertices of triangle are within new vertex count
            if (indices[i] < newVertexCount && 
                indices[i + 1] < newVertexCount && 
                indices[i + 2] < newVertexCount) {
                validIndices.add(indices[i]);
                validIndices.add(indices[i + 1]);
                validIndices.add(indices[i + 2]);
            }
        }
        
        return validIndices.stream().mapToInt(Integer::intValue).toArray();
    }
}