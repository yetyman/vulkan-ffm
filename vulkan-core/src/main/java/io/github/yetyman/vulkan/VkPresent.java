package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.util.BumpAllocator;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

public class VkPresent {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<MemorySegment> waitSemaphores = new ArrayList<>(1);
        private final List<MemorySegment> swapchains = new ArrayList<>(1);
        private final List<Integer> imageIndices = new ArrayList<>(1);

        public Builder waitSemaphore(MemorySegment semaphore) {
            waitSemaphores.add(semaphore);
            return this;
        }

        public Builder swapchain(MemorySegment swapchain, int imageIndex) {
            swapchains.add(swapchain);
            imageIndices.add(imageIndex);
            return this;
        }

        /**
         * Builds the VkPresentInfoKHR into the given long-lived arena and returns a
         * {@link Cached} handle for zero-allocation per-frame patching. Call once; do not
         * call fluent setters on this Builder afterward -- use the returned Cached's patch
         * methods instead.
         */
        public Cached buildAndCache(Arena arena) {
            MemorySegment presentInfo = VkPresentInfoKHR.allocate(arena);
            VkPresentInfoKHR.sType(presentInfo, VkStructureType.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR.value());
            VkPresentInfoKHR.pNext(presentInfo, MemorySegment.NULL);
            VkPresentInfoKHR.waitSemaphoreCount(presentInfo, waitSemaphores.size());
            MemorySegment waitSemArray = null;
            if (!waitSemaphores.isEmpty()) {
                waitSemArray = arena.allocate(ValueLayout.ADDRESS, waitSemaphores.size());
                for (int i = 0; i < waitSemaphores.size(); i++)
                    waitSemArray.setAtIndex(ValueLayout.ADDRESS, i, waitSemaphores.get(i));
                VkPresentInfoKHR.pWaitSemaphores(presentInfo, waitSemArray);
            }
            VkPresentInfoKHR.swapchainCount(presentInfo, swapchains.size());
            MemorySegment swapchainArray = arena.allocate(ValueLayout.ADDRESS, swapchains.size());
            MemorySegment imageIndexArray = arena.allocate(ValueLayout.JAVA_INT, imageIndices.size());
            for (int i = 0; i < swapchains.size(); i++) {
                swapchainArray.setAtIndex(ValueLayout.ADDRESS, i, swapchains.get(i));
                imageIndexArray.setAtIndex(ValueLayout.JAVA_INT, i, imageIndices.get(i));
            }
            VkPresentInfoKHR.pSwapchains(presentInfo, swapchainArray);
            VkPresentInfoKHR.pImageIndices(presentInfo, imageIndexArray);
            VkPresentInfoKHR.pResults(presentInfo, MemorySegment.NULL);
            return new Cached(presentInfo, waitSemArray, swapchainArray, imageIndexArray);
        }

        /**
         * Zero-allocation handle to a cached VkPresentInfoKHR struct. Patch individual slots
         * via the setters below, then call {@link #present(MemorySegment)} or
         * {@link #presentCritical(MemorySegment)}. Not thread-safe -- use from a single thread,
         * matching the confined recording/submission model of GraphicsFrame.
         */
        public static final class Cached {
            private final MemorySegment presentInfo;
            private final MemorySegment waitSemArray;
            private final MemorySegment swapchainArray;
            private final MemorySegment imageIndexArray;

            private Cached(MemorySegment presentInfo, MemorySegment waitSemArray,
                          MemorySegment swapchainArray, MemorySegment imageIndexArray) {
                this.presentInfo = presentInfo;
                this.waitSemArray = waitSemArray;
                this.swapchainArray = swapchainArray;
                this.imageIndexArray = imageIndexArray;
            }

            /** Patches the wait semaphore handle at the given index. Zero allocation. */
            public void patchWaitSemaphore(int index, MemorySegment semaphore) {
                waitSemArray.setAtIndex(ValueLayout.ADDRESS, index, semaphore);
            }

            /** Patches the swapchain handle at the given index. Zero allocation. */
            public void patchSwapchain(int index, MemorySegment swapchain) {
                swapchainArray.setAtIndex(ValueLayout.ADDRESS, index, swapchain);
            }

            /** Patches the swapchain image index at the given index. Zero allocation. */
            public void patchImageIndex(int index, int imageIndex) {
                imageIndexArray.setAtIndex(ValueLayout.JAVA_INT, index, imageIndex);
            }

            /** Presents the cached struct directly via vkQueuePresentKHR. Zero allocation. */
            public VkResult present(MemorySegment queue) {
                return Vulkan.queuePresentKHR(queue, presentInfo);
            }

            /** Same as {@link #present(MemorySegment)}, via VulkanFFMCritical. Zero allocation. */
            public VkResult presentCritical(MemorySegment queue) {
                int result = io.github.yetyman.vulkan.generated.VulkanFFMCritical.vkQueuePresentKHRCritical(queue, presentInfo);
                return VkResult.fromInt(result);
            }
        }

        public VkResult present(MemorySegment queue, SegmentAllocator allocator) {
            BumpAllocator ba = BumpAllocator.get();
            ba.push();
            try {
                MemorySegment presentInfo = ba.alloc(VkPresentInfoKHR.sizeof());
                VkPresentInfoKHR.sType(presentInfo, VkStructureType.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR.value());
                VkPresentInfoKHR.pNext(presentInfo, MemorySegment.NULL);
                VkPresentInfoKHR.waitSemaphoreCount(presentInfo, waitSemaphores.size());
                if (!waitSemaphores.isEmpty()) {
                    MemorySegment waitSemArray = ba.alloc(ValueLayout.ADDRESS.byteSize() * waitSemaphores.size());
                    for (int i = 0; i < waitSemaphores.size(); i++)
                        waitSemArray.setAtIndex(ValueLayout.ADDRESS, i, waitSemaphores.get(i));
                    VkPresentInfoKHR.pWaitSemaphores(presentInfo, waitSemArray);
                }
                VkPresentInfoKHR.swapchainCount(presentInfo, swapchains.size());
                MemorySegment swapchainArray = ba.alloc(ValueLayout.ADDRESS.byteSize() * swapchains.size());
                MemorySegment imageIndexArray = ba.alloc(ValueLayout.JAVA_INT.byteSize() * imageIndices.size());
                for (int i = 0; i < swapchains.size(); i++) {
                    swapchainArray.setAtIndex(ValueLayout.ADDRESS, i, swapchains.get(i));
                    imageIndexArray.setAtIndex(ValueLayout.JAVA_INT, i, imageIndices.get(i));
                }
                VkPresentInfoKHR.pSwapchains(presentInfo, swapchainArray);
                VkPresentInfoKHR.pImageIndices(presentInfo, imageIndexArray);
                VkPresentInfoKHR.pResults(presentInfo, MemorySegment.NULL);
                return Vulkan.queuePresentKHR(queue, presentInfo);
            } finally {
                ba.pop();
            }
        }

        /**
         * Same as present(Arena), but issues the downcall through
         * io.github.yetyman.vulkan.generated.VulkanFFMCritical, which uses
         * Linker.Option.critical(false) linkage when active. vkQueuePresentKHR can block on
         * vsync depending on the swapchain's present mode; prefer this only where that risk
         * is acceptable for the call site (e.g. mailbox/immediate present modes).
         */
        public VkResult presentCritical(MemorySegment queue, SegmentAllocator allocator) {
            BumpAllocator ba = BumpAllocator.get();
            ba.push();
            try {
                MemorySegment presentInfo = ba.alloc(VkPresentInfoKHR.sizeof());
                VkPresentInfoKHR.sType(presentInfo, VkStructureType.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR.value());
                VkPresentInfoKHR.pNext(presentInfo, MemorySegment.NULL);
                VkPresentInfoKHR.waitSemaphoreCount(presentInfo, waitSemaphores.size());
                if (!waitSemaphores.isEmpty()) {
                    MemorySegment waitSemArray = ba.alloc(ValueLayout.ADDRESS.byteSize() * waitSemaphores.size());
                    for (int i = 0; i < waitSemaphores.size(); i++)
                        waitSemArray.setAtIndex(ValueLayout.ADDRESS, i, waitSemaphores.get(i));
                    VkPresentInfoKHR.pWaitSemaphores(presentInfo, waitSemArray);
                }
                VkPresentInfoKHR.swapchainCount(presentInfo, swapchains.size());
                MemorySegment swapchainArray = ba.alloc(ValueLayout.ADDRESS.byteSize() * swapchains.size());
                MemorySegment imageIndexArray = ba.alloc(ValueLayout.JAVA_INT.byteSize() * imageIndices.size());
                for (int i = 0; i < swapchains.size(); i++) {
                    swapchainArray.setAtIndex(ValueLayout.ADDRESS, i, swapchains.get(i));
                    imageIndexArray.setAtIndex(ValueLayout.JAVA_INT, i, imageIndices.get(i));
                }
                VkPresentInfoKHR.pSwapchains(presentInfo, swapchainArray);
                VkPresentInfoKHR.pImageIndices(presentInfo, imageIndexArray);
                VkPresentInfoKHR.pResults(presentInfo, MemorySegment.NULL);
                int result = io.github.yetyman.vulkan.generated.VulkanFFMCritical.vkQueuePresentKHRCritical(queue, presentInfo);
                return VkResult.fromInt(result);
            } finally {
                ba.pop();
            }
        }
    }
}