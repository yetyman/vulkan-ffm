package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkComputePipeline;
import io.github.yetyman.vulkan.generated.VulkanFFM;
import java.lang.foreign.*;

/**
 * Vulkan push constants command wrapper for updating push constant data.
 */
public record VkPushConstantsCmd(MemorySegment pipelineLayout, int stageFlags, int offset, MemorySegment data, long size) {
    
    // Static helpers for push constants
    public static void pushConstants(VkCommandBuffer cmd, MemorySegment pipelineLayout, int stageFlags, int offset, MemorySegment data, long size) {
        pushConstants(cmd.handle(), pipelineLayout, stageFlags, offset, data, size);
    }
    
    public static void pushConstants(MemorySegment cmd, MemorySegment pipelineLayout, int stageFlags, int offset, MemorySegment data, long size) {
        VulkanFFM.vkCmdPushConstants(cmd, pipelineLayout, stageFlags, offset, (int)size, data);
    }
    
    public static void pushConstants(VkCommandBuffer cmd, VkPipeline pipeline, int stageFlags, int offset, MemorySegment data, long size) {
        pushConstants(cmd, pipeline.layout(), stageFlags, offset, data, size);
    }
    
    public static void pushConstants(MemorySegment cmd, VkPipeline pipeline, int stageFlags, int offset, MemorySegment data, long size) {
        pushConstants(cmd, pipeline.layout(), stageFlags, offset, data, size);
    }
    
    public static void pushConstants(VkCommandBuffer cmd, VkComputePipeline pipeline, int stageFlags, int offset, MemorySegment data, long size) {
        pushConstants(cmd, pipeline.layout(), stageFlags, offset, data, size);
    }
    
    public static void pushConstants(MemorySegment cmd, VkComputePipeline pipeline, int stageFlags, int offset, MemorySegment data, long size) {
        pushConstants(cmd, pipeline.layout(), stageFlags, offset, data, size);
    }
    
    // Convenience overloads for common data types
    public static void pushInt(VkCommandBuffer cmd, MemorySegment pipelineLayout, int stageFlags, int offset, int value) {
        pushInt(cmd.handle(), pipelineLayout, stageFlags, offset, value);
    }
    
    public static void pushInt(MemorySegment cmd, MemorySegment pipelineLayout, int stageFlags, int offset, int value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(ValueLayout.JAVA_INT);
            data.set(ValueLayout.JAVA_INT, 0, value);
            pushConstants(cmd, pipelineLayout, stageFlags, offset, data, 4);
        }
    }
    
    public static void pushFloat(VkCommandBuffer cmd, MemorySegment pipelineLayout, int stageFlags, int offset, float value) {
        pushFloat(cmd.handle(), pipelineLayout, stageFlags, offset, value);
    }
    
    public static void pushFloat(MemorySegment cmd, MemorySegment pipelineLayout, int stageFlags, int offset, float value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(ValueLayout.JAVA_FLOAT);
            data.set(ValueLayout.JAVA_FLOAT, 0, value);
            pushConstants(cmd, pipelineLayout, stageFlags, offset, data, 4);
        }
    }
    
    public static void pushFloats(VkCommandBuffer cmd, MemorySegment pipelineLayout, int stageFlags, int offset, float... values) {
        pushFloats(cmd.handle(), pipelineLayout, stageFlags, offset, values);
    }
    
    public static void pushFloats(MemorySegment cmd, MemorySegment pipelineLayout, int stageFlags, int offset, float... values) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(ValueLayout.JAVA_FLOAT, values.length);
            for (int i = 0; i < values.length; i++) {
                data.setAtIndex(ValueLayout.JAVA_FLOAT, i, values[i]);
            }
            pushConstants(cmd, pipelineLayout, stageFlags, offset, data, values.length * 4L);
        }
    }
    
    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }
    
    public void execute(MemorySegment cmd) {
        pushConstants(cmd, pipelineLayout, stageFlags, offset, data, size);
    }
    
    // Builder for fluent construction
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private MemorySegment pipelineLayout;
        private int stageFlags;
        private int offset = 0;
        private MemorySegment data;
        private long size;
        
        private Builder() {}
        
        public Builder pipelineLayout(MemorySegment layout) { this.pipelineLayout = layout; return this; }
        public Builder pipelineLayout(VkPipeline pipeline) { this.pipelineLayout = pipeline.layout(); return this; }
        public Builder pipelineLayout(VkComputePipeline pipeline) { this.pipelineLayout = pipeline.layout(); return this; }
        public Builder stageFlags(int flags) { this.stageFlags = flags; return this; }
        public Builder offset(int offset) { this.offset = offset; return this; }
        public Builder data(MemorySegment data, long size) { this.data = data; this.size = size; return this; }
        
        public Builder intValue(int value, Arena arena) {
            this.data = arena.allocate(ValueLayout.JAVA_INT);
            this.data.set(ValueLayout.JAVA_INT, 0, value);
            this.size = 4;
            return this;
        }
        
        public Builder floatValue(float value, Arena arena) {
            this.data = arena.allocate(ValueLayout.JAVA_FLOAT);
            this.data.set(ValueLayout.JAVA_FLOAT, 0, value);
            this.size = 4;
            return this;
        }
        
        public Builder floatValues(float[] values, Arena arena) {
            this.data = arena.allocate(ValueLayout.JAVA_FLOAT, values.length);
            for (int i = 0; i < values.length; i++) {
                this.data.setAtIndex(ValueLayout.JAVA_FLOAT, i, values[i]);
            }
            this.size = values.length * 4L;
            return this;
        }
        
        public VkPushConstantsCmd build() {
            return new VkPushConstantsCmd(pipelineLayout, stageFlags, offset, data, size);
        }
        
        public void pushConstants(VkCommandBuffer cmd) {
            build().execute(cmd);
        }
        
        public void pushConstants(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}