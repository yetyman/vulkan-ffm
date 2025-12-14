package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import java.lang.foreign.*;

/**
 * High-level encapsulation of core Vulkan objects and operations.
 * Manages instance, device, queues, and common resources.
 */
public class VulkanContext implements AutoCloseable {
    private final Arena arena;
    private final VkInstance instance;
    private final MemorySegment physicalDevice;
    private final VkDevice device;
    private final MemorySegment graphicsQueue;
    private final MemorySegment presentQueue;
    private final int graphicsQueueFamily;
    private final int presentQueueFamily;
    
    private VulkanContext(Arena arena, VkInstance instance, MemorySegment physicalDevice, 
                         VkDevice device, MemorySegment graphicsQueue, MemorySegment presentQueue,
                         int graphicsQueueFamily, int presentQueueFamily) {
        this.arena = arena;
        this.instance = instance;
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.graphicsQueue = graphicsQueue;
        this.presentQueue = presentQueue;
        this.graphicsQueueFamily = graphicsQueueFamily;
        this.presentQueueFamily = presentQueueFamily;
    }
    
    /** @return a new builder for configuring Vulkan context creation */
    public static Builder builder() {
        return new Builder();
    }
    
    /** @return the memory arena */
    public Arena arena() { return arena; }
    
    /** @return the Vulkan instance */
    public VkInstance instance() { return instance; }
    
    /** @return the physical device handle */
    public MemorySegment physicalDevice() { return physicalDevice; }
    
    /** @return the logical device */
    public VkDevice device() { return device; }
    
    /** @return the graphics queue handle */
    public MemorySegment graphicsQueue() { return graphicsQueue; }
    
    /** @return the present queue handle */
    public MemorySegment presentQueue() { return presentQueue; }
    
    /** @return the graphics queue family index */
    public int graphicsQueueFamily() { return graphicsQueueFamily; }
    
    /** @return the present queue family index */
    public int presentQueueFamily() { return presentQueueFamily; }
    
    /** Creates a command pool for graphics operations */
    public VkCommandPool createGraphicsCommandPool() {
        return VkCommandPool.builder()
            .device(device.handle())
            .queueFamilyIndex(graphicsQueueFamily)
            .build(arena);
    }
    
    /** Creates a transient command pool for short-lived operations */
    public VkCommandPool createTransientCommandPool() {
        return VkCommandPool.builder()
            .device(device.handle())
            .queueFamilyIndex(graphicsQueueFamily)
            .transientBit()
            .resetCommandBufferBit()
            .build(arena);
    }
    
    /** Creates a descriptor pool with common descriptor types */
    public VkDescriptorPool createDescriptorPool(int maxSets) {
        return VkDescriptorPool.builder()
            .device(device.handle())
            .maxSets(maxSets)
            .uniformBuffers(maxSets * 2)
            .combinedImageSamplers(maxSets * 2)
            .storageBuffers(maxSets)
            .freeDescriptorSet()
            .build(arena);
    }
    
    /** Creates a vertex buffer */
    public VkBuffer createVertexBuffer(long size) {
        return VkBuffer.builder()
            .device(device.handle())
            .physicalDevice(physicalDevice)
            .size(size)
            .vertexBuffer()
            .transferDst()
            .deviceLocal()
            .build(arena);
    }
    
    /** Creates a staging buffer for data transfer */
    public VkBuffer createStagingBuffer(long size) {
        return VkBuffer.builder()
            .device(device.handle())
            .physicalDevice(physicalDevice)
            .size(size)
            .transferSrc()
            .hostVisible()
            .build(arena);
    }
    
    /** Creates a uniform buffer */
    public VkBuffer createUniformBuffer(long size) {
        return VkBuffer.builder()
            .device(device.handle())
            .physicalDevice(physicalDevice)
            .size(size)
            .uniformBuffer()
            .hostVisible()
            .build(arena);
    }
    
    @Override
    public void close() {
        if (device != null) {
            Vulkan.deviceWaitIdle(device.handle()).check();
            device.close();
        }
        if (instance != null) {
            instance.close();
        }
        if (arena != null) {
            arena.close();
        }
    }
    
    /**
     * Builder for Vulkan context creation.
     */
    public static class Builder {
        private String applicationName = "VulkanApp";
        private int applicationVersion = 1;
        private String[] instanceExtensions = null;
        private String[] deviceExtensions = {"VK_KHR_swapchain"};
        private String[] validationLayers = null;
        private MemorySegment surface = null;
        private boolean enableValidation = false;
        
        private Builder() {}
        
        /** Sets the application name */
        public Builder applicationName(String name) {
            this.applicationName = name;
            return this;
        }
        
        /** Sets the application version */
        public Builder applicationVersion(int version) {
            this.applicationVersion = version;
            return this;
        }
        
        /** Sets instance extensions */
        public Builder instanceExtensions(String... extensions) {
            this.instanceExtensions = extensions;
            return this;
        }
        
        /** Sets device extensions */
        public Builder deviceExtensions(String... extensions) {
            this.deviceExtensions = extensions;
            return this;
        }
        
        /** Sets validation layers */
        public Builder validationLayers(String... layers) {
            this.validationLayers = layers;
            this.enableValidation = layers != null && layers.length > 0;
            return this;
        }
        
        /** Sets the surface for presentation (optional) */
        public Builder surface(MemorySegment surface) {
            this.surface = surface;
            return this;
        }
        
        /** Enables validation layers */
        public Builder enableValidation() {
            this.enableValidation = true;
            if (validationLayers == null) {
                validationLayers = new String[]{"VK_LAYER_KHRONOS_validation"};
            }
            return this;
        }
        
        /** Creates the Vulkan context */
        public VulkanContext build() {
            Arena arena = Arena.ofConfined();
            
            try {
                // Create instance
                VkInstance.Builder instanceBuilder = VkInstance.builder()
                    .applicationName(applicationName)
                    .applicationVersion(applicationVersion);
                
                if (instanceExtensions != null) {
                    instanceBuilder.extensions(instanceExtensions);
                }
                
                if (enableValidation && validationLayers != null) {
                    instanceBuilder.layers(validationLayers);
                }
                
                VkInstance instance = instanceBuilder.build(arena);
                
                // Select physical device
                MemorySegment physicalDevice = VkPhysicalDeviceOps.enumerate(instance.handle()).first(arena);
                
                // Find queue families
                int graphicsFamily = VkQueueFamily.findGraphics(physicalDevice, arena);
                int presentFamily = surface != null ? 
                    VkQueueFamily.findPresent(physicalDevice, surface, arena) : graphicsFamily;
                
                // Create logical device
                VkDevice.Builder deviceBuilder = VkDevice.builder()
                    .physicalDevice(physicalDevice)
                    .queueFamily(graphicsFamily);
                
                if (deviceExtensions != null) {
                    deviceBuilder.extensions(deviceExtensions);
                }
                
                VkDevice device = deviceBuilder.build(arena);
                
                // Get queues
                MemorySegment graphicsQueue = device.getQueue(graphicsFamily, 0);
                MemorySegment presentQueue = device.getQueue(presentFamily, 0);
                
                return new VulkanContext(arena, instance, physicalDevice, device, 
                                       graphicsQueue, presentQueue, graphicsFamily, presentFamily);
            } catch (Exception e) {
                arena.close();
                throw e;
            }
        }
    }
}