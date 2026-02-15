package io.github.yetyman.vulkan.buffers;

import java.util.*;

/**
 * Lookup table for buffer strategy selection.
 */
class BufferStrategyTable {
    
    private record TableKey(AccessFrequency cpuWrite, AccessFrequency cpuRead, 
                           AccessFrequency gpuRead, AccessFrequency gpuWrite, DataScale size) {}
    
    private static final Map<TableKey, BufferStrategySelection> TABLE = new HashMap<>();
    
    static {
        // Populate all combinations - explicit is better than implicit
        for (AccessFrequency cpuW : AccessFrequency.values()) {
            for (AccessFrequency cpuR : AccessFrequency.values()) {
                for (AccessFrequency gpuR : AccessFrequency.values()) {
                    for (AccessFrequency gpuW : AccessFrequency.values()) {
                        for (DataScale size : DataScale.values()) {
                            TABLE.put(new TableKey(cpuW, cpuR, gpuR, gpuW, size), 
                                     computeStrategy(cpuW, cpuR, gpuR, gpuW, size));
                        }
                    }
                }
            }
        }
    }
    
    static BufferStrategySelection select(AccessFrequency cpuWrite, AccessFrequency cpuRead,
                                         AccessFrequency gpuRead, AccessFrequency gpuWrite, DataScale size) {
        return TABLE.get(new TableKey(cpuWrite, cpuRead, gpuRead, gpuWrite, size));
    }
    
    private static BufferStrategySelection computeStrategy(AccessFrequency cpuW, AccessFrequency cpuR,
                                                          AccessFrequency gpuR, AccessFrequency gpuW, DataScale size) {
        // Ring buffer for MULTI_FRAME CPU access with device-local backing when GPU reads frequently
        if (cpuW == AccessFrequency.MULTI_FRAME && gpuR.isFrequent() && size != DataScale.TRIVIAL) {
            return new BufferStrategySelection(MemoryStrategy.RING_BUFFER, MemoryStrategy.DEVICE_LOCAL);
        }
        
        // Suballocator for trivial + frequent access
        if (size == DataScale.TRIVIAL && (cpuW.isFrequent() || gpuR.isFrequent())) {
            return new BufferStrategySelection(MemoryStrategy.SUBALLOCATOR, MemoryStrategy.MAPPED);
        }
        
        // MULTI_FRAME CPU writes without frequent GPU reads -> just use mapped
        if (cpuW == AccessFrequency.MULTI_FRAME) {
            return new BufferStrategySelection(MemoryStrategy.MAPPED, null);
        }
        
        // Frequent CPU writes
        if (cpuW.isFrequent()) {
            if (gpuW.isFrequent()) return new BufferStrategySelection(MemoryStrategy.MAPPED, null);
            if (gpuR.isFrequent()) {
                if (size == DataScale.LARGE) return new BufferStrategySelection(MemoryStrategy.STAGING, null);
                return new BufferStrategySelection(MemoryStrategy.MAPPED, null);
            }
            if (size == DataScale.LARGE) return new BufferStrategySelection(MemoryStrategy.STAGING, null);
            return new BufferStrategySelection(MemoryStrategy.MAPPED, null);
        }
        
        // Rare CPU writes
        if (cpuW == AccessFrequency.RARE) {
            if (gpuW.isFrequent()) return new BufferStrategySelection(MemoryStrategy.DEVICE_LOCAL, null);
            if (gpuR.isFrequent()) return new BufferStrategySelection(MemoryStrategy.STAGING, null);
            if (size == DataScale.TRIVIAL) return new BufferStrategySelection(MemoryStrategy.MAPPED, null);
            return new BufferStrategySelection(MemoryStrategy.STAGING, null);
        }
        
        // MULTI_FRAME CPU reads -> ring buffer with mapped cached backing
        if (cpuR == AccessFrequency.MULTI_FRAME) {
            return new BufferStrategySelection(MemoryStrategy.RING_BUFFER, MemoryStrategy.MAPPED_CACHED);
        }
        
        // No CPU writes
        if (cpuR.isFrequent()) return new BufferStrategySelection(MemoryStrategy.MAPPED_CACHED, null);
        if (gpuW != AccessFrequency.NEVER) return new BufferStrategySelection(MemoryStrategy.DEVICE_LOCAL, null);
        
        return new BufferStrategySelection(MemoryStrategy.DEVICE_LOCAL, null);
    }
}
