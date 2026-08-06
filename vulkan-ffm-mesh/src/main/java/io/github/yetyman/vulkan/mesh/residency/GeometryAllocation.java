package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.DeviceRange;

import java.util.Optional;

/**
 * The result of allocating space for one geometry in an allocator. Provides the buffer ranges
 * where vertex streams and indices will live once uploaded.
 *
 * <p>Locations are not guaranteed stable: a defragmenting allocator may move them, and the
 * {@code GeometryTable} (Layer 4) is the indirection point that consumers read through.
 */
public interface GeometryAllocation {

    /**
     * @return the buffer range backing stream {@code streamId} of the layout this was allocated for
     */
    DeviceRange vertexRange(int streamId);

    /**
     * @return the buffer range backing the index data, or empty if the geometry is not indexed
     */
    Optional<DeviceRange> indexRange();

    /**
     * @return first vertex index within the shared pool (the offset a draw's vertexOffset refers
     * to), or 0 for dedicated allocations where the geometry starts at the buffer's beginning
     */
    long vertexBase();

    /**
     * @return first index within the shared index pool, or 0 for dedicated allocations
     */
    long indexBase();
}
