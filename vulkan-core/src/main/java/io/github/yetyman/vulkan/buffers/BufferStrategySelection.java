package io.github.yetyman.vulkan.buffers;

/**
 * Result of buffer strategy selection.
 * Separates primary strategy from secondary strategy for composite buffers.
 */
public record BufferStrategySelection(
    MemoryStrategy memoryStrategy,
    MemoryStrategy secondaryStrategy
) {}
