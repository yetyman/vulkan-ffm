package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.util.BumpAllocator;

import java.lang.foreign.*;

public class VkSubmit {

    public static VkResult queueSubmit(MemorySegment queue, int submitCount, MemorySegment submits, MemorySegment fence) {
        int result = VulkanFFM.vkQueueSubmit(queue, submitCount, submits, fence);
        return VkResult.fromInt(result);
    }

    /**
     * Same as queueSubmit(...), but issues the downcall through
     * io.github.yetyman.vulkan.generated.VulkanFFMCritical, which uses Linker.Option.critical(false)
     * linkage when active. vkQueueSubmit is usually fast but can block if the driver's internal
     * queue is full; prefer this only where that risk is acceptable for the call site.
     */
    public static VkResult queueSubmitCritical(MemorySegment queue, int submitCount, MemorySegment submits, MemorySegment fence) {
        int result = io.github.yetyman.vulkan.generated.VulkanFFMCritical.vkQueueSubmitCritical(queue, submitCount, submits, fence);
        return VkResult.fromInt(result);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static final int INITIAL = 2;

        private MemorySegment[] waitSemaphores = new MemorySegment[INITIAL];
        private int[]           waitStages     = new int[INITIAL];
        private long[]          waitValues     = new long[INITIAL];
        private int             waitCount      = 0;

        private MemorySegment[] commandBuffers = new MemorySegment[INITIAL];
        private int             cmdCount       = 0;

        private MemorySegment[] signalSemaphores = new MemorySegment[INITIAL];
        private long[]          signalValues     = new long[INITIAL];
        private int             signalCount      = 0;

        private boolean hasTimelineValues = false;

        public Builder waitSemaphore(MemorySegment semaphore, int stage) {
            if (waitCount == waitSemaphores.length) {
                waitSemaphores = java.util.Arrays.copyOf(waitSemaphores, waitCount * 2);
                waitStages     = java.util.Arrays.copyOf(waitStages,     waitCount * 2);
                waitValues     = java.util.Arrays.copyOf(waitValues,     waitCount * 2);
            }
            waitSemaphores[waitCount] = semaphore;
            waitStages[waitCount]     = stage;
            waitValues[waitCount]     = 0L;
            waitCount++;
            return this;
        }

        public Builder waitSemaphore(VkSemaphore semaphore, int stage) {
            return waitSemaphore(semaphore.handle(), stage);
        }

        public Builder waitTimelineSemaphore(MemorySegment semaphore, long value, int stage) {
            if (waitCount == waitSemaphores.length) {
                waitSemaphores = java.util.Arrays.copyOf(waitSemaphores, waitCount * 2);
                waitStages     = java.util.Arrays.copyOf(waitStages,     waitCount * 2);
                waitValues     = java.util.Arrays.copyOf(waitValues,     waitCount * 2);
            }
            waitSemaphores[waitCount] = semaphore;
            waitStages[waitCount]     = stage;
            waitValues[waitCount]     = value;
            waitCount++;
            hasTimelineValues = true;
            return this;
        }

        public Builder waitTimelineSemaphore(VkTimelineSemaphore semaphore, long value, int stage) {
            return waitTimelineSemaphore(semaphore.handle(), value, stage);
        }

        public Builder commandBuffer(VkCommandBuffer commandBuffer) {
            if (cmdCount == commandBuffers.length)
                commandBuffers = java.util.Arrays.copyOf(commandBuffers, cmdCount * 2);
            commandBuffers[cmdCount++] = commandBuffer.handle();
            return this;
        }

        public Builder signalSemaphore(MemorySegment semaphore) {
            if (signalCount == signalSemaphores.length) {
                signalSemaphores = java.util.Arrays.copyOf(signalSemaphores, signalCount * 2);
                signalValues     = java.util.Arrays.copyOf(signalValues,     signalCount * 2);
            }
            signalSemaphores[signalCount] = semaphore;
            signalValues[signalCount]     = 0L;
            signalCount++;
            return this;
        }

        public Builder signalSemaphore(VkSemaphore semaphore) {
            return signalSemaphore(semaphore.handle());
        }

        public Builder signalTimelineSemaphore(MemorySegment semaphore, long value) {
            if (signalCount == signalSemaphores.length) {
                signalSemaphores = java.util.Arrays.copyOf(signalSemaphores, signalCount * 2);
                signalValues     = java.util.Arrays.copyOf(signalValues,     signalCount * 2);
            }
            signalSemaphores[signalCount] = semaphore;
            signalValues[signalCount]     = value;
            signalCount++;
            hasTimelineValues = true;
            return this;
        }

        public Builder signalTimelineSemaphore(VkTimelineSemaphore semaphore, long value) {
            return signalTimelineSemaphore(semaphore.handle(), value);
        }

        public VkResult submit(MemorySegment queue, MemorySegment fence, Arena arena) {
            BumpAllocator ba = BumpAllocator.get();
            ba.push();
            try {
                return queueSubmit(queue, 1, buildInternal(fence, ba), fence);
            } finally {
                ba.pop();
            }
        }

        /**
         * Builds and submits via the queue's installed {@link io.github.yetyman.vulkan.queue.IQueueSubmitter}.
         */
        public void submit(VkQueue queue, MemorySegment fence, Arena arena) {
            BumpAllocator ba = BumpAllocator.get();
            ba.push();
            try {
                queue.submit(buildInternal(fence, ba), fence);
            } finally {
                ba.pop();
            }
        }

        /**
         * Builds the {@code VkSubmitInfo} struct into the given arena without submitting.
         * Use with {@link VkQueue#submit(MemorySegment, MemorySegment)} to route through
         * the queue's submitter strategy.
         */
        public MemorySegment build(MemorySegment fence, Arena arena) {
            return buildInternal(fence, arena);
        }

        private MemorySegment buildInternal(MemorySegment fence, SegmentAllocator alloc) {
            MemorySegment submitInfo = VkSubmitInfo.allocate(alloc);
            VkSubmitInfo.sType(submitInfo, VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO.value());
            VkSubmitInfo.waitSemaphoreCount(submitInfo, waitCount);
            if (waitCount > 0) {
                MemorySegment waitSemArray   = alloc.allocate(ValueLayout.ADDRESS,   waitCount);
                MemorySegment waitStageArray = alloc.allocate(ValueLayout.JAVA_INT,  waitCount);
                for (int i = 0; i < waitCount; i++) {
                    waitSemArray.setAtIndex(ValueLayout.ADDRESS,  i, waitSemaphores[i]);
                    waitStageArray.setAtIndex(ValueLayout.JAVA_INT, i, waitStages[i]);
                }
                VkSubmitInfo.pWaitSemaphores(submitInfo, waitSemArray);
                VkSubmitInfo.pWaitDstStageMask(submitInfo, waitStageArray);
            }
            VkSubmitInfo.commandBufferCount(submitInfo, cmdCount);
            if (cmdCount > 0) {
                MemorySegment cmdBufArray = alloc.allocate(ValueLayout.ADDRESS, cmdCount);
                for (int i = 0; i < cmdCount; i++)
                    cmdBufArray.setAtIndex(ValueLayout.ADDRESS, i, commandBuffers[i]);
                VkSubmitInfo.pCommandBuffers(submitInfo, cmdBufArray);
            }
            VkSubmitInfo.signalSemaphoreCount(submitInfo, signalCount);
            if (signalCount > 0) {
                MemorySegment signalSemArray = alloc.allocate(ValueLayout.ADDRESS, signalCount);
                for (int i = 0; i < signalCount; i++)
                    signalSemArray.setAtIndex(ValueLayout.ADDRESS, i, signalSemaphores[i]);
                VkSubmitInfo.pSignalSemaphores(submitInfo, signalSemArray);
            }

            if (hasTimelineValues) {
                MemorySegment timelineInfo = VkTimelineSemaphoreSubmitInfo.allocate(alloc);
                VkTimelineSemaphoreSubmitInfo.sType(timelineInfo, VkStructureType.VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO.value());
                VkTimelineSemaphoreSubmitInfo.pNext(timelineInfo, MemorySegment.NULL);
                if (waitCount > 0) {
                    MemorySegment arr = alloc.allocate(ValueLayout.JAVA_LONG, waitCount);
                    for (int i = 0; i < waitCount; i++)
                        arr.setAtIndex(ValueLayout.JAVA_LONG, i, waitValues[i]);
                    VkTimelineSemaphoreSubmitInfo.waitSemaphoreValueCount(timelineInfo, waitCount);
                    VkTimelineSemaphoreSubmitInfo.pWaitSemaphoreValues(timelineInfo, arr);
                }
                if (signalCount > 0) {
                    MemorySegment arr = alloc.allocate(ValueLayout.JAVA_LONG, signalCount);
                    for (int i = 0; i < signalCount; i++)
                        arr.setAtIndex(ValueLayout.JAVA_LONG, i, signalValues[i]);
                    VkTimelineSemaphoreSubmitInfo.signalSemaphoreValueCount(timelineInfo, signalCount);
                    VkTimelineSemaphoreSubmitInfo.pSignalSemaphoreValues(timelineInfo, arr);
                }
                VkSubmitInfo.pNext(submitInfo, timelineInfo);
            } else {
                VkSubmitInfo.pNext(submitInfo, MemorySegment.NULL);
            }
            return submitInfo;
        }
    }
}