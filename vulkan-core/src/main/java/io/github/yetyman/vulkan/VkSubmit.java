package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.util.VkArrayBuilder;
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
        private final VkArrayBuilder.AddressArrayBuilder waitSemaphores = VkArrayBuilder.addresses();
        private final VkArrayBuilder.IntArrayBuilder waitStages = VkArrayBuilder.ints();
        private final List<Long> waitValues = new ArrayList<>();
        private final VkArrayBuilder.AddressArrayBuilder commandBuffers = VkArrayBuilder.addresses();
        private final VkArrayBuilder.AddressArrayBuilder signalSemaphores = VkArrayBuilder.addresses();
        private final List<Long> signalValues = new ArrayList<>();
        private boolean hasTimelineValues = false;

        /** Waits on a binary semaphore. */
        public Builder waitSemaphore(MemorySegment semaphore, int stage) {
            waitSemaphores.add(semaphore);
            waitStages.add(stage);
            waitValues.add(0L);
            return this;
        }

        /** Waits on a binary semaphore. */
        public Builder waitSemaphore(VkSemaphore semaphore, int stage) {
            return waitSemaphore(semaphore.handle(), stage);
        }

        /** Waits on a timeline semaphore at the given value. */
        public Builder waitTimelineSemaphore(MemorySegment semaphore, long value, int stage) {
            waitSemaphores.add(semaphore);
            waitStages.add(stage);
            waitValues.add(value);
            hasTimelineValues = true;
            return this;
        }

        /** Waits on a timeline semaphore at the given value. */
        public Builder waitTimelineSemaphore(VkTimelineSemaphore semaphore, long value, int stage) {
            return waitTimelineSemaphore(semaphore.handle(), value, stage);
        }
        
        public Builder commandBuffer(VkCommandBuffer commandBuffer) {
            commandBuffers.add(commandBuffer.handle());
            return this;
        }

        /** Signals a binary semaphore. */
        public Builder signalSemaphore(MemorySegment semaphore) {
            signalSemaphores.add(semaphore);
            signalValues.add(0L);
            return this;
        }

        /** Signals a binary semaphore. */
        public Builder signalSemaphore(VkSemaphore semaphore) {
            return signalSemaphore(semaphore.handle());
        }

        /** Signals a timeline semaphore to the given value. */
        public Builder signalTimelineSemaphore(MemorySegment semaphore, long value) {
            signalSemaphores.add(semaphore);
            signalValues.add(value);
            hasTimelineValues = true;
            return this;
        }

        /** Signals a timeline semaphore to the given value. */
        public Builder signalTimelineSemaphore(VkTimelineSemaphore semaphore, long value) {
            return signalTimelineSemaphore(semaphore.handle(), value);
        }
        
        public VkResult submit(MemorySegment queue, MemorySegment fence, Arena arena) {
            return queueSubmit(queue, 1, build(fence, arena), fence);
        }

        /** Builds and submits via the queue's installed {@link io.github.yetyman.vulkan.queue.IQueueSubmitter}. */
        public void submit(VkQueue queue, MemorySegment fence, Arena arena) {
            queue.submit(build(fence, arena), fence);
        }

        /**
         * Builds the {@code VkSubmitInfo} struct into the given arena without submitting.
         * Use with {@link VkQueue#submit(MemorySegment, MemorySegment)} to route through
         * the queue's submitter strategy.
         */
        public MemorySegment build(MemorySegment fence, Arena arena) {
            MemorySegment waitSemArray   = waitSemaphores.build(arena);
            MemorySegment waitStageArray = waitStages.build(arena);
            MemorySegment cmdBufArray    = commandBuffers.build(arena);
            MemorySegment signalSemArray = signalSemaphores.build(arena);

            MemorySegment submitInfo = VkSubmitInfo.allocate(arena);
            VkSubmitInfo.sType(submitInfo, VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO.value());
            VkSubmitInfo.waitSemaphoreCount(submitInfo, waitSemaphores.count());
            VkSubmitInfo.pWaitSemaphores(submitInfo, waitSemArray);
            VkSubmitInfo.pWaitDstStageMask(submitInfo, waitStageArray);
            VkSubmitInfo.commandBufferCount(submitInfo, commandBuffers.count());
            VkSubmitInfo.pCommandBuffers(submitInfo, cmdBufArray);
            VkSubmitInfo.signalSemaphoreCount(submitInfo, signalSemaphores.count());
            VkSubmitInfo.pSignalSemaphores(submitInfo, signalSemArray);

            if (hasTimelineValues) {
                MemorySegment timelineInfo = VkTimelineSemaphoreSubmitInfo.allocate(arena);
                VkTimelineSemaphoreSubmitInfo.sType(timelineInfo, VkStructureType.VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO.value());
                VkTimelineSemaphoreSubmitInfo.pNext(timelineInfo, MemorySegment.NULL);
                if (!waitValues.isEmpty()) {
                    MemorySegment arr = arena.allocate(ValueLayout.JAVA_LONG, waitValues.size());
                    for (int i = 0; i < waitValues.size(); i++) arr.setAtIndex(ValueLayout.JAVA_LONG, i, waitValues.get(i));
                    VkTimelineSemaphoreSubmitInfo.waitSemaphoreValueCount(timelineInfo, waitValues.size());
                    VkTimelineSemaphoreSubmitInfo.pWaitSemaphoreValues(timelineInfo, arr);
                }
                if (!signalValues.isEmpty()) {
                    MemorySegment arr = arena.allocate(ValueLayout.JAVA_LONG, signalValues.size());
                    for (int i = 0; i < signalValues.size(); i++) arr.setAtIndex(ValueLayout.JAVA_LONG, i, signalValues.get(i));
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