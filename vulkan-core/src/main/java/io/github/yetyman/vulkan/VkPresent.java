package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkPresent {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private MemorySegment[] waitSemaphores = new MemorySegment[0];
        private MemorySegment[] swapchains = new MemorySegment[0];
        private int[] imageIndices = new int[0];
        
        public Builder waitSemaphore(MemorySegment semaphore) {
            MemorySegment[] newWait = new MemorySegment[waitSemaphores.length + 1];
            System.arraycopy(waitSemaphores, 0, newWait, 0, waitSemaphores.length);
            newWait[waitSemaphores.length] = semaphore;
            waitSemaphores = newWait;
            return this;
        }
        
        public Builder swapchain(MemorySegment swapchain, int imageIndex) {
            MemorySegment[] newSwapchains = new MemorySegment[swapchains.length + 1];
            System.arraycopy(swapchains, 0, newSwapchains, 0, swapchains.length);
            newSwapchains[swapchains.length] = swapchain;
            swapchains = newSwapchains;
            
            int[] newIndices = new int[imageIndices.length + 1];
            System.arraycopy(imageIndices, 0, newIndices, 0, imageIndices.length);
            newIndices[imageIndices.length] = imageIndex;
            imageIndices = newIndices;
            return this;
        }
        
        public VkResult present(MemorySegment queue, Arena arena) {
            MemorySegment waitSemArray = waitSemaphores.length > 0 ? arena.allocate(ValueLayout.ADDRESS, waitSemaphores.length) : MemorySegment.NULL;
            MemorySegment swapchainArray = arena.allocate(ValueLayout.ADDRESS, swapchains.length);
            MemorySegment imageIndexArray = arena.allocate(ValueLayout.JAVA_INT, imageIndices.length);
            
            for (int i = 0; i < waitSemaphores.length; i++) {
                waitSemArray.setAtIndex(ValueLayout.ADDRESS, i, waitSemaphores[i]);
            }
            
            for (int i = 0; i < swapchains.length; i++) {
                swapchainArray.setAtIndex(ValueLayout.ADDRESS, i, swapchains[i]);
                imageIndexArray.setAtIndex(ValueLayout.JAVA_INT, i, imageIndices[i]);
            }
            
            MemorySegment presentInfo = VkPresentInfoKHR.allocate(arena);
            VkPresentInfoKHR.sType(presentInfo, VkStructureType.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
            VkPresentInfoKHR.pNext(presentInfo, MemorySegment.NULL);
            VkPresentInfoKHR.waitSemaphoreCount(presentInfo, waitSemaphores.length);
            VkPresentInfoKHR.pWaitSemaphores(presentInfo, waitSemArray);
            VkPresentInfoKHR.swapchainCount(presentInfo, swapchains.length);
            VkPresentInfoKHR.pSwapchains(presentInfo, swapchainArray);
            VkPresentInfoKHR.pImageIndices(presentInfo, imageIndexArray);
            VkPresentInfoKHR.pResults(presentInfo, MemorySegment.NULL);
            
            return VulkanExtensions.queuePresentKHR(queue, presentInfo);
        }
    }
}