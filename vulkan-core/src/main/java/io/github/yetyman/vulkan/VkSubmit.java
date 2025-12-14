package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkSubmit {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private MemorySegment[] waitSemaphores = new MemorySegment[0];
        private int[] waitStages = new int[0];
        private MemorySegment[] commandBuffers = new MemorySegment[0];
        private MemorySegment[] signalSemaphores = new MemorySegment[0];
        
        public Builder waitSemaphore(MemorySegment semaphore, int stage) {
            MemorySegment[] newWait = new MemorySegment[waitSemaphores.length + 1];
            System.arraycopy(waitSemaphores, 0, newWait, 0, waitSemaphores.length);
            newWait[waitSemaphores.length] = semaphore;
            waitSemaphores = newWait;
            
            int[] newStages = new int[waitStages.length + 1];
            System.arraycopy(waitStages, 0, newStages, 0, waitStages.length);
            newStages[waitStages.length] = stage;
            waitStages = newStages;
            return this;
        }
        
        public Builder commandBuffer(MemorySegment commandBuffer) {
            MemorySegment[] newCmds = new MemorySegment[commandBuffers.length + 1];
            System.arraycopy(commandBuffers, 0, newCmds, 0, commandBuffers.length);
            newCmds[commandBuffers.length] = commandBuffer;
            commandBuffers = newCmds;
            return this;
        }
        
        public Builder signalSemaphore(MemorySegment semaphore) {
            MemorySegment[] newSignal = new MemorySegment[signalSemaphores.length + 1];
            System.arraycopy(signalSemaphores, 0, newSignal, 0, signalSemaphores.length);
            newSignal[signalSemaphores.length] = semaphore;
            signalSemaphores = newSignal;
            return this;
        }
        
        public VkResult submit(MemorySegment queue, MemorySegment fence, Arena arena) {
            MemorySegment waitSemArray = waitSemaphores.length > 0 ? arena.allocate(ValueLayout.ADDRESS, waitSemaphores.length) : MemorySegment.NULL;
            MemorySegment waitStageArray = waitStages.length > 0 ? arena.allocate(ValueLayout.JAVA_INT, waitStages.length) : MemorySegment.NULL;
            MemorySegment cmdBufArray = commandBuffers.length > 0 ? arena.allocate(ValueLayout.ADDRESS, commandBuffers.length) : MemorySegment.NULL;
            MemorySegment signalSemArray = signalSemaphores.length > 0 ? arena.allocate(ValueLayout.ADDRESS, signalSemaphores.length) : MemorySegment.NULL;
            
            for (int i = 0; i < waitSemaphores.length; i++) {
                waitSemArray.setAtIndex(ValueLayout.ADDRESS, i, waitSemaphores[i]);
                waitStageArray.setAtIndex(ValueLayout.JAVA_INT, i, waitStages[i]);
            }
            
            for (int i = 0; i < commandBuffers.length; i++) {
                cmdBufArray.setAtIndex(ValueLayout.ADDRESS, i, commandBuffers[i]);
            }
            
            for (int i = 0; i < signalSemaphores.length; i++) {
                signalSemArray.setAtIndex(ValueLayout.ADDRESS, i, signalSemaphores[i]);
            }
            
            MemorySegment submitInfo = VkSubmitInfo.allocate(arena);
            VkSubmitInfo.sType(submitInfo, VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO);
            VkSubmitInfo.pNext(submitInfo, MemorySegment.NULL);
            VkSubmitInfo.waitSemaphoreCount(submitInfo, waitSemaphores.length);
            VkSubmitInfo.pWaitSemaphores(submitInfo, waitSemArray);
            VkSubmitInfo.pWaitDstStageMask(submitInfo, waitStageArray);
            VkSubmitInfo.commandBufferCount(submitInfo, commandBuffers.length);
            VkSubmitInfo.pCommandBuffers(submitInfo, cmdBufArray);
            VkSubmitInfo.signalSemaphoreCount(submitInfo, signalSemaphores.length);
            VkSubmitInfo.pSignalSemaphores(submitInfo, signalSemArray);
            
            return VulkanExtensions.queueSubmit(queue, 1, submitInfo, fence);
        }
    }
}