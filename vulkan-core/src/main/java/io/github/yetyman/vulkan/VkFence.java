package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan fence (VkFence) with automatic resource management.
 * Fences provide CPU-GPU synchronization, allowing the CPU to wait for GPU operations to complete.
 */
public class VkFence implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkFence(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a fence with optional initial signaled state.
     */
    public static VkFence create(Arena arena, MemorySegment device, boolean signaled) {
        return builder().device(device).signaled(signaled).build(arena);
    }
    
    /** @return a new builder for configuring fence creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkFence handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyFence(device, handle);
    }
    
    /**
     * Builder for fence creation.
     */
    public static class Builder {
        private MemorySegment device;
        private boolean signaled = false;
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        /** Sets fence to start in signaled state */
        public Builder signaled(boolean signaled) {
            this.signaled = signaled;
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates the fence */
        public VkFence build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            
            int finalFlags = flags;
            if (signaled) {
                finalFlags |= VkFenceCreateFlagBits.VK_FENCE_CREATE_SIGNALED_BIT;
            }
            
            MemorySegment fenceInfo = VkFenceCreateInfo.allocate(arena);
            VkFenceCreateInfo.sType(fenceInfo, VkStructureType.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            VkFenceCreateInfo.pNext(fenceInfo, MemorySegment.NULL);
            VkFenceCreateInfo.flags(fenceInfo, finalFlags);
            
            MemorySegment fencePtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createFence(device, fenceInfo, fencePtr).check();
            return new VkFence(fencePtr.get(ValueLayout.ADDRESS, 0), device);
        }
    }
}