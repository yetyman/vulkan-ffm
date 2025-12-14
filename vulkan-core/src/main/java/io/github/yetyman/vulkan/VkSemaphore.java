package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

public class VkSemaphore implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkSemaphore(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    public static VkSemaphore create(Arena arena, MemorySegment device) {
        MemorySegment semaphoreInfo = VkSemaphoreCreateInfo.allocate(arena);
        VkSemaphoreCreateInfo.sType(semaphoreInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        VkSemaphoreCreateInfo.pNext(semaphoreInfo, MemorySegment.NULL);
        VkSemaphoreCreateInfo.flags(semaphoreInfo, 0);
        
        MemorySegment semPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createSemaphore(device, semaphoreInfo, semPtr).check();
        return new VkSemaphore(semPtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroySemaphore(device, handle);
    }
}