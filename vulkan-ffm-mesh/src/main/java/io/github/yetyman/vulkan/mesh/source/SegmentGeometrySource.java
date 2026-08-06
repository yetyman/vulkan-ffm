package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;

import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link GeometrySource} backed by one or more {@link MemorySegment}s with a known
 * {@link MeshLayout}. This is the primitive implementation everything else adapts to: a file-format
 * reader's buffer views, an arena-allocated procedural mesh, or a pre-packed vertex block all fit
 * this shape.
 *
 * <p>Because the layout is known, the upload path can detect the identity case (target layout equals
 * this source's native layout) and collapse all per-attribute transcoding into one flat copy per
 * stream.
 *
 * <p>Construct via the {@link Builder}.
 */
public final class SegmentGeometrySource implements GeometrySource {

    private final MeshLayout nativeLayout;
    private final long elementCount;
    private final PrimitiveTopology topology;
    private final AABB bounds;
    private final Map<AttributeSemantic, SegmentAttributeStream> streams;
    private final IndexStream indexStream;

    private SegmentGeometrySource(Builder b) {
        this.nativeLayout = b.layout;
        this.elementCount = b.elementCount;
        this.topology = b.topology;
        this.bounds = b.bounds;
        this.streams = Collections.unmodifiableMap(b.streams);
        this.indexStream = b.indexStream;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Set<AttributeSemantic> available() {
        return streams.keySet();
    }

    @Override
    public AttributeStream stream(AttributeSemantic semantic) {
        AttributeStream s = streams.get(semantic);
        if (s == null) throw new IllegalArgumentException("source does not have '" + semantic + "'");
        return s;
    }

    @Override
    public Optional<IndexStream> indices() {
        return Optional.ofNullable(indexStream);
    }

    @Override
    public long elementCount() {
        return elementCount;
    }

    @Override
    public PrimitiveTopology topology() {
        return topology;
    }

    @Override
    public AABB bounds() {
        return bounds;
    }

    @Override
    public Optional<MeshLayout> nativeLayout() {
        return Optional.of(nativeLayout);
    }

    /**
     * Builder for constructing a segment-backed geometry source.
     *
     * <p>Two construction modes:
     * <ul>
     *   <li>Provide a {@link MeshLayout} plus one backing segment per stream and let the builder
     *       derive streams from the layout's placements automatically.</li>
     *   <li>Provide individual pre-built {@link SegmentAttributeStream} instances for full control.</li>
     * </ul>
     */
    public static final class Builder {
        private MeshLayout layout;
        private long elementCount = -1;
        private PrimitiveTopology topology = PrimitiveTopology.TRIANGLE_LIST;
        private AABB bounds;
        private final Map<AttributeSemantic, SegmentAttributeStream> streams = new LinkedHashMap<>();
        private IndexStream indexStream;
        private final Map<Integer, MemorySegment> streamSegments = new LinkedHashMap<>();

        private Builder() {}

        /** Sets the native layout. Required. */
        public Builder layout(MeshLayout layout) {
            this.layout = layout;
            return this;
        }

        /** Sets the element (vertex) count. Required. */
        public Builder elementCount(long elementCount) {
            this.elementCount = elementCount;
            return this;
        }

        /** Sets the topology. Defaults to TRIANGLE_LIST. */
        public Builder topology(PrimitiveTopology topology) {
            this.topology = topology;
            return this;
        }

        /** Sets the bounds. Required. */
        public Builder bounds(AABB bounds) {
            this.bounds = bounds;
            return this;
        }

        /**
         * Provides the backing segment for a stream ID in the layout. All attributes placed in that
         * stream will derive their {@link SegmentAttributeStream} from this segment using the
         * layout's offsets and strides.
         */
        public Builder streamData(int streamId, MemorySegment data) {
            streamSegments.put(streamId, data);
            return this;
        }

        /** Provides an index stream. */
        public Builder indices(IndexStream indices) {
            this.indexStream = indices;
            return this;
        }

        /** Convenience: provides an index stream from a tightly-packed segment. */
        public Builder indices(IndexWidth width, long indexCount, MemorySegment data) {
            this.indexStream = new SegmentIndexStream(width, indexCount, data);
            return this;
        }

        /** Adds a pre-built attribute stream directly. */
        public Builder stream(SegmentAttributeStream stream) {
            streams.put(stream.semantic(), stream);
            return this;
        }

        public SegmentGeometrySource build() {
            if (layout == null) throw new IllegalStateException("layout required");
            if (elementCount < 0) throw new IllegalStateException("elementCount required");
            if (bounds == null) throw new IllegalStateException("bounds required");

            // Derive streams from layout + streamSegments for any semantic not already provided.
            for (MeshLayout.Placement p : layout.placements()) {
                if (streams.containsKey(p.semantic())) continue;
                MemorySegment seg = streamSegments.get(p.stream());
                if (seg == null) throw new IllegalStateException(
                        "no data provided for stream " + p.stream() + " (needed by '" + p.semantic() + "')");
                streams.put(p.semantic(), new SegmentAttributeStream(
                        p.semantic(), p.format(), elementCount, seg, p.offset(), layout.strideOf(p.stream())));
            }

            return new SegmentGeometrySource(this);
        }
    }
}
