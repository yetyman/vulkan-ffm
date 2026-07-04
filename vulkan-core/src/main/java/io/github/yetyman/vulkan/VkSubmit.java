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

        public VkResult submit(MemorySegment queue, MemorySegment fence, SegmentAllocator allocator) {
            BumpAllocator ba = BumpAllocator.get();
            ba.push();
            try {
                return queueSubmit(queue, 1, buildInternal(fence, ba, null), fence);
            } finally {
                ba.pop();
            }
        }

        /**
         * Builds and submits via the queue's installed {@link io.github.yetyman.vulkan.queue.IQueueSubmitter}.
         */
        public void submit(VkQueue queue, MemorySegment fence, SegmentAllocator allocator) {
            BumpAllocator ba = BumpAllocator.get();
            ba.push();
            try {
                queue.submit(buildInternal(fence, ba, null), fence);
            } finally {
                ba.pop();
            }
        }

        /**
         * Builds the {@code VkSubmitInfo} struct into the given arena without submitting.
         * Use with {@link VkQueue#submit(MemorySegment, MemorySegment)} to route through
         * the queue's submitter strategy.
         */
        public MemorySegment build(MemorySegment fence, SegmentAllocator allocator) {
            return buildInternal(fence, allocator, null);
        }

        // --- Cached submit info: zero-allocation per-frame patch-and-submit ---
        //
        // Call buildAndCache(Arena) once (e.g. at frame-sync setup time) with the wait/signal
        // semaphore slots and command buffer slots already configured via the fluent methods
        // above. The resulting Cached handle lets callers patch individual semaphore/command
        // buffer handles and timeline values per frame (pointer/value writes only, no native
        // allocation) before calling submit(...) again.
        //
        // Only usable when the shape (wait/signal/command-buffer counts, and whether timeline
        // values are used) is fixed for the lifetime of the cache -- which is the case for
        // GraphicsFrame's per-frame-slot submit info (same command buffer per slot, same
        // number of wait/signal semaphores and timeline waits configured once before the
        // render loop starts).

        /**
         * Builds the VkSubmitInfo (and VkTimelineSemaphoreSubmitInfo, if timeline values are
         * used) into the given long-lived arena and returns a {@link Cached} handle for
         * zero-allocation per-frame patching. Call once; do not call fluent setters on this
         * Builder afterward -- use the returned Cached's patch methods instead.
         */
        public Cached buildAndCache(Arena arena) {
            CapturedArrays captured = new CapturedArrays();
            MemorySegment submitInfo = buildInternal(MemorySegment.NULL, arena, captured);
            return new Cached(submitInfo, captured.waitSemArray, captured.waitStageArray, captured.cmdBufArray,
                captured.signalSemArray, captured.waitValueArray, captured.signalValueArray);
        }

        /**
         * Zero-allocation handle to a cached VkSubmitInfo struct. Patch individual slots via
         * the setters below, then call {@link #submit(MemorySegment, MemorySegment)} or
         * {@link #submit(VkQueue, MemorySegment)}. Not thread-safe -- use from a single thread,
         * matching the confined recording/submission model of GraphicsFrame.
         */
        public static final class Cached {
            private final MemorySegment submitInfo;
            private final MemorySegment waitSemArray;
            private final MemorySegment waitStageArray;
            private final MemorySegment cmdBufArray;
            private final MemorySegment signalSemArray;
            private final MemorySegment waitValueArray;
            private final MemorySegment signalValueArray;

            private Cached(MemorySegment submitInfo, MemorySegment waitSemArray, MemorySegment waitStageArray,
                          MemorySegment cmdBufArray, MemorySegment signalSemArray,
                          MemorySegment waitValueArray, MemorySegment signalValueArray) {
                this.submitInfo = submitInfo;
                this.waitSemArray = waitSemArray;
                this.waitStageArray = waitStageArray;
                this.cmdBufArray = cmdBufArray;
                this.signalSemArray = signalSemArray;
                this.waitValueArray = waitValueArray;
                this.signalValueArray = signalValueArray;
            }

            /** Patches the wait semaphore handle at the given index. Zero allocation. */
            public void patchWaitSemaphore(int index, MemorySegment semaphore) {
                waitSemArray.setAtIndex(ValueLayout.ADDRESS, index, semaphore);
            }

            /** Patches the wait dst-stage mask at the given index. Zero allocation. */
            public void patchWaitStage(int index, int stageMask) {
                waitStageArray.setAtIndex(ValueLayout.JAVA_INT, index, stageMask);
            }

            /** Patches the command buffer handle at the given index. Zero allocation. */
            public void patchCommandBuffer(int index, MemorySegment commandBuffer) {
                cmdBufArray.setAtIndex(ValueLayout.ADDRESS, index, commandBuffer);
            }

            /** Patches the signal semaphore handle at the given index. Zero allocation. */
            public void patchSignalSemaphore(int index, MemorySegment semaphore) {
                signalSemArray.setAtIndex(ValueLayout.ADDRESS, index, semaphore);
            }

            /** Patches the timeline wait value at the given wait-slot index. Zero allocation. */
            public void patchWaitTimelineValue(int index, long value) {
                waitValueArray.setAtIndex(ValueLayout.JAVA_LONG, index, value);
            }

            /** Patches the timeline signal value at the given signal-slot index. Zero allocation. */
            public void patchSignalTimelineValue(int index, long value) {
                signalValueArray.setAtIndex(ValueLayout.JAVA_LONG, index, value);
            }

            /** Submits the cached struct directly via vkQueueSubmit. Zero allocation. */
            public VkResult submit(MemorySegment queue, MemorySegment fence) {
                return queueSubmit(queue, 1, submitInfo, fence);
            }

            /** Submits the cached struct via the queue's installed IQueueSubmitter. Zero allocation. */
            public void submit(VkQueue queue, MemorySegment fence) {
                queue.submit(submitInfo, fence);
            }

            /** Same as {@link #submit(MemorySegment, MemorySegment)}, via VulkanFFMCritical. Zero allocation. */
            public VkResult submitCritical(MemorySegment queue, MemorySegment fence) {
                return queueSubmitCritical(queue, 1, submitInfo, fence);
            }
        }

        private static final class CapturedArrays {
            MemorySegment waitSemArray, waitStageArray, cmdBufArray, signalSemArray, waitValueArray, signalValueArray;
        }

        private MemorySegment buildInternal(MemorySegment fence, SegmentAllocator alloc, CapturedArrays captured) {
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
                if (captured != null) {
                    captured.waitSemArray = waitSemArray;
                    captured.waitStageArray = waitStageArray;
                }
            }
            VkSubmitInfo.commandBufferCount(submitInfo, cmdCount);
            if (cmdCount > 0) {
                MemorySegment cmdBufArray = alloc.allocate(ValueLayout.ADDRESS, cmdCount);
                for (int i = 0; i < cmdCount; i++)
                    cmdBufArray.setAtIndex(ValueLayout.ADDRESS, i, commandBuffers[i]);
                VkSubmitInfo.pCommandBuffers(submitInfo, cmdBufArray);
                if (captured != null) captured.cmdBufArray = cmdBufArray;
            }
            VkSubmitInfo.signalSemaphoreCount(submitInfo, signalCount);
            if (signalCount > 0) {
                MemorySegment signalSemArray = alloc.allocate(ValueLayout.ADDRESS, signalCount);
                for (int i = 0; i < signalCount; i++)
                    signalSemArray.setAtIndex(ValueLayout.ADDRESS, i, signalSemaphores[i]);
                VkSubmitInfo.pSignalSemaphores(submitInfo, signalSemArray);
                if (captured != null) captured.signalSemArray = signalSemArray;
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
                    if (captured != null) captured.waitValueArray = arr;
                }
                if (signalCount > 0) {
                    MemorySegment arr = alloc.allocate(ValueLayout.JAVA_LONG, signalCount);
                    for (int i = 0; i < signalCount; i++)
                        arr.setAtIndex(ValueLayout.JAVA_LONG, i, signalValues[i]);
                    VkTimelineSemaphoreSubmitInfo.signalSemaphoreValueCount(timelineInfo, signalCount);
                    VkTimelineSemaphoreSubmitInfo.pSignalSemaphoreValues(timelineInfo, arr);
                    if (captured != null) captured.signalValueArray = arr;
                }
                VkSubmitInfo.pNext(submitInfo, timelineInfo);
            } else {
                VkSubmitInfo.pNext(submitInfo, MemorySegment.NULL);
            }
            return submitInfo;
        }
    }
}