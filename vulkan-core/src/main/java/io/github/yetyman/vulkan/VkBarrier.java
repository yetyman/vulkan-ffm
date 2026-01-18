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
    
    /**
     * Executes this barrier in a pipeline barrier command
     */
    public void execute(MemorySegment commandBuffer, int srcStage, int dstStage) {
        switch (getType()) {
            case MEMORY -> Vulkan.cmdPipelineBarrier(commandBuffer, srcStage, dstStage,
                0, 1, handle, 0, MemorySegment.NULL, 0, MemorySegment.NULL);
            case BUFFER -> Vulkan.cmdPipelineBarrier(commandBuffer, srcStage, dstStage,
                0, 0, MemorySegment.NULL, 1, handle, 0, MemorySegment.NULL);
            case IMAGE -> Vulkan.cmdPipelineBarrier(commandBuffer, srcStage, dstStage,
                0, 0, MemorySegment.NULL, 0, MemorySegment.NULL, 1, handle);
        }
    }
    
    public enum BarrierType {
        MEMORY, BUFFER, IMAGE
    }
}