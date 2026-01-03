package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.util.VkArrayBuilder;
import java.lang.foreign.*;

public class VkPresent {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final VkArrayBuilder.AddressArrayBuilder waitSemaphores = VkArrayBuilder.addresses();
        private final VkArrayBuilder.AddressArrayBuilder swapchains = VkArrayBuilder.addresses();
        private final VkArrayBuilder.IntArrayBuilder imageIndices = VkArrayBuilder.ints();
        
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
            MemorySegment waitSemArray = waitSemaphores.build(arena);
            MemorySegment swapchainArray = swapchains.build(arena);
            MemorySegment imageIndexArray = imageIndices.build(arena);
            
            MemorySegment presentInfo = VkPresentInfoKHR.allocate(arena);
            VkPresentInfoKHR.sType(presentInfo, VkStructureType.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
            VkPresentInfoKHR.pNext(presentInfo, MemorySegment.NULL);
            VkPresentInfoKHR.waitSemaphoreCount(presentInfo, waitSemaphores.count());
            VkPresentInfoKHR.pWaitSemaphores(presentInfo, waitSemArray);
            VkPresentInfoKHR.swapchainCount(presentInfo, swapchains.count());
            VkPresentInfoKHR.pSwapchains(presentInfo, swapchainArray);
            VkPresentInfoKHR.pImageIndices(presentInfo, imageIndexArray);
            VkPresentInfoKHR.pResults(presentInfo, MemorySegment.NULL);
            
            return Vulkan.queuePresentKHR(queue, presentInfo);
        }
    }
}