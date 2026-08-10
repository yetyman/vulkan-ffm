package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.ElementWindow;
import io.github.yetyman.vulkan.mesh.IndexWidth;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;
import io.github.yetyman.vulkan.mesh.source.AttributeStream;
import io.github.yetyman.vulkan.mesh.source.IndexStream;
import io.github.yetyman.vulkan.mesh.source.MutableGeometrySource;
import io.github.yetyman.vulkan.mesh.source.Residency;

import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.Set;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * A flat grid whose vertex colors are mutable. Implements {@link MutableGeometrySource} with
 * dirty tracking on the COLOR(0) attribute. Used to demonstrate in-place attribute update
 * (category 1) and ring-buffered update (category 6).
 */
public class PaintableGridSource implements MutableGeometrySource {

    private final int gridSize;
    private final int vertexCount;
    private final int indexCount;
    private final float[] positions; // 3 per vertex
    private final float[] colors;    // 3 per vertex
    private final int[] indices;
    private final AABB bounds;

    private boolean colorDirty = false;
    private ElementWindow colorDirtyWindow = ElementWindow.empty();

    public PaintableGridSource(int gridSize) {
        this.gridSize = gridSize;
        this.vertexCount = (gridSize + 1) * (gridSize + 1);
        this.positions = new float[vertexCount * 3];
        this.colors = new float[vertexCount * 3];

        // Generate grid positions centered at origin
        float halfSize = gridSize / 2.0f;
        int vi = 0;
        for (int z = 0; z <= gridSize; z++) {
            for (int x = 0; x <= gridSize; x++) {
                positions[vi * 3] = (x - halfSize) / (float) gridSize * 2f;
                positions[vi * 3 + 1] = 0;
                positions[vi * 3 + 2] = (z - halfSize) / (float) gridSize * 2f;
                // Default color: white
                colors[vi * 3] = 0.8f;
                colors[vi * 3 + 1] = 0.8f;
                colors[vi * 3 + 2] = 0.8f;
                vi++;
            }
        }

        // Generate triangle indices
        this.indexCount = gridSize * gridSize * 6;
        this.indices = new int[indexCount];
        int ii = 0;
        int w = gridSize + 1;
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                int tl = z * w + x;
                int tr = tl + 1;
                int bl = (z + 1) * w + x;
                int br = bl + 1;
                indices[ii++] = tl; indices[ii++] = bl; indices[ii++] = tr;
                indices[ii++] = tr; indices[ii++] = bl; indices[ii++] = br;
            }
        }

        bounds = new AABB(new Vec3(-1, -0.5f, -1), new Vec3(1, 0.5f, 1));
    }

    /**
     * Paint a vertex color and mark dirty.
     */
    public void paintVertex(int index, float r, float g, float b) {
        if (index < 0 || index >= vertexCount) return;
        colors[index * 3] = r;
        colors[index * 3 + 1] = g;
        colors[index * 3 + 2] = b;
        markColorDirty(index);
    }

    /**
     * Paint all vertices based on a time-varying pattern.
     */
    public void paintWave(float time) {
        for (int i = 0; i < vertexCount; i++) {
            float x = positions[i * 3];
            float z = positions[i * 3 + 2];
            float wave = (float) (Math.sin(x * 4 + time * 2) * 0.5 + 0.5);
            float wave2 = (float) (Math.cos(z * 3 + time * 1.5) * 0.5 + 0.5);
            colors[i * 3] = wave;
            colors[i * 3 + 1] = wave2 * 0.7f;
            colors[i * 3 + 2] = 1.0f - wave * wave2;
        }
        colorDirty = true;
        colorDirtyWindow = ElementWindow.all(vertexCount);
    }

    private void markColorDirty(int index) {
        colorDirty = true;
        colorDirtyWindow = colorDirtyWindow.union(ElementWindow.single(index));
    }

    // --- MutableGeometrySource ---

    @Override
    public boolean isDirty(AttributeSemantic semantic) {
        if (semantic.equals(AttributeSemantic.COLOR(0))) return colorDirty;
        return false;
    }

    @Override
    public ElementWindow dirtyWindow(AttributeSemantic semantic) {
        if (semantic.equals(AttributeSemantic.COLOR(0))) return colorDirtyWindow;
        return ElementWindow.empty();
    }

    @Override
    public void clearDirty(AttributeSemantic semantic) {
        if (semantic.equals(AttributeSemantic.COLOR(0))) {
            colorDirty = false;
            colorDirtyWindow = ElementWindow.empty();
        }
    }

    // --- GeometrySource ---

    @Override
    public Set<AttributeSemantic> available() {
        return Set.of(AttributeSemantic.POSITION, AttributeSemantic.COLOR(0));
    }

    @Override
    public AttributeStream stream(AttributeSemantic semantic) {
        if (semantic.equals(AttributeSemantic.POSITION)) return posStream;
        if (semantic.equals(AttributeSemantic.COLOR(0))) return colorStream;
        throw new IllegalArgumentException("Unknown semantic: " + semantic);
    }

    @Override
    public Optional<IndexStream> indices() {
        return Optional.of(idxStream);
    }

    @Override
    public long elementCount() { return vertexCount; }

    @Override
    public PrimitiveTopology topology() { return PrimitiveTopology.TRIANGLE_LIST; }

    @Override
    public AABB bounds() { return bounds; }

    @Override
    public Optional<MeshLayout> nativeLayout() { return Optional.empty(); }

    // --- Streams ---

    private final AttributeStream posStream = new AttributeStream() {
        @Override public AttributeSemantic semantic() { return AttributeSemantic.POSITION; }
        @Override public AttributeFormat sourceFormat() { return AttributeFormat.F32x3; }
        @Override public long elementCount() { return vertexCount; }
        @Override public Residency residency() { return Residency.HOST; }
        @Override public boolean isHostReadable() { return true; }
        @Override public Optional<DeviceRange> deviceRange() { return Optional.empty(); }
        @Override
        public void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                                  long dstStride, long firstElement, long elementCount) {
            long pos = dstOffset;
            int first = (int) firstElement;
            int count = (int) elementCount;
            for (int i = first; i < first + count; i++) {
                dst.set(JAVA_FLOAT_UNALIGNED, pos, positions[i * 3]);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 4, positions[i * 3 + 1]);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 8, positions[i * 3 + 2]);
                pos += dstStride;
            }
        }
    };

    private final AttributeStream colorStream = new AttributeStream() {
        @Override public AttributeSemantic semantic() { return AttributeSemantic.COLOR(0); }
        @Override public AttributeFormat sourceFormat() { return AttributeFormat.F32x3; }
        @Override public long elementCount() { return vertexCount; }
        @Override public Residency residency() { return Residency.HOST; }
        @Override public boolean isHostReadable() { return true; }
        @Override public Optional<DeviceRange> deviceRange() { return Optional.empty(); }
        @Override
        public void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                                  long dstStride, long firstElement, long elementCount) {
            long pos = dstOffset;
            int first = (int) firstElement;
            int count = (int) elementCount;
            for (int i = first; i < first + count; i++) {
                dst.set(JAVA_FLOAT_UNALIGNED, pos, colors[i * 3]);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 4, colors[i * 3 + 1]);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 8, colors[i * 3 + 2]);
                pos += dstStride;
            }
        }
    };

    private final IndexStream idxStream = new IndexStream() {
        @Override public IndexWidth sourceWidth() { return IndexWidth.U32; }
        @Override public long indexCount() { return indexCount; }
        @Override public Residency residency() { return Residency.HOST; }
        @Override public boolean isHostReadable() { return true; }
        @Override public Optional<DeviceRange> deviceRange() { return Optional.empty(); }
        @Override
        public void transcodeInto(IndexWidth targetWidth, long vertexBaseOffset,
                                  MemorySegment dst, long dstOffset,
                                  long firstIndex, long indexCount) {
            long pos = dstOffset;
            int first = (int) firstIndex;
            int count = (int) indexCount;
            int dstByte = targetWidth.byteSize();
            for (int i = first; i < first + count; i++) {
                long value = Integer.toUnsignedLong(indices[i]) + vertexBaseOffset;
                switch (targetWidth) {
                    case U16 -> dst.set(java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED, pos, (short) value);
                    case U32 -> dst.set(JAVA_INT_UNALIGNED, pos, (int) value);
                    default -> dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, pos, (byte) value);
                }
                pos += dstByte;
            }
        }
    };
}
