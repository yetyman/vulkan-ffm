package io.github.yetyman.vulkan.mesh.consume;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.partition.GeometryPartition;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocation;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;

/**
 * The fixed 64-byte base record layout stored per partition in a {@link GeometryTable}.
 * Cache-line sized on most hardware.
 *
 * <pre>
 * offset  0: uint   vertexBase
 * offset  4: uint   indexBase
 * offset  8: uint   indexCount
 * offset 12: uint   vertexCount
 * offset 16: vec3   boundsMin  (12 bytes)
 * offset 28: uint   flags
 * offset 32: vec3   boundsMax  (12 bytes)
 * offset 44: uint   _pad
 * offset 48: uint64 tag
 * offset 56: uint64 sortKey
 * </pre>
 *
 * <p>This class provides static read/write methods for encoding and decoding records in this
 * layout. It does not own memory; it operates on caller-provided segments at specified offsets.</p>
 *
 * <p>Paradigm-specific per-partition data (meshlet cones, cluster error, terrain neighbour masks)
 * goes in attached metadata channels uploaded as parallel arrays, never in the base record.</p>
 *
 * @see GeometryTable
 */
public final class GeometryTableRecord {

    /** Bytes per record. Cache-line sized. */
    public static final int STRIDE = 64;

    // Field offsets within one record
    public static final int OFFSET_VERTEX_BASE = 0;
    public static final int OFFSET_INDEX_BASE = 4;
    public static final int OFFSET_INDEX_COUNT = 8;
    public static final int OFFSET_VERTEX_COUNT = 12;
    public static final int OFFSET_BOUNDS_MIN = 16;
    public static final int OFFSET_FLAGS = 28;
    public static final int OFFSET_BOUNDS_MAX = 32;
    public static final int OFFSET_PAD = 44;
    public static final int OFFSET_TAG = 48;
    public static final int OFFSET_SORT_KEY = 56;

    /** Flag: partition data is resident and usable. */
    public static final int FLAG_RESIDENT = 1;
    /** Flag: partition uses indexed drawing. */
    public static final int FLAG_INDEXED = 2;

    private GeometryTableRecord() {}

    /**
     * Writes a complete record into {@code dst} at the given record index.
     *
     * @param dst         destination segment (the CPU mirror or mapped buffer)
     * @param recordIndex zero-based record slot
     * @param allocation  the geometry's device allocation (provides vertex/index bases)
     * @param partition   the partition descriptor (provides bounds, topology, tag, sortKey)
     */
    public static void write(MemorySegment dst, int recordIndex,
                             GeometryAllocation allocation, GeometryPartition partition) {
        long base = (long) recordIndex * STRIDE;

        dst.set(JAVA_INT_UNALIGNED, base + OFFSET_VERTEX_BASE, (int) allocation.vertexBase());
        dst.set(JAVA_INT_UNALIGNED, base + OFFSET_INDEX_BASE, (int) allocation.indexBase());
        dst.set(JAVA_INT_UNALIGNED, base + OFFSET_INDEX_COUNT, (int) partition.indexCount());
        dst.set(JAVA_INT_UNALIGNED, base + OFFSET_VERTEX_COUNT, (int) partition.vertexCount());

        // boundsMin
        AABB bounds = partition.bounds();
        dst.set(JAVA_FLOAT_UNALIGNED, base + OFFSET_BOUNDS_MIN, bounds.min.x);
        dst.set(JAVA_FLOAT_UNALIGNED, base + OFFSET_BOUNDS_MIN + 4, bounds.min.y);
        dst.set(JAVA_FLOAT_UNALIGNED, base + OFFSET_BOUNDS_MIN + 8, bounds.min.z);

        // flags
        int flags = FLAG_RESIDENT;
        if (partition.isIndexed()) flags |= FLAG_INDEXED;
        dst.set(JAVA_INT_UNALIGNED, base + OFFSET_FLAGS, flags);

        // boundsMax
        dst.set(JAVA_FLOAT_UNALIGNED, base + OFFSET_BOUNDS_MAX, bounds.max.x);
        dst.set(JAVA_FLOAT_UNALIGNED, base + OFFSET_BOUNDS_MAX + 4, bounds.max.y);
        dst.set(JAVA_FLOAT_UNALIGNED, base + OFFSET_BOUNDS_MAX + 8, bounds.max.z);

        // padding
        dst.set(JAVA_INT_UNALIGNED, base + OFFSET_PAD, 0);

        // tag and sortKey
        dst.set(JAVA_LONG_UNALIGNED, base + OFFSET_TAG, partition.tag());
        dst.set(JAVA_LONG_UNALIGNED, base + OFFSET_SORT_KEY, partition.sortKey());
    }

    /**
     * Clears a record (zeros all 64 bytes). The GPU sees zeroed bounds/counts/flags.
     */
    public static void clear(MemorySegment dst, int recordIndex) {
        long base = (long) recordIndex * STRIDE;
        dst.asSlice(base, STRIDE).fill((byte) 0);
    }

    /**
     * Reads the flags field from a record.
     */
    public static int readFlags(MemorySegment src, int recordIndex) {
        return src.get(JAVA_INT_UNALIGNED, (long) recordIndex * STRIDE + OFFSET_FLAGS);
    }

    /**
     * Reads the vertex base from a record.
     */
    public static int readVertexBase(MemorySegment src, int recordIndex) {
        return src.get(JAVA_INT_UNALIGNED, (long) recordIndex * STRIDE + OFFSET_VERTEX_BASE);
    }

    /**
     * Reads the index base from a record.
     */
    public static int readIndexBase(MemorySegment src, int recordIndex) {
        return src.get(JAVA_INT_UNALIGNED, (long) recordIndex * STRIDE + OFFSET_INDEX_BASE);
    }

    /**
     * Reads the index count from a record.
     */
    public static int readIndexCount(MemorySegment src, int recordIndex) {
        return src.get(JAVA_INT_UNALIGNED, (long) recordIndex * STRIDE + OFFSET_INDEX_COUNT);
    }

    /**
     * Reads the vertex count from a record.
     */
    public static int readVertexCount(MemorySegment src, int recordIndex) {
        return src.get(JAVA_INT_UNALIGNED, (long) recordIndex * STRIDE + OFFSET_VERTEX_COUNT);
    }

    /**
     * Reads the tag from a record.
     */
    public static long readTag(MemorySegment src, int recordIndex) {
        return src.get(JAVA_LONG_UNALIGNED, (long) recordIndex * STRIDE + OFFSET_TAG);
    }

    /**
     * Reads the sort key from a record.
     */
    public static long readSortKey(MemorySegment src, int recordIndex) {
        return src.get(JAVA_LONG_UNALIGNED, (long) recordIndex * STRIDE + OFFSET_SORT_KEY);
    }

    /**
     * Updates just the flags field (e.g. to mark non-resident without clearing the record).
     */
    public static void writeFlags(MemorySegment dst, int recordIndex, int flags) {
        dst.set(JAVA_INT_UNALIGNED, (long) recordIndex * STRIDE + OFFSET_FLAGS, flags);
    }
}
