package io.github.yetyman.vulkan.mesh.consume;

import io.github.yetyman.vulkan.highlevel.VkVertexFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.residency.GeometryAllocation;

import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.Optional;

/**
 * The resolved answer to "which buffers, at which offsets and strides, for which semantics".
 *
 * <p>Does not bind. Exposes what is needed to bind. The same binding object serves the vertex-input
 * path, the vertex-pulling path, and the mesh-shader path, because all three want the same
 * underlying facts in different shapes.
 */
public final class GeometryBinding {

    private final MeshLayout layout;
    private final GeometryAllocation allocation;
    private final IndexWidth indexWidth;

    /**
     * @param layout      the layout the geometry was uploaded in
     * @param allocation  where the geometry's data lives
     * @param indexWidth  width of index elements, or null if not indexed
     */
    public GeometryBinding(MeshLayout layout, GeometryAllocation allocation, IndexWidth indexWidth) {
        if (layout == null) throw new IllegalArgumentException("layout required");
        if (allocation == null) throw new IllegalArgumentException("allocation required");
        this.layout = layout;
        this.allocation = allocation;
        this.indexWidth = indexWidth;
    }

    public MeshLayout layout() {
        return layout;
    }

    // -------------------------------------------------------------------------
    // Vertex-input path
    // -------------------------------------------------------------------------

    /**
     * @return buffer handles for vertex binding, one per stream. Streams without data are NULL.
     */
    public MemorySegment[] vertexBufferHandles() {
        int count = layout.streamCount();
        MemorySegment[] handles = new MemorySegment[count];
        for (int s = 0; s < count; s++) {
            handles[s] = allocation.vertexRange(s).buffer().handle();
        }
        return handles;
    }

    /**
     * @return byte offsets for vertex binding, one per stream.
     */
    public long[] vertexBufferOffsets() {
        int count = layout.streamCount();
        long[] offsets = new long[count];
        for (int s = 0; s < count; s++) {
            offsets[s] = allocation.vertexRange(s).offset();
        }
        return offsets;
    }

    /**
     * @return the index buffer handle, or empty if not indexed
     */
    public Optional<MemorySegment> indexBufferHandle() {
        return allocation.indexRange().map(r -> r.buffer().handle());
    }

    /**
     * @return byte offset into the index buffer, or 0 if not indexed
     */
    public long indexBufferOffset() {
        return allocation.indexRange().map(DeviceRange::offset).orElse(0L);
    }

    /**
     * @return index element width, or null if not indexed
     */
    public IndexWidth indexWidth() {
        return indexWidth;
    }

    /**
     * Derives a pipeline vertex input description for attributes that have a real VkFormat.
     *
     * @param semanticToLocation shader location assignments
     */
    public VkVertexFormat vertexFormat(Map<AttributeSemantic, Integer> semanticToLocation) {
        return layout.toVertexFormat(semanticToLocation);
    }

    // -------------------------------------------------------------------------
    // Vertex-pulling / mesh-shader path
    // -------------------------------------------------------------------------

    /**
     * @return the device range for the given attribute, for storage-buffer binding
     */
    public DeviceRange rangeOf(AttributeSemantic semantic) {
        int streamId = layout.streamOf(semantic);
        DeviceRange fullStream = allocation.vertexRange(streamId);
        long attrOffset = layout.offsetOf(semantic);
        return new DeviceRange(fullStream.buffer(), fullStream.offset() + attrOffset,
                fullStream.size() - attrOffset, layout.strideOf(streamId));
    }
}
