package io.github.yetyman.vulkan.mesh.consume;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.BufferWriteScope;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.mesh.partition.GeometryPartition;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocation;

import java.lang.foreign.MemorySegment;
import java.util.BitSet;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;

/**
 * A GPU-resident per-partition descriptor table: an SSBO that a culling or LOD compute shader
 * indexes by partition ID to answer "where does partition N live, how big is it, what are its
 * bounds, what is its tag" without CPU involvement.
 *
 * <p>This is what makes maximum-throughput bulk rendering possible. GPU-resident vertex data alone
 * is not sufficient; the GPU also needs this metadata. A culling compute shader reads the table
 * and writes indirect draw arguments. The CPU records a fixed, scene-size-independent number of
 * commands.
 *
 * <p>The base record is deliberately small and fixed (64 bytes, cache-line sized on most hardware):
 * <pre>
 * offset 0:  uint  vertexBase
 * offset 4:  uint  indexBase
 * offset 8:  uint  indexCount
 * offset 12: uint  vertexCount
 * offset 16: vec3  boundsMin  (12 bytes)
 * offset 28: uint  flags
 * offset 32: vec3  boundsMax  (12 bytes)
 * offset 44: uint  _pad
 * offset 48: uint64 tag
 * offset 56: uint64 sortKey
 * </pre>
 *
 * <p>Paradigm-specific per-partition data (meshlet cones, cluster error, terrain neighbour masks)
 * goes in attached metadata channels uploaded as parallel arrays, never in the base record.
 *
 * <h2>Dirty tracking</h2>
 * Uses {@code DEVICE_LOCAL_MIRRORED} with deferred mode. Writes land in the CPU mirror
 * immediately and are flushed to the GPU via {@link #flush(VkQueue)}, which delegates to the
 * buffer's built-in dirty tracking and multi-region copy.
 */
public final class GeometryTable implements AutoCloseable {

    /** Bytes per base record. Cache-line sized. */
    public static final int RECORD_STRIDE = 64;

    /** Flag bits packed into the flags field. */
    public static final int FLAG_RESIDENT = 1;
    public static final int FLAG_INDEXED = 2;

    private final ManagedBuffer buffer;
    private final int capacity;
    private final BitSet occupied;
    private int count;

    /**
     * @param device   the device to allocate the backing SSBO on
     * @param queue    the transfer queue for uploads
     * @param capacity maximum number of partitions this table can hold
     */
    public GeometryTable(VkDevice device, VkQueue queue, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        long totalSize = (long) capacity * RECORD_STRIDE;
        IBuffer created = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL_MIRRORED, null,
                totalSize, BufferUsage.STORAGE, device, queue);
        if (!(created instanceof ManagedBuffer managed)) {
            throw new IllegalStateException("DEVICE_LOCAL_MIRRORED should produce a ManagedBuffer");
        }
        this.buffer = managed;
        this.buffer.setDeferred(true);
        this.occupied = new BitSet(capacity);
    }

    /**
     * Registers a partition, returning its stable index. The record is written into the CPU mirror
     * and marked dirty; call {@link #flush(VkQueue)} to upload.
     *
     * @return the partition's table index
     */
    public int register(GeometryAllocation allocation, GeometryPartition partition) {
        int slot = findFreeSlot();
        writeRecord(slot, allocation, partition);
        occupied.set(slot);
        count++;
        return slot;
    }

    /**
     * Removes a partition from the table. Its slot becomes available for reuse.
     */
    public void unregister(int partitionIndex) {
        checkSlot(partitionIndex);
        occupied.clear(partitionIndex);
        // Zero the record so GPU reads see zeroed bounds/counts.
        long offset = (long) partitionIndex * RECORD_STRIDE;
        try (BufferWriteScope scope = buffer.acquireWrite(offset, RECORD_STRIDE, null)) {
            scope.segment().fill((byte) 0);
        }
        count--;
    }

    /**
     * Updates a record in place, e.g. after defragmentation moves an allocation or bounds change.
     */
    public void update(int partitionIndex, GeometryAllocation allocation, GeometryPartition partition) {
        checkSlot(partitionIndex);
        writeRecord(partitionIndex, allocation, partition);
    }

    /**
     * Uploads dirty records to the GPU. Only the dirty ranges are transferred, coalesced by
     * the buffer's built-in dirty tracking strategy.
     *
     * @return a completion for the upload, or an already-complete token if nothing was dirty
     */
    public GpuCompletion flush(VkQueue queue) {
        return buffer.flushDirty(queue);
    }

    /**
     * @return the SSBO a culling or LOD shader binds
     */
    public IBuffer buffer() {
        return buffer;
    }

    /**
     * @return bytes per record
     */
    public int recordStride() {
        return RECORD_STRIDE;
    }

    /**
     * @return maximum number of partitions this table can hold
     */
    public int capacity() {
        return capacity;
    }

    /**
     * @return current number of registered partitions
     */
    public int count() {
        return count;
    }

    /**
     * @return true if the given slot is occupied
     */
    public boolean isOccupied(int slot) {
        return occupied.get(slot);
    }

    @Override
    public void close() {
        buffer.close();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void writeRecord(int slot, GeometryAllocation allocation, GeometryPartition partition) {
        long offset = (long) slot * RECORD_STRIDE;
        try (BufferWriteScope scope = buffer.acquireWrite(offset, RECORD_STRIDE, null)) {
            MemorySegment seg = scope.segment();
            seg.set(JAVA_INT_UNALIGNED, 0, (int) allocation.vertexBase());
            seg.set(JAVA_INT_UNALIGNED, 4, (int) allocation.indexBase());
            seg.set(JAVA_INT_UNALIGNED, 8, (int) partition.indexCount());
            seg.set(JAVA_INT_UNALIGNED, 12, (int) partition.vertexCount());
            // boundsMin
            seg.set(JAVA_FLOAT_UNALIGNED, 16, partition.bounds().min.x);
            seg.set(JAVA_FLOAT_UNALIGNED, 20, partition.bounds().min.y);
            seg.set(JAVA_FLOAT_UNALIGNED, 24, partition.bounds().min.z);
            // flags
            int flags = FLAG_RESIDENT | (partition.isIndexed() ? FLAG_INDEXED : 0);
            seg.set(JAVA_INT_UNALIGNED, 28, flags);
            // boundsMax
            seg.set(JAVA_FLOAT_UNALIGNED, 32, partition.bounds().max.x);
            seg.set(JAVA_FLOAT_UNALIGNED, 36, partition.bounds().max.y);
            seg.set(JAVA_FLOAT_UNALIGNED, 40, partition.bounds().max.z);
            // pad
            seg.set(JAVA_INT_UNALIGNED, 44, 0);
            // tag, sortKey
            seg.set(JAVA_LONG_UNALIGNED, 48, partition.tag());
            seg.set(JAVA_LONG_UNALIGNED, 56, partition.sortKey());
        }
    }

    private int findFreeSlot() {
        int slot = occupied.nextClearBit(0);
        if (slot >= capacity) throw new IllegalStateException("GeometryTable is full (capacity " + capacity + ")");
        return slot;
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= capacity)
            throw new IndexOutOfBoundsException("slot " + slot + " out of bounds for capacity " + capacity);
    }
}
