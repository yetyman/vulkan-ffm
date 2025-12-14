package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkDevice implements AutoCloseable {
    private final MemorySegment handle;
    private final Arena arena;
    
    private VkDevice(MemorySegment handle, Arena arena) {
        this.handle = handle;
        this.arena = arena;
    }
    
    public static VkDevice create(Arena arena, MemorySegment physicalDevice, int queueFamilyIndex, String[] extensions) {
        MemorySegment queuePriorities = arena.allocate(ValueLayout.JAVA_FLOAT);
        queuePriorities.set(ValueLayout.JAVA_FLOAT, 0, 1.0f);
        
        MemorySegment queueCreateInfo = VkDeviceQueueCreateInfo.allocate(arena);
        VkDeviceQueueCreateInfo.sType(queueCreateInfo, VkStructureType.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
        VkDeviceQueueCreateInfo.pNext(queueCreateInfo, MemorySegment.NULL);
        VkDeviceQueueCreateInfo.flags(queueCreateInfo, 0);
        VkDeviceQueueCreateInfo.queueFamilyIndex(queueCreateInfo, queueFamilyIndex);
        VkDeviceQueueCreateInfo.queueCount(queueCreateInfo, 1);
        VkDeviceQueueCreateInfo.pQueuePriorities(queueCreateInfo, queuePriorities);
        
        MemorySegment queueCreateInfos = arena.allocate(VkDeviceQueueCreateInfo.layout());
        MemorySegment.copy(queueCreateInfo, 0, queueCreateInfos, 0, VkDeviceQueueCreateInfo.layout().byteSize());
        
        MemorySegment deviceCreateInfo = VkDeviceCreateInfo.allocate(arena);
        VkDeviceCreateInfo.sType(deviceCreateInfo, VkStructureType.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
        VkDeviceCreateInfo.pNext(deviceCreateInfo, MemorySegment.NULL);
        VkDeviceCreateInfo.flags(deviceCreateInfo, 0);
        VkDeviceCreateInfo.queueCreateInfoCount(deviceCreateInfo, 1);
        VkDeviceCreateInfo.pQueueCreateInfos(deviceCreateInfo, queueCreateInfos);
        VkDeviceCreateInfo.enabledLayerCount(deviceCreateInfo, 0);
        VkDeviceCreateInfo.ppEnabledLayerNames(deviceCreateInfo, MemorySegment.NULL);
        
        if (extensions != null && extensions.length > 0) {
            MemorySegment extArray = arena.allocate(ValueLayout.ADDRESS, extensions.length);
            for (int i = 0; i < extensions.length; i++) {
                extArray.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(extensions[i]));
            }
            VkDeviceCreateInfo.enabledExtensionCount(deviceCreateInfo, extensions.length);
            VkDeviceCreateInfo.ppEnabledExtensionNames(deviceCreateInfo, extArray);
        } else {
            VkDeviceCreateInfo.enabledExtensionCount(deviceCreateInfo, 0);
            VkDeviceCreateInfo.ppEnabledExtensionNames(deviceCreateInfo, MemorySegment.NULL);
        }
        
        VkDeviceCreateInfo.pEnabledFeatures(deviceCreateInfo, MemorySegment.NULL);
        
        MemorySegment devicePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createDevice(physicalDevice, deviceCreateInfo, devicePtr).check();
        return new VkDevice(devicePtr.get(ValueLayout.ADDRESS, 0), arena);
    }
    
    public MemorySegment handle() {
        return handle;
    }
    
    public MemorySegment getQueue(int queueFamilyIndex, int queueIndex) {
        MemorySegment queuePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.getDeviceQueue(handle, queueFamilyIndex, queueIndex, queuePtr);
        return queuePtr.get(ValueLayout.ADDRESS, 0);
    }
    
    @Override
    public void close() {
        Vulkan.destroyDevice(handle);
    }
}
