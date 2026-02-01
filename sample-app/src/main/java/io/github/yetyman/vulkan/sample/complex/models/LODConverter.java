package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.sample.complex.models.decimation.MeshSimplifier;
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
        Logger.debug("Generating LOD levels with QEM decimation...");
        List<LODLevel> lodLevels = new ArrayList<>();
        MeshSimplifier simplifier = new MeshSimplifier();
        
        float[] ratios = {1.0f, 0.65f, 0.40f, 0.20f, 0.08f};
        float[] distances = {10.0f, 25.0f, 50.0f, 100.0f, Float.MAX_VALUE};
        
        for (int i = 0; i < 5; i++) {
            SimplifiedMesh mesh = (i == 0) ? new SimplifiedMesh(vertices, indices) 
                                           : simplifier.simplify(vertices, indices, ratios[i]);
            LODLevel lod = createLODLevel(mesh.vertices(), mesh.indices(), distances[i], ratios[i]);
            lodLevels.add(lod);
            Logger.debug("LOD" + i + ": " + mesh.triangleCount() + " triangles");
        }
        
        return new LODModel(arena, lodLevels);
    }
    
    public StreamingLODModel generateStreamingLODModel(float[] vertices, int[] indices) {
        Logger.debug("Generating streaming LOD model with QEM decimation...");
        MeshSimplifier simplifier = new MeshSimplifier();
        
        float[] ratios = {1.0f, 0.65f, 0.40f, 0.20f, 0.08f};
        float[] distances = {10.0f, 25.0f, 50.0f, 100.0f, Float.MAX_VALUE};
        
        float[][] vertexData = new float[5][];
        int[][] indexData = new int[5][];
        
        SimplifiedMesh[] meshes = new SimplifiedMesh[5];
        meshes[0] = new SimplifiedMesh(vertices, indices);
        for (int i = 1; i < 5; i++) {
            meshes[i] = simplifier.simplify(vertices, indices, ratios[i]);
        }
        
        for (int i = 0; i < 5; i++) {
            vertexData[i] = meshes[i].vertices();
            indexData[i] = meshes[i].indices();
            Logger.debug("LOD" + i + ": " + meshes[i].triangleCount() + " triangles (cached)");
        }
        
        return new StreamingLODModel(arena, device, physicalDevice,
                                    vertexData, indexData, distances, ratios);
    }    
    private LODLevel createLODLevel(float[] vertices, int[] indices, float maxDistance, float detailFactor) {
        if (device == null || physicalDevice == null || vertices.length == 0 || indices.length == 0) {
            return new LODLevel(MemorySegment.NULL, MemorySegment.NULL, indices.length, maxDistance, 
                              indices.length / 3, detailFactor);
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
        
        return new LODLevel(vertexBuffer.handle(), indexBuffer.handle(), indices.length, 
                          maxDistance, indices.length / 3, detailFactor);
    }
    

}