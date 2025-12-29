package io.github.yetyman.vulkan;

import java.lang.foreign.MemorySegment;

/**
 * Base class for Vulkan memory barriers.
 * All barriers are used in pipeline barrier commands but have different scopes.
 */
public abstract class VkBarrier {
    protected final MemorySegment handle;
    
    protected VkBarrier(MemorySegment handle) {
        this.handle = handle;
    }
    
    /** @return the barrier's memory segment handle */
    public MemorySegment handle() { 
        return handle; 
    }
    
    /** @return the barrier type for pipeline barrier arrays */
    public abstract BarrierType getType();
    
    public enum BarrierType {
        MEMORY, BUFFER, IMAGE
    }
}