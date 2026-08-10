package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.util.Optional;

/**
 * N-buffered geometry allocator: wraps another {@link GeometryAllocator} and maintains one
 * allocation per frame slot. Each frame writes into a different slot, so the GPU can read from
 * the previous frame's slot while the CPU writes the current frame's slot without hazards.
 *
 * <p>This is the mesh-level analogue of {@code RingBuffer} in vulkan-core. Where {@code RingBuffer}
 * is a single {@link io.github.yetyman.vulkan.buffers.IBuffer} with N internal slots,
 * {@code RingAllocator} is N independent {@link GeometryAllocation}s obtained from a backing
 * allocator. This distinction matters because geometry allocations may live in shared pools and
 * have complex internal structure (multiple streams, index ranges, vertex bases), which a single
 * ring-buffered {@code IBuffer} cannot represent.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * RingAllocator ring = new RingAllocator(backing, 2); // 2 frames in flight
 * GeometryAllocation alloc = ring.allocate(layout, capacity, indexWidth, indexCapacity);
 *
 * // Each frame:
 * ring.advance(); // rotates to the next slot
 * GeometryAllocation current = ring.currentAllocation(alloc);
 * // write into current; previous frame's slot is still being read by the GPU
 * }</pre>
 *
 * <p>When used with {@link io.github.yetyman.vulkan.mesh.Mesh}, the mesh's binding must be
 * rebuilt each frame from {@link #currentAllocation}, or callers should bind the current slot's
 * buffers directly. This allocator is not transparent to consumers that cache bindings.
 */
public final class RingAllocator implements GeometryAllocator {

    private final GeometryAllocator backing;
    private final int slotCount;
    private int currentSlot;

    /**
     * @param backing   the underlying allocator that produces each slot's allocation
     * @param slotCount number of frame slots (typically 2 or 3 for frames in flight)
     */
    public RingAllocator(GeometryAllocator backing, int slotCount) {
        if (backing == null) throw new IllegalArgumentException("backing allocator required");
        if (slotCount < 2) throw new IllegalArgumentException("slotCount must be >= 2, got " + slotCount);
        this.backing = backing;
        this.slotCount = slotCount;
        this.currentSlot = 0;
    }

    /**
     * @return the number of frame slots
     */
    public int slotCount() {
        return slotCount;
    }

    /**
     * @return the current slot index (0-based)
     */
    public int currentSlot() {
        return currentSlot;
    }

    /**
     * Advances to the next frame slot. Call once per frame before writing geometry.
     */
    public void advance() {
        currentSlot = (currentSlot + 1) % slotCount;
    }

    /**
     * Allocates N slots of the given geometry, one per frame slot. Returns a
     * {@link RingAllocation} that holds all N underlying allocations and exposes the current
     * slot's ranges.
     */
    @Override
    public GeometryAllocation allocate(MeshLayout layout, long vertexCapacity,
                                       IndexWidth indexWidth, long indexCapacity) {
        GeometryAllocation[] slots = new GeometryAllocation[slotCount];
        for (int i = 0; i < slotCount; i++) {
            slots[i] = backing.allocate(layout, vertexCapacity, indexWidth, indexCapacity);
        }
        return new RingAllocation(slots);
    }

    @Override
    public void free(GeometryAllocation allocation) {
        if (allocation instanceof RingAllocation ring) {
            for (GeometryAllocation slot : ring.slots) {
                backing.free(slot);
            }
        }
    }

    @Override
    public IndexBaseMode indexBaseMode() {
        return backing.indexBaseMode();
    }

    /**
     * Returns the allocation for the current frame slot from a ring allocation.
     * Use this to get the correct binding/ranges for the current frame's writes.
     *
     * @param allocation a {@link RingAllocation} returned by this allocator's {@link #allocate}
     * @return the underlying allocation for the current slot
     */
    public GeometryAllocation currentAllocation(GeometryAllocation allocation) {
        if (allocation instanceof RingAllocation ring) {
            return ring.slots[currentSlot];
        }
        return allocation; // Not a ring allocation; pass through
    }

    /**
     * Returns the allocation for a specific slot from a ring allocation.
     *
     * @param allocation a {@link RingAllocation} returned by this allocator's {@link #allocate}
     * @param slot       the slot index
     * @return the underlying allocation for the specified slot
     */
    public GeometryAllocation slotAllocation(GeometryAllocation allocation, int slot) {
        if (allocation instanceof RingAllocation ring) {
            return ring.slots[slot];
        }
        return allocation;
    }

    @Override
    public void close() {
        // The ring allocator does not own the backing allocator - caller manages that.
        // Individual allocations are freed via free().
    }

    /**
     * Holds N allocations, one per frame slot. The {@link GeometryAllocation} interface methods
     * delegate to the current slot via the owning {@link RingAllocator}'s current slot index.
     * However, since this class does not hold a reference to the ring allocator, it delegates to
     * slot 0 when accessed via the interface directly. Prefer using
     * {@link RingAllocator#currentAllocation} for correct per-frame access.
     */
    static final class RingAllocation implements GeometryAllocation {
        final GeometryAllocation[] slots;

        RingAllocation(GeometryAllocation[] slots) {
            this.slots = slots;
        }

        @Override
        public DeviceRange vertexRange(int streamId) {
            // Default to slot 0 for interface-only access.
            // Callers should use RingAllocator.currentAllocation() instead.
            return slots[0].vertexRange(streamId);
        }

        @Override
        public Optional<DeviceRange> indexRange() {
            return slots[0].indexRange();
        }

        @Override
        public long vertexBase() {
            return slots[0].vertexBase();
        }

        @Override
        public long indexBase() {
            return slots[0].indexBase();
        }
    }
}
