package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

public class VkSubmit {

    public static VkResult queueSubmit(MemorySegment queue, int submitCount, MemorySegment submits, MemorySegment fence) {
        int result = VulkanFFM.vkQueueSubmit(queue, submitCount, submits, fence);
        return VkResult.fromInt(result);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<MemorySegment> waitSemaphores = new ArrayList<>(1);
        private final List<Integer> waitStages = new ArrayList<>(1);
        private final List<Long> waitValues = new ArrayList<>(1);
        private final List<MemorySegment> commandBuffers = new ArrayList<>(1);
        private final List<MemorySegment> signalSemaphores = new ArrayList<>(1);
        private final List<Long> signalValues = new ArrayList<>(1);
        private boolean hasTimelineValues = false;

        public Builder waitSemaphore(MemorySegment semaphore, int stage) {
            waitSemaphores.add(semaphore);
            waitStages.add(stage);
            waitValues.add(0L);
            return this;
        }

        public Builder waitSemaphore(VkSemaphore semaphore, int stage) {
            return waitSemaphore(semaphore.handle(), stage);
        }

        public Builder waitTimelineSemaphore(MemorySegment semaphore, long value, int stage) {
            waitSemaphores.add(semaphore);
            waitStages.add(stage);
            waitValues.add(value);
            hasTimelineValues = true;
            return this;
        }

        public Builder waitTimelineSemaphore(VkTimelineSemaphore semaphore, long value, int stage) {
            return waitTimelineSemaphore(semaphore.handle(), value, stage);
        }

        public Builder commandBuffer(VkCommandBuffer commandBuffer) {
            commandBuffers.add(commandBuffer.handle());
            return this;
        }

        public Builder signalSemaphore(MemorySegment semaphore) {
            signalSemaphores.add(semaphore);
            signalValues.add(0L);
            return this;
        }

        public Builder signalSemaphore(VkSemaphore semaphore) {
            return signalSemaphore(semaphore.handle());
        }

        public Builder signalTimelineSemaphore(MemorySegment semaphore, long value) {
            signalSemaphores.add(semaphore);
            signalValues.add(value);
            hasTimelineValues = true;
            return this;
        }

        public Builder signalTimelineSemaphore(VkTimelineSemaphore semaphore, long value) {
            return signalTimelineSemaphore(semaphore.handle(), value);
        }

        public VkResult submit(MemorySegment queue, MemorySegment fence, Arena arena) {
            return queueSubmit(queue, 1, build(fence, arena), fence);
        }

        /**
         * Builds and submits via the queue's installed {@link io.github.yetyman.vulkan.queue.IQueueSubmitter}.
         */
        public void submit(VkQueue queue, MemorySegment fence, Arena arena) {
            queue.submit(build(fence, arena), fence);
        }

        /**
         * Builds the {@code VkSubmitInfo} struct into the given arena without submitting.
         * Use with {@link VkQueue#submit(MemorySegment, MemorySegment)} to route through
         * the queue's submitter strategy.
         */
        public MemorySegment build(MemorySegment fence, Arena arena) {
            MemorySegment submitInfo = VkSubmitInfo.allocate(arena);
            VkSubmitInfo.sType(submitInfo, VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO.value());
            VkSubmitInfo.waitSemaphoreCount(submitInfo, waitSemaphores.size());
            if (!waitSemaphores.isEmpty()) {
                MemorySegment waitSemArray = arena.allocate(ValueLayout.ADDRESS, waitSemaphores.size());
                MemorySegment waitStageArray = arena.allocate(ValueLayout.JAVA_INT, waitStages.size());
                for (int i = 0; i < waitSemaphores.size(); i++) {
                    waitSemArray.setAtIndex(ValueLayout.ADDRESS, i, waitSemaphores.get(i));
                    waitStageArray.setAtIndex(ValueLayout.JAVA_INT, i, waitStages.get(i));
                }
                VkSubmitInfo.pWaitSemaphores(submitInfo, waitSemArray);
                VkSubmitInfo.pWaitDstStageMask(submitInfo, waitStageArray);
            }
            VkSubmitInfo.commandBufferCount(submitInfo, commandBuffers.size());
            if (!commandBuffers.isEmpty()) {
                MemorySegment cmdBufArray = arena.allocate(ValueLayout.ADDRESS, commandBuffers.size());
                for (int i = 0; i < commandBuffers.size(); i++)
                    cmdBufArray.setAtIndex(ValueLayout.ADDRESS, i, commandBuffers.get(i));
                VkSubmitInfo.pCommandBuffers(submitInfo, cmdBufArray);
            }
            VkSubmitInfo.signalSemaphoreCount(submitInfo, signalSemaphores.size());
            if (!signalSemaphores.isEmpty()) {
                MemorySegment signalSemArray = arena.allocate(ValueLayout.ADDRESS, signalSemaphores.size());
                for (int i = 0; i < signalSemaphores.size(); i++)
                    signalSemArray.setAtIndex(ValueLayout.ADDRESS, i, signalSemaphores.get(i));
                VkSubmitInfo.pSignalSemaphores(submitInfo, signalSemArray);
            }

            if (hasTimelineValues) {
                MemorySegment timelineInfo = VkTimelineSemaphoreSubmitInfo.allocate(arena);
                VkTimelineSemaphoreSubmitInfo.sType(timelineInfo, VkStructureType.VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO.value());
                VkTimelineSemaphoreSubmitInfo.pNext(timelineInfo, MemorySegment.NULL);
                if (!waitValues.isEmpty()) {
                    MemorySegment arr = arena.allocate(ValueLayout.JAVA_LONG, waitValues.size());
                    for (int i = 0; i < waitValues.size(); i++)
                        arr.setAtIndex(ValueLayout.JAVA_LONG, i, waitValues.get(i));
                    VkTimelineSemaphoreSubmitInfo.waitSemaphoreValueCount(timelineInfo, waitValues.size());
                    VkTimelineSemaphoreSubmitInfo.pWaitSemaphoreValues(timelineInfo, arr);
                }
                if (!signalValues.isEmpty()) {
                    MemorySegment arr = arena.allocate(ValueLayout.JAVA_LONG, signalValues.size());
                    for (int i = 0; i < signalValues.size(); i++)
                        arr.setAtIndex(ValueLayout.JAVA_LONG, i, signalValues.get(i));
                    VkTimelineSemaphoreSubmitInfo.signalSemaphoreValueCount(timelineInfo, signalValues.size());
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