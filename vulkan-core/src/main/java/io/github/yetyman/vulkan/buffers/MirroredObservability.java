package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Mirrored CPU observability: allocates a separate host-visible, persistently-mapped
 * {@code VkBuffer} companion that serves as both:
 * <ul>
 *   <li>The CPU-readable surface (zero-cost random-access reads)</li>
 *   <li>The staging source for CPU->GPU copies (no second memcpy into a throwaway staging buffer)</li>
 * </ul>
 *
 * <p>The mirror diverges from the primary (device-local) buffer between writes and flushes.
 * In deferred mode, writes land in the mirror only; the primary is updated on explicit flush.
 * In immediate mode, writes land in the mirror and are copied to the primary immediately.
 *
 * <p>Implements {@link MirrorCapable} to expose the mirror's resources for direct access
 * and copy operations by {@link ManagedBuffer}'s flush logic.
 *
 * <p>Used for {@code DEVICE_LOCAL_MIRRORED} buffers.
 *
 * <p>NOTE: Mirroring for Ring/Sparse/Suballocator composites requires per-composite
 * implementations and is deferred to future work.
 */
public final class MirroredObservability implements CpuObservability, MirrorCapable {

    private VkBuffer mirrorBuffer;
    private MemorySegment mirrorMapped;
    private MemorySegment primaryHandle;
    private long bufferSize;
    private Arena mirrorArena;

    public MirroredObservability() {
    }

    @Override
    public MemorySegment acquireReadable(long offset, long size) {
        if (mirrorMapped == null) return null;
        return mirrorMapped.asSlice(offset, size);
    }

    @Override
    public boolean isReadable() {
        return mirrorMapped != null;
    }

    @Override
    public boolean isMirrored() {
        return true;
    }

    @Override
    public void initialize(VkDevice device, long bufferSize, MemorySegment primaryHandle, Arena arena) {
        this.bufferSize = bufferSize;
        this.primaryHandle = primaryHandle;
        this.mirrorArena = Arena.ofShared();
        try {
            this.mirrorBuffer = VkBuffer.builder()
                    .device(device)
                    .size(bufferSize)
                    .transferSrc()
                    .transferDst()
                    .hostVisible()
                    .build(mirrorArena);
            this.mirrorMapped = mirrorBuffer.map(mirrorArena);
        } catch (Exception e) {
            mirrorArena.close();
            mirrorArena = null;
            throw e;
        }
    }

    // -- MirrorCapable --

    @Override
    public MemorySegment mirrorMemory() {
        return mirrorMapped;
    }

    @Override
    public MemorySegment mirrorHandle() {
        return mirrorBuffer.handle();
    }

    @Override
    public long mirrorSize() {
        return bufferSize;
    }

    // -- Lifecycle --

    @Override
    public void close() {
        if (mirrorBuffer != null) {
            mirrorBuffer.close();
            mirrorBuffer = null;
        }
        if (mirrorArena != null) {
            mirrorArena.close();
            mirrorArena = null;
        }
        mirrorMapped = null;
        primaryHandle = null;
    }
}
