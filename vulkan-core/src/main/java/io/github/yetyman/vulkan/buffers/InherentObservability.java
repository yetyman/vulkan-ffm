package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Inherent CPU observability: the buffer's primary allocation is already host-visible and
 * persistently mapped. CPU reads go directly to the mapped device memory with zero copy.
 *
 * <p>Used for {@code MAPPED}, {@code MAPPED_CACHED}, and {@code REBAR} buffers where the
 * allocation strategy provides persistent CPU access as part of its normal operation.
 *
 * <p>Since writes land directly in GPU-visible memory, there is no divergence between
 * CPU and GPU state — {@link #isMirrored()} returns false.
 */
public final class InherentObservability implements CpuObservability {

    private MemorySegment mappedMemory;
    private long bufferSize;

    public InherentObservability() {
    }

    @Override
    public MemorySegment acquireReadable(long offset, long size) {
        if (mappedMemory == null) return null;
        return mappedMemory.asSlice(offset, size);
    }

    @Override
    public boolean isReadable() {
        return mappedMemory != null;
    }

    @Override
    public boolean isMirrored() {
        return false;
    }

    @Override
    public void initialize(VkDevice device, long bufferSize, MemorySegment primaryHandle, Arena arena) {
        // The mapped memory is set externally after allocation, since it comes from
        // the AllocationStrategy's persistentMap call. See ManagedBuffer constructor.
        this.bufferSize = bufferSize;
    }

    /**
     * Sets the mapped memory reference. Called by {@link ManagedBuffer} after the allocation
     * strategy's persistent map completes.
     */
    void setMappedMemory(MemorySegment mapped) {
        this.mappedMemory = mapped;
    }

    @Override
    public void close() {
        // no-op: the mapped memory belongs to the primary buffer's arena
        mappedMemory = null;
    }
}
