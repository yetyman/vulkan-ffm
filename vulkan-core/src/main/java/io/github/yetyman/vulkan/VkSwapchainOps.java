package io.github.yetyman.vulkan;

import java.lang.foreign.*;

public class VkSwapchainOps {
    
    public static AcquireBuilder acquireNextImage(VkDevice device, MemorySegment swapchain) {
        return new AcquireBuilder(device, swapchain);
    }
    
    public static class AcquireBuilder {
        private final VkDevice device;
        private final MemorySegment swapchain;
        private long timeout = 0xFFFFFFFFFFFFFFFFL;
        private MemorySegment semaphore = MemorySegment.NULL;
        private MemorySegment fence = MemorySegment.NULL;
        
        private AcquireBuilder(VkDevice device, MemorySegment swapchain) {
            this.device = device;
            this.swapchain = swapchain;
        }
        
        public AcquireBuilder timeout(long nanoseconds) {
            this.timeout = nanoseconds;
            return this;
        }
        
        public AcquireBuilder semaphore(MemorySegment semaphore) {
            this.semaphore = semaphore;
            return this;
        }
        
        public AcquireBuilder fence(MemorySegment fence) {
            this.fence = fence;
            return this;
        }
        
        public int execute(Arena arena) {
            MemorySegment imageIndex = arena.allocate(ValueLayout.JAVA_INT);
            Vulkan.acquireNextImageKHR(device.handle(), swapchain, timeout, semaphore, fence, imageIndex).check();
            return imageIndex.get(ValueLayout.JAVA_INT, 0);
        }
    }
}