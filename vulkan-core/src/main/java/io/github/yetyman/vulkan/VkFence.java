package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import static io.github.yetyman.vulkan.VkConstants.*;

/**
 * Wrapper for Vulkan fence (VkFence) with automatic resource management.
 * Fences provide CPU-GPU synchronization, allowing the CPU to wait for GPU operations to complete.
 */
public class VkFence implements AutoCloseable {
    private final MemorySegment handle;
    private final MemorySegment device;
    
    private VkFence(MemorySegment handle, MemorySegment device) {
        this.handle = handle;
        this.device = device;
    }
    
    /**
     * Creates a fence with optional initial signaled state.
     * @param arena memory arena for allocations
     * @param device the VkDevice handle
     * @param signaled if true, fence starts in signaled state; if false, starts unsignaled
     * @return a new VkFence instance
     */
    public static VkFence create(Arena arena, MemorySegment device, boolean signaled) {
        int flags = signaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0;
        MemorySegment fenceInfo = VkFenceCreateInfo.allocate(arena);
        VkFenceCreateInfo.sType(fenceInfo, VkStructureType.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
        VkFenceCreateInfo.pNext(fenceInfo, MemorySegment.NULL);
        VkFenceCreateInfo.flags(fenceInfo, flags);
        
        MemorySegment fencePtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createFence(device, fenceInfo, fencePtr).check();
        return new VkFence(fencePtr.get(ValueLayout.ADDRESS, 0), device);
    }
    
    /** @return the VkFence handle */
    public MemorySegment handle() { return handle; }
    
    @Override
    public void close() {
        VulkanExtensions.destroyFence(device, handle);
    }
}