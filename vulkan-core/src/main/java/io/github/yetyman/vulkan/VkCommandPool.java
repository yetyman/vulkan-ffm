package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkCommandPool implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkCommandPool(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    public static VkCommandPool create(Arena arena, MemorySegment device, int queueFamilyIndex) {
        MemorySegment poolInfo = VkCommandPoolCreateInfo.allocate(arena);
        VkCommandPoolCreateInfo.sType(poolInfo, VkStructureType.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
        VkCommandPoolCreateInfo.pNext(poolInfo, MemorySegment.NULL);
        VkCommandPoolCreateInfo.flags(poolInfo, 0);
        VkCommandPoolCreateInfo.queueFamilyIndex(poolInfo, queueFamilyIndex);
        
        MemorySegment commandPoolPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createCommandPool(device, poolInfo, commandPoolPtr).check();
        return new VkCommandPool(commandPoolPtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyCommandPool(device, handle);
    }
}