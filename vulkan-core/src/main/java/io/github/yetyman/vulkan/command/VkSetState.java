package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.util.BumpAllocator;

import java.lang.foreign.*;

/**
 * Vulkan dynamic state command wrapper for viewport, scissor, line width, depth bias, etc.
 */
public record VkSetState(StateType type, float[] floatValues, int[] intValues, long[] longValues) {

    public enum StateType {
        VIEWPORT, SCISSOR, LINE_WIDTH, DEPTH_BIAS, BLEND_CONSTANTS,
        DEPTH_BOUNDS, STENCIL_COMPARE_MASK, STENCIL_WRITE_MASK, STENCIL_REFERENCE
    }

    // Static helpers for viewport
    public static void setViewport(VkCommandBuffer cmd, int firstViewport, float x, float y, float width, float height, float minDepth, float maxDepth) {
        setViewport(cmd.handle(), firstViewport, x, y, width, height, minDepth, maxDepth);
    }

    public static void setViewport(MemorySegment cmd, int firstViewport, float x, float y, float width, float height, float minDepth, float maxDepth) {
        BumpAllocator ba = BumpAllocator.get();
        ba.push();
        try {
            MemorySegment viewport = ba.alloc(VkViewport.sizeof());
            VkViewport.x(viewport, x);
            VkViewport.y(viewport, y);
            VkViewport.width(viewport, width);
            VkViewport.height(viewport, height);
            VkViewport.minDepth(viewport, minDepth);
            VkViewport.maxDepth(viewport, maxDepth);
            VulkanFFM.vkCmdSetViewport(cmd, firstViewport, 1, viewport);
        } finally {
            ba.pop();
        }
    }

    public static void setViewport(VkCommandBuffer cmd, float x, float y, float width, float height) {
        setViewport(cmd, 0, x, y, width, height, 0.0f, 1.0f);
    }

    public static void setViewport(MemorySegment cmd, float x, float y, float width, float height) {
        setViewport(cmd, 0, x, y, width, height, 0.0f, 1.0f);
    }

    // Static helpers for scissor
    public static void setScissor(VkCommandBuffer cmd, int firstScissor, int x, int y, int width, int height) {
        setScissor(cmd.handle(), firstScissor, x, y, width, height);
    }

    public static void setScissor(MemorySegment cmd, int firstScissor, int x, int y, int width, int height) {
        BumpAllocator ba = BumpAllocator.get();
        ba.push();
        try {
            MemorySegment scissor = ba.alloc(VkRect2D.sizeof());
            MemorySegment offset = VkRect2D.offset(scissor);
            MemorySegment extent = VkRect2D.extent(scissor);
            VkOffset2D.x(offset, x);
            VkOffset2D.y(offset, y);
            VkExtent2D.width(extent, width);
            VkExtent2D.height(extent, height);
            VulkanFFM.vkCmdSetScissor(cmd, firstScissor, 1, scissor);
        } finally {
            ba.pop();
        }
    }

    public static void setScissor(VkCommandBuffer cmd, int x, int y, int width, int height) {
        setScissor(cmd, 0, x, y, width, height);
    }

    public static void setScissor(MemorySegment cmd, int x, int y, int width, int height) {
        setScissor(cmd, 0, x, y, width, height);
    }

    // Static helpers for line width
    public static void setLineWidth(VkCommandBuffer cmd, float lineWidth) {
        setLineWidth(cmd.handle(), lineWidth);
    }

    public static void setLineWidth(MemorySegment cmd, float lineWidth) {
        VulkanFFM.vkCmdSetLineWidth(cmd, lineWidth);
    }

    // Static helpers for depth bias
    public static void setDepthBias(VkCommandBuffer cmd, float constantFactor, float clamp, float slopeFactor) {
        setDepthBias(cmd.handle(), constantFactor, clamp, slopeFactor);
    }

    public static void setDepthBias(MemorySegment cmd, float constantFactor, float clamp, float slopeFactor) {
        VulkanFFM.vkCmdSetDepthBias(cmd, constantFactor, clamp, slopeFactor);
    }

    // Static helpers for blend constants
    public static void setBlendConstants(VkCommandBuffer cmd, float r, float g, float b, float a) {
        setBlendConstants(cmd.handle(), r, g, b, a);
    }

    public static void setBlendConstants(MemorySegment cmd, float r, float g, float b, float a) {
        BumpAllocator ba = BumpAllocator.get();
        ba.push();
        try {
            MemorySegment constants = ba.alloc(4 * ValueLayout.JAVA_FLOAT.byteSize());
            constants.setAtIndex(ValueLayout.JAVA_FLOAT, 0, r);
            constants.setAtIndex(ValueLayout.JAVA_FLOAT, 1, g);
            constants.setAtIndex(ValueLayout.JAVA_FLOAT, 2, b);
            constants.setAtIndex(ValueLayout.JAVA_FLOAT, 3, a);
            VulkanFFM.vkCmdSetBlendConstants(cmd, constants);
        } finally {
            ba.pop();
        }
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }

    public void execute(MemorySegment cmd) {
        switch (type) {
            case VIEWPORT -> {
                if (floatValues.length >= 6) {
                    setViewport(cmd, 0, floatValues[0], floatValues[1], floatValues[2], floatValues[3], floatValues[4], floatValues[5]);
                }
            }
            case SCISSOR -> {
                if (intValues.length >= 4) {
                    setScissor(cmd, 0, intValues[0], intValues[1], intValues[2], intValues[3]);
                }
            }
            case LINE_WIDTH -> {
                if (floatValues.length >= 1) {
                    setLineWidth(cmd, floatValues[0]);
                }
            }
            case DEPTH_BIAS -> {
                if (floatValues.length >= 3) {
                    setDepthBias(cmd, floatValues[0], floatValues[1], floatValues[2]);
                }
            }
            case BLEND_CONSTANTS -> {
                if (floatValues.length >= 4) {
                    setBlendConstants(cmd, floatValues[0], floatValues[1], floatValues[2], floatValues[3]);
                }
            }
            default -> throw new UnsupportedOperationException("State type not implemented: " + type);
        }
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private StateType type;
        private float[] floatValues = new float[0];
        private int[] intValues = new int[0];
        private long[] longValues = new long[0];

        private Builder() {
        }

        public Builder viewport(float x, float y, float width, float height, float minDepth, float maxDepth) {
            this.type = StateType.VIEWPORT;
            this.floatValues = new float[]{x, y, width, height, minDepth, maxDepth};
            return this;
        }

        public Builder viewport(float x, float y, float width, float height) {
            return viewport(x, y, width, height, 0.0f, 1.0f);
        }

        public Builder scissor(int x, int y, int width, int height) {
            this.type = StateType.SCISSOR;
            this.intValues = new int[]{x, y, width, height};
            return this;
        }

        public Builder lineWidth(float width) {
            this.type = StateType.LINE_WIDTH;
            this.floatValues = new float[]{width};
            return this;
        }

        public Builder depthBias(float constantFactor, float clamp, float slopeFactor) {
            this.type = StateType.DEPTH_BIAS;
            this.floatValues = new float[]{constantFactor, clamp, slopeFactor};
            return this;
        }

        public Builder blendConstants(float r, float g, float b, float a) {
            this.type = StateType.BLEND_CONSTANTS;
            this.floatValues = new float[]{r, g, b, a};
            return this;
        }

        public VkSetState build() {
            return new VkSetState(type, floatValues, intValues, longValues);
        }

        public void setState(VkCommandBuffer cmd) {
            build().execute(cmd);
        }

        public void setState(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}