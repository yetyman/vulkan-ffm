package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static io.github.yetyman.vulkan.enums.VkStructureType.VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;

public class VkMappedMemoryRange {
    
    public static MemorySegment allocate(Arena arena, MemorySegment memory, long offset, long size) {
        MemorySegment range = arena.allocate(40);
        range.set(ValueLayout.JAVA_INT, 0, VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE.value());
        range.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        range.set(ValueLayout.ADDRESS, 16, memory);
        range.set(ValueLayout.JAVA_LONG, 24, offset);
        range.set(ValueLayout.JAVA_LONG, 32, size);
        return range;
    }
}
