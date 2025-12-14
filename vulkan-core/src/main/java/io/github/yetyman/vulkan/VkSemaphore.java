package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan semaphore (VkSemaphore) with automatic resource management.
 * Semaphores provide GPU-GPU synchronization between queue operations.
 */
public class VkSemaphore implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkSemaphore(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a new semaphore.
     */
    public static VkSemaphore create(Arena arena, MemorySegment device) {
        return builder().device(device).build(arena);
    }
    
    /** @return a new builder for configuring semaphore creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkSemaphore handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroySemaphore(device, handle);
    }
    
    /**
     * Builder for semaphore creation.
     */
    public static class Builder {
        private MemorySegment device;
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates the semaphore */
        public VkSemaphore build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            
            MemorySegment semaphoreInfo = VkSemaphoreCreateInfo.allocate(arena);
            VkSemaphoreCreateInfo.sType(semaphoreInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            VkSemaphoreCreateInfo.pNext(semaphoreInfo, MemorySegment.NULL);
            VkSemaphoreCreateInfo.flags(semaphoreInfo, flags);
            
            MemorySegment semPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createSemaphore(device, semaphoreInfo, semPtr).check();
            return new VkSemaphore(semPtr.get(ValueLayout.ADDRESS, 0), device);
        }
    }
}