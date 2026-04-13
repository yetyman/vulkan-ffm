package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.*;

/**
 * Vulkan compute dispatch command wrapper supporting both immediate execution and reusable command objects.
 */
public record VkDispatch(int groupCountX, int groupCountY, int groupCountZ) {

    // Static helpers (zero allocation)
    public static void dispatch(VkCommandBuffer cmd, int groupCountX, int groupCountY, int groupCountZ) {
        VulkanFFM.vkCmdDispatch(cmd.handle(), groupCountX, groupCountY, groupCountZ);
    }

    public static void dispatch(MemorySegment cmd, int groupCountX, int groupCountY, int groupCountZ) {
        VulkanFFM.vkCmdDispatch(cmd, groupCountX, groupCountY, groupCountZ);
    }

    public static void dispatch(VkCommandBuffer cmd, int groupCountX, int groupCountY) {
        dispatch(cmd, groupCountX, groupCountY, 1);
    }

    public static void dispatch(MemorySegment cmd, int groupCountX, int groupCountY) {
        dispatch(cmd, groupCountX, groupCountY, 1);
    }

    public static void dispatch(VkCommandBuffer cmd, int groupCountX) {
        dispatch(cmd, groupCountX, 1, 1);
    }

    public static void dispatch(MemorySegment cmd, int groupCountX) {
        dispatch(cmd, groupCountX, 1, 1);
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        dispatch(cmd, groupCountX, groupCountY, groupCountZ);
    }

    public void execute(MemorySegment cmd) {
        dispatch(cmd, groupCountX, groupCountY, groupCountZ);
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int groupCountX = 1;
        private int groupCountY = 1;
        private int groupCountZ = 1;

        private Builder() {
        }

        public Builder groupCountX(int count) {
            this.groupCountX = count;
            return this;
        }

        public Builder groupCountY(int count) {
            this.groupCountY = count;
            return this;
        }

        public Builder groupCountZ(int count) {
            this.groupCountZ = count;
            return this;
        }

        public Builder groupCount(int x, int y, int z) {
            this.groupCountX = x;
            this.groupCountY = y;
            this.groupCountZ = z;
            return this;
        }

        public VkDispatch build() {
            return new VkDispatch(groupCountX, groupCountY, groupCountZ);
        }

        public void dispatch(VkCommandBuffer cmd) {
            VkDispatch.dispatch(cmd, groupCountX, groupCountY, groupCountZ);
        }

        public void dispatch(MemorySegment cmd) {
            VkDispatch.dispatch(cmd, groupCountX, groupCountY, groupCountZ);
        }
    }
}