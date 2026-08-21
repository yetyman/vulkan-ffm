package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * No CPU observability: the buffer cannot be read from the CPU without a pipeline-stalling
 * readback. This is the default for device-local buffers without mirroring, and for sparse
 * buffers without host-visible pages.
 *
 * <p>All methods are no-ops or return null/false. No resources are allocated.
 */
public final class NoneObservability implements CpuObservability {

    /** Shared singleton instance — no state, safe to reuse. */
    public static final NoneObservability INSTANCE = new NoneObservability();

    public NoneObservability() {
    }

    @Override
    public MemorySegment acquireReadable(long offset, long size) {
        return null;
    }

    @Override
    public boolean isReadable() {
        return false;
    }

    @Override
    public boolean isMirrored() {
        return false;
    }

    @Override
    public void initialize(VkDevice device, long bufferSize, MemorySegment primaryHandle, Arena arena) {
        // no-op: nothing to allocate
    }

    @Override
    public void close() {
        // no-op: nothing to release
    }
}
