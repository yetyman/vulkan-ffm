package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.*;

/**
 * Vulkan indexed indirect draw command wrapper supporting both immediate execution and reusable command objects.
 */
public record VkDrawIndexedIndirect(MemorySegment buffer, long offset, int drawCount, int stride) {

    // Static helpers (zero allocation)
    public static void drawIndexedIndirect(VkCommandBuffer cmd, MemorySegment buffer, long offset, int drawCount, int stride) {
        VulkanFFM.vkCmdDrawIndexedIndirect(cmd.handle(), buffer, offset, drawCount, stride);
    }

    public static void drawIndexedIndirect(MemorySegment cmd, MemorySegment buffer, long offset, int drawCount, int stride) {
        VulkanFFM.vkCmdDrawIndexedIndirect(cmd, buffer, offset, drawCount, stride);
    }

    public static void drawIndexedIndirect(VkCommandBuffer cmd, VkBuffer buffer, long offset, int drawCount, int stride) {
        drawIndexedIndirect(cmd, buffer.handle(), offset, drawCount, stride);
    }

    public static void drawIndexedIndirect(MemorySegment cmd, VkBuffer buffer, long offset, int drawCount, int stride) {
        drawIndexedIndirect(cmd, buffer.handle(), offset, drawCount, stride);
    }

    public static void drawIndexedIndirect(VkCommandBuffer cmd, MemorySegment buffer, long offset, int drawCount) {
        drawIndexedIndirect(cmd, buffer, offset, drawCount, 20); // sizeof(VkDrawIndexedIndirectCommand)
    }

    public static void drawIndexedIndirect(MemorySegment cmd, MemorySegment buffer, long offset, int drawCount) {
        drawIndexedIndirect(cmd, buffer, offset, drawCount, 20);
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        drawIndexedIndirect(cmd, buffer, offset, drawCount, stride);
    }

    public void execute(MemorySegment cmd) {
        drawIndexedIndirect(cmd, buffer, offset, drawCount, stride);
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemorySegment buffer;
        private long offset = 0;
        private int drawCount = 1;
        private int stride = 20; // sizeof(VkDrawIndexedIndirectCommand)

        private Builder() {
        }

        public Builder buffer(MemorySegment buffer) {
            this.buffer = buffer;
            return this;
        }

        public Builder buffer(VkBuffer buffer) {
            this.buffer = buffer.handle();
            return this;
        }

        public Builder offset(long offset) {
            this.offset = offset;
            return this;
        }

        public Builder drawCount(int count) {
            this.drawCount = count;
            return this;
        }

        public Builder stride(int stride) {
            this.stride = stride;
            return this;
        }

        public VkDrawIndexedIndirect build() {
            return new VkDrawIndexedIndirect(buffer, offset, drawCount, stride);
        }

        public void drawIndexedIndirect(VkCommandBuffer cmd) {
            VkDrawIndexedIndirect.drawIndexedIndirect(cmd, buffer, offset, drawCount, stride);
        }

        public void drawIndexedIndirect(MemorySegment cmd) {
            VkDrawIndexedIndirect.drawIndexedIndirect(cmd, buffer, offset, drawCount, stride);
        }
    }
}