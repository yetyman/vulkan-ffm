package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.Objects;

/**
 * Wrapper for Vulkan logical device (VkDevice) with automatic resource management.
 */
public class VkDevice implements AutoCloseable {
    private final MemorySegment handle;
    private final VkPhysicalDevice physicalDevice;
    
    private VkDevice(MemorySegment handle, VkPhysicalDevice physicalDevice) {
        this.handle = handle;
        this.physicalDevice = physicalDevice;
    }
    
    /** @return a new builder for configuring device creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** Wraps an existing device handle */
    public static VkDevice wrap(MemorySegment deviceHandle) {
        return new VkDevice(deviceHandle, null);
    }
    
    /** @return the VkDevice handle */
    public MemorySegment handle() { return handle; }
    
    /** @return the physical device handle */
    public VkPhysicalDevice physicalDevice() { return physicalDevice; }
    
    // Instance methods for common operations
    public VkResult waitIdle() {
        return Vulkan.deviceWaitIdle(handle);
    }
    
    public VkResult mapMemory(MemorySegment memory, long offset, long size, int flags, Arena arena) {
        MemorySegment mappedPtr = arena.allocate(ValueLayout.ADDRESS);
        int result = VulkanFFM.vkMapMemory(handle, memory, offset, size, flags, mappedPtr);
        return VkResult.fromInt(result);
    }
    
    public void unmapMemory(MemorySegment memory) {
        VulkanFFM.vkUnmapMemory(handle, memory);
    }
    
    public VkResult allocateMemory(MemorySegment allocateInfo, Arena arena) {
        MemorySegment memoryPtr = arena.allocate(ValueLayout.ADDRESS);
        int result = VulkanFFM.vkAllocateMemory(handle, allocateInfo, MemorySegment.NULL, memoryPtr);
        return VkResult.fromInt(result);
    }
    
    public void freeMemory(MemorySegment memory) {
        VulkanFFM.vkFreeMemory(handle, memory, MemorySegment.NULL);
    }
    
    public VkResult bindBufferMemory(MemorySegment buffer, MemorySegment memory, long offset) {
        int result = VulkanFFM.vkBindBufferMemory(handle, buffer, memory, offset);
        return VkResult.fromInt(result);
    }
    
    public VkResult bindImageMemory(MemorySegment image, MemorySegment memory, long offset) {
        int result = VulkanFFM.vkBindImageMemory(handle, image, memory, offset);
        return VkResult.fromInt(result);
    }
    
    public void getBufferMemoryRequirements(MemorySegment buffer, MemorySegment requirements) {
        VulkanFFM.vkGetBufferMemoryRequirements(handle, buffer, requirements);
    }
    
    public void getImageMemoryRequirements(MemorySegment image, MemorySegment requirements) {
        VulkanFFM.vkGetImageMemoryRequirements(handle, image, requirements);
    }
    
    /** Gets a queue from the device */
    public MemorySegment getQueue(int queueFamilyIndex, int queueIndex) {
        MemorySegment queuePtr = Arena.global().allocate(ValueLayout.ADDRESS);
        VulkanFFM.vkGetDeviceQueue(handle, queueFamilyIndex, queueIndex, queuePtr);
        return queuePtr.get(ValueLayout.ADDRESS, 0);
    }
    
    /** Simple queue submit for single command buffer */
    public void submitAndWait(VkQueue queue, VkCommandBuffer commandBuffer, VkFence fence, Arena arena) {
        MemorySegment submitInfo = arena.allocate(72);
        submitInfo.set(ValueLayout.JAVA_INT, 0, 4); // VK_STRUCTURE_TYPE_SUBMIT_INFO
        submitInfo.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        submitInfo.set(ValueLayout.JAVA_INT, 16, 0); // waitSemaphoreCount
        submitInfo.set(ValueLayout.ADDRESS, 24, MemorySegment.NULL); // pWaitSemaphores
        submitInfo.set(ValueLayout.ADDRESS, 32, MemorySegment.NULL); // pWaitDstStageMask
        submitInfo.set(ValueLayout.JAVA_INT, 40, 1); // commandBufferCount
        MemorySegment cmdBufferArray = arena.allocate(ValueLayout.ADDRESS);
        cmdBufferArray.set(ValueLayout.ADDRESS, 0, commandBuffer.handle());
        submitInfo.set(ValueLayout.ADDRESS, 48, cmdBufferArray); // pCommandBuffers
        submitInfo.set(ValueLayout.JAVA_INT, 56, 0); // signalSemaphoreCount
        submitInfo.set(ValueLayout.ADDRESS, 64, MemorySegment.NULL); // pSignalSemaphores
        
        VulkanFFM.vkQueueSubmit(queue.handle(), 1, submitInfo, fence.handle());
    }
    
    @Override
    public void close() {
        VulkanFFM.vkDestroyDevice(handle, MemorySegment.NULL);
    }
    
    /**
     * Builder for device creation.
     */
    public static class Builder {
        private VkPhysicalDevice physicalDevice;
        private int[] queueFamilyIndices = new int[0];
        private float[] queuePriorities = new float[0];
        private String[] extensions = new String[0];
        private String[] layers = new String[0];
        
        private Builder() {}
        
        /** Sets the physical device */
        public Builder physicalDevice(VkPhysicalDevice physicalDevice) {
            this.physicalDevice = physicalDevice;
            return this;
        }
        
        /** Adds a queue family */
        public Builder queueFamily(int familyIndex) {
            return queueFamily(familyIndex, 1.0f);
        }
        
        /** Adds a queue family with priority */
        public Builder queueFamily(int familyIndex, float priority) {
            int[] newIndices = new int[queueFamilyIndices.length + 1];
            System.arraycopy(queueFamilyIndices, 0, newIndices, 0, queueFamilyIndices.length);
            newIndices[queueFamilyIndices.length] = familyIndex;
            queueFamilyIndices = newIndices;
            
            float[] newPriorities = new float[queuePriorities.length + 1];
            System.arraycopy(queuePriorities, 0, newPriorities, 0, queuePriorities.length);
            newPriorities[queuePriorities.length] = priority;
            queuePriorities = newPriorities;
            return this;
        }
        
        /** Sets device extensions */
        public Builder extensions(String... extensions) {
            this.extensions = extensions;
            return this;
        }
        
        /** Sets validation layers */
        public Builder layers(String... layers) {
            this.layers = layers;
            return this;
        }
        
        /** Creates the device */
        public VkDevice build(Arena arena) {
            if (physicalDevice == null) throw new IllegalStateException("physicalDevice not set");
            if (queueFamilyIndices.length == 0) throw new IllegalStateException("no queue families specified");
            
            // Create queue create infos
            MemorySegment queueCreateInfos = arena.allocate(VkDeviceQueueCreateInfo.layout(), queueFamilyIndices.length);
            for (int i = 0; i < queueFamilyIndices.length; i++) {
                MemorySegment queueCreateInfo = queueCreateInfos.asSlice(i * VkDeviceQueueCreateInfo.layout().byteSize());
                VkDeviceQueueCreateInfo.sType(queueCreateInfo, VkStructureType.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO.value());
                VkDeviceQueueCreateInfo.queueFamilyIndex(queueCreateInfo, queueFamilyIndices[i]);
                VkDeviceQueueCreateInfo.queueCount(queueCreateInfo, 1);
                
                MemorySegment priorityPtr = arena.allocate(ValueLayout.JAVA_FLOAT);
                priorityPtr.set(ValueLayout.JAVA_FLOAT, 0, queuePriorities[i]);
                VkDeviceQueueCreateInfo.pQueuePriorities(queueCreateInfo, priorityPtr);
            }
            
            // Create device create info
            MemorySegment createInfo = VkDeviceCreateInfo.allocate(arena);
            VkDeviceCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO.value());
            VkDeviceCreateInfo.queueCreateInfoCount(createInfo, queueFamilyIndices.length);
            VkDeviceCreateInfo.pQueueCreateInfos(createInfo, queueCreateInfos);
            
            // Extensions
            if (extensions.length > 0) {
                MemorySegment extensionNames = arena.allocate(ValueLayout.ADDRESS, extensions.length);
                for (int i = 0; i < extensions.length; i++) {
                    MemorySegment extensionName = arena.allocateFrom(extensions[i]);
                    extensionNames.setAtIndex(ValueLayout.ADDRESS, i, extensionName);
                }
                VkDeviceCreateInfo.enabledExtensionCount(createInfo, extensions.length);
                VkDeviceCreateInfo.ppEnabledExtensionNames(createInfo, extensionNames);
            }
            
            // Layers
            if (layers.length > 0) {
                MemorySegment layerNames = arena.allocate(ValueLayout.ADDRESS, layers.length);
                for (int i = 0; i < layers.length; i++) {
                    MemorySegment layerName = arena.allocateFrom(layers[i]);
                    layerNames.setAtIndex(ValueLayout.ADDRESS, i, layerName);
                }
                VkDeviceCreateInfo.enabledLayerCount(createInfo, layers.length);
                VkDeviceCreateInfo.ppEnabledLayerNames(createInfo, layerNames);
            }
            
            MemorySegment devicePtr = arena.allocate(ValueLayout.ADDRESS);
            int result = VulkanFFM.vkCreateDevice(Objects.requireNonNullElse(physicalDevice.handle(), MemorySegment.NULL), createInfo, MemorySegment.NULL, devicePtr);
            VkResult.fromInt(result).check();
            MemorySegment device = devicePtr.get(ValueLayout.ADDRESS, 0);
            
            return new VkDevice(device, physicalDevice);
        }
    }
}