package io.github.yetyman.vulkan.mesh.residency;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A stable identity for one piece of geometry across its lifetime, independent of where its bytes
 * currently live. Residency tracking, streaming, and eviction all key off this rather than off a
 * {@code GeometryAllocation} or table slot, because both of those are expected to change: an
 * allocation moves under defragmentation, a table slot is reused after eviction. The id does not.
 *
 * <p>Deliberately opaque and comparable only by identity/equality, not by any encoded meaning. Do
 * not parse it, do not expect ordering to mean anything, do not persist it across process restarts
 * unless the issuing code guarantees it (this default implementation does not).
 */
public final class GeometryId {

    private static final AtomicLong NEXT = new AtomicLong(1);

    private final long value;

    private GeometryId(long value) {
        this.value = value;
    }

    /**
     * @return a fresh id, unique within this process
     */
    public static GeometryId create() {
        return new GeometryId(NEXT.getAndIncrement());
    }

    /**
     * Wraps an externally-assigned value, for callers that already have a stable identifier (e.g. a
     * database primary key or a content hash) and want to avoid double bookkeeping. No uniqueness
     * check is performed; the caller is responsible for not colliding with {@link #create()} or
     * with other wrapped values it did not itself assign.
     */
    public static GeometryId of(long externalValue) {
        return new GeometryId(externalValue);
    }

    /**
     * @return the raw value, for callers that need to log or index by it externally
     */
    public long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GeometryId other && other.value == value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "GeometryId[" + value + "]";
    }
}
