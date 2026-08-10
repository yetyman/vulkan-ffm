package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.MemorySegment;

/**
 * Wraps {@code vkCmdDrawIndexedIndirectCount}: one call draws up to {@code maxDrawCount} indexed
 * draws read from {@code buffer}, using a GPU-written count read from {@code countBuffer} to decide
 * how many of them are actually issued. This is the call that makes bulk rendering CPU-recording-cost
 * independent of scene size: a compute culling pass writes both the surviving draw commands and their
 * count, and the CPU records exactly one draw call regardless of how many partitions exist or how
 * many survive culling in a given frame.
 *
 * <p>Core Vulkan 1.2 API (no extension required on a 1.2+ device). See
 * {@code vulkan-ffm-mesh}'s {@code IndirectDrawEncoder} for the command layout this call consumes,
 * and {@code plans/mesh/09-roadmap.md} Phase 5 for why this exists.
 */
public record VkDrawIndexedIndirectCount(
        MemorySegment buffer, long offset,
        MemorySegment countBuffer, long countBufferOffset,
        int maxDrawCount, int stride) {

    /**
     * @param cmd               command buffer to record into
     * @param buffer            buffer of tightly-packed {@code VkDrawIndexedIndirectCommand} structs
     * @param offset            byte offset into {@code buffer} of the first command
     * @param countBuffer       buffer containing a single {@code uint32_t} draw count
     * @param countBufferOffset byte offset into {@code countBuffer} of the count value
     * @param maxDrawCount      upper bound on draws issued; also bounds how much of {@code buffer}
     *                          is read, so it must not exceed the buffer's actual capacity
     * @param stride            bytes between successive commands in {@code buffer}, normally
     *                          {@link io.github.yetyman.vulkan.mesh.consume.IndirectDrawEncoder#INDEXED_STRIDE}
     */
    public static void drawIndexedIndirectCount(VkCommandBuffer cmd, MemorySegment buffer, long offset,
                                                MemorySegment countBuffer, long countBufferOffset,
                                                int maxDrawCount, int stride) {
        VulkanFFM.vkCmdDrawIndexedIndirectCount(cmd.handle(), buffer, offset,
                countBuffer, countBufferOffset, maxDrawCount, stride);
    }

    public static void drawIndexedIndirectCount(MemorySegment cmd, MemorySegment buffer, long offset,
                                                MemorySegment countBuffer, long countBufferOffset,
                                                int maxDrawCount, int stride) {
        VulkanFFM.vkCmdDrawIndexedIndirectCount(cmd, buffer, offset,
                countBuffer, countBufferOffset, maxDrawCount, stride);
    }

    public static void drawIndexedIndirectCount(VkCommandBuffer cmd, VkBuffer buffer, long offset,
                                                VkBuffer countBuffer, long countBufferOffset,
                                                int maxDrawCount, int stride) {
        drawIndexedIndirectCount(cmd, buffer.handle(), offset, countBuffer.handle(), countBufferOffset,
                maxDrawCount, stride);
    }

    /**
     * Reusable form: construct once, call {@link #execute} each frame.
     */
    public void execute(VkCommandBuffer cmd) {
        drawIndexedIndirectCount(cmd, buffer, offset, countBuffer, countBufferOffset, maxDrawCount, stride);
    }

    public void execute(MemorySegment cmd) {
        drawIndexedIndirectCount(cmd, buffer, offset, countBuffer, countBufferOffset, maxDrawCount, stride);
    }
}
