package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe push constants abstraction
 */
public class VkPushConstants {
    private final MemorySegment data;
    private final int stageFlags;
    private final int offset;
    
    private VkPushConstants(MemorySegment data, int stageFlags, int offset) {
        this.data = data;
        this.stageFlags = stageFlags;
        this.offset = offset;
    }
    
    /**
     * Updates the float value in this push constant object
     */
    public VkPushConstants updateFloat(float value) {
        data.set(ValueLayout.JAVA_FLOAT, 0, value);
        return this;
    }
    
    /**
     * Updates the int value in this push constant object
     */
    public VkPushConstants updateInt(int value) {
        data.set(ValueLayout.JAVA_INT, 0, value);
        return this;
    }
    
    /**
     * Updates the float array in this push constant object
     */
    public VkPushConstants updateFloatArray(float[] values) {
        for (int i = 0; i < Math.min(values.length, data.byteSize() / 4); i++) {
            data.setAtIndex(ValueLayout.JAVA_FLOAT, i, values[i]);
        }
        return this;
    }
    
    public static Builder builder(Arena arena) {
        return new Builder(arena);
    }
    
    public static VkPushConstants floatValue(float value, int stageFlags, Arena arena) {
        return floatValue(value, stageFlags, 0, arena);
    }
    
    public static VkPushConstants floatValue(float value, int stageFlags, int offset, Arena arena) {
        MemorySegment data = arena.allocate(ValueLayout.JAVA_FLOAT);
        data.set(ValueLayout.JAVA_FLOAT, 0, value);
        return new VkPushConstants(data, stageFlags, offset);
    }
    
    public static VkPushConstants intValue(int value, int stageFlags, Arena arena) {
        return intValue(value, stageFlags, 0, arena);
    }
    
    public static VkPushConstants intValue(int value, int stageFlags, int offset, Arena arena) {
        MemorySegment data = arena.allocate(ValueLayout.JAVA_INT);
        data.set(ValueLayout.JAVA_INT, 0, value);
        return new VkPushConstants(data, stageFlags, offset);
    }
    
    public static VkPushConstants floatArray(float[] values, int stageFlags, Arena arena) {
        return floatArray(values, stageFlags, 0, arena);
    }
    
    public static VkPushConstants floatArray(float[] values, int stageFlags, int offset, Arena arena) {
        MemorySegment data = arena.allocate(ValueLayout.JAVA_FLOAT, values.length);
        for (int i = 0; i < values.length; i++) {
            data.setAtIndex(ValueLayout.JAVA_FLOAT, i, values[i]);
        }
        return new VkPushConstants(data, stageFlags, offset);
    }
    
    public void push(MemorySegment commandBuffer, MemorySegment pipelineLayout) {
        Vulkan.cmdPushConstants(commandBuffer, pipelineLayout, stageFlags, offset, (int)data.byteSize(), data);
    }
    
    public void push(MemorySegment commandBuffer, MemorySegment pipelineLayout, int stageFlags, int offset) {
        Vulkan.cmdPushConstants(commandBuffer, pipelineLayout, stageFlags, offset, (int)data.byteSize(), data);
    }
    
    public static class Builder {
        private final Arena arena;
        private final List<Float> floats = new ArrayList<>();
        private final List<Integer> ints = new ArrayList<>();
        private int stageFlags = 0;
        private int offset = 0;
        
        private Builder(Arena arena) {
            this.arena = arena;
        }
        
        public Builder floatValue(float value) {
            floats.add(value);
            return this;
        }
        
        public Builder intValue(int value) {
            ints.add(value);
            return this;
        }
        
        public Builder stageFlags(int flags) {
            this.stageFlags = flags;
            return this;
        }
        
        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }
        
        public VkPushConstants build() {
            int totalSize = floats.size() * 4 + ints.size() * 4;
            MemorySegment data = arena.allocate(totalSize);
            
            int byteOffset = 0;
            for (float f : floats) {
                data.set(ValueLayout.JAVA_FLOAT, byteOffset, f);
                byteOffset += 4;
            }
            for (int i : ints) {
                data.set(ValueLayout.JAVA_INT, byteOffset, i);
                byteOffset += 4;
            }
            
            return new VkPushConstants(data, stageFlags, offset);
        }
    }
}