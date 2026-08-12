package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

/**
 * An {@link AttributeStream} backed by a procedural generator function that writes elements
 * directly into the caller's destination memory on demand.
 *
 * <p>This is the stream type for procedural geometry (marching cubes, parametric surfaces,
 * L-systems, terrain heightmaps) where the data does not exist ahead of time and materializing
 * it into an intermediate host array would be wasteful. The generator writes directly into the
 * destination segment obtained from {@code IBuffer.acquireWrite}, achieving the one-copy
 * file-to-VRAM invariant even for procedural sources.</p>
 *
 * <p>The generator function receives the destination segment, the offset and stride for writing,
 * and the element window, so it can be parallelized across threads the same way any other
 * stream can.</p>
 *
 * <p>Residency is always {@link Residency#HOST} because the generator can always produce data
 * on demand. If caching generated results is desired, wrap with a {@link SegmentAttributeStream}
 * after generation.</p>
 */
public final class GeneratedAttributeStream implements AttributeStream {

    /**
     * A generator function that writes attribute elements directly into a destination segment.
     */
    @FunctionalInterface
    public interface Generator {
        /**
         * Generates elements and writes them into {@code dst}.
         *
         * @param dst          destination segment to write into
         * @param dstOffset    byte offset of the first element in {@code dst}
         * @param dstStride    bytes between consecutive elements in the destination
         * @param firstElement first element index to generate (0-based)
         * @param elementCount number of elements to generate
         */
        void generate(MemorySegment dst, long dstOffset, long dstStride,
                      long firstElement, long elementCount);
    }

    private final AttributeSemantic semantic;
    private final AttributeFormat format;
    private final long elementCount;
    private final Generator generator;

    /**
     * @param semantic     which attribute this stream carries
     * @param format       how each element is encoded when written by the generator
     * @param elementCount total number of elements the generator can produce
     * @param generator    the function that writes elements into a destination
     */
    public GeneratedAttributeStream(AttributeSemantic semantic, AttributeFormat format,
                                    long elementCount, Generator generator) {
        if (semantic == null) throw new IllegalArgumentException("semantic required");
        if (format == null) throw new IllegalArgumentException("format required");
        if (generator == null) throw new IllegalArgumentException("generator required");
        if (elementCount < 0) throw new IllegalArgumentException("elementCount must be >= 0");
        this.semantic = semantic;
        this.format = format;
        this.elementCount = elementCount;
        this.generator = generator;
    }

    @Override
    public AttributeSemantic semantic() {
        return semantic;
    }

    @Override
    public AttributeFormat sourceFormat() {
        return format;
    }

    @Override
    public long elementCount() {
        return elementCount;
    }

    @Override
    public Residency residency() {
        return Residency.HOST;
    }

    @Override
    public boolean isHostReadable() {
        return true;
    }

    @Override
    public Optional<DeviceRange> deviceRange() {
        return Optional.empty();
    }

    /**
     * Invokes the generator to write elements directly into {@code dst}. The generator writes
     * in the source format; format conversion is not applied here (it would need to be handled
     * by wrapping or composing generators for different target formats).
     *
     * <p>When the target format matches the source format, the generator output lands directly
     * in the final destination with no intermediate copy.</p>
     */
    @Override
    public void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                              long dstStride, long firstElement, long elementCount) {
        if (firstElement < 0 || firstElement + elementCount > this.elementCount)
            throw new IndexOutOfBoundsException("element window [" + firstElement + ", "
                    + (firstElement + elementCount) + ") exceeds elementCount " + this.elementCount);

        AttributeFormat targetFormat = targetLayout.formatOf(semantic);
        if (!format.equals(targetFormat)) {
            // For generated streams, the generator produces in its declared format.
            // If the target wants a different format, we would need a conversion wrapper.
            if (format.byteSize() != targetFormat.byteSize()
                    || format.componentType() != targetFormat.componentType()
                    || format.componentCount() != targetFormat.componentCount()
                    || format.normalized() != targetFormat.normalized()) {
                throw new UnsupportedOperationException(
                        "Format conversion from " + format + " to " + targetFormat
                        + " not yet supported for GeneratedAttributeStream. "
                        + "The generator must produce data in the target format, or use a "
                        + "converting wrapper. Semantic: '" + semantic.name() + "'");
            }
        }

        generator.generate(dst, dstOffset, dstStride, firstElement, elementCount);
    }

    /**
     * @return the backing generator, for callers that need direct access
     */
    public Generator generator() {
        return generator;
    }
}
