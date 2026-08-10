package io.github.yetyman.vulkan.sample.mesh;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.ElementWindow;
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

/**
 * A non-indexed triangle-list ribbon that grows over time. Each segment is a quad (2 triangles)
 * giving the line visible thickness. Implements {@link MutableGeometrySource} so that only
 * newly-added vertices are uploaded.
 *
 * <p>Used to demonstrate:
 * <ul>
 *   <li>Category 2: count grows within capacity (live count increases, no realloc)</li>
 *   <li>Category 3: unbounded growth (eventually exceeds initial capacity, triggers realloc)</li>
 * </ul>
 */
public class GrowingStrokeSource implements MutableGeometrySource {

    private final float ribbonWidth;
    private float[] positions; // 3 per vertex (6 verts per segment = 2 triangles)
    private float[] colors;   // 3 per vertex
    private int segmentCount; // number of completed segments
    private int vertexCount;  // = segmentCount * 6

    // Track the spine points separately for generating the ribbon
    private float[] spineX;
    private float[] spineY;
    private float[] spineZ;
    private int spineCount;

    private boolean positionDirty = false;
    private boolean colorDirty = false;
    private ElementWindow positionDirtyWindow = ElementWindow.empty();
    private ElementWindow colorDirtyWindow = ElementWindow.empty();

    /**
     * @param maxSegments initial capacity in segments (each segment = 6 vertices)
     */
    public GrowingStrokeSource(int maxSegments) {
        this.ribbonWidth = 0.05f;
        this.positions = new float[maxSegments * 6 * 3];
        this.colors = new float[maxSegments * 6 * 3];
        this.spineX = new float[maxSegments + 1];
        this.spineY = new float[maxSegments + 1];
        this.spineZ = new float[maxSegments + 1];
        this.segmentCount = 0;
        this.vertexCount = 0;
        this.spineCount = 0;
    }

    /**
     * Adds a spine point. When there are at least 2 spine points, a new ribbon segment is formed.
     * Returns true if the backing arrays were grown (CPU-side reallocation).
     */
    public boolean addSpinePoint(float x, float y, float z, float r, float g, float b) {
        boolean realloced = false;
        if (spineCount >= spineX.length) {
            int newCap = spineX.length * 2;
            float[] nsx = new float[newCap]; System.arraycopy(spineX, 0, nsx, 0, spineCount); spineX = nsx;
            float[] nsy = new float[newCap]; System.arraycopy(spineY, 0, nsy, 0, spineCount); spineY = nsy;
            float[] nsz = new float[newCap]; System.arraycopy(spineZ, 0, nsz, 0, spineCount); spineZ = nsz;
        }
        spineX[spineCount] = x;
        spineY[spineCount] = y;
        spineZ[spineCount] = z;
        spineCount++;

        if (spineCount < 2) return false;

        // Build a ribbon segment from the last two spine points
        int newSegments = segmentCount + 1;
        int neededVerts = newSegments * 6;
        if (neededVerts * 3 > positions.length) {
            int newCap = Math.max(positions.length / 3 * 2, neededVerts + 6);
            float[] newPos = new float[newCap * 3];
            float[] newCol = new float[newCap * 3];
            System.arraycopy(positions, 0, newPos, 0, vertexCount * 3);
            System.arraycopy(colors, 0, newCol, 0, vertexCount * 3);
            positions = newPos;
            colors = newCol;
            realloced = true;
        }

        int i0 = spineCount - 2;
        int i1 = spineCount - 1;
        float dx = spineX[i1] - spineX[i0];
        float dy = spineY[i1] - spineY[i0];
        float dz = spineZ[i1] - spineZ[i0];
        // Perpendicular in XZ plane (for visible width)
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float px, pz;
        if (len > 1e-6f) {
            px = -dz / len * ribbonWidth;
            pz = dx / len * ribbonWidth;
        } else {
            px = ribbonWidth;
            pz = 0;
        }

        // Two triangles forming a quad
        int base = vertexCount * 3;
        // v0 = p0 - perp, v1 = p0 + perp, v2 = p1 - perp, v3 = p1 + perp
        float v0x = spineX[i0] - px, v0y = spineY[i0], v0z = spineZ[i0] - pz;
        float v1x = spineX[i0] + px, v1y = spineY[i0], v1z = spineZ[i0] + pz;
        float v2x = spineX[i1] - px, v2y = spineY[i1], v2z = spineZ[i1] - pz;
        float v3x = spineX[i1] + px, v3y = spineY[i1], v3z = spineZ[i1] + pz;

        // Triangle 1: v0, v2, v1
        positions[base]     = v0x; positions[base + 1] = v0y; positions[base + 2] = v0z;
        positions[base + 3] = v2x; positions[base + 4] = v2y; positions[base + 5] = v2z;
        positions[base + 6] = v1x; positions[base + 7] = v1y; positions[base + 8] = v1z;
        // Triangle 2: v1, v2, v3
        positions[base + 9]  = v1x; positions[base + 10] = v1y; positions[base + 11] = v1z;
        positions[base + 12] = v2x; positions[base + 13] = v2y; positions[base + 14] = v2z;
        positions[base + 15] = v3x; positions[base + 16] = v3y; positions[base + 17] = v3z;

        // Color all 6 vertices
        for (int v = 0; v < 6; v++) {
            colors[base + v * 3] = r;
            colors[base + v * 3 + 1] = g;
            colors[base + v * 3 + 2] = b;
        }

        int firstNewVert = vertexCount;
        vertexCount += 6;
        segmentCount++;

        ElementWindow w = new ElementWindow(firstNewVert, 6);
        positionDirty = true;
        positionDirtyWindow = positionDirtyWindow.union(w);
        colorDirty = true;
        colorDirtyWindow = colorDirtyWindow.union(w);

        return realloced;
    }

    public int liveCount() { return vertexCount; }
    public int segmentCount() { return segmentCount; }

    /** Resets to empty, marking all as dirty so the GPU buffer is cleared on next upload. */
    public void reset() {
        segmentCount = 0;
        vertexCount = 0;
        spineCount = 0;
        positionDirty = true;
        colorDirty = true;
        positionDirtyWindow = ElementWindow.empty();
        colorDirtyWindow = ElementWindow.empty();
    }

    // --- MutableGeometrySource ---

    @Override
    public boolean isDirty(AttributeSemantic semantic) {
        if (semantic.equals(AttributeSemantic.POSITION)) return positionDirty;
        if (semantic.equals(AttributeSemantic.COLOR(0))) return colorDirty;
        return false;
    }

    @Override
    public ElementWindow dirtyWindow(AttributeSemantic semantic) {
        if (semantic.equals(AttributeSemantic.POSITION)) return positionDirtyWindow;
        if (semantic.equals(AttributeSemantic.COLOR(0))) return colorDirtyWindow;
        return ElementWindow.empty();
    }

    @Override
    public void clearDirty(AttributeSemantic semantic) {
        if (semantic.equals(AttributeSemantic.POSITION)) {
            positionDirty = false;
            positionDirtyWindow = ElementWindow.empty();
        }
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
    public Optional<IndexStream> indices() { return Optional.empty(); }

    @Override
    public long elementCount() { return vertexCount; }

    @Override
    public PrimitiveTopology topology() { return PrimitiveTopology.TRIANGLE_LIST; }

    @Override
    public AABB bounds() {
        return new AABB(new Vec3(-1.5f, -1.5f, -1.5f), new Vec3(1.5f, 1.5f, 1.5f));
    }

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
            int count = (int) Math.min(elementCount, vertexCount - first);
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
            int count = (int) Math.min(elementCount, vertexCount - first);
            for (int i = first; i < first + count; i++) {
                dst.set(JAVA_FLOAT_UNALIGNED, pos, colors[i * 3]);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 4, colors[i * 3 + 1]);
                dst.set(JAVA_FLOAT_UNALIGNED, pos + 8, colors[i * 3 + 2]);
                pos += dstStride;
            }
        }
    };
}
