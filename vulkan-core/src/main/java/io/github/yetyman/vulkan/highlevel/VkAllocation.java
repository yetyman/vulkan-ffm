package io.github.yetyman.vulkan.highlevel;

import java.lang.foreign.MemorySegment; /**
 * Represents an allocated memory region.
 */
public record VkAllocation(MemorySegment memory, long offset, long size) {}
