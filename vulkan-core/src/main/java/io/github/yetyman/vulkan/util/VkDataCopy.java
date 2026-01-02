package io.github.yetyman.vulkan.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Utility for efficient data copying operations
 */
public class VkDataCopy {
    
    public static MemorySegment copyFloatArray(float[] array, Arena arena) {
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_FLOAT, array.length);
        MemorySegment.copy(array, 0, segment, ValueLayout.JAVA_FLOAT, 0, array.length);
        return segment;
    }
    
    public static MemorySegment copyIntArray(int[] array, Arena arena) {
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT, array.length);
        MemorySegment.copy(array, 0, segment, ValueLayout.JAVA_INT, 0, array.length);
        return segment;
    }
    
    public static void copyFloatArrayTo(float[] array, MemorySegment target) {
        MemorySegment.copy(array, 0, target, ValueLayout.JAVA_FLOAT, 0, array.length);
    }
    
    public static void copyIntArrayTo(int[] array, MemorySegment target) {
        MemorySegment.copy(array, 0, target, ValueLayout.JAVA_INT, 0, array.length);
    }
}