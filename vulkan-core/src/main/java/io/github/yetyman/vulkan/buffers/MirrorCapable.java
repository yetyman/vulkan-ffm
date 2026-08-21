package io.github.yetyman.vulkan.buffers;

import java.lang.foreign.MemorySegment;

/**
 * Capability interface for {@link CpuObservability} implementations that maintain a separate
 * CPU-side mirror buffer. Check via {@code instanceof} on the buffer's
 * {@link ManagedBuffer#observability()}.
 *
 * <p>Provides access to the mirror's underlying resources for flush operations and direct
 * memory access. The deferred/dirty/flush logic lives on {@link ManagedBuffer} and composes
 * this with the {@link DirtyStrategy} — those concerns do not belong on this interface.
 *
 * <p>Usage:
 * <pre>{@code
 * if (buffer.observability() instanceof MirrorCapable mirror) {
 *     MemorySegment readable = mirror.mirrorMemory();
 *     // direct CPU access to mirrored data
 * }
 * }</pre>
 */
public interface MirrorCapable {

    /**
     * @return the mirror's mapped memory for direct CPU access. The full buffer size is
     *         readable/writable at any offset. Valid as long as the buffer is alive.
     */
    MemorySegment mirrorMemory();

    /**
     * @return the mirror VkBuffer handle. Used as the source for CPU->GPU copies and the
     *         destination for GPU->CPU copies.
     */
    MemorySegment mirrorHandle();

    /**
     * @return the size of the mirror buffer in bytes (matches the primary buffer size)
     */
    long mirrorSize();
}
