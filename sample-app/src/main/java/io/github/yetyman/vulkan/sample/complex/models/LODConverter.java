package io.github.yetyman.vulkan.sample.complex.models;

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
    
    public LODConverter(Arena arena) {
        this.arena = arena;
    }
    
    /**
     * Generate LOD levels from base geometry using progressive mesh decimation
     */
    public LODModel generateLODModel(float[] vertices, int[] indices) {
        List<LODLevel> lodLevels = new ArrayList<>();
        
        // LOD 0: Full detail (100%)
        lodLevels.add(createLODLevel(vertices, indices, 5.0f));
        
        // LOD 1: High detail (75%)
        float[] vertices1 = decimateVertices(vertices, 0.75f);
        int[] indices1 = decimateIndices(indices, vertices1.length / 3);
        lodLevels.add(createLODLevel(vertices1, indices1, 15.0f));
        
        // LOD 2: Medium detail (30%)
        float[] vertices2 = decimateVertices(vertices, 0.30f);
        int[] indices2 = decimateIndices(indices, vertices2.length / 3);
        lodLevels.add(createLODLevel(vertices2, indices2, 50.0f));
        
        // LOD 3: Low detail (10%)
        float[] vertices3 = decimateVertices(vertices, 0.10f);
        int[] indices3 = decimateIndices(indices, vertices3.length / 3);
        lodLevels.add(createLODLevel(vertices3, indices3, 150.0f));
        
        // LOD 4: Minimal detail (1%)
        float[] vertices4 = decimateVertices(vertices, 0.01f);
        int[] indices4 = decimateIndices(indices, vertices4.length / 3);
        lodLevels.add(createLODLevel(vertices4, indices4, Float.MAX_VALUE));
        
        return new LODModel(arena, lodLevels);
    }
    
    private LODLevel createLODLevel(float[] vertices, int[] indices, float maxDistance) {
        // Allocate vertex buffer
        MemorySegment vertexBuffer = arena.allocate(vertices.length * Float.BYTES);
        for (int i = 0; i < vertices.length; i++) {
            vertexBuffer.setAtIndex(ValueLayout.JAVA_FLOAT, i, vertices[i]);
        }
        
        // Allocate index buffer
        MemorySegment indexBuffer = arena.allocate(indices.length * Integer.BYTES);
        for (int i = 0; i < indices.length; i++) {
            indexBuffer.setAtIndex(ValueLayout.JAVA_INT, i, indices[i]);
        }
        
        return new LODLevel(vertexBuffer, indexBuffer, indices.length, maxDistance, indices.length / 3);
    }
    
    /**
     * Simple vertex decimation - removes every nth vertex based on detail factor
     */
    private float[] decimateVertices(float[] vertices, float detailFactor) {
        int originalVertexCount = vertices.length / 3; // Assuming 3 components per vertex
        int targetVertexCount = (int)(originalVertexCount * detailFactor);
        
        if (targetVertexCount >= originalVertexCount) {
            return vertices.clone();
        }
        
        float[] decimated = new float[targetVertexCount * 3];
        float step = (float)originalVertexCount / targetVertexCount;
        
        for (int i = 0; i < targetVertexCount; i++) {
            int sourceIndex = (int)(i * step) * 3;
            decimated[i * 3] = vertices[sourceIndex];
            decimated[i * 3 + 1] = vertices[sourceIndex + 1];
            decimated[i * 3 + 2] = vertices[sourceIndex + 2];
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