package io.github.yetyman.vulkan.sample.complex.models;

import java.lang.foreign.MemorySegment;

/**
 * Pre-defined batch with static geometry and dynamic instance data
 */
public class StaticBatch {
    private final LODLevel lodLevel;
    private final MemorySegment commandBuffer;
    private final int[] instanceIds;
    private final int instanceCount;
    
    public StaticBatch(LODLevel lodLevel, MemorySegment commandBuffer, int[] instanceIds) {
        this.lodLevel = lodLevel;
        this.commandBuffer = commandBuffer;
        this.instanceIds = instanceIds.clone();
        this.instanceCount = instanceIds.length;
    }
    
    public LODLevel getLodLevel() { return lodLevel; }
    public MemorySegment getCommandBuffer() { return commandBuffer; }
    public int[] getInstanceIds() { return instanceIds.clone(); }
    public int getInstanceCount() { return instanceCount; }
}