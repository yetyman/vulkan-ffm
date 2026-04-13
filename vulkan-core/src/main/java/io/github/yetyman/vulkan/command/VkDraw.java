package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.*;

/**
 * Vulkan draw command wrapper supporting both immediate execution and reusable command objects.
 */
public record VkDraw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {

    // Static helpers (zero allocation)
    public static void draw(VkCommandBuffer cmd, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        VulkanFFM.vkCmdDraw(cmd.handle(), vertexCount, instanceCount, firstVertex, firstInstance);
    }

    public static void draw(MemorySegment cmd, int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        VulkanFFM.vkCmdDraw(cmd, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    public static void draw(VkCommandBuffer cmd, int vertexCount, int instanceCount) {
        draw(cmd, vertexCount, instanceCount, 0, 0);
    }

    public static void draw(MemorySegment cmd, int vertexCount, int instanceCount) {
        draw(cmd, vertexCount, instanceCount, 0, 0);
    }

    public static void draw(VkCommandBuffer cmd, int vertexCount) {
        draw(cmd, vertexCount, 1, 0, 0);
    }

    public static void draw(MemorySegment cmd, int vertexCount) {
        draw(cmd, vertexCount, 1, 0, 0);
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        draw(cmd, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    public void execute(MemorySegment cmd) {
        draw(cmd, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int vertexCount = 0;
        private int instanceCount = 1;
        private int firstVertex = 0;
        private int firstInstance = 0;

        private Builder() {
        }

        public Builder vertexCount(int count) {
            this.vertexCount = count;
            return this;
        }

        public Builder instanceCount(int count) {
            this.instanceCount = count;
            return this;
        }

        public Builder firstVertex(int first) {
            this.firstVertex = first;
            return this;
        }

        public Builder firstInstance(int first) {
            this.firstInstance = first;
            return this;
        }

        public VkDraw build() {
            return new VkDraw(vertexCount, instanceCount, firstVertex, firstInstance);
        }

        public void draw(VkCommandBuffer cmd) {
            VkDraw.draw(cmd, vertexCount, instanceCount, firstVertex, firstInstance);
        }

        public void draw(MemorySegment cmd) {
            VkDraw.draw(cmd, vertexCount, instanceCount, firstVertex, firstInstance);
        }
    }
}