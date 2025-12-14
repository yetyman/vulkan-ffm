package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkQueueFamily {
    
    public static int findGraphics(MemorySegment physicalDevice, Arena arena) {
        MemorySegment queueFamilyCount = arena.allocate(ValueLayout.JAVA_INT);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, MemorySegment.NULL);
        int count = queueFamilyCount.get(ValueLayout.JAVA_INT, 0);
        
        if (count == 0) {
            throw new VulkanException("No queue families found");
        }
        
        MemorySegment queueFamilies = arena.allocate(VkQueueFamilyProperties.layout(), count);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, queueFamilies);
        
        for (int i = 0; i < count; i++) {
            MemorySegment queueFamily = queueFamilies.asSlice(i * VkQueueFamilyProperties.layout().byteSize(), VkQueueFamilyProperties.layout());
            int queueFlags = VkQueueFamilyProperties.queueFlags(queueFamily);
            
            if ((queueFlags & VkQueueFlagBits.VK_QUEUE_GRAPHICS_BIT) != 0) {
                return i;
            }
        }
        
        throw new VulkanException("No graphics queue family found");
    }
    
    public static int findPresent(MemorySegment physicalDevice, MemorySegment surface, Arena arena) {
        MemorySegment queueFamilyCount = arena.allocate(ValueLayout.JAVA_INT);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, MemorySegment.NULL);
        int count = queueFamilyCount.get(ValueLayout.JAVA_INT, 0);
        
        for (int i = 0; i < count; i++) {
            MemorySegment presentSupport = arena.allocate(ValueLayout.JAVA_INT);
            VulkanExtensions.getPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, presentSupport).check();
            if (presentSupport.get(ValueLayout.JAVA_INT, 0) != 0) {
                return i;
            }
        }
        
        return findGraphics(physicalDevice, arena);
    }
}