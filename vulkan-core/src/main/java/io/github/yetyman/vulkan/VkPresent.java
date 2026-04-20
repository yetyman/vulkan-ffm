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

        public VkResult present(MemorySegment queue, Arena arena) {
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
    }
}