package io.github.yetyman.vulkan;

import java.lang.foreign.*;

public class VkFenceOps {
    
    public static Builder waitFor(MemorySegment device) {
        return new Builder(device);
    }
    
    public static class Builder {
        private final MemorySegment device;
        private MemorySegment[] fences = new MemorySegment[0];
        private boolean waitAll = true;
        private long timeout = 0xFFFFFFFFFFFFFFFFL;
        
        private Builder(MemorySegment device) {
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
            return VulkanExtensions.waitForFences(device, fences.length, fenceArray, waitAll ? 1 : 0, timeout);
        }
        
        public VkResult reset(Arena arena) {
            MemorySegment fenceArray = arena.allocate(ValueLayout.ADDRESS, fences.length);
            for (int i = 0; i < fences.length; i++) {
                fenceArray.setAtIndex(ValueLayout.ADDRESS, i, fences[i]);
            }
            return VulkanExtensions.resetFences(device, fences.length, fenceArray);
        }
    }
}