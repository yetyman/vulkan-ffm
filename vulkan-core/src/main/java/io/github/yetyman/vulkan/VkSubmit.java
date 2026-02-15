package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import io.github.yetyman.vulkan.util.VkArrayBuilder;
import java.lang.foreign.*;

public class VkSubmit {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final VkArrayBuilder.AddressArrayBuilder waitSemaphores = VkArrayBuilder.addresses();
        private final VkArrayBuilder.IntArrayBuilder waitStages = VkArrayBuilder.ints();
        private final VkArrayBuilder.AddressArrayBuilder commandBuffers = VkArrayBuilder.addresses();
        private final VkArrayBuilder.AddressArrayBuilder signalSemaphores = VkArrayBuilder.addresses();
        
        public Builder waitSemaphore(MemorySegment semaphore, int stage) {
            waitSemaphores.add(semaphore);
            waitStages.add(stage);
            return this;
        }
        
        public Builder commandBuffer(VkCommandBuffer commandBuffer) {
            commandBuffers.add(commandBuffer.handle());
            return this;
        }
        
        public Builder signalSemaphore(MemorySegment semaphore) {
            signalSemaphores.add(semaphore);
            return this;
        }
        
        public VkResult submit(MemorySegment queue, MemorySegment fence, Arena arena) {
            MemorySegment waitSemArray = waitSemaphores.build(arena);
            MemorySegment waitStageArray = waitStages.build(arena);
            MemorySegment cmdBufArray = commandBuffers.build(arena);
            MemorySegment signalSemArray = signalSemaphores.build(arena);
            
            MemorySegment submitInfo = VkSubmitInfo.allocate(arena);
            VkSubmitInfo.sType(submitInfo, VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO.value());
            VkSubmitInfo.pNext(submitInfo, MemorySegment.NULL);
            VkSubmitInfo.waitSemaphoreCount(submitInfo, waitSemaphores.count());
            VkSubmitInfo.pWaitSemaphores(submitInfo, waitSemArray);
            VkSubmitInfo.pWaitDstStageMask(submitInfo, waitStageArray);
            VkSubmitInfo.commandBufferCount(submitInfo, commandBuffers.count());
            VkSubmitInfo.pCommandBuffers(submitInfo, cmdBufArray);
            VkSubmitInfo.signalSemaphoreCount(submitInfo, signalSemaphores.count());
            VkSubmitInfo.pSignalSemaphores(submitInfo, signalSemArray);
            
            return Vulkan.queueSubmit(queue, 1, submitInfo, fence);
        }
    }
}