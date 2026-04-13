package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;

import java.lang.foreign.*;
import java.util.Set;

/**
 * High-level encapsulation of core Vulkan objects and operations.
 * Manages instance, device, queues, and common resources.
 */
public class VulkanContext implements AutoCloseable {
    private final Arena arena;
    private final VkInstance instance;
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final VkQueue graphicsQueue;
    private final VkQueue presentQueue;
    private final VkQueue computeQueue;
    private final int graphicsQueueFamily;
    private final int presentQueueFamily;
    private final int computeQueueFamily;

    private VulkanContext(Arena arena, VkInstance instance, VkPhysicalDevice physicalDevice,
                          VkDevice device, VkQueue graphicsQueue, VkQueue presentQueue,
                          VkQueue computeQueue, int graphicsQueueFamily, int presentQueueFamily,
                          int computeQueueFamily) {
        this.arena = arena;
        this.instance = instance;
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.graphicsQueue = graphicsQueue;
        this.presentQueue = presentQueue;
        this.computeQueue = computeQueue;
        this.graphicsQueueFamily = graphicsQueueFamily;
        this.presentQueueFamily = presentQueueFamily;
        this.computeQueueFamily = computeQueueFamily;
    }

    /**
     * @return a new builder for configuring Vulkan context creation
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the memory arena
     */
    public Arena arena() {
        return arena;
    }

    /**
     * @return the Vulkan instance
     */
    public VkInstance instance() {
        return instance;
    }

    /**
     * @return the physical device
     */
    public VkPhysicalDevice physicalDevice() {
        return physicalDevice;
    }

    /**
     * @return the logical device
     */
    public VkDevice device() {
        return device;
    }

    /**
     * @return the graphics queue handle
     */
    public MemorySegment graphicsQueue() {
        return graphicsQueue.handle();
    }

    /**
     * @return the graphics queue
     */
    public VkQueue graphicsVkQueue() {
        return graphicsQueue;
    }

    /**
     * @return the present queue handle
     */
    public MemorySegment presentQueue() {
        return presentQueue.handle();
    }

    /**
     * @return the present queue
     */
    public VkQueue presentVkQueue() {
        return presentQueue;
    }

    /**
     * @return the compute queue handle
     */
    public MemorySegment computeQueue() {
        return computeQueue.handle();
    }

    /**
     * @return the compute queue
     */
    public VkQueue computeVkQueue() {
        return computeQueue;
    }

    /**
     * @return the graphics queue family index
     */
    public int graphicsQueueFamily() {
        return graphicsQueueFamily;
    }

    /**
     * @return the present queue family index
     */
    public int presentQueueFamily() {
        return presentQueueFamily;
    }

    /**
     * @return the compute queue family index (may equal graphicsQueueFamily if no dedicated compute family exists)
     */
    public int computeQueueFamily() {
        return computeQueueFamily;
    }

    /**
     * Creates a command pool for graphics operations
     */
    public VkCommandPool createGraphicsCommandPool() {
        return VkCommandPool.builder()
                .device(device)
                .queueFamilyIndex(graphicsQueueFamily)
                .resetCommandBufferBit()
                .build(arena);
    }

    /**
     * Creates a transient command pool for short-lived operations
     */
    public VkCommandPool createTransientCommandPool() {
        return VkCommandPool.builder()
                .device(device)
                .queueFamilyIndex(graphicsQueueFamily)
                .transientBit()
                .resetCommandBufferBit()
                .build(arena);
    }

    /**
     * Creates a descriptor pool with common descriptor types
     */
    public VkDescriptorPool createDescriptorPool(int maxSets) {
        return VkDescriptorPool.builder()
                .device(device)
                .maxSets(maxSets)
                .uniformBuffers(maxSets * 2)
                .combinedImageSamplers(maxSets * 2)
                .storageBuffers(maxSets)
                .freeDescriptorSet()
                .build(arena);
    }

    /**
     * Creates a vertex buffer
     */
    public VkBuffer createVertexBuffer(long size) {
        return VkBuffer.builder()
                .device(device)
                .size(size)
                .vertexBuffer()
                .transferDst()
                .deviceLocal()
                .build(arena);
    }

    /**
     * Creates a staging buffer for data transfer
     */
    public VkBuffer createStagingBuffer(long size) {
        return VkBuffer.builder()
                .device(device)
                .size(size)
                .transferSrc()
                .hostVisible()
                .build(arena);
    }

    /**
     * Creates a uniform buffer
     */
    public VkBuffer createUniformBuffer(long size) {
        return VkBuffer.builder()
                .device(device)
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

        private Builder() {
        }

        /**
         * Sets the application name
         */
        public Builder applicationName(String name) {
            this.applicationName = name;
            return this;
        }

        /**
         * Sets the application version
         */
        public Builder applicationVersion(int version) {
            this.applicationVersion = version;
            return this;
        }

        /**
         * Sets instance extensions
         */
        public Builder instanceExtensions(String... extensions) {
            this.instanceExtensions = extensions;
            return this;
        }

        /**
         * Sets device extensions
         */
        public Builder deviceExtensions(String... extensions) {
            this.deviceExtensions = extensions;
            return this;
        }

        /**
         * Sets validation layers
         */
        public Builder validationLayers(String... layers) {
            this.validationLayers = layers;
            this.enableValidation = layers != null && layers.length > 0;
            return this;
        }

        /**
         * Sets the surface for presentation (optional)
         */
        public Builder surface(MemorySegment surface) {
            this.surface = surface;
            return this;
        }

        /**
         * Enables validation layers
         */
        public Builder enableValidation() {
            this.enableValidation = true;
            if (validationLayers == null) {
                validationLayers = new String[]{"VK_LAYER_KHRONOS_validation"};
            }
            return this;
        }

        private static Set<String> getAvailableExtensions(VkPhysicalDevice physicalDevice, Arena arena) {
            Set<String> extensions = new java.util.HashSet<>();
            MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);
            Vulkan.enumerateDeviceExtensionProperties(physicalDevice.handle(), MemorySegment.NULL, countPtr, MemorySegment.NULL);
            int count = countPtr.get(ValueLayout.JAVA_INT, 0);
            if (count > 0) {
                MemorySegment props = arena.allocate(io.github.yetyman.vulkan.generated.VkExtensionProperties.layout(), count);
                Vulkan.enumerateDeviceExtensionProperties(physicalDevice.handle(), MemorySegment.NULL, countPtr, props);
                for (int i = 0; i < count; i++) {
                    MemorySegment ext = props.asSlice(i * io.github.yetyman.vulkan.generated.VkExtensionProperties.layout().byteSize(),
                            io.github.yetyman.vulkan.generated.VkExtensionProperties.layout());
                    extensions.add(io.github.yetyman.vulkan.generated.VkExtensionProperties.extensionName(ext).getString(0));
                }
            }
            return extensions;
        }

        private static boolean isVulkan13(VkPhysicalDevice physicalDevice, Arena arena) {
            MemorySegment props = io.github.yetyman.vulkan.generated.VkPhysicalDeviceProperties.allocate(arena);
            io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceProperties(physicalDevice.handle(), props);
            int apiVersion = io.github.yetyman.vulkan.generated.VkPhysicalDeviceProperties.apiVersion(props);
            int major = (apiVersion >> 22) & 0x7F;
            int minor = (apiVersion >> 12) & 0x3FF;
            return major > 1 || (major == 1 && minor >= 3);
        }

        /**
         * Creates the Vulkan context
         */
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
                MemorySegment physicalDeviceHandle = VkPhysicalDeviceOps.enumerate(instance.handle()).first(arena);
                VkPhysicalDevice physicalDevice = VkPhysicalDevice.wrap(physicalDeviceHandle);

                // Find queue families
                int graphicsFamily = VkQueueFamily.findGraphics(physicalDevice, arena);
                int presentFamily = surface != null ?
                        VkQueueFamily.findPresent(physicalDevice, surface, arena) : graphicsFamily;
                int computeFamily = VkQueueFamily.findCompute(physicalDevice, arena);

                // Query how many queues the compute family exposes
                int computeQueueCount = VkQueueFamily.queueCount(physicalDevice, computeFamily, arena);
                // When compute shares the graphics family we need a second queue index for isolation.
                // Only request 2 if the family actually has 2+ queues.
                boolean sharedFamily = computeFamily == graphicsFamily;
                int computeQueueIndex = (sharedFamily && computeQueueCount >= 2) ? 1 : 0;
                int computeQueuesToRequest = (sharedFamily && computeQueueCount >= 2) ? 2 : 1;

                // Create logical device
                VkDevice.Builder deviceBuilder = VkDevice.builder()
                        .physicalDevice(physicalDevice)
                        .queueFamily(graphicsFamily, computeQueuesToRequest, 1.0f);

                if (!sharedFamily) {
                    deviceBuilder.queueFamily(computeFamily);
                }

                if (deviceExtensions != null) {
                    deviceBuilder.extensions(deviceExtensions);
                }

                Set<String> availableExts = getAvailableExtensions(physicalDevice, arena);
                boolean supportsDynamicRendering = availableExts.contains("VK_KHR_dynamic_rendering")
                        || isVulkan13(physicalDevice, arena);
                if (supportsDynamicRendering) {
                    deviceBuilder.enableDynamicRendering();
                }

                deviceBuilder.enableTimelineSemaphore();

                VkDevice device = deviceBuilder.build(arena);
                VulkanCapabilities.initialize(physicalDevice);

                VkQueue graphicsQueue = new VkQueue(device, device.getQueue(graphicsFamily, 0), graphicsFamily);
                VkQueue presentQueue = new VkQueue(device, device.getQueue(presentFamily, 0), presentFamily);
                VkQueue computeQueue = new VkQueue(device, device.getQueue(computeFamily, computeQueueIndex), computeFamily);

                return new VulkanContext(arena, instance, physicalDevice, device,
                        graphicsQueue, presentQueue, computeQueue,
                        graphicsFamily, presentFamily, computeFamily);
            } catch (Exception e) {
                arena.close();
                throw e;
            }
        }
    }
}