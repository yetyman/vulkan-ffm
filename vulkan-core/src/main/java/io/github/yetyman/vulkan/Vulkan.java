package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.VulkanFFM;
import java.lang.foreign.*;

public class Vulkan {
    static { VulkanLibrary.load(); }
    
    public static VkResult createInstance(MemorySegment createInfo, MemorySegment instance) {
        int result = VulkanFFM.vkCreateInstance(createInfo, MemorySegment.NULL, instance);
        return VkResult.fromInt(result);
    }
    
    public static void destroyInstance(MemorySegment instance) {
        VulkanFFM.vkDestroyInstance(instance, MemorySegment.NULL);
    }
    
    public static VkResult enumeratePhysicalDevices(MemorySegment instance, MemorySegment count, MemorySegment devices) {
        int result = VulkanFFM.vkEnumeratePhysicalDevices(instance, count, devices);
        return VkResult.fromInt(result);
    }
    
    public static void getPhysicalDeviceProperties(MemorySegment physicalDevice, MemorySegment properties) {
        VulkanFFM.vkGetPhysicalDeviceProperties(physicalDevice, properties);
    }
    
    public static void getPhysicalDeviceQueueFamilyProperties(MemorySegment physicalDevice, 
                                                               MemorySegment count, MemorySegment properties) {
        VulkanFFM.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, count, properties);
    }
    
    public static VkResult createDevice(MemorySegment physicalDevice, MemorySegment createInfo, MemorySegment device) {
        int result = VulkanFFM.vkCreateDevice(physicalDevice, createInfo, MemorySegment.NULL, device);
        return VkResult.fromInt(result);
    }
    
    public static void destroyDevice(MemorySegment device) {
        VulkanFFM.vkDestroyDevice(device, MemorySegment.NULL);
    }
    
    public static void getDeviceQueue(MemorySegment device, int queueFamilyIndex, int queueIndex, MemorySegment queue) {
        VulkanFFM.vkGetDeviceQueue(device, queueFamilyIndex, queueIndex, queue);
    }
    
    public static VkResult createBuffer(MemorySegment device, MemorySegment createInfo, MemorySegment buffer) {
        int result = VulkanFFM.vkCreateBuffer(device, createInfo, MemorySegment.NULL, buffer);
        return VkResult.fromInt(result);
    }
    
    public static void destroyBuffer(MemorySegment device, MemorySegment buffer) {
        VulkanFFM.vkDestroyBuffer(device, buffer, MemorySegment.NULL);
    }
    
    public static VkResult deviceWaitIdle(MemorySegment device) {
        int result = VulkanFFM.vkDeviceWaitIdle(device);
        return VkResult.fromInt(result);
    }
    
    public static VkResult queueWaitIdle(MemorySegment queue) {
        int result = VulkanFFM.vkQueueWaitIdle(queue);
        return VkResult.fromInt(result);
    }
    
    public static VkResult allocateMemory(MemorySegment device, MemorySegment allocateInfo, MemorySegment memory) {
        int result = VulkanFFM.vkAllocateMemory(device, allocateInfo, MemorySegment.NULL, memory);
        return VkResult.fromInt(result);
    }
    
    public static void freeMemory(MemorySegment device, MemorySegment memory) {
        VulkanFFM.vkFreeMemory(device, memory, MemorySegment.NULL);
    }
    
    public static VkResult bindBufferMemory(MemorySegment device, MemorySegment buffer, MemorySegment memory, long offset) {
        int result = VulkanFFM.vkBindBufferMemory(device, buffer, memory, offset);
        return VkResult.fromInt(result);
    }
    
    public static int makeVersion(int major, int minor, int patch) {
        return (major << 22) | (minor << 12) | patch;
    }
    
    public static final int VK_API_VERSION_1_0 = makeVersion(1, 0, 0);
    public static final int VK_API_VERSION_1_1 = makeVersion(1, 1, 0);
    public static final int VK_API_VERSION_1_2 = makeVersion(1, 2, 0);
    public static final int VK_API_VERSION_1_3 = makeVersion(1, 3, 0);
    
    public static final int VK_BUFFER_USAGE_TRANSFER_SRC_BIT = 0x00000001;
    public static final int VK_BUFFER_USAGE_TRANSFER_DST_BIT = 0x00000002;
    public static final int VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT = 0x00000010;
    public static final int VK_BUFFER_USAGE_STORAGE_BUFFER_BIT = 0x00000020;
    public static final int VK_BUFFER_USAGE_VERTEX_BUFFER_BIT = 0x00000080;
    public static final int VK_BUFFER_USAGE_INDEX_BUFFER_BIT = 0x00000040;
    
    public static final int VK_SHARING_MODE_EXCLUSIVE = 0;
    public static final int VK_SHARING_MODE_CONCURRENT = 1;
}