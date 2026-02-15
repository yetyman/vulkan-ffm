package io.github.yetyman.vulkan.auto;

/**
 * Result of buffer strategy selection.
 * Separates primary strategy from secondary strategy for composite buffers.
 */
public record BufferStrategySelection(
    MemoryStrategy memoryStrategy,
    MemoryStrategy secondaryStrategy,
    int recommendedFrameCount
) {
    public BufferStrategySelection(MemoryStrategy memoryStrategy, MemoryStrategy secondaryStrategy) {
        this(memoryStrategy, secondaryStrategy, 3);
    }
}
