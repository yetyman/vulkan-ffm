package io.github.yetyman.vulkan.auto;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import java.nio.ByteBuffer;

import static io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits.*;
import static io.github.yetyman.vulkan.enums.VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static io.github.yetyman.vulkan.enums.VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static io.github.yetyman.vulkan.enums.VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static io.github.yetyman.vulkan.enums.VkStructureType.*;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdBindDescriptorSets;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkUpdateDescriptorSets;

public abstract class AbstractBuffer implements ManagedBuffer {
    protected final VkDevice device;
    protected final VkPhysicalDevice physicalDevice;
    protected final Arena arena;
    protected final long size;
    protected final BufferUsage usage;
    protected final MemoryStrategy memoryStrategy;
    
    protected VkBuffer vkBuffer;
    protected boolean closed = false;
    
    protected AbstractBuffer(VkDevice device, Arena arena, 
                           long size, BufferUsage usage, MemoryStrategy memoryStrategy) {
        this.device = device;
        this.physicalDevice = device.physicalDevice();
        this.arena = arena;
        this.size = size;
        this.usage = usage;
        this.memoryStrategy = memoryStrategy;
    }
    
    @Override
    public MemorySegment getVkBuffer() {
        return vkBuffer != null ? vkBuffer.handle() : MemorySegment.NULL;
    }
    
    @Override
    public long size() {
        return size;
    }
    
    @Override
    public BufferUsage usage() {
        return usage;
    }
    
    public MemoryStrategy memoryStrategy() {
        return memoryStrategy;
    }
    
    @Override
    public void bindAsUniform(VkCommandBuffer commandBuffer, VkPipeline pipeline, int set, int binding, VkDescriptorSet descriptorSet) {
        if ((usage.toVkFlags() & VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT.value()) == 0) {
            throw new IllegalStateException("Buffer not created with UNIFORM usage");
        }
        
        descriptorSet.updateBuffer(binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER.value(), getVkBuffer(), 0, size, arena);
        descriptorSet.bind(commandBuffer.handle(), VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.layout(), set, arena);
    }
    
    @Override
    public void bindAsStorage(VkCommandBuffer commandBuffer, VkPipeline pipeline, int set, int binding, VkDescriptorSet descriptorSet) {
        if ((usage.toVkFlags() & VK_BUFFER_USAGE_STORAGE_BUFFER_BIT.value()) == 0) {
            throw new IllegalStateException("Buffer not created with STORAGE usage");
        }
        
        descriptorSet.updateBuffer(binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value(), getVkBuffer(), 0, size, arena);
        descriptorSet.bind(commandBuffer.handle(), VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.layout(), set, arena);
    }
    
    @Override
    public void bindAsVertex(VkCommandBuffer commandBuffer, int binding) {
        if ((usage.toVkFlags() & VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.value()) == 0) {
            throw new IllegalStateException("Buffer not created with VERTEX usage");
        }
        VkVertexBufferBinding.create()
            .buffer(getVkBuffer())
            .bind(commandBuffer.handle(), binding, arena);
    }
    
    @Override
    public void close() {
        if (!closed) {
            if (vkBuffer != null) vkBuffer.close();
            closed = true;
        }
    }
    
    protected void createBuffer(int usageFlags, int memoryProperties) {
        vkBuffer = VkBuffer.builder()
            .device(device)
            .size(size)
            .usage(usageFlags)
            .memoryProperties(memoryProperties)
            .build(arena);
    }
    
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        write(data, offset);
        return null; // No async for base implementation
    }
}