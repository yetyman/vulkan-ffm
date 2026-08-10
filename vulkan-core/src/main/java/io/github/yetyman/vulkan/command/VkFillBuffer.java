package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.MemorySegment;

/**
 * Wraps {@code vkCmdFillBuffer}: fills a device buffer range with a repeated 4-byte word entirely
 * on the GPU timeline, no staging buffer or host copy involved. The common use is zeroing a
 * GPU-written counter (an atomic draw count, an indirect dispatch argument) immediately before the
 * compute pass that writes it, since a compute shader cannot safely reset its own output across
 * workgroup boundaries.
 */
public record VkFillBuffer(MemorySegment buffer, long offset, long size, int data) {

    public static void fillBuffer(VkCommandBuffer cmd, MemorySegment buffer, long offset, long size, int data) {
        VulkanFFM.vkCmdFillBuffer(cmd.handle(), buffer, offset, size, data);
    }

    public static void fillBuffer(MemorySegment cmd, MemorySegment buffer, long offset, long size, int data) {
        VulkanFFM.vkCmdFillBuffer(cmd, buffer, offset, size, data);
    }

    public static void fillBuffer(VkCommandBuffer cmd, VkBuffer buffer, long offset, long size, int data) {
        fillBuffer(cmd, buffer.handle(), offset, size, data);
    }

    /** Fills the whole buffer with zero. */
    public static void zero(VkCommandBuffer cmd, VkBuffer buffer) {
        fillBuffer(cmd, buffer.handle(), 0, buffer.size(), 0);
    }

    public void execute(VkCommandBuffer cmd) {
        fillBuffer(cmd, buffer, offset, size, data);
    }

    public void execute(MemorySegment cmd) {
        fillBuffer(cmd, buffer, offset, size, data);
    }
}
