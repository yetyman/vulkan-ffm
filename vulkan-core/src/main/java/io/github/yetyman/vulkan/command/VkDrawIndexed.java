package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.generated.VulkanFFM;
import java.lang.foreign.*;

/**
 * Vulkan indexed draw command wrapper supporting both immediate execution and reusable command objects.
 */
public record VkDrawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
    
    // Static helpers (zero allocation)
    public static void drawIndexed(VkCommandBuffer cmd, int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        VulkanFFM.vkCmdDrawIndexed(cmd.handle(), indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }
    
    public static void drawIndexed(MemorySegment cmd, int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        VulkanFFM.vkCmdDrawIndexed(cmd, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }
    
    public static void drawIndexed(VkCommandBuffer cmd, int indexCount, int instanceCount) {
        drawIndexed(cmd, indexCount, instanceCount, 0, 0, 0);
    }
    
    public static void drawIndexed(MemorySegment cmd, int indexCount, int instanceCount) {
        drawIndexed(cmd, indexCount, instanceCount, 0, 0, 0);
    }
    
    public static void drawIndexed(VkCommandBuffer cmd, int indexCount) {
        drawIndexed(cmd, indexCount, 1, 0, 0, 0);
    }
    
    public static void drawIndexed(MemorySegment cmd, int indexCount) {
        drawIndexed(cmd, indexCount, 1, 0, 0, 0);
    }
    
    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        drawIndexed(cmd, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }
    
    public void execute(MemorySegment cmd) {
        drawIndexed(cmd, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }
    
    // Builder for fluent construction
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private int indexCount = 0;
        private int instanceCount = 1;
        private int firstIndex = 0;
        private int vertexOffset = 0;
        private int firstInstance = 0;
        
        private Builder() {}
        
        public Builder indexCount(int count) { this.indexCount = count; return this; }
        public Builder instanceCount(int count) { this.instanceCount = count; return this; }
        public Builder firstIndex(int first) { this.firstIndex = first; return this; }
        public Builder vertexOffset(int offset) { this.vertexOffset = offset; return this; }
        public Builder firstInstance(int first) { this.firstInstance = first; return this; }
        
        public VkDrawIndexed build() {
            return new VkDrawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
        
        public void drawIndexed(VkCommandBuffer cmd) {
            VkDrawIndexed.drawIndexed(cmd, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
        
        public void drawIndexed(MemorySegment cmd) {
            VkDrawIndexed.drawIndexed(cmd, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
        }
    }
}