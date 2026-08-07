package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.mesh.DeviceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@link GeometryAllocation} over buffer ranges somebody else owns.
 *
 * <p>This is how geometry the CPU never produced enters the mesh system: a compute shader that
 * writes vertices, a simulation stepping positions in place, a skinning pass, or geometry imported
 * from another subsystem entirely. There is no upload, no allocator, and no ownership -- the ranges
 * are adopted as-is and are never freed by the mesh module.
 *
 * <p>Pair with {@code Mesh.builder().allocation(...)} to build a mesh over existing device memory.
 */
public final class ExternalAllocation implements GeometryAllocation {

    private final DeviceRange[] vertexRanges;
    private final DeviceRange indexRange;
    private final long vertexBase;
    private final long indexBase;

    private ExternalAllocation(DeviceRange[] vertexRanges, DeviceRange indexRange,
                               long vertexBase, long indexBase) {
        this.vertexRanges = vertexRanges;
        this.indexRange = indexRange;
        this.vertexBase = vertexBase;
        this.indexBase = indexBase;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience for the single-stream, non-indexed case.
     */
    public static ExternalAllocation of(DeviceRange vertexStream) {
        return builder().vertexRange(0, vertexStream).build();
    }

    @Override
    public DeviceRange vertexRange(int streamId) {
        if (streamId < 0 || streamId >= vertexRanges.length || vertexRanges[streamId] == null)
            throw new IndexOutOfBoundsException("no external vertex range for stream " + streamId);
        return vertexRanges[streamId];
    }

    @Override
    public Optional<DeviceRange> indexRange() {
        return Optional.ofNullable(indexRange);
    }

    @Override
    public long vertexBase() {
        return vertexBase;
    }

    @Override
    public long indexBase() {
        return indexBase;
    }

    public static final class Builder {
        private final List<DeviceRange> vertexRanges = new ArrayList<>();
        private DeviceRange indexRange;
        private long vertexBase = 0;
        private long indexBase = 0;

        private Builder() {}

        /** Sets the range backing a vertex stream. Streams may be declared in any order. */
        public Builder vertexRange(int streamId, DeviceRange range) {
            while (vertexRanges.size() <= streamId) vertexRanges.add(null);
            vertexRanges.set(streamId, range);
            return this;
        }

        /** Sets the range backing indices. */
        public Builder indexRange(DeviceRange range) {
            this.indexRange = range;
            return this;
        }

        /**
         * Sets the first vertex index within a shared pool, if these ranges are a slice of one.
         * Draws will carry this as their {@code vertexOffset}.
         */
        public Builder vertexBase(long vertexBase) {
            this.vertexBase = vertexBase;
            return this;
        }

        /** Sets the first index within a shared index pool, if applicable. */
        public Builder indexBase(long indexBase) {
            this.indexBase = indexBase;
            return this;
        }

        public ExternalAllocation build() {
            if (vertexRanges.isEmpty())
                throw new IllegalStateException("at least one vertex range required");
            return new ExternalAllocation(vertexRanges.toArray(new DeviceRange[0]),
                    indexRange, vertexBase, indexBase);
        }
    }
}
