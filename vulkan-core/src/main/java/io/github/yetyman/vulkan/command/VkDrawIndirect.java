package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.*;

/**
 * Vulkan indirect draw command wrapper supporting both immediate execution and reusable command objects.
 */
public record VkDrawIndirect(MemorySegment buffer, long offset, int drawCount, int stride) {

    // Static helpers (zero allocation)
    public static void drawIndirect(VkCommandBuffer cmd, MemorySegment buffer, long offset, int drawCount, int stride) {
        VulkanFFM.vkCmdDrawIndirect(cmd.handle(), buffer, offset, drawCount, stride);
    }

    public static void drawIndirect(MemorySegment cmd, MemorySegment buffer, long offset, int drawCount, int stride) {
        VulkanFFM.vkCmdDrawIndirect(cmd, buffer, offset, drawCount, stride);
    }

    public static void drawIndirect(VkCommandBuffer cmd, VkBuffer buffer, long offset, int drawCount, int stride) {
        drawIndirect(cmd, buffer.handle(), offset, drawCount, stride);
    }

    public static void drawIndirect(MemorySegment cmd, VkBuffer buffer, long offset, int drawCount, int stride) {
        drawIndirect(cmd, buffer.handle(), offset, drawCount, stride);
    }

    public static void drawIndirect(VkCommandBuffer cmd, MemorySegment buffer, long offset, int drawCount) {
        drawIndirect(cmd, buffer, offset, drawCount, 16); // sizeof(VkDrawIndirectCommand)
    }

    public static void drawIndirect(MemorySegment cmd, MemorySegment buffer, long offset, int drawCount) {
        drawIndirect(cmd, buffer, offset, drawCount, 16);
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        drawIndirect(cmd, buffer, offset, drawCount, stride);
    }

    public void execute(MemorySegment cmd) {
        drawIndirect(cmd, buffer, offset, drawCount, stride);
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MemorySegment buffer;
        private long offset = 0;
        private int drawCount = 1;
        private int stride = 16; // sizeof(VkDrawIndirectCommand)

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

        public VkDrawIndirect build() {
            return new VkDrawIndirect(buffer, offset, drawCount, stride);
        }

        public void drawIndirect(VkCommandBuffer cmd) {
            VkDrawIndirect.drawIndirect(cmd, buffer, offset, drawCount, stride);
        }

        public void drawIndirect(MemorySegment cmd) {
            VkDrawIndirect.drawIndirect(cmd, buffer, offset, drawCount, stride);
        }
    }
}