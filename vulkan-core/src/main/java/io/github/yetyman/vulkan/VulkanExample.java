package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VulkanExample {
    static { VulkanLibrary.load(); }
    
    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment appInfo = VkApplicationInfo.allocate(arena);
            VkApplicationInfo.sType(appInfo, VkStructureType.VK_STRUCTURE_TYPE_APPLICATION_INFO);
            VkApplicationInfo.pNext(appInfo, MemorySegment.NULL);
            VkApplicationInfo.pApplicationName(appInfo, arena.allocateFrom("VulkanApp"));
            VkApplicationInfo.applicationVersion(appInfo, 1);
            VkApplicationInfo.pEngineName(appInfo, arena.allocateFrom("NoEngine"));
            VkApplicationInfo.engineVersion(appInfo, 0);
            VkApplicationInfo.apiVersion(appInfo, Vulkan.VK_API_VERSION_1_0);
            
            MemorySegment createInfo = VkInstanceCreateInfo.allocate(arena);
            VkInstanceCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            VkInstanceCreateInfo.pNext(createInfo, MemorySegment.NULL);
            VkInstanceCreateInfo.flags(createInfo, 0);
            VkInstanceCreateInfo.pApplicationInfo(createInfo, appInfo);
            VkInstanceCreateInfo.enabledLayerCount(createInfo, 0);
            VkInstanceCreateInfo.ppEnabledLayerNames(createInfo, MemorySegment.NULL);
            VkInstanceCreateInfo.enabledExtensionCount(createInfo, 0);
            VkInstanceCreateInfo.ppEnabledExtensionNames(createInfo, MemorySegment.NULL);
            
            MemorySegment instancePtr = arena.allocate(ValueLayout.ADDRESS);
            VkResult result = Vulkan.createInstance(createInfo, instancePtr);
            result.check();
            
            MemorySegment instance = instancePtr.get(ValueLayout.ADDRESS, 0);
            System.out.println("Vulkan instance created successfully!");
            
            MemorySegment deviceCount = arena.allocate(ValueLayout.JAVA_INT);
            Vulkan.enumeratePhysicalDevices(instance, deviceCount, MemorySegment.NULL).check();
            int count = deviceCount.get(ValueLayout.JAVA_INT, 0);
            System.out.println("Physical devices found: " + count);
            
            Vulkan.destroyInstance(instance);
            System.out.println("Vulkan instance destroyed");
        }
    }
}
