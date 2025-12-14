package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Builder wrapper for Vulkan 2D rectangle (VkRect2D) with fluent API.
 */
public class VkRect2D {
    
    private VkRect2D() {
        // Utility class - no instances
    }
    
    /**
     * Creates a rectangle covering the entire area starting at origin.
     * @param arena memory arena for allocation
     * @param width rectangle width
     * @param height rectangle height
     * @return allocated VkRect2D memory segment
     */
    public static MemorySegment create(Arena arena, int width, int height) {
        return builder()
            .offset(0, 0)
            .extent(width, height)
            .build(arena);
    }
    
    /** @return a new builder for configuring rectangle */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for flexible rectangle configuration.
     */
    public static class Builder {
        private int offsetX = 0;
        private int offsetY = 0;
        private int width = 0;
        private int height = 0;
        
        private Builder() {}
        
        /** Sets rectangle offset */
        public Builder offset(int x, int y) {
            this.offsetX = x;
            this.offsetY = y;
            return this;
        }
        
        /** Sets rectangle extent */
        public Builder extent(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        /** Creates the rectangle */
        public MemorySegment build(Arena arena) {
            MemorySegment rect = arena.allocate(io.github.yetyman.vulkan.generated.VkRect2D.layout());
            MemorySegment offset = io.github.yetyman.vulkan.generated.VkRect2D.offset(rect);
            MemorySegment extent = io.github.yetyman.vulkan.generated.VkRect2D.extent(rect);
            
            io.github.yetyman.vulkan.generated.VkOffset2D.x(offset, offsetX);
            io.github.yetyman.vulkan.generated.VkOffset2D.y(offset, offsetY);
            io.github.yetyman.vulkan.generated.VkExtent2D.width(extent, width);
            io.github.yetyman.vulkan.generated.VkExtent2D.height(extent, height);
            
            return rect;
        }
    }
}