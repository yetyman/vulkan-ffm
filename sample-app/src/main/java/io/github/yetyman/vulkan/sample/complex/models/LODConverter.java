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
        int[] indices1 = decimateIndices(indices, vertices1.length / 8);
        lodLevels.add(createLODLevel(vertices1, indices1, 15.0f));
        
        // LOD 2: Medium detail (30%)
        float[] vertices2 = decimateVertices(vertices, 0.30f);
        int[] indices2 = decimateIndices(indices, vertices2.length / 8);
        lodLevels.add(createLODLevel(vertices2, indices2, 50.0f));
        
        // LOD 3: Low detail (10%)
        float[] vertices3 = decimateVertices(vertices, 0.10f);
        int[] indices3 = decimateIndices(indices, vertices3.length / 8);
        lodLevels.add(createLODLevel(vertices3, indices3, 150.0f));
        
        // LOD 4: Minimal detail (1%)
        float[] vertices4 = decimateVertices(vertices, 0.01f);
        int[] indices4 = decimateIndices(indices, vertices4.length / 8);
        lodLevels.add(createLODLevel(vertices4, indices4, Float.MAX_VALUE));
        
        return new LODModel(arena, lodLevels);
    }
    
    private LODLevel createLODLevel(float[] vertices, int[] indices, float maxDistance) {
        // Don't create GPU buffers here - they'll be created later by the streaming system
        // Just store the geometry data for later GPU upload
        return new LODLevel(MemorySegment.NULL, MemorySegment.NULL, indices.length, maxDistance, indices.length / 3);
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