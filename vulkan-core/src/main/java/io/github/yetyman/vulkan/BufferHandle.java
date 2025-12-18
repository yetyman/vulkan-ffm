package io.github.yetyman.vulkan;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe buffer handle that manages VkBuffer lifecycle across async operations
 */
public class BufferHandle {
    private final int handleId;
    private final AtomicReference<MemorySegment> vkBuffer = new AtomicReference<>(MemorySegment.NULL);
    private volatile boolean isReady = false;
    
    public BufferHandle(int handleId) {
        this.handleId = handleId;
    }
    
    public void setVkBuffer(MemorySegment buffer) {
        vkBuffer.set(buffer);
        isReady = !buffer.equals(MemorySegment.NULL);
    }
    
    public MemorySegment getVkBuffer() {
        return vkBuffer.get();
    }
    
    public MemorySegment handle() {
        MemorySegment buffer = vkBuffer.get();
        if (buffer == null || buffer.equals(MemorySegment.NULL)) {
            return MemorySegment.NULL;
        }
        return buffer;
    }
    
    public boolean isReady() {
        return isReady;
    }
    
    public int getHandleId() {
        return handleId;
    }
}