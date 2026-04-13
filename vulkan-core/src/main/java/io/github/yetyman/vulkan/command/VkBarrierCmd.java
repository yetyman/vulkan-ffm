package io.github.yetyman.vulkan.command;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.generated.VulkanFFM;

import java.lang.foreign.*;

/**
 * Vulkan pipeline barrier command wrapper for memory synchronization.
 */
public record VkBarrierCmd(int srcStageMask, int dstStageMask, int dependencyFlags,
                           MemorySegment memoryBarriers, int memoryBarrierCount,
                           MemorySegment bufferBarriers, int bufferBarrierCount,
                           MemorySegment imageBarriers, int imageBarrierCount) {

    // Static helpers for pipeline barriers
    public static void pipelineBarrier(VkCommandBuffer cmd, int srcStageMask, int dstStageMask, int dependencyFlags,
                                       int memoryBarrierCount, MemorySegment memoryBarriers,
                                       int bufferBarrierCount, MemorySegment bufferBarriers,
                                       int imageBarrierCount, MemorySegment imageBarriers) {
        pipelineBarrier(cmd.handle(), srcStageMask, dstStageMask, dependencyFlags,
                memoryBarrierCount, memoryBarriers, bufferBarrierCount, bufferBarriers,
                imageBarrierCount, imageBarriers);
    }

    public static void pipelineBarrier(MemorySegment cmd, int srcStageMask, int dstStageMask, int dependencyFlags,
                                       int memoryBarrierCount, MemorySegment memoryBarriers,
                                       int bufferBarrierCount, MemorySegment bufferBarriers,
                                       int imageBarrierCount, MemorySegment imageBarriers) {
        VulkanFFM.vkCmdPipelineBarrier(cmd, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarrierCount, memoryBarriers, bufferBarrierCount, bufferBarriers,
                imageBarrierCount, imageBarriers);
    }

    // Reusable execution methods
    public void execute(VkCommandBuffer cmd) {
        execute(cmd.handle());
    }

    public void execute(MemorySegment cmd) {
        pipelineBarrier(cmd, srcStageMask, dstStageMask, dependencyFlags,
                memoryBarrierCount, memoryBarriers, bufferBarrierCount, bufferBarriers,
                imageBarrierCount, imageBarriers);
    }

    // Builder for fluent construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int srcStageMask = 0;
        private int dstStageMask = 0;
        private int dependencyFlags = 0;
        private MemorySegment memoryBarriers = MemorySegment.NULL;
        private int memoryBarrierCount = 0;
        private MemorySegment bufferBarriers = MemorySegment.NULL;
        private int bufferBarrierCount = 0;
        private MemorySegment imageBarriers = MemorySegment.NULL;
        private int imageBarrierCount = 0;

        private Builder() {
        }

        public Builder srcStage(int srcStageMask) {
            this.srcStageMask = srcStageMask;
            return this;
        }

        public Builder dstStage(int dstStageMask) {
            this.dstStageMask = dstStageMask;
            return this;
        }

        public Builder dependencyFlags(int dependencyFlags) {
            this.dependencyFlags = dependencyFlags;
            return this;
        }

        public Builder memoryBarriers(MemorySegment barriers, int count) {
            this.memoryBarriers = barriers;
            this.memoryBarrierCount = count;
            return this;
        }

        public Builder bufferBarriers(MemorySegment barriers, int count) {
            this.bufferBarriers = barriers;
            this.bufferBarrierCount = count;
            return this;
        }

        public Builder imageBarriers(MemorySegment barriers, int count) {
            this.imageBarriers = barriers;
            this.imageBarrierCount = count;
            return this;
        }

        public VkBarrierCmd build() {
            return new VkBarrierCmd(srcStageMask, dstStageMask, dependencyFlags,
                    memoryBarriers, memoryBarrierCount,
                    bufferBarriers, bufferBarrierCount,
                    imageBarriers, imageBarrierCount);
        }

        public void barrier(VkCommandBuffer cmd) {
            build().execute(cmd);
        }

        public void barrier(MemorySegment cmd) {
            build().execute(cmd);
        }
    }
}