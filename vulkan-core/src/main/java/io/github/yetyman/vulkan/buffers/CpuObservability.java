package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Strategy for how the CPU observes (reads) buffer contents. Third axis of {@link ManagedBuffer}
 * composition, orthogonal to {@link AllocationStrategy} and {@link TransferStrategy}.
 *
 * <p>Determines whether CPU reads are:
 * <ul>
 *   <li><b>None</b>: no CPU reads expected, {@link #acquireReadable} returns null</li>
 *   <li><b>Inherent</b>: allocation is already host-visible, reads are free</li>
 *   <li><b>Mirrored</b>: a separate host-visible companion buffer is maintained in sync</li>
 * </ul>
 *
 * <p>Like {@link AllocationStrategy} and {@link TransferStrategy}, this is a public, visible
 * strategy axis. Users composing via {@link ManagedBuffer#builder()} can pass one explicitly;
 * users going through {@link BufferFactory} get one selected automatically.
 */
public interface CpuObservability {

    /**
     * @return readable memory for [offset, offset+size), or null if this buffer is not
     *         CPU-observable. The returned segment is valid as long as the buffer is alive.
     */
    MemorySegment acquireReadable(long offset, long size);

    /**
     * @return true if the CPU can read this buffer's contents without a GPU stall
     */
    boolean isReadable();

    /**
     * @return true if this observability maintains a separate mirror that can diverge from
     *         the primary GPU buffer (i.e. writes land in the mirror and must be explicitly
     *         flushed to reach the GPU)
     */
    boolean isMirrored();

    /**
     * Lifecycle: initialize with the owning buffer's device, size, and primary handle.
     * Called once by {@link ManagedBuffer}'s constructor after the primary VkBuffer is allocated.
     *
     * @param device        the owning device
     * @param bufferSize    the primary buffer's size in bytes
     * @param primaryHandle the primary VkBuffer handle (for copy operations)
     * @param arena         the arena managing the primary buffer's lifetime
     */
    void initialize(VkDevice device, long bufferSize, MemorySegment primaryHandle, Arena arena);

    /**
     * Lifecycle: release any resources owned by this observability (e.g. a mirror VkBuffer).
     * Called by {@link ManagedBuffer#close()}.
     */
    void close();
}
