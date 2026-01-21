package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.sample.complex.models.decimation.MeshSimplifier;
import io.github.yetyman.vulkan.sample.complex.models.decimation.MorphTargetGenerator;
import io.github.yetyman.vulkan.sample.complex.models.decimation.SimplifiedMesh;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts base geometry to LOD models with QEM-based decimation
 * Supports both eager loading (LODModel) and streaming (StreamingLODModel)
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
        Logger.debug("Generating LOD levels with QEM decimation and morph targets...");
        List<LODLevel> lodLevels = new ArrayList<>();
        MeshSimplifier simplifier = new MeshSimplifier();
        
        // Generate all LOD geometries first
        float[] ratios = {1.0f, 0.75f, 0.50f, 0.25f, 0.10f};
        float[] distances = {10.0f, 25.0f, 50.0f, 100.0f, Float.MAX_VALUE};
        SimplifiedMesh[] meshes = new SimplifiedMesh[5];
        
        meshes[0] = new SimplifiedMesh(vertices, indices);
        for (int i = 1; i < 5; i++) {
            meshes[i] = simplifier.simplify(vertices, indices, ratios[i]);
        }
        
        // Create LOD levels with morph targets
        for (int i = 0; i < 5; i++) {
            SimplifiedMesh current = meshes[i];
            SimplifiedMesh next = (i < 4) ? meshes[i + 1] : null;
            
            LODLevel lod = createLODLevelWithMorph(current.vertices(), current.indices(), 
                                                   next != null ? next.vertices() : null,
                                                   distances[i], ratios[i]);
            lodLevels.add(lod);
            Logger.debug("LOD" + i + ": " + current.triangleCount() + " triangles" +
                        (next != null ? " (with morph targets)" : ""));
        }
        
        return new LODModel(arena, lodLevels);
    }
    
    public StreamingLODModel generateStreamingLODModel(float[] vertices, int[] indices) {
        Logger.debug("Generating streaming LOD model with QEM decimation...");
        MeshSimplifier simplifier = new MeshSimplifier();
        
        float[] ratios = {1.0f, 0.75f, 0.50f, 0.25f, 0.10f};
        float[] distances = {10.0f, 25.0f, 50.0f, 100.0f, Float.MAX_VALUE};
        
        float[][] vertexData = new float[5][];
        int[][] indexData = new int[5][];
        float[][] morphData = new float[4][];
        
        SimplifiedMesh[] meshes = new SimplifiedMesh[5];
        meshes[0] = new SimplifiedMesh(vertices, indices);
        for (int i = 1; i < 5; i++) {
            meshes[i] = simplifier.simplify(vertices, indices, ratios[i]);
        }
        
        for (int i = 0; i < 5; i++) {
            vertexData[i] = meshes[i].vertices();
            indexData[i] = meshes[i].indices();
            if (i < 4) {
                morphData[i] = MorphTargetGenerator.generateMorphTargets(
                    meshes[i].vertices(), meshes[i + 1].vertices());
            }
            Logger.debug("LOD" + i + ": " + meshes[i].triangleCount() + " triangles (cached)");
        }
        
        return new StreamingLODModel(arena, device, physicalDevice,
                                    vertexData, indexData, morphData,
                                    distances, ratios);
    }    
    private LODLevel createLODLevelWithMorph(float[] vertices, int[] indices, float[] nextVertices,
                                            float maxDistance, float detailFactor) {
        if (device == null || physicalDevice == null || vertices.length == 0 || indices.length == 0) {
            return new LODLevel(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL,
                              indices.length, maxDistance, indices.length / 3, detailFactor);
        }
        
        // Create vertex and index buffers
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
        
        // Create morph target buffer if next LOD exists
        VkBuffer morphBuffer = null;
        if (nextVertices != null) {
            float[] morphTargets = MorphTargetGenerator.generateMorphTargets(vertices, nextVertices);
            morphBuffer = VkBuffer.builder()
                .device(device)
                .physicalDevice(physicalDevice)
                .size(morphTargets.length * Float.BYTES)
                .vertexBuffer()
                .hostVisible()
                .build(arena);
            
            try (Arena tempArena = Arena.ofConfined()) {
                MemorySegment morphMapped = morphBuffer.map(tempArena);
                for (int i = 0; i < morphTargets.length; i++) {
                    morphMapped.setAtIndex(ValueLayout.JAVA_FLOAT, i, morphTargets[i]);
                }
                morphBuffer.unmap();
            }
        }
        
        // Upload vertex and index data
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
        
        return new LODLevel(vertexBuffer.handle(), indexBuffer.handle(),
                          morphBuffer != null ? morphBuffer.handle() : MemorySegment.NULL,
                          indices.length, maxDistance, indices.length / 3, detailFactor);
    }
    

}