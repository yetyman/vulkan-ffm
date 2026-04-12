package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkQueueFamily {
    
    public static final int VK_QUEUE_FAMILY_IGNORED = ~0;
    
    public static int findGraphics(VkPhysicalDevice physicalDevice, Arena arena) {
        MemorySegment queueFamilyCount = arena.allocate(ValueLayout.JAVA_INT);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), queueFamilyCount, MemorySegment.NULL);
        int count = queueFamilyCount.get(ValueLayout.JAVA_INT, 0);
        
        if (count == 0) {
            throw new VulkanException("No queue families found");
        }
        
        MemorySegment queueFamilies = arena.allocate(VkQueueFamilyProperties.layout(), count);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), queueFamilyCount, queueFamilies);
        
        for (int i = 0; i < count; i++) {
            MemorySegment queueFamily = queueFamilies.asSlice(i * VkQueueFamilyProperties.layout().byteSize(), VkQueueFamilyProperties.layout());
            int queueFlags = VkQueueFamilyProperties.queueFlags(queueFamily);
            
            if ((queueFlags & VkQueueFlagBits.VK_QUEUE_GRAPHICS_BIT.value()) != 0) {
                return i;
            }
        }
        
        throw new VulkanException("No graphics queue family found");
    }
    
    /**
     * Returns the number of queues available in the given family.
     */
    public static int queueCount(VkPhysicalDevice physicalDevice, int familyIndex, Arena arena) {
        MemorySegment countPtr = arena.allocate(ValueLayout.JAVA_INT);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), countPtr, MemorySegment.NULL);
        int count = countPtr.get(ValueLayout.JAVA_INT, 0);
        if (familyIndex >= count) return 0;
        MemorySegment families = arena.allocate(VkQueueFamilyProperties.layout(), count);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), countPtr, families);
        MemorySegment family = families.asSlice(familyIndex * VkQueueFamilyProperties.layout().byteSize(), VkQueueFamilyProperties.layout());
        return VkQueueFamilyProperties.queueCount(family);
    }

    /**
     * Finds a dedicated compute queue family (no graphics bit), falling back to the graphics family.
     * A dedicated compute family allows async compute overlap with graphics work.
     */
    public static int findCompute(VkPhysicalDevice physicalDevice, Arena arena) {
        MemorySegment queueFamilyCount = arena.allocate(ValueLayout.JAVA_INT);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), queueFamilyCount, MemorySegment.NULL);
        int count = queueFamilyCount.get(ValueLayout.JAVA_INT, 0);

        MemorySegment queueFamilies = arena.allocate(VkQueueFamilyProperties.layout(), count);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), queueFamilyCount, queueFamilies);

        // Prefer a family that has compute but NOT graphics — true async compute
        for (int i = 0; i < count; i++) {
            MemorySegment qf = queueFamilies.asSlice(i * VkQueueFamilyProperties.layout().byteSize(), VkQueueFamilyProperties.layout());
            int flags = VkQueueFamilyProperties.queueFlags(qf);
            boolean hasCompute  = (flags & VkQueueFlagBits.VK_QUEUE_COMPUTE_BIT.value())  != 0;
            boolean hasGraphics = (flags & VkQueueFlagBits.VK_QUEUE_GRAPHICS_BIT.value()) != 0;
            if (hasCompute && !hasGraphics) return i;
        }

        // Fall back to any family with compute (usually the graphics family)
        for (int i = 0; i < count; i++) {
            MemorySegment qf = queueFamilies.asSlice(i * VkQueueFamilyProperties.layout().byteSize(), VkQueueFamilyProperties.layout());
            if ((VkQueueFamilyProperties.queueFlags(qf) & VkQueueFlagBits.VK_QUEUE_COMPUTE_BIT.value()) != 0) return i;
        }

        throw new VulkanException("No compute queue family found");
    }

    public static int findSparseBinding(VkPhysicalDevice physicalDevice, Arena arena) {
        MemorySegment queueFamilyCount = arena.allocate(ValueLayout.JAVA_INT);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), queueFamilyCount, MemorySegment.NULL);
        int count = queueFamilyCount.get(ValueLayout.JAVA_INT, 0);

        MemorySegment queueFamilies = arena.allocate(VkQueueFamilyProperties.layout(), count);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), queueFamilyCount, queueFamilies);

        for (int i = 0; i < count; i++) {
            MemorySegment queueFamily = queueFamilies.asSlice(i * VkQueueFamilyProperties.layout().byteSize(), VkQueueFamilyProperties.layout());
            int queueFlags = VkQueueFamilyProperties.queueFlags(queueFamily);
            if ((queueFlags & VkQueueFlagBits.VK_QUEUE_SPARSE_BINDING_BIT.value()) != 0) {
                return i;
            }
        }

        throw new VulkanException("No sparse binding queue family found");
    }

    public static int findPresent(VkPhysicalDevice physicalDevice, MemorySegment surface, Arena arena) {
        MemorySegment queueFamilyCount = arena.allocate(ValueLayout.JAVA_INT);
        Vulkan.getPhysicalDeviceQueueFamilyProperties(physicalDevice.handle(), queueFamilyCount, MemorySegment.NULL);
        int count = queueFamilyCount.get(ValueLayout.JAVA_INT, 0);
        
        for (int i = 0; i < count; i++) {
            MemorySegment presentSupport = arena.allocate(ValueLayout.JAVA_INT);
            Vulkan.getPhysicalDeviceSurfaceSupportKHR(physicalDevice.handle(), i, surface, presentSupport).check();
            if (presentSupport.get(ValueLayout.JAVA_INT, 0) != 0) {
                return i;
            }
        }
        
        return findGraphics(physicalDevice, arena);
    }
}