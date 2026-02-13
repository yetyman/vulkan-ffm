package io.github.yetyman.vulkan.auto;

/**
 * Result of buffer strategy selection.
 * Separates memory allocation strategy from synchronization strategy (ring buffering).
 */
public record BufferStrategySelection(
    MemoryStrategy memoryStrategy,
    boolean useRingBuffer,
    int recommendedFrameCount
) {
    public BufferStrategySelection(MemoryStrategy memoryStrategy, boolean useRingBuffer) {
        this(memoryStrategy, useRingBuffer, 3);
    }
}
