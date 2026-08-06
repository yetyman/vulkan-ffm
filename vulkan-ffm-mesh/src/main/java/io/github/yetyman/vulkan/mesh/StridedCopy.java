package io.github.yetyman.vulkan.mesh;

/**
 * One step in a transcode plan between two {@link MeshLayout}s for one attribute.
 *
 * <p>When source and target formats match, this is a pure strided memory copy and can be executed
 * by the bulk primitives on the typed buffers ({@code writeStrided}). When they differ, the
 * {@link #converter} is non-null and per-element conversion is needed.
 *
 * <p>A complete transcode plan for all shared attributes between two layouts is a list of these,
 * one per attribute, obtained from {@link MeshLayout#transcodeOps(MeshLayout, long, long)}.
 *
 * @param semantic        which attribute this step transcodes
 * @param srcStreamId     source stream index
 * @param srcOffset       byte offset of element 0 in the source stream
 * @param srcStride       bytes between elements in the source stream
 * @param dstStreamId     destination stream index
 * @param dstOffset       byte offset of element 0 in the destination stream
 * @param dstStride       bytes between elements in the destination stream
 * @param elementByteSize bytes per element that are copied (the smaller of src and dst format sizes
 *                        when no conversion; the source size when conversion is present)
 * @param converter       null when source and target formats match exactly (pure strided copy);
 *                        non-null when per-element format conversion is needed
 */
public record StridedCopy(
        AttributeSemantic semantic,
        int srcStreamId,
        long srcOffset,
        long srcStride,
        int dstStreamId,
        long dstOffset,
        long dstStride,
        int elementByteSize,
        FormatConverter converter
) {

    /**
     * @return true when this copy requires no per-element logic and can be dispatched as a bulk
     * strided memory copy
     */
    public boolean isPureCopy() {
        return converter == null;
    }

    /**
     * Per-element format conversion function. Reads one element from source, writes one converted
     * element into destination, both at explicit offsets.
     */
    @FunctionalInterface
    public interface FormatConverter {
        /**
         * Converts one element.
         *
         * @param src       source memory
         * @param srcOffset byte offset of the element in the source
         * @param dst       destination memory
         * @param dstOffset byte offset of the element in the destination
         */
        void convert(java.lang.foreign.MemorySegment src, long srcOffset,
                     java.lang.foreign.MemorySegment dst, long dstOffset);
    }
}
