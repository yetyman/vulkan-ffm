package io.github.yetyman.vulkan.auto;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDescriptorSet;
import io.github.yetyman.vulkan.VkPipeline;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public interface ManagedBuffer extends AutoCloseable {
    
    // Data access (strategy-agnostic)
    void write(ByteBuffer data, long offset);
    TransferCompletion writeAsync(ByteBuffer data, long offset);
    ByteBuffer read(long offset, long size);
    void flush(); // No-op for coherent memory
    
    // Vulkan binding (usage-specific)
    MemorySegment getVkBuffer();
    void bindAsUniform(VkCommandBuffer commandBuffer, VkPipeline pipeline, int set, int binding, VkDescriptorSet descriptorSet);
    void bindAsStorage(VkCommandBuffer commandBuffer, VkPipeline pipeline, int set, int binding, VkDescriptorSet descriptorSet);
    void bindAsVertex(VkCommandBuffer commandBuffer, int binding);
    
    // Resource management
    long size();
    BufferUsage usage();
    MemoryStrategy memoryStrategy();
    
    @Override
    void close();
}