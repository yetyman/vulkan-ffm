package io.github.yetyman.vulkan;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class Vulkan {
    private static final MethodHandle vkCreateInstance;
    private static final MethodHandle vkDestroyInstance;
    private static final MethodHandle vkEnumeratePhysicalDevices;
    private static final MethodHandle vkGetPhysicalDeviceProperties;
    private static final MethodHandle vkGetPhysicalDeviceQueueFamilyProperties;
    private static final MethodHandle vkCreateDevice;
    private static final MethodHandle vkDestroyDevice;
    private static final MethodHandle vkGetDeviceQueue;
    private static final MethodHandle vkCreateBuffer;
    private static final MethodHandle vkDestroyBuffer;
    
    static {
        vkCreateInstance = VulkanLibrary.findFunction("vkCreateInstance",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        vkDestroyInstance = VulkanLibrary.findFunction("vkDestroyInstance",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        vkEnumeratePhysicalDevices = VulkanLibrary.findFunction("vkEnumeratePhysicalDevices",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        vkGetPhysicalDeviceProperties = VulkanLibrary.findFunction("vkGetPhysicalDeviceProperties",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        vkGetPhysicalDeviceQueueFamilyProperties = VulkanLibrary.findFunction("vkGetPhysicalDeviceQueueFamilyProperties",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        vkCreateDevice = VulkanLibrary.findFunction("vkCreateDevice",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        vkDestroyDevice = VulkanLibrary.findFunction("vkDestroyDevice",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        vkGetDeviceQueue = VulkanLibrary.findFunction("vkGetDeviceQueue",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        
        vkCreateBuffer = VulkanLibrary.findFunction("vkCreateBuffer",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        
        vkDestroyBuffer = VulkanLibrary.findFunction("vkDestroyBuffer",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }
    
    public static VkResult createInstance(MemorySegment createInfo, MemorySegment instance) {
        try {
            int result = (int) vkCreateInstance.invoke(createInfo, MemorySegment.NULL, instance);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyInstance(MemorySegment instance) {
        try {
            vkDestroyInstance.invoke(instance, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult enumeratePhysicalDevices(MemorySegment instance, MemorySegment count, MemorySegment devices) {
        try {
            int result = (int) vkEnumeratePhysicalDevices.invoke(instance, count, devices);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void getPhysicalDeviceProperties(MemorySegment physicalDevice, MemorySegment properties) {
        try {
            vkGetPhysicalDeviceProperties.invoke(physicalDevice, properties);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void getPhysicalDeviceQueueFamilyProperties(MemorySegment physicalDevice, 
                                                               MemorySegment count, MemorySegment properties) {
        try {
            vkGetPhysicalDeviceQueueFamilyProperties.invoke(physicalDevice, count, properties);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createDevice(MemorySegment physicalDevice, MemorySegment createInfo, MemorySegment device) {
        try {
            int result = (int) vkCreateDevice.invoke(physicalDevice, createInfo, MemorySegment.NULL, device);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyDevice(MemorySegment device) {
        try {
            vkDestroyDevice.invoke(device, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void getDeviceQueue(MemorySegment device, int queueFamilyIndex, int queueIndex, MemorySegment queue) {
        try {
            vkGetDeviceQueue.invoke(device, queueFamilyIndex, queueIndex, queue);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static VkResult createBuffer(MemorySegment device, MemorySegment createInfo, MemorySegment buffer) {
        try {
            int result = (int) vkCreateBuffer.invoke(device, createInfo, MemorySegment.NULL, buffer);
            return VkResult.fromInt(result);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void destroyBuffer(MemorySegment device, MemorySegment buffer) {
        try {
            vkDestroyBuffer.invoke(device, buffer, MemorySegment.NULL);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
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
