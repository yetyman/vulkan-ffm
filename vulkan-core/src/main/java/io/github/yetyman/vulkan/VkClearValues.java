package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for clear value arrays
 */
public class VkClearValues {
    private final List<Object> clearValues = new ArrayList<>();
    
    private VkClearValues() {}
    
    public static VkClearValues create() {
        return new VkClearValues();
    }
    
    public VkClearValues color(float r, float g, float b, float a) {
        clearValues.add(new ColorClear(r, g, b, a));
        return this;
    }
    
    public VkClearValues depthStencil(float depth, int stencil) {
        clearValues.add(new DepthClear(depth, stencil));
        return this;
    }
    
    public MemorySegment build(Arena arena) {
        if (clearValues.isEmpty()) return MemorySegment.NULL;
        
        MemorySegment array = arena.allocate(VkClearValue.layout(), clearValues.size());
        
        for (int i = 0; i < clearValues.size(); i++) {
            MemorySegment clearValue;
            Object value = clearValues.get(i);
            
            if (value instanceof ColorClear color) {
                clearValue = VkClearValue.color(arena, color.r, color.g, color.b, color.a);
            } else if (value instanceof DepthClear depth) {
                clearValue = VkClearValue.depthStencil(arena, depth.depth, depth.stencil);
            } else {
                continue;
            }
            
            MemorySegment.copy(clearValue, 0, array, i * VkClearValue.layout().byteSize(), VkClearValue.layout().byteSize());
        }
        
        return array;
    }
    
    public int count() {
        return clearValues.size();
    }
    
    private record ColorClear(float r, float g, float b, float a) {}
    private record DepthClear(float depth, int stencil) {}
}