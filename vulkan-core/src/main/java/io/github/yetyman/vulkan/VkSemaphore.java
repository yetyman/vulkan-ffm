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
    private final VkDevice device;
    
    VkSemaphore(MemorySegment handle, VkDevice device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a new semaphore.
     */
    public static VkSemaphore create(Arena arena, VkDevice device) {
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
        Vulkan.destroySemaphore(device.handle(), handle);
    }
    
    /**
     * Builder for semaphore creation.
     */
    public static class Builder {
        private VkDevice device;
        private int flags = 0;
        private boolean timeline = false;
        private long initialValue = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates a timeline semaphore with initial value */
        public Builder timeline(long initialValue) {
            this.timeline = true;
            this.initialValue = initialValue;
            return this;
        }
        
        /** Creates the semaphore */
        public VkSemaphore build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            
            MemorySegment semaphoreInfo = VkSemaphoreCreateInfo.allocate(arena);
            VkSemaphoreCreateInfo.sType(semaphoreInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO.value());
            VkSemaphoreCreateInfo.flags(semaphoreInfo, flags);
            
            if (timeline) {
                MemorySegment typeInfo = VkSemaphoreTypeCreateInfo.allocate(arena);
                VkSemaphoreTypeCreateInfo.sType(typeInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO.value());
                VkSemaphoreTypeCreateInfo.semaphoreType(typeInfo, VkSemaphoreType.VK_SEMAPHORE_TYPE_TIMELINE.value());
                VkSemaphoreTypeCreateInfo.initialValue(typeInfo, initialValue);
                VkSemaphoreCreateInfo.pNext(semaphoreInfo, typeInfo);
            } else {
                VkSemaphoreCreateInfo.pNext(semaphoreInfo, MemorySegment.NULL);
            }
            
            MemorySegment semPtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createSemaphore(device.handle(), semaphoreInfo, semPtr).check();
            return new VkSemaphore(semPtr.get(ValueLayout.ADDRESS, 0), device);
        }
    }
}