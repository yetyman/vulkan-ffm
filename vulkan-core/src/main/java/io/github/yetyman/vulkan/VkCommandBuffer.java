package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkCommandBuffer {
    private final MemorySegment handle;
    
    public VkCommandBuffer(MemorySegment handle) {
        this.handle = handle;
    }
    
    public MemorySegment handle() { return handle; }
    
    public void copyBuffer(VkBuffer srcBuffer, VkBuffer dstBuffer, long srcOffset, long dstOffset, long size) {
        MemorySegment copyRegion = Arena.ofAuto().allocate(24);
        copyRegion.set(ValueLayout.JAVA_LONG, 0, srcOffset);
        copyRegion.set(ValueLayout.JAVA_LONG, 8, dstOffset);
        copyRegion.set(ValueLayout.JAVA_LONG, 16, size);
        VulkanFFM.vkCmdCopyBuffer(handle, srcBuffer.handle(), dstBuffer.handle(), 1, copyRegion);
    }
    
    public void end() {
        VulkanFFM.vkEndCommandBuffer(handle);
    }
    
    public static Builder begin(VkCommandBuffer commandBuffer) {
        return new Builder(commandBuffer.handle);
    }
    
    public static Builder begin(MemorySegment commandBuffer) {
        return new Builder(commandBuffer);
    }
    
    public static Builder begin(MemorySegment commandBuffer, int usageFlags) {
        return new Builder(commandBuffer).flags(usageFlags);
    }
    
    public static VkResult reset(VkCommandBuffer commandBuffer) {
        int result = VulkanFFM.vkResetCommandBuffer(commandBuffer.handle, 0);
        return VkResult.fromInt(result);
    }
    
    public static VkResult reset(MemorySegment commandBuffer) {
        int result = VulkanFFM.vkResetCommandBuffer(commandBuffer, 0);
        return VkResult.fromInt(result);
    }
    
    public static VkResult reset(MemorySegment commandBuffer, int flags) {
        int result = VulkanFFM.vkResetCommandBuffer(commandBuffer, flags);
        return VkResult.fromInt(result);
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
            this.flags |= VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value();
            return this;
        }
        
        private MemorySegment inheritanceRenderPass;
        private int inheritanceSubpass;
        private MemorySegment inheritanceFramebuffer;
        
        public Builder inheritanceInfo(MemorySegment renderPass, int subpass, MemorySegment framebuffer) {
            this.inheritanceRenderPass = renderPass;
            this.inheritanceSubpass = subpass;
            this.inheritanceFramebuffer = framebuffer;
            return this;
        }
        
        public void execute(Arena arena) {
            MemorySegment beginInfo = VkCommandBufferBeginInfo.allocate(arena);
            VkCommandBufferBeginInfo.sType(beginInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO.value());
            VkCommandBufferBeginInfo.pNext(beginInfo, MemorySegment.NULL);
            VkCommandBufferBeginInfo.flags(beginInfo, flags);
            
            MemorySegment inheritanceInfo = MemorySegment.NULL;
            if (inheritanceRenderPass != null) {
                inheritanceInfo = VkCommandBufferInheritanceInfo.allocate(arena);
                VkCommandBufferInheritanceInfo.sType(inheritanceInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO.value());
                VkCommandBufferInheritanceInfo.pNext(inheritanceInfo, MemorySegment.NULL);
                VkCommandBufferInheritanceInfo.renderPass(inheritanceInfo, inheritanceRenderPass);
                VkCommandBufferInheritanceInfo.subpass(inheritanceInfo, inheritanceSubpass);
                VkCommandBufferInheritanceInfo.framebuffer(inheritanceInfo, inheritanceFramebuffer != null ? inheritanceFramebuffer : MemorySegment.NULL);
            }
            
            VkCommandBufferBeginInfo.pInheritanceInfo(beginInfo, inheritanceInfo);
            
            int result = VulkanFFM.vkBeginCommandBuffer(commandBuffer, beginInfo);
            VkResult.fromInt(result).check();
        }
    }
    
    public static RenderPassBuilder beginRenderPass(VkCommandBuffer commandBuffer, MemorySegment renderPass, MemorySegment framebuffer) {
        return new RenderPassBuilder(commandBuffer.handle, renderPass, framebuffer);
    }
    
    public static RenderPassBuilder beginRenderPass(MemorySegment commandBuffer, MemorySegment renderPass, MemorySegment framebuffer) {
        return new RenderPassBuilder(commandBuffer, renderPass, framebuffer);
    }
    
    public static class RenderPassBuilder {
        private final MemorySegment commandBuffer;
        private final MemorySegment renderPass;
        private final MemorySegment framebuffer;
        private int x = 0, y = 0, width = 800, height = 600;
        private final VkClearValues clearValues = VkClearValues.create();
        private int subpassContents = VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE.value();
        
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
            clearValues.color(r, g, b, a);
            return this;
        }
        
        public RenderPassBuilder clearDepth(float depth, int stencil) {
            clearValues.depthStencil(depth, stencil);
            return this;
        }
        
        public RenderPassBuilder contents(int contents) {
            this.subpassContents = contents;
            return this;
        }
        
        public void execute(Arena arena) {
            MemorySegment clearValuesArray = clearValues.build(arena);
            
            MemorySegment renderPassInfo = VkRenderPassBeginInfo.allocate(arena);
            VkRenderPassBeginInfo.sType(renderPassInfo, VkStructureType.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO.value());
            VkRenderPassBeginInfo.pNext(renderPassInfo, MemorySegment.NULL);
            VkRenderPassBeginInfo.renderPass(renderPassInfo, renderPass);
            VkRenderPassBeginInfo.framebuffer(renderPassInfo, framebuffer);
            MemorySegment renderArea = io.github.yetyman.vulkan.VkRect2D.builder()
                .offset(x, y)
                .extent(width, height)
                .build(arena);
            MemorySegment.copy(renderArea, 0, VkRenderPassBeginInfo.renderArea(renderPassInfo), 0, renderArea.byteSize());
            VkRenderPassBeginInfo.clearValueCount(renderPassInfo, clearValues.count());
            VkRenderPassBeginInfo.pClearValues(renderPassInfo, clearValuesArray);
            
            VulkanFFM.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, subpassContents);
        }
    }
}