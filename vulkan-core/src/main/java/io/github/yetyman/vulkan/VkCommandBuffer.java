package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkCommandBuffer {
    
    public static Builder begin(MemorySegment commandBuffer) {
        return new Builder(commandBuffer);
    }
    
    public static class Builder {
        private final MemorySegment commandBuffer;
        private int flags = 0;
        
        private Builder(MemorySegment commandBuffer) {
            this.commandBuffer = commandBuffer;
        }
        
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        public Builder oneTimeSubmit() {
            this.flags |= VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
            return this;
        }
        
        public void execute(Arena arena) {
            MemorySegment beginInfo = VkCommandBufferBeginInfo.allocate(arena);
            VkCommandBufferBeginInfo.sType(beginInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            VkCommandBufferBeginInfo.pNext(beginInfo, MemorySegment.NULL);
            VkCommandBufferBeginInfo.flags(beginInfo, flags);
            VkCommandBufferBeginInfo.pInheritanceInfo(beginInfo, MemorySegment.NULL);
            
            VulkanExtensions.beginCommandBuffer(commandBuffer, beginInfo).check();
        }
    }
    
    public static RenderPassBuilder beginRenderPass(MemorySegment commandBuffer, MemorySegment renderPass, MemorySegment framebuffer) {
        return new RenderPassBuilder(commandBuffer, renderPass, framebuffer);
    }
    
    public static class RenderPassBuilder {
        private final MemorySegment commandBuffer;
        private final MemorySegment renderPass;
        private final MemorySegment framebuffer;
        private int x = 0, y = 0, width = 800, height = 600;
        private float[] clearColor = {0.0f, 0.0f, 0.0f, 1.0f};
        
        private RenderPassBuilder(MemorySegment commandBuffer, MemorySegment renderPass, MemorySegment framebuffer) {
            this.commandBuffer = commandBuffer;
            this.renderPass = renderPass;
            this.framebuffer = framebuffer;
        }
        
        public RenderPassBuilder renderArea(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }
        
        public RenderPassBuilder clearColor(float r, float g, float b, float a) {
            this.clearColor = new float[]{r, g, b, a};
            return this;
        }
        
        public void execute(Arena arena) {
            MemorySegment clearValue = arena.allocate(16);
            clearValue.set(ValueLayout.JAVA_FLOAT, 0, clearColor[0]);
            clearValue.set(ValueLayout.JAVA_FLOAT, 4, clearColor[1]);
            clearValue.set(ValueLayout.JAVA_FLOAT, 8, clearColor[2]);
            clearValue.set(ValueLayout.JAVA_FLOAT, 12, clearColor[3]);
            
            MemorySegment renderPassInfo = VkRenderPassBeginInfo.allocate(arena);
            VkRenderPassBeginInfo.sType(renderPassInfo, VkStructureType.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
            VkRenderPassBeginInfo.pNext(renderPassInfo, MemorySegment.NULL);
            VkRenderPassBeginInfo.renderPass(renderPassInfo, renderPass);
            VkRenderPassBeginInfo.framebuffer(renderPassInfo, framebuffer);
            MemorySegment renderArea = VkRenderPassBeginInfo.renderArea(renderPassInfo);
            MemorySegment offset = VkRect2D.offset(renderArea);
            VkOffset2D.x(offset, x);
            VkOffset2D.y(offset, y);
            MemorySegment extent = VkRect2D.extent(renderArea);
            VkExtent2D.width(extent, width);
            VkExtent2D.height(extent, height);
            VkRenderPassBeginInfo.clearValueCount(renderPassInfo, 1);
            VkRenderPassBeginInfo.pClearValues(renderPassInfo, clearValue);
            
            VulkanExtensions.cmdBeginRenderPass(commandBuffer, renderPassInfo, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE);
        }
    }
}