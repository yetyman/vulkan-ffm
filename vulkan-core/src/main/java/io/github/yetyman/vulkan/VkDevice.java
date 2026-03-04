package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.buffers.TransferBatchManager;
import io.github.yetyman.vulkan.highlevel.VkCommandPoolRegistry;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/**
 * Wrapper for Vulkan logical device (VkDevice) with automatic resource management.
 */
public class VkDevice implements AutoCloseable {
    private final MemorySegment handle;
    private final VkPhysicalDevice physicalDevice;
    private final MethodHandle vkGetSemaphoreCounterValue;
    private final MethodHandle vkWaitSemaphores;
    private final MethodHandle vkSignalSemaphore;
    
    private VkDevice(MemorySegment handle, VkPhysicalDevice physicalDevice) {
        this.handle = handle;
        this.physicalDevice = physicalDevice;
        Linker linker = Linker.nativeLinker();
        this.vkGetSemaphoreCounterValue = loadFn(handle, linker, "vkGetSemaphoreCounterValue",
            FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
        this.vkWaitSemaphores = loadFn(handle, linker, "vkWaitSemaphores",
            FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER, VulkanFFM.C_LONG_LONG));
        this.vkSignalSemaphore = loadFn(handle, linker, "vkSignalSemaphore",
            FunctionDescriptor.of(VulkanFFM.C_INT, VulkanFFM.C_POINTER, VulkanFFM.C_POINTER));
    }

    private static MethodHandle loadFn(MemorySegment device, Linker linker, String name, FunctionDescriptor desc) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment fnPtr = VulkanFFM.vkGetDeviceProcAddr(device, tmp.allocateFrom(name));
            if (fnPtr.equals(MemorySegment.NULL))
                fnPtr = VulkanFFM.vkGetDeviceProcAddr(device, tmp.allocateFrom(name + "KHR"));
            if (fnPtr.equals(MemorySegment.NULL)) return null;
            return linker.downcallHandle(fnPtr, desc);
        }
    }

    public VkResult getSemaphoreCounterValue(MemorySegment semaphore, MemorySegment valuePtr) {
        if (vkGetSemaphoreCounterValue == null) throw new UnsupportedOperationException("vkGetSemaphoreCounterValue not available — enable timeline semaphore feature on device creation");
        try { return VkResult.fromInt((int) vkGetSemaphoreCounterValue.invokeExact(handle, semaphore, valuePtr)); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public VkResult waitSemaphores(MemorySegment waitInfo, long timeout) {
        if (vkWaitSemaphores == null) throw new UnsupportedOperationException("vkWaitSemaphores not available — enable timeline semaphore feature on device creation");
        try { return VkResult.fromInt((int) vkWaitSemaphores.invokeExact(handle, waitInfo, timeout)); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public VkResult signalSemaphore(MemorySegment signalInfo) {
        if (vkSignalSemaphore == null) throw new UnsupportedOperationException("vkSignalSemaphore not available — enable timeline semaphore feature on device creation");
        try { return VkResult.fromInt((int) vkSignalSemaphore.invokeExact(handle, signalInfo)); }
        catch (Throwable t) { throw new RuntimeException(t); }
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
    
    public VkCommandPool getOrCreateCommandPool(int queueFamilyIndex) {
        return VkCommandPoolRegistry.getOrCreate(this, queueFamilyIndex);
    }

    @Override
    public void close() {
        TransferBatchManager.destroyAll(this);
        VkCommandPoolRegistry.destroyAll(this);
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
        private boolean sparseBinding = false;
        private boolean timelineSemaphore = false;
        
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

        /** Enables sparse binding feature */
        public Builder enableSparseBinding() {
            this.sparseBinding = true;
            return this;
        }

        /** Enables the timeline semaphore feature (required for VK_SEMAPHORE_TYPE_TIMELINE) */
        public Builder enableTimelineSemaphore() {
            this.timelineSemaphore = true;
            // VK_KHR_timeline_semaphore is required on Vulkan < 1.2
            String ext = "VK_KHR_timeline_semaphore";
            for (String e : extensions) if (e.equals(ext)) return this;
            String[] newExts = new String[extensions.length + 1];
            System.arraycopy(extensions, 0, newExts, 0, extensions.length);
            newExts[extensions.length] = ext;
            extensions = newExts;
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
            VkDeviceCreateInfo.pNext(createInfo, MemorySegment.NULL);
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
            
            if (sparseBinding) {
                MemorySegment features = VkPhysicalDeviceFeatures.allocate(arena);
                VkPhysicalDeviceFeatures.sparseBinding(features, 1);
                VkDeviceCreateInfo.pEnabledFeatures(createInfo, features);
            }

            if (timelineSemaphore) {
                MemorySegment timelineFeatures = VkPhysicalDeviceTimelineSemaphoreFeatures.allocate(arena);
                VkPhysicalDeviceTimelineSemaphoreFeatures.sType(timelineFeatures, VkStructureType.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES.value());
                VkPhysicalDeviceTimelineSemaphoreFeatures.pNext(timelineFeatures, MemorySegment.NULL);
                VkPhysicalDeviceTimelineSemaphoreFeatures.timelineSemaphore(timelineFeatures, 1);
                VkDeviceCreateInfo.pNext(createInfo, timelineFeatures);
            }

            MemorySegment devicePtr = arena.allocate(ValueLayout.ADDRESS);
            int result = VulkanFFM.vkCreateDevice(Objects.requireNonNullElse(physicalDevice.handle(), MemorySegment.NULL), createInfo, MemorySegment.NULL, devicePtr);
            VkResult.fromInt(result).check();
            MemorySegment device = devicePtr.get(ValueLayout.ADDRESS, 0);
            
            return new VkDevice(device, physicalDevice);
        }
    }
}