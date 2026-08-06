package io.github.yetyman.vulkan.mesh;

import io.github.yetyman.vulkan.highlevel.VkVertexFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps each attribute onto a stream, a byte offset within that stream's element, and that stream's
 * stride. One type covers every arrangement anyone uses:
 *
 * <table border="1">
 *   <caption>Arrangements</caption>
 *   <tr><th>Arrangement</th><th>How it is expressed</th></tr>
 *   <tr><td>Fully interleaved</td><td>all attributes in stream 0 at distinct offsets, one shared stride</td></tr>
 *   <tr><td>Fully planar (SoA)</td><td>each attribute in its own stream, stride equal to its own size</td></tr>
 *   <tr><td>Hybrid</td><td>position alone in stream 0, everything else interleaved in stream 1</td></tr>
 *   <tr><td>Instanced</td><td>an additional stream marked {@link InputRate#INSTANCE}</td></tr>
 * </table>
 *
 * <p>The hybrid case is not exotic. A position-only stream is what depth prepasses, shadow passes,
 * and meshlet cone culling all want, because it keeps the bytes they touch dense.
 *
 * <p>A layout is a property of a placement, not of a type. Nothing in this module serializes itself;
 * a layout describes where bytes go, and derivations toward specific consumers (vertex input state,
 * storage buffer bindings, transcode operations) are pure functions on the layout, so adding a new
 * consumer is a new function rather than a new field.
 *
 * <p>Planned: {@code transcodeOps(MeshLayout src, long firstElement, long elementCount)} returning
 * the strided copies that convert one layout to another. See {@code plans/mesh/01-vocabulary.md}.
 */
public final class MeshLayout {

    /**
     * The placement of one attribute: which stream, at what offset within that stream's element,
     * in what format.
     */
    public record Placement(AttributeSemantic semantic, AttributeFormat format, int stream, long offset) {
    }

    private final Map<AttributeSemantic, Placement> placements;
    private final long[] strides;
    private final InputRate[] rates;

    private MeshLayout(Map<AttributeSemantic, Placement> placements, long[] strides, InputRate[] rates) {
        this.placements = placements;
        this.strides = strides;
        this.rates = rates;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a single-stream packed interleaved layout, attributes placed in iteration order.
     * Pass a {@link LinkedHashMap} to control the order.
     */
    public static MeshLayout interleaved(Map<AttributeSemantic, AttributeFormat> attributes) {
        Builder b = builder().stream(0);
        attributes.forEach(b::attribute);
        return b.build();
    }

    /**
     * Creates a fully planar layout: one stream per attribute, each stream tightly packed.
     * Streams are numbered in iteration order.
     */
    public static MeshLayout planar(Map<AttributeSemantic, AttributeFormat> attributes) {
        Builder b = builder();
        int stream = 0;
        for (var e : attributes.entrySet()) {
            b.stream(stream++).attribute(e.getKey(), e.getValue());
        }
        return b.build();
    }

    /**
     * @return every attribute this layout places, in placement order
     */
    public Set<AttributeSemantic> semantics() {
        return Collections.unmodifiableSet(placements.keySet());
    }

    /**
     * @return the placements, in placement order
     */
    public List<Placement> placements() {
        return List.copyOf(placements.values());
    }

    /**
     * @return true if this layout places {@code semantic}
     */
    public boolean has(AttributeSemantic semantic) {
        return placements.containsKey(semantic);
    }

    /**
     * @return the placement of {@code semantic}
     * @throws IllegalArgumentException if this layout does not place it
     */
    public Placement placementOf(AttributeSemantic semantic) {
        Placement p = placements.get(semantic);
        if (p == null) throw new IllegalArgumentException("layout does not place '" + semantic + "'");
        return p;
    }

    public AttributeFormat formatOf(AttributeSemantic semantic) {
        return placementOf(semantic).format();
    }

    public int streamOf(AttributeSemantic semantic) {
        return placementOf(semantic).stream();
    }

    /**
     * @return byte offset of {@code semantic} within its stream's element
     */
    public long offsetOf(AttributeSemantic semantic) {
        return placementOf(semantic).offset();
    }

    /**
     * @return bytes between consecutive elements of stream {@code streamId}
     */
    public long strideOf(int streamId) {
        checkStream(streamId);
        return strides[streamId];
    }

    public InputRate inputRateOf(int streamId) {
        checkStream(streamId);
        return rates[streamId];
    }

    public int streamCount() {
        return strides.length;
    }

    /**
     * @return byte offset of element {@code elementIndex} of {@code semantic} within its stream
     */
    public long elementOffset(AttributeSemantic semantic, long elementIndex) {
        Placement p = placementOf(semantic);
        return p.offset() + elementIndex * strides[p.stream()];
    }

    /**
     * @return total bytes stream {@code streamId} occupies for {@code elementCount} elements
     */
    public long streamByteSize(int streamId, long elementCount) {
        checkStream(streamId);
        return strides[streamId] * elementCount;
    }

    /**
     * @return the attributes placed in {@code streamId}, in placement order
     */
    public List<Placement> placementsIn(int streamId) {
        checkStream(streamId);
        List<Placement> out = new ArrayList<>();
        for (Placement p : placements.values()) {
            if (p.stream() == streamId) out.add(p);
        }
        return out;
    }

    /**
     * @return the semantics this layout places that cannot be bound as vertex input, because their
     * format has no {@code VkFormat}. These must be consumed as storage buffer ranges instead.
     * A non-empty result is a normal situation, not an error.
     */
    public Set<AttributeSemantic> shaderDecodedSemantics() {
        LinkedHashMap<AttributeSemantic, Boolean> out = new LinkedHashMap<>();
        for (Placement p : placements.values()) {
            if (!p.format().isVertexInputCapable()) out.put(p.semantic(), Boolean.TRUE);
        }
        return Collections.unmodifiableSet(out.keySet());
    }

    /**
     * Derives a pipeline vertex input description for the subset of attributes that have a real
     * {@code VkFormat}.
     *
     * <p>The semantic-to-location mapping is supplied by the caller because location assignment is a
     * shader contract, not a geometry property. Semantics absent from the map are skipped, as are
     * semantics whose format is shader-decoded; use {@link #shaderDecodedSemantics()} to find those
     * and bind them as storage buffers instead.
     *
     * <p>Only streams that contribute at least one bound attribute are declared, so a layout that is
     * only partly expressible as vertex input still produces a valid description of that part.
     */
    public VkVertexFormat toVertexFormat(Map<AttributeSemantic, Integer> semanticToLocation) {
        VkVertexFormat.Builder b = VkVertexFormat.builder();
        boolean[] streamUsed = new boolean[strides.length];
        List<Placement> bound = new ArrayList<>();
        for (Placement p : placements.values()) {
            Integer location = semanticToLocation.get(p.semantic());
            if (location == null) continue;
            if (!p.format().isVertexInputCapable()) continue;
            streamUsed[p.stream()] = true;
            bound.add(p);
        }
        for (int s = 0; s < strides.length; s++) {
            if (!streamUsed[s]) continue;
            b.binding(s, (int) strides[s], rates[s].vkValue());
        }
        for (Placement p : bound) {
            b.attribute(semanticToLocation.get(p.semantic()), p.stream(),
                    p.format().vertexInputFormat().orElseThrow(), (int) p.offset());
        }
        return b.build();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MeshLayout[");
        for (int s = 0; s < strides.length; s++) {
            if (s > 0) sb.append("; ");
            sb.append("stream ").append(s).append(" (stride ").append(strides[s])
                    .append(", ").append(rates[s]).append("): ");
            List<Placement> in = placementsIn(s);
            for (int i = 0; i < in.size(); i++) {
                if (i > 0) sb.append(", ");
                Placement p = in.get(i);
                sb.append(p.semantic()).append('@').append(p.offset()).append(':').append(p.format());
            }
        }
        return sb.append(']').toString();
    }

    private void checkStream(int streamId) {
        if (streamId < 0 || streamId >= strides.length)
            throw new IndexOutOfBoundsException("stream " + streamId + " out of bounds for streamCount " + strides.length);
    }

    /**
     * Fluent builder. Attributes are added to the stream selected by the most recent
     * {@link #stream(int)} or {@link #instanceStream(int)} call; stream 0 per-vertex is the default.
     *
     * <p>Offsets are assigned sequentially within a stream unless given explicitly by
     * {@link #attributeAt}. Each stream's stride defaults to the end of its last attribute unless
     * set explicitly by {@link #stride}.
     */
    public static final class Builder {

        private final LinkedHashMap<AttributeSemantic, Placement> placements = new LinkedHashMap<>();
        private final Map<Integer, Long> nextOffset = new LinkedHashMap<>();
        private final Map<Integer, Long> explicitStride = new LinkedHashMap<>();
        private final Map<Integer, InputRate> streamRates = new LinkedHashMap<>();
        private int currentStream = 0;

        private Builder() {
            streamRates.put(0, InputRate.VERTEX);
        }

        /** Selects stream {@code id} for subsequent attributes, at per-vertex rate. */
        public Builder stream(int id) {
            if (id < 0) throw new IllegalArgumentException("stream id must be >= 0");
            currentStream = id;
            streamRates.putIfAbsent(id, InputRate.VERTEX);
            return this;
        }

        /** Selects stream {@code id} for subsequent attributes, at per-instance rate. */
        public Builder instanceStream(int id) {
            if (id < 0) throw new IllegalArgumentException("stream id must be >= 0");
            currentStream = id;
            streamRates.put(id, InputRate.INSTANCE);
            return this;
        }

        /** Adds an attribute to the current stream at the next free offset. */
        public Builder attribute(AttributeSemantic semantic, AttributeFormat format) {
            long offset = nextOffset.getOrDefault(currentStream, 0L);
            return attributeAt(semantic, format, offset);
        }

        /** Adds an attribute to the current stream at an explicit offset. */
        public Builder attributeAt(AttributeSemantic semantic, AttributeFormat format, long offset) {
            if (semantic == null) throw new IllegalArgumentException("semantic required");
            if (format == null) throw new IllegalArgumentException("format required");
            if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
            if (placements.containsKey(semantic))
                throw new IllegalArgumentException("'" + semantic + "' is already placed in this layout");
            placements.put(semantic, new Placement(semantic, format, currentStream, offset));
            long end = offset + format.byteSize();
            nextOffset.merge(currentStream, end, Math::max);
            return this;
        }

        /** Sets an explicit stride for a stream, for matching an external layout with padding. */
        public Builder stride(int streamId, long stride) {
            if (stride < 0) throw new IllegalArgumentException("stride must be >= 0");
            explicitStride.put(streamId, stride);
            return this;
        }

        public MeshLayout build() {
            if (placements.isEmpty()) throw new IllegalStateException("layout places no attributes");
            int maxStream = 0;
            for (Placement p : placements.values()) maxStream = Math.max(maxStream, p.stream());
            for (Integer s : explicitStride.keySet()) maxStream = Math.max(maxStream, s);

            long[] strides = new long[maxStream + 1];
            InputRate[] rates = new InputRate[maxStream + 1];
            for (int s = 0; s <= maxStream; s++) {
                Long explicit = explicitStride.get(s);
                long packed = nextOffset.getOrDefault(s, 0L);
                if (explicit != null) {
                    if (explicit < packed)
                        throw new IllegalStateException("explicit stride " + explicit + " for stream " + s
                                + " is smaller than its packed size " + packed);
                    strides[s] = explicit;
                } else {
                    strides[s] = packed;
                }
                rates[s] = streamRates.getOrDefault(s, InputRate.VERTEX);
            }
            return new MeshLayout(new LinkedHashMap<>(placements), strides, rates);
        }
    }
}
