package io.github.yetyman.vulkan.auto;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Queue;

import static io.github.yetyman.vulkan.enums.VkBufferCreateFlagBits.VK_BUFFER_CREATE_SPARSE_BINDING_BIT;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkAllocateMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFreeMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceFeatures;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceMemoryProperties;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceProperties;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkQueueBindSparse;

/**
 * Sparse buffer with dynamic page-level memory binding.
 * Allows huge virtual address space with on-demand physical memory allocation.
 */
public class SparseBuffer extends AbstractBuffer {
    private final long pageSize;
    private final MemorySegment sparseQueue;
    private final VkCommandPool commandPool;
    private final MemorySegment transferQueue;
    private final Map<Long, MemorySegment> boundPages = new HashMap<>();
    private final Queue<MemorySegment> freeMemoryPool = new ArrayDeque<>();
    
    public SparseBuffer(VkDevice device, Arena arena,
                       long size, BufferUsage usage, MemorySegment sparseQueue, 
                       MemorySegment transferQueue, VkCommandPool commandPool) {
        super(device, arena, size, usage, MemoryStrategy.SPARSE);
        this.sparseQueue = sparseQueue;
        this.transferQueue = transferQueue;
        this.commandPool = commandPool;
        this.pageSize = device.physicalDevice().getSparsePageSize();
        
        checkSparseSupport();
        createSparseBuffer();
    }
    
    private void checkSparseSupport() {
        MemorySegment features = arena.allocate(220); // VkPhysicalDeviceFeatures
        vkGetPhysicalDeviceFeatures(physicalDevice.handle(), features);
        int sparseBinding = features.get(ValueLayout.JAVA_INT, 0); // sparseBinding is first field
        if (sparseBinding == 0) {
            throw new UnsupportedOperationException("Device does not support sparse binding");
        }
    }
    
    private void createSparseBuffer() {
        vkBuffer = VkBuffer.builder()
            .device(device)
            .size(size)
            .usage(usage.toVkFlags())
            .flags(VK_BUFFER_CREATE_SPARSE_BINDING_BIT.value())
            .build(arena);
    }
    
    /**
     * Binds physical memory to a page at the given offset.
     * Offset must be page-aligned.
     */
    public void bindPage(long offset) {
        if (offset % pageSize != 0) {
            throw new IllegalArgumentException("Offset must be page-aligned");
        }
        if (boundPages.containsKey(offset)) {
            return; // Already bound
        }
        
        // Get memory from pool or allocate new
        MemorySegment memory = freeMemoryPool.poll();
        if (memory == null) {
            memory = allocatePageMemory();
        }
        
        // Create sparse bind info
        MemorySegment bind = arena.allocate(32); // VkSparseMemoryBind
        bind.set(ValueLayout.JAVA_LONG, 0, offset); // resourceOffset
        bind.set(ValueLayout.JAVA_LONG, 8, pageSize); // size
        bind.set(ValueLayout.ADDRESS, 16, memory); // memory
        bind.set(ValueLayout.JAVA_LONG, 24, 0L); // memoryOffset
        
        MemorySegment bufferBind = arena.allocate(24); // VkSparseBufferMemoryBindInfo
        bufferBind.set(ValueLayout.ADDRESS, 0, vkBuffer.handle());
        bufferBind.set(ValueLayout.JAVA_INT, 8, 1); // bindCount
        bufferBind.set(ValueLayout.ADDRESS, 16, bind);
        
        MemorySegment bindInfo = arena.allocate(64); // VkBindSparseInfo
        bindInfo.set(ValueLayout.JAVA_INT, 0, 1000009); // VK_STRUCTURE_TYPE_BIND_SPARSE_INFO
        bindInfo.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        bindInfo.set(ValueLayout.JAVA_INT, 16, 0); // waitSemaphoreCount
        bindInfo.set(ValueLayout.JAVA_INT, 24, 1); // bufferBindCount
        bindInfo.set(ValueLayout.ADDRESS, 32, bufferBind);
        
        VkFence fence = VkFence.builder().device(device).build(arena);
        int result = vkQueueBindSparse(sparseQueue, 1, bindInfo, fence.handle());
        if (result != 0) {
            throw new RuntimeException("Failed to bind sparse memory: " + result);
        }
        
        VkFenceOps.waitFor(device).fence(fence.handle()).execute(arena).check();
        fence.close();
        
        boundPages.put(offset, memory);
    }
    
    /**
     * Unbinds physical memory from a page at the given offset.
     * Memory is returned to pool for reuse.
     */
    public void unbindPage(long offset) {
        MemorySegment memory = boundPages.remove(offset);
        if (memory == null) {
            return; // Not bound
        }
        
        // Create unbind (bind with NULL memory)
        MemorySegment bind = arena.allocate(32);
        bind.set(ValueLayout.JAVA_LONG, 0, offset);
        bind.set(ValueLayout.JAVA_LONG, 8, pageSize);
        bind.set(ValueLayout.ADDRESS, 16, MemorySegment.NULL);
        bind.set(ValueLayout.JAVA_LONG, 24, 0L);
        
        MemorySegment bufferBind = arena.allocate(24);
        bufferBind.set(ValueLayout.ADDRESS, 0, vkBuffer.handle());
        bufferBind.set(ValueLayout.JAVA_INT, 8, 1);
        bufferBind.set(ValueLayout.ADDRESS, 16, bind);
        
        MemorySegment bindInfo = arena.allocate(64);
        bindInfo.set(ValueLayout.JAVA_INT, 0, 1000009);
        bindInfo.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        bindInfo.set(ValueLayout.JAVA_INT, 16, 0);
        bindInfo.set(ValueLayout.JAVA_INT, 24, 1);
        bindInfo.set(ValueLayout.ADDRESS, 32, bufferBind);
        
        VkFence fence = VkFence.builder().device(device).build(arena);
        vkQueueBindSparse(sparseQueue, 1, bindInfo, fence.handle());
        VkFenceOps.waitFor(device).fence(fence.handle()).execute(arena).check();
        fence.close();
        
        freeMemoryPool.add(memory);
    }
    
    /**
     * Checks if a page at the given offset is bound.
     */
    public boolean isPageBound(long offset) {
        return boundPages.containsKey(offset);
    }
    
    /**
     * Returns the page size for this sparse buffer.
     */
    public long getPageSize() {
        return pageSize;
    }
    
    private MemorySegment allocatePageMemory() {
        MemorySegment allocInfo = arena.allocate(32);
        allocInfo.set(ValueLayout.JAVA_INT, 0, 5); // VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
        allocInfo.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        allocInfo.set(ValueLayout.JAVA_LONG, 16, pageSize);
        
        // Find memory type
        MemorySegment memProps = arena.allocate(520);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice.handle(), memProps);
        int memoryTypeIndex = findMemoryType(memProps, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value());
        allocInfo.set(ValueLayout.JAVA_INT, 24, memoryTypeIndex);
        
        MemorySegment memoryPtr = arena.allocate(ValueLayout.ADDRESS);
        int result = vkAllocateMemory(device.handle(), allocInfo, MemorySegment.NULL, memoryPtr);
        if (result != 0) {
            throw new RuntimeException("Failed to allocate page memory: " + result);
        }
        
        return memoryPtr.get(ValueLayout.ADDRESS, 0);
    }
    
    private int findMemoryType(MemorySegment memProps, int properties) {
        int typeCount = memProps.get(ValueLayout.JAVA_INT, 0);
        for (int i = 0; i < typeCount; i++) {
            int props = memProps.get(ValueLayout.JAVA_INT, 4 + i * 8 + 4);
            if ((props & properties) == properties) {
                return i;
            }
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }
    
    @Override
    public void write(ByteBuffer data, long offset) {
        throw new UnsupportedOperationException("Use bindPage() and transfer operations for sparse buffers");
    }
    
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        throw new UnsupportedOperationException("Use bindPage() and transfer operations for sparse buffers");
    }
    
    @Override
    public ByteBuffer read(long offset, long size) {
        throw new UnsupportedOperationException("Cannot read from sparse device-local buffer");
    }
    
    @Override
    public void flush() {
    }
    
    @Override
    public void close() {
        // Unbind all pages
        for (long offset : boundPages.keySet().toArray(new Long[0])) {
            unbindPage(offset);
        }
        
        // Free pooled memory
        for (MemorySegment memory : freeMemoryPool) {
            vkFreeMemory(device.handle(), memory, MemorySegment.NULL);
        }
        
        super.close();
    }
}
