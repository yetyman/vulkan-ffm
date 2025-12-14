package io.github.yetyman.vulkan;

import java.lang.foreign.*;

/**
 * Builder wrapper for Vulkan clear value (VkClearValue) with fluent API.
 */
public class VkClearValue {
    
    private VkClearValue() {
        // Utility class - no instances
    }
    
    /** Creates a color clear value */
    public static MemorySegment color(Arena arena, float r, float g, float b, float a) {
        MemorySegment clearValue = arena.allocate(16);
        clearValue.set(ValueLayout.JAVA_FLOAT, 0, r);
        clearValue.set(ValueLayout.JAVA_FLOAT, 4, g);
        clearValue.set(ValueLayout.JAVA_FLOAT, 8, b);
        clearValue.set(ValueLayout.JAVA_FLOAT, 12, a);
        return clearValue;
    }
    
    /** Creates a depth/stencil clear value */
    public static MemorySegment depthStencil(Arena arena, float depth, int stencil) {
        MemorySegment clearValue = arena.allocate(16);
        clearValue.set(ValueLayout.JAVA_FLOAT, 0, depth);
        clearValue.set(ValueLayout.JAVA_INT, 4, stencil);
        return clearValue;
    }
}