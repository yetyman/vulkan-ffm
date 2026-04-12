package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkRenderPass;
import io.github.yetyman.vulkan.VkFramebuffer;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Vulkan render pass command wrapper for beginning, ending, and transitioning between subpasses.
 */
public record VkRenderPassCmd(VkDevice device, MemorySegment renderPass, MemorySegment framebuffer, int x, int y, int width, int height,
                              MemorySegment clearValues, int clearValueCount, RenderPassType type, int contents) {
    
    public enum RenderPassType { BEGIN, END, NEXT_SUBPASS }
    
    // Static helpers for render pass operations
    public static void beginRenderPass(VkCommandBuffer cmd, VkRenderPass renderPass, VkFramebuffer framebuffer, 
                                       int x, int y, int width, int height, int contents) {
        beginRenderPass(cmd.handle(), renderPass.handle(), framebuffer.handle(), x, y, width, height, 
                        MemorySegment.NULL, 0, contents);
    }
    
    public static void beginRenderPass(MemorySegment cmd, MemorySegment renderPass, MemorySegment framebuffer,
                                       int x, int y, int width, int height, int contents) {
        beginRenderPass(cmd, renderPass, framebuffer, x, y, width, height, MemorySegment.NULL, 0, contents);
    }
    
    public static void beginRenderPass(VkCommandBuffer cmd, VkRenderPass renderPass, VkFramebuffer framebuffer,
                                       int x, int y, int width, int height, MemorySegment clearValues, int clearValueCount, int contents) {
        beginRenderPass(cmd.handle(), renderPass.handle(), framebuffer.handle(), x, y, width, height, clearValues, clearValueCount, contents);
    }
    
    public static void beginRenderPass(MemorySegment cmd, MemorySegment renderPass, MemorySegment framebuffer,
                                       int x, int y, int width, int height, MemorySegment clearValues, int clearValueCount, int contents) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment beginInfo = VkRenderPassBeginInfo.allocate(arena);
            VkRenderPassBeginInfo.sType(beginInfo, VkStructureType.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO.value());
            VkRenderPassBeginInfo.pNext(beginInfo, MemorySegment.NULL);
            VkRenderPassBeginInfo.renderPass(beginInfo, renderPass);
            VkRenderPassBeginInfo.framebuffer(beginInfo, framebuffer);
            
            MemorySegment renderArea = VkRenderPassBeginInfo.renderArea(beginInfo);
            MemorySegment offset = VkRect2D.offset(renderArea);
            MemorySegment extent = VkRect2D.extent(renderArea);
            VkOffset2D.x(offset, x);
            VkOffset2D.y(offset, y);
            VkExtent2D.width(extent, width);
            VkExtent2D.height(extent, height);
            
            VkRenderPassBeginInfo.clearValueCount(beginInfo, clearValueCount);
            VkRenderPassBeginInfo.pClearValues(beginInfo, clearValues);
            
            VulkanFFM.vkCmdBeginRenderPass(cmd, beginInfo, contents);
        }
    }
    
    public static void endRenderPass(VkCommandBuffer cmd) {
        endRenderPass(cmd.handle());
    }
    
    public static void endRenderPass(MemorySegment cmd) {
        VulkanFFM.vkCmdEndRenderPass(cmd);
    }
    
    public static void nextSubpass(VkCommandBuffer cmd, int contents) {
        nextSubpass(cmd.handle(), contents);
    }
    
    public static void nextSubpass(MemorySegment cmd, int contents) {
        VulkanFFM.vkCmdNextSubpass(cmd, contents);
    }
    
    public static void nextSubpass(VkCommandBuffer cmd) {
        nextSubpass(cmd, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE.value());
    }
    
    public static void nextSubpass(MemorySegment cmd) {
        nextSubpass(cmd, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE.value());
    }
    
    // Dynamic rendering methods (Vulkan 1.3 / VK_KHR_dynamic_rendering)
    public static void beginRendering(VkDevice device, VkCommandBuffer cmd, MemorySegment renderingInfo) {
        beginRendering(device, cmd.handle(), renderingInfo);
    }
    
    public static void beginRendering(VkDevice device, MemorySegment cmd, MemorySegment renderingInfo) {
        device.cmdBeginRendering(cmd, renderingInfo);
    }
    
    public static void endRendering(VkDevice device, VkCommandBuffer cmd) {
        endRendering(device, cmd.handle());
    }
    
    public static void endRendering(VkDevice device, MemorySegment cmd) {
        device.cmdEndRendering(cmd);
    }
    
    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }
    
    public void execute(MemorySegment cmd) {
        switch (type) {
            case BEGIN -> beginRenderPass(cmd, renderPass, framebuffer, x, y, width, height, clearValues, clearValueCount, contents);
            case END -> endRenderPass(cmd);
            case NEXT_SUBPASS -> nextSubpass(cmd, contents);
            default -> throw new UnsupportedOperationException("Render pass type not implemented: " + type);
        }
    }
    
    // Builder for fluent construction
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private VkDevice device;
        private MemorySegment renderPass;
        private MemorySegment framebuffer;
        private int x = 0, y = 0, width = 0, height = 0;
        private MemorySegment clearValues = MemorySegment.NULL;
        private int clearValueCount = 0;
        private RenderPassType type;
        private int contents = VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE.value();
        
        private Builder() {}
        
        public Builder device(VkDevice device) { this.device = device; return this; }
        public Builder renderPass(VkRenderPass renderPass) { this.renderPass = renderPass.handle(); return this; }
        public Builder renderPass(MemorySegment renderPass) { this.renderPass = renderPass; return this; }
        public Builder framebuffer(VkFramebuffer framebuffer) { this.framebuffer = framebuffer.handle(); return this; }
        public Builder framebuffer(MemorySegment framebuffer) { this.framebuffer = framebuffer; return this; }
        public Builder renderArea(int x, int y, int width, int height) { 
            this.x = x; this.y = y; this.width = width; this.height = height; 
            return this; 
        }
        public Builder clearValues(MemorySegment clearValues, int count) { 
            this.clearValues = clearValues; 
            this.clearValueCount = count; 
            return this; 
        }
        public Builder beginRenderPass() { this.type = RenderPassType.BEGIN; return this; }
        public Builder endRenderPass() { this.type = RenderPassType.END; return this; }
        public Builder nextSubpass() { this.type = RenderPassType.NEXT_SUBPASS; return this; }
        public Builder inline() { this.contents = VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE.value(); return this; }
        public Builder secondaryCommandBuffers() { this.contents = VkSubpassContents.VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS.value(); return this; }
        
        public VkRenderPassCmd build() {
            if (device == null) throw new IllegalStateException("device not set");
            return new VkRenderPassCmd(device, renderPass, framebuffer, x, y, width, height, clearValues, clearValueCount, type, contents);
        }
        
        public void render(VkCommandBuffer cmd) {
            build().execute(cmd);
        }
        
        public void render(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}