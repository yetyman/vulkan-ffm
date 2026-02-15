package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.buffers.BufferStrategyTable;

/**
 * Automatic buffer strategy selector based on access patterns and data size.
 * 
 * <p>This class implements a comprehensive decision matrix for choosing optimal
 * Vulkan buffer management strategies based on:
 * <ul>
 *   <li>CPU write frequency</li>
 *   <li>CPU read frequency</li>
 *   <li>GPU read frequency</li>
 *   <li>GPU write frequency</li>
 *   <li>Data size scale</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Camera matrices updated every frame
 * BufferStrategySelection selection = BufferStrategySelector.select(
 *     AccessFrequency.FRAME,  // CPU writes every frame
 *     AccessFrequency.NEVER,  // CPU never reads
 *     AccessFrequency.FRAME,  // GPU reads every frame
 *     AccessFrequency.NEVER,  // GPU never writes
 *     DataScale.TRIVIAL       // 256 bytes
 * );
 * // Returns: MemoryStrategy.MAPPED, useRingBuffer=false
 * 
 * // Static model geometry
 * BufferStrategySelection selection = BufferStrategySelector.select(
 *     AccessFrequency.RARE,   // CPU uploads once
 *     AccessFrequency.NEVER,
 *     AccessFrequency.MULTI_FRAME, // GPU reads many times
 *     AccessFrequency.NEVER,
 *     DataScale.MEDIUM        // 50MB
 * );
 * // Returns: MemoryStrategy.STAGING, useRingBuffer=false
 * }</pre>
 */
public class BufferStrategySelector {
    
    public static BufferStrategySelection select(
            AccessFrequency cpuWrite,
            AccessFrequency cpuRead,
            AccessFrequency gpuRead,
            AccessFrequency gpuWrite,
            DataScale size) {
        
        return BufferStrategyTable.select(cpuWrite, cpuRead, gpuRead, gpuWrite, size);
    }
}
