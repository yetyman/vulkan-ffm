package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;

/**
 * Wrapper for Vulkan semaphore (VkSemaphore) with automatic resource management.
 * Semaphores provide GPU-GPU synchronization between queue operations.
 */
public class VkSemaphore implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkSemaphore(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a new semaphore.
     * @param arena memory arena for allocations
     * @param device the VkDevice handle
     * @return a new VkSemaphore instance
     */
    public static VkSemaphore create(Arena arena, MemorySegment device) {
        MemorySegment semaphoreInfo = VkSemaphoreCreateInfo.allocate(arena);
        VkSemaphoreCreateInfo.sType(semaphoreInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        VkSemaphoreCreateInfo.pNext(semaphoreInfo, MemorySegment.NULL);
        VkSemaphoreCreateInfo.flags(semaphoreInfo, 0);
        
        MemorySegment semPtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createSemaphore(device, semaphoreInfo, semPtr).check();
        return new VkSemaphore(semPtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    /** @return the VkSemaphore handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroySemaphore(device, handle);
    }
}