package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Builder wrapper for Vulkan viewport (VkViewport) with fluent API.
 * Viewports define the transformation from normalized device coordinates to framebuffer coordinates.
 */
public class VkViewport {
    
    private VkViewport() {
        // Utility class - no instances
    }
    
    /**
     * Creates a viewport covering the entire framebuffer with standard depth range.
     * @param arena memory arena for allocation
     * @param width framebuffer width
     * @param height framebuffer height
     * @return allocated VkViewport memory segment
     */
    public static MemorySegment create(Arena arena, int width, int height) {
        return builder()
            .position(0, 0)
            .size(width, height)
            .depthRange(0.0f, 1.0f)
            .build(arena);
    }
    
    /** @return a new builder for configuring viewport */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for flexible viewport configuration.
     */
    public static class Builder {
        private float x = 0.0f;
        private float y = 0.0f;
        private float width = 0.0f;
        private float height = 0.0f;
        private float minDepth = 0.0f;
        private float maxDepth = 1.0f;
        
        private Builder() {}
        
        /** Sets viewport position */
        public Builder position(float x, float y) {
            this.x = x;
            this.y = y;
            return this;
        }
        
        /** Sets viewport size */
        public Builder size(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        /** Sets viewport depth range */
        public Builder depthRange(float minDepth, float maxDepth) {
            this.minDepth = minDepth;
            this.maxDepth = maxDepth;
            return this;
        }
        
        /** Creates the viewport */
        public MemorySegment build(Arena arena) {
            MemorySegment viewport = arena.allocate(io.github.yetyman.vulkan.generated.VkViewport.layout());
            io.github.yetyman.vulkan.generated.VkViewport.x(viewport, x);
            io.github.yetyman.vulkan.generated.VkViewport.y(viewport, y);
            io.github.yetyman.vulkan.generated.VkViewport.width(viewport, width);
            io.github.yetyman.vulkan.generated.VkViewport.height(viewport, height);
            io.github.yetyman.vulkan.generated.VkViewport.minDepth(viewport, minDepth);
            io.github.yetyman.vulkan.generated.VkViewport.maxDepth(viewport, maxDepth);
            return viewport;
        }
    }
}