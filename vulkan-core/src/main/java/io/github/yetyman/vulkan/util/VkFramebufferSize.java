package io.github.yetyman.vulkan.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Utility for framebuffer size operations
 */
public class VkFramebufferSize {
    public final int width;
    public final int height;
    
    private VkFramebufferSize(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    public static VkFramebufferSize query(WindowSystemQuery windowSystem, MemorySegment window, Arena arena) {
        MemorySegment widthPtr = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment heightPtr = arena.allocate(ValueLayout.JAVA_INT);
        windowSystem.getFramebufferSize(window, widthPtr, heightPtr);
        return new VkFramebufferSize(
            widthPtr.get(ValueLayout.JAVA_INT, 0),
            heightPtr.get(ValueLayout.JAVA_INT, 0)
        );
    }
    
    public boolean isValid() {
        return width > 0 && height > 0;
    }
    
    @FunctionalInterface
    public interface WindowSystemQuery {
        void getFramebufferSize(MemorySegment window, MemorySegment widthPtr, MemorySegment heightPtr);
    }
}