package io.github.yetyman.vulkan.auto;

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
        
        boolean ringBuffer = (cpuWrite == AccessFrequency.MULTI_FRAME) || (cpuRead == AccessFrequency.MULTI_FRAME);
        
        if (ringBuffer) {
            // Ring buffer reduces access frequency to underlying buffers
            AccessFrequency adjustedCpuWrite = cpuWrite == AccessFrequency.MULTI_FRAME ? AccessFrequency.FRAME : cpuWrite;
            AccessFrequency adjustedCpuRead = cpuRead == AccessFrequency.MULTI_FRAME ? AccessFrequency.FRAME : cpuRead;
            MemoryStrategy underlyingStrategy = selectMemoryStrategy(adjustedCpuWrite, adjustedCpuRead, gpuRead, gpuWrite, size);
            return new BufferStrategySelection(MemoryStrategy.RING_BUFFER, underlyingStrategy);
        } else {
            MemoryStrategy strategy = selectMemoryStrategy(cpuWrite, cpuRead, gpuRead, gpuWrite, size);
            return new BufferStrategySelection(strategy, MemoryStrategy.DEVICE_LOCAL);
        }
    }
    
    private static MemoryStrategy selectMemoryStrategy(
            AccessFrequency cpuWrite,
            AccessFrequency cpuRead,
            AccessFrequency gpuRead,
            AccessFrequency gpuWrite,
            DataScale size) {
        
        if (cpuWrite == AccessFrequency.FRAME || cpuWrite == AccessFrequency.MULTI_FRAME) {
            return handleFrequentCpuWrites(gpuWrite, gpuRead, size);
        }
        if (cpuWrite == AccessFrequency.RARE) {
            return handleRareCpuWrites(gpuWrite, gpuRead, size);
        }
        if (cpuWrite == AccessFrequency.NEVER) {
            return handleNoCpuWrites(cpuRead, gpuWrite);
        }
        throw new IllegalArgumentException("Invalid access pattern");
    }
    
    private static MemoryStrategy handleFrequentCpuWrites(AccessFrequency gpuWrite, AccessFrequency gpuRead, DataScale size) {
        if (gpuWrite == AccessFrequency.FRAME || gpuWrite == AccessFrequency.MULTI_FRAME) {
            throw new IllegalArgumentException("CPU and GPU both writing frequently - conflict");
        }
        
        // CPU writes frequently, GPU reads - prefer MAPPED for convenience unless GPU reads are critical
        if (gpuRead == AccessFrequency.FRAME || gpuRead == AccessFrequency.MULTI_FRAME) {
            // High-frequency GPU reads: prefer DEVICE_LOCAL for performance, but MAPPED acceptable for small sizes
            return switch (size) {
                case TRIVIAL -> MemoryStrategy.MAPPED; // Small enough that PCIe overhead is negligible
                case SMALL, MEDIUM -> MemoryStrategy.MAPPED; // Frequent CPU writes make MAPPED practical
                case LARGE -> MemoryStrategy.STAGING; // Too large for mapped, use staging
            };
        }
        
        // Rare/never GPU reads: MAPPED is fine
        return switch (size) {
            case TRIVIAL, SMALL, MEDIUM -> MemoryStrategy.MAPPED;
            case LARGE -> MemoryStrategy.STAGING;
        };
    }
    
    private static MemoryStrategy handleRareCpuWrites(AccessFrequency gpuWrite, AccessFrequency gpuRead, DataScale size) {
        if (gpuWrite == AccessFrequency.FRAME || gpuWrite == AccessFrequency.MULTI_FRAME) {
            // GPU owns the data, CPU rarely updates
            return MemoryStrategy.DEVICE_LOCAL;
        }
        
        // CPU uploads static/rare data
        if (gpuRead == AccessFrequency.FRAME || gpuRead == AccessFrequency.MULTI_FRAME) {
            // GPU reads frequently: MUST use DEVICE_LOCAL for performance
            return MemoryStrategy.STAGING; // Upload via staging to DEVICE_LOCAL
        }
        
        // GPU reads rarely: MAPPED acceptable for trivial sizes
        return switch (size) {
            case TRIVIAL -> MemoryStrategy.MAPPED;
            case SMALL, MEDIUM, LARGE -> MemoryStrategy.STAGING;
        };
    }
    
    private static MemoryStrategy handleNoCpuWrites(AccessFrequency cpuRead, AccessFrequency gpuWrite) {
        // CPU reads frequently - readback scenario
        if (cpuRead == AccessFrequency.FRAME || cpuRead == AccessFrequency.MULTI_FRAME) {
            if (gpuWrite == AccessFrequency.FRAME || gpuWrite == AccessFrequency.MULTI_FRAME || gpuWrite == AccessFrequency.RARE) {
                return MemoryStrategy.MAPPED_CACHED; // GPU writes, CPU reads back
            }
            // CPU reads but GPU doesn't write - invalid (nothing to read)
            throw new IllegalArgumentException("CPU reads frequently but GPU never writes - invalid");
        }
        
        // GPU-owned data (GPU writes, CPU doesn't)
        if (gpuWrite == AccessFrequency.FRAME || gpuWrite == AccessFrequency.MULTI_FRAME || gpuWrite == AccessFrequency.RARE) {
            return MemoryStrategy.DEVICE_LOCAL;
        }
        
        // Nobody writes - static/read-only data (pre-initialized)
        return MemoryStrategy.DEVICE_LOCAL;
    }
}
