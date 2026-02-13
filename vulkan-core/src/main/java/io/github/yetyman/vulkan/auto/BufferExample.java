package io.github.yetyman.vulkan.auto;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Example usage of the ManagedBuffer API showing how different strategies
 * are automatically selected and used with a common interface.
 */
public class BufferExample {
    
    public static void demonstrateBufferUsage(VkDevice device, 
                                              MemorySegment transferQueue, VkCommandPool commandPool) {
        try (Arena arena = Arena.ofConfined()) {
            
            // Example 1: Camera matrices (frequent CPU writes, small size)
            ManagedBuffer cameraBuffer = BufferFactory.createAutomatic(
                AccessFrequency.FRAME,     // CPU writes every frame
                AccessFrequency.NEVER,     // CPU never reads
                AccessFrequency.FRAME,     // GPU reads every frame
                AccessFrequency.NEVER,     // GPU never writes
                DataScale.TRIVIAL,         // Small data (<1KB)
                BufferUsage.UNIFORM,       // Uniform buffer usage
                device, transferQueue, commandPool, arena
            );
            // Strategy selected: MAPPED_UBO
            
            ByteBuffer cameraData = ByteBuffer.allocateDirect(64); // 4x4 matrix
            cameraBuffer.write(cameraData, 0);
            // No flush needed - coherent memory
            
            
            // Example 2: Static mesh data (rare CPU writes, medium size)
            ManagedBuffer meshBuffer = BufferFactory.createAutomatic(
                AccessFrequency.RARE,      // CPU writes rarely
                AccessFrequency.NEVER,     // CPU never reads
                AccessFrequency.FRAME,     // GPU reads every frame
                AccessFrequency.NEVER,     // GPU never writes
                DataScale.MEDIUM,          // Medium data (MB range)
                BufferUsage.VERTEX,        // Vertex buffer usage
                device, transferQueue, commandPool, arena
            );
            // Strategy selected: STAGING_TO_DEVICE_LOCAL
            
            ByteBuffer meshData = ByteBuffer.allocateDirect(1024 * 1024); // 1MB mesh
            meshBuffer.write(meshData, 0);
            // Automatically stages to device-local memory
            
            
            // Example 3: GPU particle system (GPU-owned data)
            ManagedBuffer particleBuffer = BufferFactory.createAutomatic(
                AccessFrequency.NEVER,     // CPU never writes
                AccessFrequency.NEVER,     // CPU never reads
                AccessFrequency.FRAME,     // GPU reads every frame
                AccessFrequency.FRAME,     // GPU writes every frame (compute shader)
                DataScale.SMALL,           // Small to medium data
                BufferUsage.STORAGE,       // Storage buffer usage
                device, transferQueue, commandPool, arena
            );
            // Strategy selected: DEVICE_LOCAL_SSBO
            
            
            // Example 4: Dynamic UI geometry (high-frequency CPU writes)
            ManagedBuffer uiBuffer = BufferFactory.createAutomatic(
                AccessFrequency.MULTI_FRAME, // CPU writes multiple times per frame
                AccessFrequency.NEVER,        // CPU never reads
                AccessFrequency.FRAME,        // GPU reads every frame
                AccessFrequency.NEVER,        // GPU never writes
                DataScale.SMALL,              // Small data
                BufferUsage.VERTEX,           // Vertex buffer usage
                device, transferQueue, commandPool, arena
            );
            // Strategy selected: RING_BUFFER_MAPPED
            
            // Ring buffer usage
            if (uiBuffer instanceof RingBuffer ringBuffer) {
                ByteBuffer uiData = ByteBuffer.allocateDirect(4096);
                ringBuffer.write(uiData, 0);
                ringBuffer.nextFrame(); // Switch to next buffer in ring
            }
            
            
            // Common binding interface regardless of strategy
            VkCommandBuffer commandBuffer = new VkCommandBuffer(MemorySegment.NULL); // Placeholder
            VkPipeline pipeline = null; // Placeholder
            VkDescriptorSet descriptorSet = new VkDescriptorSet(MemorySegment.NULL, device); // Placeholder
            
            // Bind buffers using common API
            cameraBuffer.bindAsUniform(commandBuffer, pipeline, 0, 0, descriptorSet);
            meshBuffer.bindAsVertex(commandBuffer, 0);
            particleBuffer.bindAsStorage(commandBuffer, pipeline, 1, 0, descriptorSet);
            uiBuffer.bindAsVertex(commandBuffer, 1);
            
            
            // Resource cleanup is automatic with try-with-resources
            cameraBuffer.close();
            meshBuffer.close();
            particleBuffer.close();
            uiBuffer.close();
        }
    }
    
    /**
     * Example of manual strategy selection when you know exactly what you need.
     */
    public static void demonstrateManualSelection(VkDevice device,
                                                   MemorySegment transferQueue, VkCommandPool commandPool) {
        try (Arena arena = Arena.ofConfined()) {
            
            // Manually select staging buffer for large static data
            ManagedBuffer staticBuffer = BufferFactory.create(
                MemoryStrategy.STAGING,
                16 * 1024 * 1024, // 16MB
                BufferUsage.MIXED, // Can be used as uniform, storage, or vertex
                device, transferQueue, commandPool, arena
            );
            
            // Same API regardless of manual or automatic selection
            ByteBuffer data = ByteBuffer.allocateDirect(1024);
            staticBuffer.write(data, 0);
            
            staticBuffer.close();
        }
    }
}