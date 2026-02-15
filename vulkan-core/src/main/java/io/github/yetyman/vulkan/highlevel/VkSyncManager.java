package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.VkResult;
import java.lang.foreign.*;

/**
 * High-level synchronization manager that delegates to focused components.
 * Provides a unified interface for semaphores, fences, and barriers.
 */
public class VkSyncManager implements AutoCloseable {
    private final VkSemaphoreManager semaphoreManager;
    private final FencePool fencePool;
    private final Arena arena;
    
    private VkSyncManager(VkDevice device, Arena arena) {
        this.arena = arena;
        this.semaphoreManager = VkSemaphoreManager.builder().device(device).build(arena);
        this.fencePool = FencePool.builder().device(device).build(arena);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates or gets a timeline semaphore by name.
     */
    public VkSemaphoreManager.TimelineSemaphore getTimelineSemaphore(String name) {
        return semaphoreManager.getTimelineSemaphore(name);
    }
    
    /**
     * Creates a binary semaphore for one-time synchronization.
     */
    public VkSemaphore createBinarySemaphore() {
        return semaphoreManager.createBinarySemaphore();
    }
    
    /**
     * Gets a fence from the pool.
     */
    public VkFence acquireFence() {
        return fencePool.acquire();
    }
    
    /**
     * Returns a fence to the pool.
     */
    public void releaseFence(VkFence fence) {
        fencePool.release(fence);
    }
    
    /**
     * Waits for a fence with timeout.
     */
    public VkResult waitForFence(VkFence fence, long timeoutNs) {
        return fencePool.waitForFence(fence, timeoutNs);
    }
    
    /**
     * Creates a memory barrier for synchronization.
     */
    public io.github.yetyman.vulkan.VkMemoryBarrier createMemoryBarrier(int srcAccessMask, int dstAccessMask) {
        return io.github.yetyman.vulkan.VkMemoryBarrier.builder()
            .srcAccess(srcAccessMask)
            .dstAccess(dstAccessMask)
            .build(arena);
    }
    
    /**
     * Creates a buffer memory barrier.
     */
    public io.github.yetyman.vulkan.VkBufferBarrier createBufferBarrier(MemorySegment buffer, int srcAccessMask, int dstAccessMask,
                                           int srcQueueFamily, int dstQueueFamily, long offset, long size) {
        return io.github.yetyman.vulkan.VkBufferBarrier.builder()
            .buffer(buffer)
            .srcAccess(srcAccessMask)
            .dstAccess(dstAccessMask)
            .queueFamilyTransfer(srcQueueFamily, dstQueueFamily)
            .range(offset, size)
            .build(arena);
    }
    
    /**
     * Creates an image memory barrier.
     */
    public io.github.yetyman.vulkan.VkImageBarrier createImageBarrier(MemorySegment image, int srcAccessMask, int dstAccessMask,
                                          int oldLayout, int newLayout, int srcQueueFamily, int dstQueueFamily,
                                          int aspectMask, int baseMipLevel, int levelCount, int baseArrayLayer, int layerCount) {
        return io.github.yetyman.vulkan.VkImageBarrier.builder()
            .image(image)
            .srcAccess(srcAccessMask)
            .dstAccess(dstAccessMask)
            .transition(oldLayout, newLayout)
            .queueFamilyTransfer(srcQueueFamily, dstQueueFamily)
            .aspectMask(aspectMask)
            .mipLevels(baseMipLevel, levelCount)
            .arrayLayers(baseArrayLayer, layerCount)
            .build(arena);
    }
    
    /**
     * Records a pipeline barrier command with barrier wrappers.
     */
    public void pipelineBarrier(MemorySegment commandBuffer, int srcStageMask, int dstStageMask, VkBarrier... barriers) {
        MemorySegment[] memoryBarriers = null;
        MemorySegment[] bufferBarriers = null;
        MemorySegment[] imageBarriers = null;
        
        // Separate barriers by type
        int memCount = 0, bufCount = 0, imgCount = 0;
        for (VkBarrier barrier : barriers) {
            switch (barrier.getType()) {
                case MEMORY -> memCount++;
                case BUFFER -> bufCount++;
                case IMAGE -> imgCount++;
            }
        }
        
        if (memCount > 0) memoryBarriers = new MemorySegment[memCount];
        if (bufCount > 0) bufferBarriers = new MemorySegment[bufCount];
        if (imgCount > 0) imageBarriers = new MemorySegment[imgCount];
        
        int memIdx = 0, bufIdx = 0, imgIdx = 0;
        for (VkBarrier barrier : barriers) {
            switch (barrier.getType()) {
                case MEMORY -> memoryBarriers[memIdx++] = barrier.handle();
                case BUFFER -> bufferBarriers[bufIdx++] = barrier.handle();
                case IMAGE -> imageBarriers[imgIdx++] = barrier.handle();
            }
        }
        
        // Execute pipeline barrier
        MemorySegment memBarriersArray = MemorySegment.NULL;
        MemorySegment bufBarriersArray = MemorySegment.NULL;
        MemorySegment imgBarriersArray = MemorySegment.NULL;
        
        int memBarrierCount = 0;
        int bufBarrierCount = 0;
        int imgBarrierCount = 0;
        
        if (memoryBarriers != null && memoryBarriers.length > 0) {
            memBarrierCount = memoryBarriers.length;
            memBarriersArray = arena.allocate(io.github.yetyman.vulkan.generated.VkMemoryBarrier.layout(), memBarrierCount);
            for (int i = 0; i < memBarrierCount; i++) {
                MemorySegment.copy(memoryBarriers[i], 0, memBarriersArray, 
                    i * io.github.yetyman.vulkan.generated.VkMemoryBarrier.layout().byteSize(), io.github.yetyman.vulkan.generated.VkMemoryBarrier.layout().byteSize());
            }
        }
        
        if (bufferBarriers != null && bufferBarriers.length > 0) {
            bufBarrierCount = bufferBarriers.length;
            bufBarriersArray = arena.allocate(io.github.yetyman.vulkan.generated.VkBufferMemoryBarrier.layout(), bufBarrierCount);
            for (int i = 0; i < bufBarrierCount; i++) {
                MemorySegment.copy(bufferBarriers[i], 0, bufBarriersArray,
                    i * io.github.yetyman.vulkan.generated.VkBufferMemoryBarrier.layout().byteSize(), io.github.yetyman.vulkan.generated.VkBufferMemoryBarrier.layout().byteSize());
            }
        }
        
        if (imageBarriers != null && imageBarriers.length > 0) {
            imgBarrierCount = imageBarriers.length;
            imgBarriersArray = arena.allocate(io.github.yetyman.vulkan.generated.VkImageMemoryBarrier.layout(), imgBarrierCount);
            for (int i = 0; i < imgBarrierCount; i++) {
                MemorySegment.copy(imageBarriers[i], 0, imgBarriersArray,
                    i * io.github.yetyman.vulkan.generated.VkImageMemoryBarrier.layout().byteSize(), io.github.yetyman.vulkan.generated.VkImageMemoryBarrier.layout().byteSize());
            }
        }
        
        Vulkan.cmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0,
            memBarrierCount, memBarriersArray, bufBarrierCount, bufBarriersArray, imgBarrierCount, imgBarriersArray);
    }
    
    /**
     * Creates a frame-in-flight synchronization helper.
     */
    public FrameSync createFrameSync(int framesInFlight) {
        return new FrameSync(this, framesInFlight);
    }
    
    @Override
    public void close() {
        semaphoreManager.close();
        fencePool.close();
    }
    
    /**
     * Frame-in-flight synchronization helper for common rendering patterns.
     */
    public static class FrameSync implements AutoCloseable {
        private final VkSyncManager syncManager;
        private final VkSemaphore[] imageAvailable;
        private final VkSemaphore[] renderFinished;
        private final VkFence[] inFlight;
        private final int maxFramesInFlight;
        private int currentFrame = 0;
        
        FrameSync(VkSyncManager syncManager, int framesInFlight) {
            this.syncManager = syncManager;
            this.maxFramesInFlight = framesInFlight;
            this.imageAvailable = new VkSemaphore[framesInFlight];
            this.renderFinished = new VkSemaphore[framesInFlight];
            this.inFlight = new VkFence[framesInFlight];
            
            for (int i = 0; i < framesInFlight; i++) {
                imageAvailable[i] = syncManager.createBinarySemaphore();
                renderFinished[i] = syncManager.createBinarySemaphore();
                inFlight[i] = syncManager.acquireFence();
            }
        }
        
        /**
         * Acquires synchronization objects for the current frame.
         */
        public FrameSyncState acquireFrame() {
            syncManager.waitForFence(inFlight[currentFrame], Long.MAX_VALUE);
            return new FrameSyncState(
                imageAvailable[currentFrame],
                renderFinished[currentFrame], 
                inFlight[currentFrame],
                currentFrame
            );
        }
        
        /**
         * Advances to the next frame.
         */
        public void nextFrame() {
            currentFrame = (currentFrame + 1) % maxFramesInFlight;
        }
        
        @Override
        public void close() {
            for (int i = 0; i < maxFramesInFlight; i++) {
                syncManager.releaseFence(inFlight[i]);
            }
        }
    }
    
    /**
     * Synchronization state for a single frame.
     */
    public static class FrameSyncState {
        public final VkSemaphore imageAvailable;
        public final VkSemaphore renderFinished;
        public final VkFence inFlight;
        public final int frameIndex;
        
        FrameSyncState(VkSemaphore imageAvailable, VkSemaphore renderFinished,
                       VkFence inFlight, int frameIndex) {
            this.imageAvailable = imageAvailable;
            this.renderFinished = renderFinished;
            this.inFlight = inFlight;
            this.frameIndex = frameIndex;
        }
    }
    
    public static class Builder {
        private VkDevice device;
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public VkSyncManager build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            return new VkSyncManager(device, arena);
        }
    }
}