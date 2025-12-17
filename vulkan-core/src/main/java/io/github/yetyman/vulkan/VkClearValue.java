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
    
    /** Returns the memory layout for VkClearValue */
    public static MemoryLayout layout() {
        return MemoryLayout.unionLayout(
            MemoryLayout.structLayout(
                ValueLayout.JAVA_FLOAT.withName("r"),
                ValueLayout.JAVA_FLOAT.withName("g"),
                ValueLayout.JAVA_FLOAT.withName("b"),
                ValueLayout.JAVA_FLOAT.withName("a")
            ).withName("color"),
            MemoryLayout.structLayout(
                ValueLayout.JAVA_FLOAT.withName("depth"),
                ValueLayout.JAVA_INT.withName("stencil")
            ).withName("depthStencil")
        );
    }
}