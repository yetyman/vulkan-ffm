package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.HashMap;
import java.util.Map;

/**
 * High-level mesh abstraction combining vertex and index buffers.
 * Provides convenient draw command generation and vertex format management.
 * 
 * Example usage:
 * <pre>{@code
 * // Create vertex format with multiple bindings
 * VkVertexFormat format = VkVertexFormat.builder()
 *     .perVertexBinding(0, 32)    // Static geometry
 *     .perInstanceBinding(1, 64)  // Instance data
 *     .vec3Attribute(0, 0, 0)     // position
 *     .vec3Attribute(1, 0, 12)    // normal
 *     .vec2Attribute(2, 0, 24)    // texCoord
 *     .mat4Attribute(3, 1, 0)     // instance transform
 *     .build();
 * 
 * // Create mesh with multiple vertex buffers
 * VkMesh mesh = VkMesh.builder()
 *     .device(device)
 *     .physicalDevice(physicalDevice)
 *     .vertexFormat(format)
 *     .vertexBuffer(0, geometryBuffer, vertexCount)
 *     .vertexBuffer(1, instanceBuffer, instanceCount)
 *     .indexBuffer(indexBuffer, indexCount)
 *     .build(arena);
 * 
 * // Render with instancing
 * mesh.bind(commandBuffer);
 * mesh.draw(commandBuffer); // Draws instanceCount instances
 * }</pre>
 */
public class VkMesh implements AutoCloseable {
    private final Map<Integer, VertexBufferBinding> vertexBuffers = new HashMap<>();
    private final VkBuffer indexBuffer;
    private final VkVertexFormat vertexFormat;
    private final int indexCount;
    private final int indexType;
    
    private VkMesh(Map<Integer, VertexBufferBinding> vertexBuffers, VkBuffer indexBuffer,
                   VkVertexFormat vertexFormat, int indexCount, int indexType) {
        this.vertexBuffers.putAll(vertexBuffers);
        this.indexBuffer = indexBuffer;
        this.vertexFormat = vertexFormat;
        this.indexCount = indexCount;
        this.indexType = indexType;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return vertex buffer for the specified binding */
    public VkBuffer getVertexBuffer(int binding) { 
        VertexBufferBinding vbb = vertexBuffers.get(binding);
        return vbb != null ? vbb.buffer : null;
    }
    
    /** @return the index buffer (may be null) */
    public VkBuffer indexBuffer() { return indexBuffer; }
    
    /** @return the vertex format */
    public VkVertexFormat vertexFormat() { return vertexFormat; }
    
    /** @return vertex count for the specified binding */
    public int getVertexCount(int binding) {
        VertexBufferBinding vbb = vertexBuffers.get(binding);
        return vbb != null ? vbb.count : 0;
    }
    
    /** @return number of indices */
    public int indexCount() { return indexCount; }
    
    /** @return whether this mesh uses indices */
    public boolean isIndexed() { return indexBuffer != null; }
    
    /**
     * Binds vertex and index buffers to the command buffer.
     */
    public void bind(MemorySegment commandBuffer) {
        if (!vertexBuffers.isEmpty()) {
            // Find max binding to determine array size
            int maxBinding = vertexBuffers.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
            
            MemorySegment bufferArray = Arena.ofAuto().allocate(ValueLayout.ADDRESS, maxBinding + 1);
            MemorySegment offsetArray = Arena.ofAuto().allocate(ValueLayout.JAVA_LONG, maxBinding + 1);
            
            // Fill arrays (null buffers will be MemorySegment.NULL)
            for (int i = 0; i <= maxBinding; i++) {
                VertexBufferBinding binding = vertexBuffers.get(i);
                if (binding != null) {
                    bufferArray.setAtIndex(ValueLayout.ADDRESS, i, binding.buffer.handle());
                    offsetArray.setAtIndex(ValueLayout.JAVA_LONG, i, 0L);
                } else {
                    bufferArray.setAtIndex(ValueLayout.ADDRESS, i, MemorySegment.NULL);
                    offsetArray.setAtIndex(ValueLayout.JAVA_LONG, i, 0L);
                }
            }
            
            VulkanExtensions.cmdBindVertexBuffers(commandBuffer, 0, maxBinding + 1, bufferArray, offsetArray);
        }
        
        // Bind index buffer if present
        if (indexBuffer != null) {
            VulkanExtensions.cmdBindIndexBuffer(commandBuffer, indexBuffer.handle(), 0, indexType);
        }
    }
    
    /**
     * Records a draw command for this mesh.
     */
    public void draw(MemorySegment commandBuffer) {
        // Calculate instance count from per-instance bindings
        int instanceCount = calculateInstanceCount();
        draw(commandBuffer, instanceCount, 0);
    }
    
    /**
     * Records a draw command with custom instance parameters.
     */
    public void draw(MemorySegment commandBuffer, int instanceCount, int firstInstance) {
        int vertexCount = calculateVertexCount();
        
        if (isIndexed()) {
            VulkanExtensions.cmdDrawIndexed(commandBuffer, indexCount, instanceCount, 0, 0, firstInstance);
        } else {
            VulkanExtensions.cmdDraw(commandBuffer, vertexCount, instanceCount, 0, firstInstance);
        }
    }
    
    private int calculateInstanceCount() {
        // Find per-instance bindings and return their count
        for (var binding : vertexFormat.getBindings()) {
            if (binding.inputRate() == VkVertexInputRate.VK_VERTEX_INPUT_RATE_INSTANCE) {
                VertexBufferBinding vbb = vertexBuffers.get(binding.binding());
                if (vbb != null) {
                    return vbb.count;
                }
            }
        }
        return 1; // Default to 1 instance if no per-instance data
    }
    
    private int calculateVertexCount() {
        // Find per-vertex bindings and return their count
        for (var binding : vertexFormat.getBindings()) {
            if (binding.inputRate() == VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX) {
                VertexBufferBinding vbb = vertexBuffers.get(binding.binding());
                if (vbb != null) {
                    return vbb.count;
                }
            }
        }
        return 0;
    }
    
    /**
     * Records a draw command with custom parameters.
     */
    public void drawIndexed(MemorySegment commandBuffer, int indexCount, int instanceCount,
                           int firstIndex, int vertexOffset, int firstInstance) {
        if (!isIndexed()) {
            throw new IllegalStateException("Mesh is not indexed");
        }
        VulkanExtensions.cmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }
    
    @Override
    public void close() {
        if (indexBuffer != null) {
            indexBuffer.close();
        }
        for (VertexBufferBinding binding : vertexBuffers.values()) {
            if (binding.buffer != null) {
                binding.buffer.close();
            }
        }
    }
    
    private record VertexBufferBinding(VkBuffer buffer, int count) {}
    
    public static class Builder {
        private MemorySegment device;
        private MemorySegment physicalDevice;
        private VkVertexFormat vertexFormat;
        private final Map<Integer, VertexBufferData> vertexBuffers = new HashMap<>();
        private MemorySegment indexData;
        private int indexType = VkIndexType.VK_INDEX_TYPE_UINT32;
        private int indexCount;
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public Builder physicalDevice(MemorySegment physicalDevice) {
            this.physicalDevice = physicalDevice;
            return this;
        }
        
        public Builder vertexFormat(VkVertexFormat format) {
            this.vertexFormat = format;
            return this;
        }
        
        public Builder vertexBuffer(int binding, MemorySegment data, int count) {
            vertexBuffers.put(binding, new VertexBufferData(data, count));
            return this;
        }
        
        public Builder indexBuffer(MemorySegment data, int count) {
            this.indexData = data;
            this.indexCount = count;
            this.indexType = VkIndexType.VK_INDEX_TYPE_UINT32;
            return this;
        }
        
        public Builder indexBuffer16(MemorySegment data, int count) {
            this.indexData = data;
            this.indexCount = count;
            this.indexType = VkIndexType.VK_INDEX_TYPE_UINT16;
            return this;
        }
        
        public VkMesh build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            if (physicalDevice == null) throw new IllegalStateException("physicalDevice not set");
            if (vertexFormat == null) throw new IllegalStateException("vertexFormat not set");
            if (vertexBuffers.isEmpty()) throw new IllegalStateException("no vertex buffers set");
            
            Map<Integer, VertexBufferBinding> bufferBindings = new HashMap<>();
            
            // Create vertex buffers
            for (var entry : vertexBuffers.entrySet()) {
                int binding = entry.getKey();
                VertexBufferData data = entry.getValue();
                
                VkBuffer buffer = VkBuffer.builder()
                    .device(device)
                    .size(data.data.byteSize())
                    .vertexBuffer()
                    .transferDst()
                    .build(arena);
                
                bufferBindings.put(binding, new VertexBufferBinding(buffer, data.count));
            }
            
            // Create index buffer if provided
            VkBuffer indexBuffer = null;
            if (indexData != null && indexCount > 0) {
                indexBuffer = VkBuffer.builder()
                    .device(device)
                    .size(indexData.byteSize())
                    .indexBuffer()
                    .transferDst()
                    .build(arena);
            }
            
            return new VkMesh(bufferBindings, indexBuffer, vertexFormat, indexCount, indexType);
        }
        
        private record VertexBufferData(MemorySegment data, int count) {}
    }
}