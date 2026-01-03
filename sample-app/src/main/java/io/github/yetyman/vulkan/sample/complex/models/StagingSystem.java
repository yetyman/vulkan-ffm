package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.highlevel.VkTransientCommandBuffer;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

/**
 * Coordinates staging buffers and device buffer allocation
 */
public class StagingSystem {
    private final ConcurrentLinkedQueue<StagingBuffer> availableStagingBuffers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CopyRequest> pendingCopies = new ConcurrentLinkedQueue<>();
    private final BufferManager bufferManager;
    private final Arena arena;
    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;
    private final MemorySegment queue;
    private final VkCommandPool commandPool;
    private final AtomicInteger nextRequestId = new AtomicInteger(0);
    
    private static final long STAGING_BUFFER_SIZE = 16 * 1024 * 1024; // 16MB staging buffers
    
    public StagingSystem(Arena arena, VkDevice device, VkPhysicalDevice physicalDevice, MemorySegment queue) {
        this.arena = Arena.ofShared(); // Use shared arena for cross-thread access
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.queue = queue;
        this.commandPool = VkCommandPool.builder()
            .device(device)
            .queueFamilyIndex(0) // Assume graphics queue family 0
            .transientBit()
            .build(this.arena);
        this.bufferManager = new BufferManager(Arena.ofShared(), device, physicalDevice); // BufferManager needs shared arena too
        
        // Create initial staging buffers on main thread
        for (int i = 0; i < 4; i++) {
            try {
                StagingBuffer stagingBuffer = new StagingBuffer(this.arena, device, physicalDevice, STAGING_BUFFER_SIZE);
                availableStagingBuffers.offer(stagingBuffer);
                Logger.debug("Created staging buffer " + i + " with size " + STAGING_BUFFER_SIZE);
            } catch (Exception e) {
                System.err.println("[STAGING] Failed to create staging buffer " + i + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Upload vertex data to staging buffer (called from background thread)
     */
    public int stageVertexData(MemorySegment vertexData, MemorySegment indexData) {
        StagingBuffer stagingBuffer = availableStagingBuffers.poll();
        if (stagingBuffer == null) {
            // Create new staging buffer if none available
            try {
                stagingBuffer = new StagingBuffer(this.arena, device, physicalDevice, STAGING_BUFFER_SIZE);
                Logger.debug("Created new staging buffer on demand");
            } catch (Exception e) {
                System.err.println("[STAGING] Failed to create staging buffer on demand: " + e.getMessage());
                return -1;
            }
        }
        
        // Upload vertex data
        long vertexOffset = stagingBuffer.uploadData(vertexData);
        if (vertexOffset == -1) {
            // Staging buffer full, reset and try again
            stagingBuffer.reset();
            vertexOffset = stagingBuffer.uploadData(vertexData);
        }
        
        // Upload index data
        long indexOffset = stagingBuffer.uploadData(indexData);
        if (indexOffset == -1) {
            System.err.println("[STAGING] Failed to upload index data, buffer too small");
            availableStagingBuffers.offer(stagingBuffer);
            return -1;
        }
        
        // Create copy request
        int requestId = nextRequestId.incrementAndGet();
        CopyRequest request = new CopyRequest(requestId, stagingBuffer, 
            vertexOffset, vertexData.byteSize(), indexOffset, indexData.byteSize());
        pendingCopies.offer(request);
        
        Logger.debug("Staged data for request " + requestId + 
                          " (V:" + vertexData.byteSize() + ", I:" + indexData.byteSize() + ")");
        return requestId;
    }
    
    /**
     * Process pending copies to device buffers (called from main thread)
     */
    public void processPendingCopies(int maxCopies) {
        for (int i = 0; i < maxCopies; i++) {
            CopyRequest request = pendingCopies.poll();
            if (request == null) break;
            
            // Acquire device buffers
            VkBuffer vertexBuffer = bufferManager.acquireVertexBuffer(request.vertexSize);
            VkBuffer indexBuffer = bufferManager.acquireIndexBuffer(request.indexSize);
            
            if (vertexBuffer != null && indexBuffer != null) {
                copyBufferData(request, vertexBuffer, indexBuffer);
                Logger.debug("Copied request " + request.requestId + " to GPU buffers");
                
                // Store buffer handles in request for retrieval
                request.deviceVertexBuffer = vertexBuffer;
                request.deviceIndexBuffer = indexBuffer;
                request.completed = true;
                completedRequests.put(request.requestId, request);
            } else {
                // Put request back if buffers not available
                pendingCopies.offer(request);
                break;
            }
            
            // Return staging buffer to pool
            request.stagingBuffer.reset();
            availableStagingBuffers.offer(request.stagingBuffer);
        }
    }
    
    private final Map<Integer, CopyRequest> completedRequests = new ConcurrentHashMap<>();
    
    /**
     * Get completed copy request
     */
    public CopyRequest getCompletedRequest(int requestId) {
        return completedRequests.remove(requestId);
    }
    
    public BufferManager getBufferManager() {
        return bufferManager;
    }
    
    public int getPendingCopyCount() {
        return pendingCopies.size();
    }
    
    private void copyBufferData(CopyRequest request, VkBuffer vertexBuffer, VkBuffer indexBuffer) {
        try {
            // Use VkTransientCommandBuffer for simplified copy operations
            VkTransientCommandBuffer.execute(commandPool, queue, arena, cmd -> {
                // Copy vertex data
                MemorySegment vertexCopy = VkBufferCopy.allocate(arena, request.vertexOffset, 0, request.vertexSize);
                Vulkan.cmdCopyBuffer(cmd, request.stagingBuffer.getBuffer().handle(), vertexBuffer.handle(), 1, vertexCopy);
                
                // Copy index data
                MemorySegment indexCopy = VkBufferCopy.allocate(arena, request.indexOffset, 0, request.indexSize);
                Vulkan.cmdCopyBuffer(cmd, request.stagingBuffer.getBuffer().handle(), indexBuffer.handle(), 1, indexCopy);
            });
            
            // Store the buffers in the request
            request.deviceVertexBuffer = vertexBuffer;
            request.deviceIndexBuffer = indexBuffer;
            
        } catch (Exception e) {
            System.err.println("[STAGING] Failed to copy buffer data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    /**
     * Create an immediate buffer for testing (synchronous)
     */
    public VkBuffer createImmediateBuffer(float[] data) {
        try {
            // Create vertex data in staging buffer using shared arena
            MemorySegment vertexData = arena.allocate(data.length * 4);
            for (int i = 0; i < data.length; i++) {
                vertexData.setAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, i, data[i]);
            }
            
            // Stage the data and get device buffer
            int requestId = stageVertexData(vertexData, arena.allocate(4)); // dummy index data
            
            // Process the copy immediately (synchronous for testing)
            processPendingCopies(1);
            
            // Get the completed request
            CopyRequest request = getCompletedRequest(requestId);
            if (request != null && request.deviceVertexBuffer != null) {
                return request.deviceVertexBuffer;
            }
        } catch (Exception e) {
            System.err.println("[STAGING] Failed to create immediate buffer: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Release a buffer back to the pool
     */
    public void releaseBuffer(MemorySegment bufferHandle, boolean isVertex) {
        // Find the VkBuffer from handle and release it
        // This is a simplified approach - in practice we'd need better tracking
        if (isVertex) {
            // bufferManager.releaseVertexBuffer(buffer);
        } else {
            // bufferManager.releaseIndexBuffer(buffer);
        }
    }
    
    public void cleanup() {
        // Wait for device to be idle before cleanup
        if (device != null && !device.handle().equals(MemorySegment.NULL)) {
            io.github.yetyman.vulkan.Vulkan.deviceWaitIdle(device.handle()).check();
            Logger.debug("Device idle - starting StagingSystem cleanup");
        }
        
        bufferManager.cleanup();
        
        if (commandPool != null) {
            commandPool.close();
        }
        
        StagingBuffer buffer;
        while ((buffer = availableStagingBuffers.poll()) != null) {
            buffer.close();
        }
        
        Logger.debug("StagingSystem cleanup complete");
    }
    
    /**
     * Represents a copy request from staging to device buffers
     */
    public static class CopyRequest {
        public final int requestId;
        public final StagingBuffer stagingBuffer;
        public final long vertexOffset;
        public final long vertexSize;
        public final long indexOffset;
        public final long indexSize;
        public VkBuffer deviceVertexBuffer;
        public VkBuffer deviceIndexBuffer;
        public boolean completed = false;
        
        public CopyRequest(int requestId, StagingBuffer stagingBuffer, 
                          long vertexOffset, long vertexSize, long indexOffset, long indexSize) {
            this.requestId = requestId;
            this.stagingBuffer = stagingBuffer;
            this.vertexOffset = vertexOffset;
            this.vertexSize = vertexSize;
            this.indexOffset = indexOffset;
            this.indexSize = indexSize;
        }
    }
}