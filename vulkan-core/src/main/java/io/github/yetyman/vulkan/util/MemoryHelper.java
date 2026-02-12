package io.github.yetyman.vulkan.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Utility for efficient data copying operations
 */
public class MemoryHelper {
    
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

    /**
     * Since the typical game system includes off-thread loading and processing
     *  we can clean up code a lot by using confined arenas in all the places it counts
     *  but still transfer the data at clear hand-offs without copy overhead.
     * @param sourceSegment
     * @param targetArena
     * @return
     */
    public static MemorySegment copyConfined(MemorySegment sourceSegment, Arena targetArena) {
        // These calls work even if sourceSegment is confined to another thread
        long address = sourceSegment.address();
        long size = sourceSegment.byteSize();
        Arena sourceArena = (Arena) sourceSegment.scope();

        return MemorySegment.ofAddress(address)
                .reinterpret(size, targetArena, _ -> sourceArena.close());
    }
}