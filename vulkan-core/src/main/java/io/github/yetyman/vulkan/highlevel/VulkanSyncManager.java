package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.*;

/**
 * Manages synchronization objects for frame-in-flight rendering.
 */
public class VulkanSyncManager implements AutoCloseable {
    private final Arena arena;
    private final MemorySegment device;
    private final VkSemaphore[] imageAvailableSemaphores;
    private final VkSemaphore[] renderFinishedSemaphores;
    private final VkFence[] inFlightFences;
    private final int maxFramesInFlight;
    private int currentFrame = 0;
    
    // Pre-allocated fence arrays to avoid FFM overhead
    private final MemorySegment[] waitFenceArrays;
    private final MemorySegment[] resetFenceArrays;
    

    
    private VulkanSyncManager(Arena arena, MemorySegment device, int maxFramesInFlight) {
        this.arena = arena;
        this.device = device;
        this.maxFramesInFlight = maxFramesInFlight;
        
        imageAvailableSemaphores = new VkSemaphore[maxFramesInFlight];
        renderFinishedSemaphores = new VkSemaphore[maxFramesInFlight];
        inFlightFences = new VkFence[maxFramesInFlight];
        waitFenceArrays = new MemorySegment[maxFramesInFlight];
        resetFenceArrays = new MemorySegment[maxFramesInFlight];
        
        for (int i = 0; i < maxFramesInFlight; i++) {
            imageAvailableSemaphores[i] = VkSemaphore.builder()
                .device(device)
                .build(arena);
            renderFinishedSemaphores[i] = VkSemaphore.builder()
                .device(device)
                .build(arena);
            inFlightFences[i] = VkFence.builder()
                .device(device)
                .signaled(true)
                .build(arena);
            
            // Pre-allocate fence arrays
            waitFenceArrays[i] = arena.allocate(ValueLayout.ADDRESS, 1);
            waitFenceArrays[i].setAtIndex(ValueLayout.ADDRESS, 0, inFlightFences[i].handle());
            resetFenceArrays[i] = arena.allocate(ValueLayout.ADDRESS, 1);
            resetFenceArrays[i].setAtIndex(ValueLayout.ADDRESS, 0, inFlightFences[i].handle());
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public FrameSync acquireFrame() {
        // Use pre-allocated fence arrays to avoid FFM overhead
        VulkanExtensions.waitForFences(device, 1, waitFenceArrays[currentFrame], 1, 0xFFFFFFFFFFFFFFFFL).check();
        VulkanExtensions.resetFences(device, 1, resetFenceArrays[currentFrame]).check();
        
        return new FrameSync(
            imageAvailableSemaphores[currentFrame],
            renderFinishedSemaphores[currentFrame],
            inFlightFences[currentFrame],
            currentFrame
        );
    }
    
    public void nextFrame() {
        currentFrame = (currentFrame + 1) % maxFramesInFlight;
    }
    
    @Override
    public void close() {
        for (int i = 0; i < maxFramesInFlight; i++) {
            imageAvailableSemaphores[i].close();
            renderFinishedSemaphores[i].close();
            inFlightFences[i].close();
        }
    }
    
    public static class FrameSync {
        public final VkSemaphore imageAvailable;
        public final VkSemaphore renderFinished;
        public final VkFence inFlight;
        public final int frameIndex;
        
        FrameSync(VkSemaphore imageAvailable, VkSemaphore renderFinished, VkFence inFlight, int frameIndex) {
            this.imageAvailable = imageAvailable;
            this.renderFinished = renderFinished;
            this.inFlight = inFlight;
            this.frameIndex = frameIndex;
        }
    }
    
    public static class Builder {
        private Arena arena;
        private MemorySegment device;
        private int framesInFlight = 2;
        
        public Builder arena(Arena arena) {
            this.arena = arena;
            return this;
        }
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public Builder context(VulkanContext context) {
            this.arena = context.arena();
            this.device = context.device().handle();
            return this;
        }
        
        public Builder framesInFlight(int count) {
            this.framesInFlight = count;
            return this;
        }
        
        public VulkanSyncManager build() {
            if (arena == null) throw new IllegalStateException("arena not set");
            if (device == null) throw new IllegalStateException("device not set");
            return new VulkanSyncManager(arena, device, framesInFlight);
        }
    }
}