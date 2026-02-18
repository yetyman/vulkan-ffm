package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Queue;

import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkMemoryAllocateInfo;
import io.github.yetyman.vulkan.generated.VkMemoryType;
import io.github.yetyman.vulkan.generated.VkPhysicalDeviceMemoryProperties;
import static io.github.yetyman.vulkan.enums.VkBufferCreateFlagBits.VK_BUFFER_CREATE_SPARSE_BINDING_BIT;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceFeatures;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkQueueBindSparse;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceMemoryProperties;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkAllocateMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkMapMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkUnmapMemory;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFlushMappedMemoryRanges;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkFreeMemory;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;

/**
 * Sparse buffer with dynamic page-level memory binding.
 * Allows huge virtual address space with on-demand physical memory allocation.
 */
public class SparseBuffer extends AbstractBuffer {
    private final long pageSize;
    private final VkQueue sparseQueue;
    private final VkCommandPool commandPool;
    private final VkQueue transferQueue;
    private final Map<Long, MemorySegment> boundPages = new HashMap<>();
    private final Queue<MemorySegment> freeMemoryPool = new ArrayDeque<>();
    private final int memoryProperties;
    private final boolean isHostVisible;
    private final boolean isHostCoherent;
    private MemorySegment mappedMemory;
    
    public SparseBuffer(VkDevice device, Arena arena,
                       long size, BufferUsage usage, MemoryStrategy underlyingStrategy,
                       VkQueue sparseQueue, VkQueue transferQueue, VkCommandPool commandPool) {
        super(device, arena, size, usage, MemoryStrategy.SPARSE);
        this.sparseQueue = sparseQueue;
        this.transferQueue = transferQueue;
        this.commandPool = commandPool;
        
        if (underlyingStrategy == MemoryStrategy.SPARSE) {
            throw new IllegalArgumentException("Cannot nest sparse buffers");
        }
        
        this.memoryProperties = switch (underlyingStrategy) {
            case MAPPED, MAPPED_CACHED -> VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value() | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value();
            case DEVICE_LOCAL -> VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value();
            default -> throw new IllegalArgumentException("Unsupported underlying strategy for sparse buffer: " + underlyingStrategy);
        };
        
        this.isHostVisible = (memoryProperties & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value()) != 0;
        this.isHostCoherent = (memoryProperties & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT.value()) != 0;
        
        checkSparseSupport();
        createSparseBuffer();
        this.pageSize = querySparsePageSize();
    }
    
    private void checkSparseSupport() {
        MemorySegment features = arena.allocate(220); // VkPhysicalDeviceFeatures
        vkGetPhysicalDeviceFeatures(device.physicalDevice().handle(), features);
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

    private long querySparsePageSize() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment req = io.github.yetyman.vulkan.generated.VkMemoryRequirements.allocate(tmp);
            io.github.yetyman.vulkan.generated.VulkanFFM.vkGetBufferMemoryRequirements(device.handle(), vkBuffer.handle(), req);
            return io.github.yetyman.vulkan.generated.VkMemoryRequirements.alignment(req);
        }
    }
    
    /**
     * Binds physical memory to a page at the given offset.
     * Offset must be page-aligned.
     */
    private void bindPage(long offset) {
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
        
        // Create sparse bind info using builder
        MemorySegment bind = VkSparseMemoryBind.builder()
            .resourceOffset(offset)
            .size(pageSize)
            .memory(memory)
            .memoryOffset(0)
            .flags(0)
            .build(arena);
        
        MemorySegment bufferBind = VkSparseBufferMemoryBindInfo.builder()
            .buffer(vkBuffer.handle())
            .binds(bind)
            .build(arena);
        
        MemorySegment bindInfo = VkBindSparseInfo.builder()
            .bufferBinds(bufferBind)
            .build(arena);
        
        VkFence fence = VkFence.builder().device(device).build(arena);
        int result = vkQueueBindSparse(sparseQueue.handle(), 1, bindInfo, fence.handle());
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
    private void unbindPage(long offset) {
        MemorySegment memory = boundPages.remove(offset);
        if (memory == null) {
            return; // Not bound
        }
        
        // Create unbind (bind with NULL memory) using builder
        MemorySegment bind = VkSparseMemoryBind.builder()
            .resourceOffset(offset)
            .size(pageSize)
            .memory(MemorySegment.NULL)
            .memoryOffset(0)
            .flags(0)
            .build(arena);
        
        MemorySegment bufferBind = VkSparseBufferMemoryBindInfo.builder()
            .buffer(vkBuffer.handle())
            .binds(bind)
            .build(arena);
        
        MemorySegment bindInfo = VkBindSparseInfo.builder()
            .bufferBinds(bufferBind)
            .build(arena);
        
        VkFence fence = VkFence.builder().device(device).build(arena);
        vkQueueBindSparse(sparseQueue.handle(), 1, bindInfo, fence.handle());
        VkFenceOps.waitFor(device).fence(fence.handle()).execute(arena).check();
        fence.close();
        
        freeMemoryPool.add(memory);
    }
    
    /**
     * Checks if a page at the given offset is bound.
     */
    private boolean isPageBound(long offset) {
        return boundPages.containsKey(offset);
    }
    
    /**
     * Returns the page size for this sparse buffer.
     */
    public long getPageSize() {
        return pageSize;
    }
    
    private MemorySegment allocatePageMemory() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment req = io.github.yetyman.vulkan.generated.VkMemoryRequirements.allocate(tmp);
            io.github.yetyman.vulkan.generated.VulkanFFM.vkGetBufferMemoryRequirements(device.handle(), vkBuffer.handle(), req);
            int memTypeBits = io.github.yetyman.vulkan.generated.VkMemoryRequirements.memoryTypeBits(req);

            MemorySegment memProps = VkPhysicalDeviceMemoryProperties.allocate(tmp);
            vkGetPhysicalDeviceMemoryProperties(device.physicalDevice().handle(), memProps);
            int memoryTypeIndex = findMemoryType(memProps, memTypeBits, memoryProperties);

            MemorySegment allocInfo = VkMemoryAllocateInfo.allocate(arena);
            VkMemoryAllocateInfo.sType(allocInfo, VkStructureType.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO.value());
            VkMemoryAllocateInfo.pNext(allocInfo, MemorySegment.NULL);
            VkMemoryAllocateInfo.allocationSize(allocInfo, pageSize);
            VkMemoryAllocateInfo.memoryTypeIndex(allocInfo, memoryTypeIndex);

            MemorySegment memoryPtr = arena.allocate(ValueLayout.ADDRESS);
            int result = vkAllocateMemory(device.handle(), allocInfo, MemorySegment.NULL, memoryPtr);
            if (result != 0) throw new RuntimeException("Failed to allocate page memory: " + result);
            return memoryPtr.get(ValueLayout.ADDRESS, 0);
        }
    }

    private void mapBuffer() {
        if (!isHostVisible) {
            throw new UnsupportedOperationException("Cannot map non-host-visible buffer");
        }
        
        MemorySegment mappedPtr = arena.allocate(ValueLayout.ADDRESS);
        int result = vkMapMemory(device.handle(), vkBuffer.handle(), 0, size, 0, mappedPtr);
        if (result != 0) {
            throw new RuntimeException("Failed to map buffer: " + result);
        }
        
        mappedMemory = mappedPtr.get(ValueLayout.ADDRESS, 0);
    }
    
    /**
     * Ensures all pages in the given range are committed, binding them if necessary.
     */
    private void ensurePagesCommitted(long offset, long size) {
        long startPage = offset / pageSize;
        long endPage = (offset + size - 1) / pageSize;
        
        for (long page = startPage; page <= endPage; page++) {
            long pageOffset = page * pageSize;
            if (!isPageBound(pageOffset)) {
                bindPage(pageOffset);
            }
        }
    }
    
    /**
     * Validates that all pages in the given range are committed, throwing if not.
     */
    private void validatePagesCommitted(long offset, long size) {
        long startPage = offset / pageSize;
        long endPage = (offset + size - 1) / pageSize;
        
        for (long page = startPage; page <= endPage; page++) {
            long pageOffset = page * pageSize;
            if (!isPageBound(pageOffset)) {
                throw new IllegalStateException("Attempting to read from uncommitted sparse buffer page at offset " + pageOffset);
            }
        }
    }
    
    private int findMemoryType(MemorySegment memProps, int typeBits, int properties) {
        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(memProps);
        for (int i = 0; i < typeCount; i++) {
            if ((typeBits & (1 << i)) == 0) continue;
            MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(memProps, i);
            int props = VkMemoryType.propertyFlags(memType);
            if ((props & properties) == properties) return i;
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }
    
    /**
     * Writes data to the sparse buffer, automatically committing pages as needed.
     */
    @Override
    public void write(ByteBuffer data, long offset) {
        ensurePagesCommitted(offset, data.remaining());
        
        if (isHostVisible) {
            // Map all affected pages at once
            long startPage = offset / pageSize;
            long endPage = (offset + data.remaining() - 1) / pageSize;
            long pageCount = endPage - startPage + 1;
            
            // Map contiguous page range
            MemorySegment firstPageMemory = boundPages.get(startPage * pageSize);
            MemorySegment mappedPtr = arena.allocate(ValueLayout.ADDRESS);
            vkMapMemory(device.handle(), firstPageMemory, 0, pageCount * pageSize, 0, mappedPtr);
            MemorySegment mappedMemory = mappedPtr.get(ValueLayout.ADDRESS, 0);
            
            // Single copy operation
            long writeOffset = offset - (startPage * pageSize);
            MemorySegment source = MemorySegment.ofBuffer(data);
            MemorySegment.copy(source, 0, mappedMemory, writeOffset, data.remaining());
            
            // Flush if not coherent
            if (!isHostCoherent) {
                MemorySegment flushRange = arena.allocate(40);
                flushRange.set(ValueLayout.JAVA_INT, 0, 6); // VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE
                flushRange.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
                flushRange.set(ValueLayout.ADDRESS, 16, firstPageMemory);
                flushRange.set(ValueLayout.JAVA_LONG, 24, writeOffset);
                flushRange.set(ValueLayout.JAVA_LONG, 32, data.remaining());
                vkFlushMappedMemoryRanges(device.handle(), 1, flushRange);
            }
            
            // Unmap
            vkUnmapMemory(device.handle(), firstPageMemory);
        } else {
            // Use device-local buffer for staging
            try (var stagingBuffer = new DeviceLocalBuffer(device, arena, data.remaining(), BufferUsage.TRANSFER, transferQueue, commandPool, false)) {
                stagingBuffer.write(data, 0);
                
                VkCommandBuffer cmd = commandPool.allocateCommandBuffer(arena);
                VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(arena);
                cmd.copyBuffer(stagingBuffer.vkBuffer, vkBuffer, 0, offset, data.remaining());
                cmd.end();
                
                VkFence fence = VkFence.builder().device(device).build(arena);
                device.submitAndWait(transferQueue, cmd, fence, arena);
                VkFenceOps.waitFor(device).fence(fence.handle()).execute(arena).check();
                fence.close();
            }
        }
    }
    
    /**
     * Writes data asynchronously, automatically committing pages as needed.
     */
    @Override
    public TransferCompletion writeAsync(ByteBuffer data, long offset) {
        ensurePagesCommitted(offset, data.remaining());
        
        if (isHostVisible) {
            // Direct write is synchronous for host-visible
            write(data, offset);
            return new TransferCompletion(device, null, arena);
        } else {
            // Use device-local buffer for staging
            var stagingBuffer = new DeviceLocalBuffer(device, arena, data.remaining(), BufferUsage.TRANSFER, transferQueue, commandPool, false);
            stagingBuffer.write(data, 0);
            
            VkCommandBuffer cmd = commandPool.allocateCommandBuffer(arena);
            VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(arena);
            cmd.copyBuffer(stagingBuffer.vkBuffer, vkBuffer, 0, offset, data.remaining());
            cmd.end();
            
            VkFence fence = VkFence.builder().device(device).build(arena);
            device.submitAndWait(transferQueue, cmd, fence, arena);
            
            return new TransferCompletion(device, fence, arena) {
                @Override
                public void await() {
                    super.await();
                    stagingBuffer.close();
                }
            };
        }
    }
    
    /**
     * Reads from sparse buffer - throws error if pages not committed.
     */
    @Override
    public ByteBuffer read(long offset, long size) {
        validatePagesCommitted(offset, size);
        
        if (isHostVisible) {
            ByteBuffer result = ByteBuffer.allocate((int)size);
            long startPage = offset / pageSize;
            long endPage = (offset + size - 1) / pageSize;
            long pageCount = endPage - startPage + 1;
            
            // Map contiguous page range
            MemorySegment firstPageMemory = boundPages.get(startPage * pageSize);
            MemorySegment mappedPtr = arena.allocate(ValueLayout.ADDRESS);
            vkMapMemory(device.handle(), firstPageMemory, 0, pageCount * pageSize, 0, mappedPtr);
            MemorySegment mappedMemory = mappedPtr.get(ValueLayout.ADDRESS, 0);
            
            // Single copy operation
            long readOffset = offset - (startPage * pageSize);
            MemorySegment target = MemorySegment.ofBuffer(result);
            MemorySegment.copy(mappedMemory, readOffset, target, 0, size);
            
            // Unmap
            vkUnmapMemory(device.handle(), firstPageMemory);
            
            return result;
        } else {
            System.err.println("WARNING: Synchronous read from device-local buffer requires staging buffer and GPU->CPU transfer. This is extremely slow and will stall the pipeline. Consider using MAPPED/MAPPED_CACHED strategy for frequent reads.");
            VkBuffer readbackBuf = VkBuffer.builder().device(device).size(size).transferDst().hostVisible().build(arena);
            try {
                VkCommandBuffer cmd = commandPool.allocateCommandBuffer(arena);
                VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(arena);
                cmd.copyBuffer(vkBuffer, readbackBuf, offset, 0, size);
                cmd.end();
                VkFence fence = VkFence.builder().device(device).build(arena);
                device.submitAndWait(transferQueue, cmd, fence, arena);
                VkFenceOps.waitFor(device).fence(fence.handle()).execute(arena).check();
                fence.close();
                MemorySegment mapped = readbackBuf.map(arena);
                ByteBuffer result = ByteBuffer.allocate((int) size);
                MemorySegment.copy(mapped, 0, MemorySegment.ofBuffer(result), 0, size);
                readbackBuf.unmap();
                return result.rewind();
            } finally {
                readbackBuf.close();
            }
        }
    }
    
    /**
     * Reads from sparse buffer asynchronously using staging buffer for device-local memory.
     */
    public TransferCompletion readAsync(long offset, long size) {
        validatePagesCommitted(offset, size);
        
        if (isHostVisible) {
            ByteBuffer result = read(offset, size);
            return new TransferCompletion(device, null, arena) {
                public ByteBuffer getResult() { return result; }
            };
        } else {
            var stagingBuffer = new DeviceLocalBuffer(device, arena, size, BufferUsage.TRANSFER, transferQueue, commandPool, false);
            
            VkCommandBuffer cmd = commandPool.allocateCommandBuffer(arena);
            VkCommandBuffer.begin(cmd).oneTimeSubmit().execute(arena);
            cmd.copyBuffer(vkBuffer, stagingBuffer.vkBuffer, offset, 0, size);
            cmd.end();
            
            VkFence fence = VkFence.builder().device(device).build(arena);
            device.submitAndWait(transferQueue, cmd, fence, arena);
            
            return new TransferCompletion(device, fence, arena) {
                public ByteBuffer getResult() {
                    await();
                    return stagingBuffer.read(0, size);
                }
                
                @Override
                public void await() {
                    super.await();
                    stagingBuffer.close();
                }
            };
        }
    }
    
    private void flushMappedRange(long offset, long size) {
        MemorySegment flushRange = arena.allocate(32); // VkMappedMemoryRange
        flushRange.set(ValueLayout.JAVA_INT, 0, 6); // VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE
        flushRange.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        flushRange.set(ValueLayout.ADDRESS, 16, vkBuffer.handle());
        flushRange.set(ValueLayout.JAVA_LONG, 24, offset);
        flushRange.set(ValueLayout.JAVA_LONG, 32, size);
        
        vkFlushMappedMemoryRanges(device.handle(), 1, flushRange);
    }

    
    @Override
    public void flush() {
    }
    
    @Override
    public void closeImpl() {
        // Unbind all pages
        for (long offset : boundPages.keySet().toArray(new Long[0])) {
            unbindPage(offset);
        }
        
        // Free pooled memory
        for (MemorySegment memory : freeMemoryPool) {
            vkFreeMemory(device.handle(), memory, MemorySegment.NULL);
        }
        
        super.closeImpl();
    }
}
