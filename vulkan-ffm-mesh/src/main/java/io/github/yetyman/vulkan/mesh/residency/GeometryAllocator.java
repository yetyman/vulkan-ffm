package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;

/**
 * Allocates GPU buffer space for geometry. Different implementations provide different tradeoffs:
 * one buffer per mesh (simple, debuggable), slab suballocation (O(1) fixed-size), global pools
 * (single bind for the whole scene), or sparse-bound virtual pools (streaming with page-level
 * eviction).
 *
 * <p>This is the interface that makes meshlet-pool rendering a configuration choice rather than
 * a rewrite. The mesh module's Phase 5 falsification test is: swapping {@code DedicatedAllocator}
 * for {@code PoolAllocator} must require no changes above this interface.
 */
public interface GeometryAllocator extends AutoCloseable {

    /**
     * Reserves space for one geometry.
     *
     * <p>The counts are <em>capacities</em>, not live counts. Reserving headroom lets a mesh grow
     * later with an upload instead of a reallocation; {@code Mesh} tracks how much of the capacity
     * is currently live and derives draw ranges from that.
     *
     * @param layout         the layout the geometry will be uploaded in (determines streams and strides)
     * @param vertexCapacity maximum number of vertices this allocation must hold
     * @param indexWidth     width of each index, or null if not indexed
     * @param indexCapacity  maximum number of indices this allocation must hold (0 if not indexed)
     * @return the allocation describing where streams and indices will live
     */
    GeometryAllocation allocate(MeshLayout layout, long vertexCapacity,
                                IndexWidth indexWidth, long indexCapacity);

    /**
     * Releases a previously allocated region. The allocator may reuse the space immediately
     * (slab, pool) or defer reclamation until a future defragmentation pass.
     *
     * <p>Freeing an allocation that submitted-but-unfinished command buffers still reference is a
     * use-after-free. Route frees through a {@link RetireQueue} when frames may still be in flight.
     */
    void free(GeometryAllocation allocation);

    /**
     * @return how this allocator handles index values relative to shared vertex pools.
     * Dedicated allocators use {@link IndexBaseMode#RELATIVE_WITH_DRAW_OFFSET} because there is
     * no shared pool. Pool allocators choose based on whether they want merged draws.
     */
    IndexBaseMode indexBaseMode();

    @Override
    void close();
}
