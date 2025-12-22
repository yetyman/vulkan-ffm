package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkCommandBuffer {
    
    public static Builder begin(MemorySegment commandBuffer) {
        return new Builder(commandBuffer);
    }
    
    public static Builder begin(MemorySegment commandBuffer, int usageFlags) {
        return new Builder(commandBuffer).flags(usageFlags);
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
            this.flags |= VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
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
            VkCommandBufferBeginInfo.sType(beginInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            VkCommandBufferBeginInfo.pNext(beginInfo, MemorySegment.NULL);
            VkCommandBufferBeginInfo.flags(beginInfo, flags);
            
            MemorySegment inheritanceInfo = MemorySegment.NULL;
            if (inheritanceRenderPass != null) {
                inheritanceInfo = VkCommandBufferInheritanceInfo.allocate(arena);
                VkCommandBufferInheritanceInfo.sType(inheritanceInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO);
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
    
    public static RenderPassBuilder beginRenderPass(MemorySegment commandBuffer, MemorySegment renderPass, MemorySegment framebuffer) {
        return new RenderPassBuilder(commandBuffer, renderPass, framebuffer);
    }
    
    public static class RenderPassBuilder {
        private final MemorySegment commandBuffer;
        private final MemorySegment renderPass;
        private final MemorySegment framebuffer;
        private int x = 0, y = 0, width = 800, height = 600;
        private float[] clearColorValues = {0.0f, 0.0f, 0.0f, 1.0f};
        private float depthValue = 1.0f;
        private int stencilValue = 0;
        private boolean hasDepthClear = false;
        private int subpassContents = VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE;
        
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
            this.clearColorValues = new float[]{r, g, b, a};
            return this;
        }
        
        public RenderPassBuilder clearDepth(float depth, int stencil) {
            this.depthValue = depth;
            this.stencilValue = stencil;
            this.hasDepthClear = true;
            return this;
        }
        
        public RenderPassBuilder contents(int contents) {
            this.subpassContents = contents;
            return this;
        }
        
        public void execute(Arena arena) {
            int clearValueCount = hasDepthClear ? 2 : 1;
            MemorySegment clearValues = arena.allocate(VkClearValue.layout(), clearValueCount);
            
            // Color clear value
            MemorySegment colorClear = VkClearValue.color(arena, 
                clearColorValues[0], clearColorValues[1], clearColorValues[2], clearColorValues[3]);
            MemorySegment.copy(colorClear, 0, clearValues, 0, VkClearValue.layout().byteSize());
            
            // Depth clear value if needed
            if (hasDepthClear) {
                MemorySegment depthClear = VkClearValue.depthStencil(arena, depthValue, stencilValue);
                MemorySegment.copy(depthClear, 0, clearValues, VkClearValue.layout().byteSize(), VkClearValue.layout().byteSize());
            }
            
            MemorySegment renderPassInfo = VkRenderPassBeginInfo.allocate(arena);
            VkRenderPassBeginInfo.sType(renderPassInfo, VkStructureType.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
            VkRenderPassBeginInfo.pNext(renderPassInfo, MemorySegment.NULL);
            VkRenderPassBeginInfo.renderPass(renderPassInfo, renderPass);
            VkRenderPassBeginInfo.framebuffer(renderPassInfo, framebuffer);
            MemorySegment renderArea = io.github.yetyman.vulkan.VkRect2D.builder()
                .offset(x, y)
                .extent(width, height)
                .build(arena);
            MemorySegment.copy(renderArea, 0, VkRenderPassBeginInfo.renderArea(renderPassInfo), 0, renderArea.byteSize());
            VkRenderPassBeginInfo.clearValueCount(renderPassInfo, clearValueCount);
            VkRenderPassBeginInfo.pClearValues(renderPassInfo, clearValues);
            
            VulkanFFM.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, subpassContents);
        }
    }
}