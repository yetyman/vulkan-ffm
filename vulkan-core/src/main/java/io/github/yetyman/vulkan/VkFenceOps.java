package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.VulkanFFM;
import java.lang.foreign.*;

public class VkFenceOps {
    
    public static Builder waitFor(VkDevice device) {
        return new Builder(device);
    }
    
    /**
     * Wait for a single fence with timeout.
     */
    public static VkResult wait(VkDevice device, VkFence fence, long timeout, Arena arena) {
        return waitFor(device).fence(fence.handle()).timeout(timeout).execute(arena);
    }
    
    /**
     * Get status of a single fence without blocking.
     */
    public static VkResult getStatus(VkDevice device, VkFence fence) {
        int result = VulkanFFM.vkGetFenceStatus(device.handle(), fence.handle());
        return VkResult.fromInt(result);
    }
    
    /**
     * Reset a single fence to unsignaled state.
     */
    public static VkResult reset(VkDevice device, VkFence fence, Arena arena) {
        MemorySegment fenceArray = arena.allocate(ValueLayout.ADDRESS);
        fenceArray.set(ValueLayout.ADDRESS, 0, fence.handle());
        return Vulkan.resetFences(device.handle(), 1, fenceArray);
    }
    
    public static class Builder {
        private final VkDevice device;
        private MemorySegment[] fences = new MemorySegment[0];
        private boolean waitAll = true;
        private long timeout = 0xFFFFFFFFFFFFFFFFL;
        
        private Builder(VkDevice device) {
            this.device = device;
        }
        
        public Builder fence(MemorySegment fence) {
            MemorySegment[] newFences = new MemorySegment[fences.length + 1];
            System.arraycopy(fences, 0, newFences, 0, fences.length);
            newFences[fences.length] = fence;
            fences = newFences;
            return this;
        }
        
        public Builder waitAny() {
            this.waitAll = false;
            return this;
        }
        
        public Builder timeout(long nanoseconds) {
            this.timeout = nanoseconds;
            return this;
        }
        
        public VkResult execute(Arena arena) {
            MemorySegment fenceArray = arena.allocate(ValueLayout.ADDRESS, fences.length);
            for (int i = 0; i < fences.length; i++) {
                fenceArray.setAtIndex(ValueLayout.ADDRESS, i, fences[i]);
            }
            return Vulkan.waitForFences(device.handle(), fences.length, fenceArray, waitAll ? 1 : 0, timeout);
        }
        
        public VkResult reset(Arena arena) {
            MemorySegment fenceArray = arena.allocate(ValueLayout.ADDRESS, fences.length);
            for (int i = 0; i < fences.length; i++) {
                fenceArray.setAtIndex(ValueLayout.ADDRESS, i, fences[i]);
            }
            return Vulkan.resetFences(device.handle(), fences.length, fenceArray);
        }
    }
}