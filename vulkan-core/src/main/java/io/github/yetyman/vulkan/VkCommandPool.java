package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan command pool (VkCommandPool) with automatic resource management.
 * Command pools manage the memory used to store command buffers.
 */
public class VkCommandPool implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkCommandPool(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }

    /**
     * Creates a command pool for the specified queue family.
     */
    public static VkCommandPool create(Arena arena, MemorySegment device, int queueFamilyIndex) {
        return builder()
            .device(device)
            .queueFamilyIndex(queueFamilyIndex)
            .build(arena);
    }
    
    /** @return a new builder for configuring command pool creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the VkCommandPool handle */
    public MemorySegment handle() { return handle; }

    /** @return the VkCommandPool device */
    public MemorySegment device() { return device; }

    @Override
    public void close() {
        VulkanExtensions.destroyCommandPool(device, handle);
    }
    
    /**
     * Builder for command pool creation.
     */
    public static class Builder {
        private MemorySegment device;
        private int queueFamilyIndex;
        private int flags = 0;
        
        private Builder() {}
        
        /** Sets the logical device */
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        /** Sets the queue family index */
        public Builder queueFamilyIndex(int index) {
            this.queueFamilyIndex = index;
            return this;
        }
        
        /** Enables transient command buffers (short-lived, frequently reset) */
        public Builder transientBit() {
            this.flags |= VkCommandPoolCreateFlagBits.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
            return this;
        }
        
        /** Allows command buffers to be reset individually */
        public Builder resetCommandBufferBit() {
            this.flags |= VkCommandPoolCreateFlagBits.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
            return this;
        }
        
        /** Sets creation flags */
        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }
        
        /** Creates the command pool */
        public VkCommandPool build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            
            MemorySegment poolInfo = VkCommandPoolCreateInfo.allocate(arena);
            VkCommandPoolCreateInfo.sType(poolInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            VkCommandPoolCreateInfo.pNext(poolInfo, MemorySegment.NULL);
            VkCommandPoolCreateInfo.flags(poolInfo, flags);
            VkCommandPoolCreateInfo.queueFamilyIndex(poolInfo, queueFamilyIndex);
            
            MemorySegment commandPoolPtr = arena.allocate(ValueLayout.ADDRESS);
            VulkanExtensions.createCommandPool(device, poolInfo, commandPoolPtr).check();
            return new VkCommandPool(commandPoolPtr.get(ValueLayout.ADDRESS, 0), device);
        }
    }
}