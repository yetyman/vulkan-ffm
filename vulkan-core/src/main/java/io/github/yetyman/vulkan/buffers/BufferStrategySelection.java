package io.github.yetyman.vulkan.buffers;

/**
 * Result of buffer strategy selection.
 * Separates primary strategy from secondary strategy for composite buffers,
 * and provides recommended descriptor usage hints.
 */
public record BufferStrategySelection(
        MemoryStrategy memoryStrategy,
        MemoryStrategy secondaryStrategy
) {
    /**
     * Whether this selection uses a ring buffer — i.e. N copies of the data exist,
     * one per frame-in-flight, and the active copy rotates each frame.
     */
    public boolean rotates() {
        return memoryStrategy == MemoryStrategy.RING_BUFFER;
    }

    /**
     * Recommended BufferUsage for descriptor binding.
     * UBO is recommended for small, non-rotating, GPU-read-only data where the driver
     * can cache it in constant memory. SSBO is recommended otherwise.
     */
    public BufferUsage recommendedUsage(AccessFrequency gpuWrite, DataScale size) {
        if (!rotates()
                && gpuWrite == AccessFrequency.NEVER
                && (size == DataScale.TRIVIAL || size == DataScale.SMALL)) {
            return BufferUsage.UNIFORM;
        }
        return BufferUsage.STORAGE;
    }
}
