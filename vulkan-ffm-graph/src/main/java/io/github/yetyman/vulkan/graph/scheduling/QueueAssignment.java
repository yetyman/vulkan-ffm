package io.github.yetyman.vulkan.graph.scheduling;

import java.lang.foreign.MemorySegment;

/**
 * The physical queue a node has been assigned to by the scheduler.
 */
public record QueueAssignment(
    MemorySegment queueHandle,
    int queueFamilyIndex,
    QueueCapability capability
) {}
