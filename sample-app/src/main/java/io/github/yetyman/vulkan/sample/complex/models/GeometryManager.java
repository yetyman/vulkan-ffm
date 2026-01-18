package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Manages GPU geometry buffers and streaming - independent of models and rendering
 */
public class GeometryManager {
    private final AsyncGeometryStreamer geometryStreamer;
    private MemorySegment staticVertexBuffer = MemorySegment.NULL;
    private MemorySegment staticInstanceBuffer = MemorySegment.NULL;
    
    public GeometryManager(Arena arena, VkDevice device, VkPhysicalDevice physicalDevice, MemorySegment queue) {
        this.geometryStreamer = new AsyncGeometryStreamer(arena, device, physicalDevice, queue);
    }
    
    public void updateStreaming(ModelData[] modelDataArray, float[] cameraPosition) {
        geometryStreamer.updateStreaming(modelDataArray, cameraPosition);
    }
    
    public void processPendingCopies(int maxCopies, ModelData[] modelDataArray) {
        geometryStreamer.processPendingCopies(maxCopies, modelDataArray);
    }
    
    public MemorySegment createTestBuffer(float[] data) {
        return geometryStreamer.createTestBuffer(data);
    }
    
    public MemorySegment getStaticVertexBuffer() {
        if (staticVertexBuffer.equals(MemorySegment.NULL)) {
            float[] vertices = {
                -0.8f, -0.8f, 0.2f,  0.0f, 0.0f, 1.0f,  0.0f, 0.0f,
                 0.8f, -0.8f, 0.2f,  0.0f, 0.0f, 1.0f,  1.0f, 0.0f,
                 0.0f,  0.8f, 0.2f,  0.0f, 0.0f, 1.0f,  0.5f, 1.0f
            };
            staticVertexBuffer = createTestBuffer(vertices);
        }
        return staticVertexBuffer;
    }
    
    public MemorySegment getStaticInstanceBuffer() {
        if (staticInstanceBuffer.equals(MemorySegment.NULL)) {
            float[] matrix = {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
            };
            staticInstanceBuffer = createTestBuffer(matrix);
        }
        return staticInstanceBuffer;
    }
    
    public void cleanup() {
        geometryStreamer.shutdown();
        Logger.info("GeometryManager cleanup complete");
    }
}
