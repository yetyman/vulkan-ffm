package io.github.yetyman.vulkan.sample.complex.models;

import java.lang.foreign.MemorySegment;

/**
 * Pre-recorded command buffer for static LOD batches
 */
public record PreRecordedBatch(
    MemorySegment commandBuffer,
    LODLevel lodLevel,
    int instanceCount,
    boolean isDirty
) {
}