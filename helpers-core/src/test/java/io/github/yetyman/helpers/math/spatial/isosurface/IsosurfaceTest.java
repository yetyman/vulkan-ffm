package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;
import io.github.yetyman.helpers.math.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IsosurfaceTest {

    // Sphere SDF: negative inside, positive outside
    private static final ScalarField3D SPHERE = (x, y, z) -> (float) Math.sqrt(x*x + y*y + z*z) - 1f;
    // Circle SDF for 2D
    private static final ScalarField2D CIRCLE = (x, y) -> (float) Math.sqrt(x*x + y*y) - 1f;

    @Test void marchingCubesProducesTriangles() {
        MeshOutput mesh = MarchingCubes.extract(SPHERE, new Vec3(-2,-2,-2), new Vec3(2,2,2), 10, 10, 10, 0f);
        assertTrue(mesh.vertexCount() > 0, "Expected vertices");
        assertTrue(mesh.indexCount() > 0, "Expected indices");
        assertEquals(0, mesh.indexCount() % 3, "Indices should be multiple of 3");
    }

    @Test void marchingCubesNoSurfaceOutside() {
        // Field always positive (outside) in sampled region
        ScalarField3D outsideOnly = (x, y, z) -> 10f;
        MeshOutput mesh = MarchingCubes.extract(outsideOnly, new Vec3(0,0,0), new Vec3(1,1,1), 5, 5, 5, 0f);
        assertEquals(0, mesh.vertexCount());
        assertEquals(0, mesh.indexCount());
    }

    @Test void marchingSquaresProducesContour() {
        ContourOutput contour = MarchingSquares.extract(CIRCLE, new Vec2(-2,-2), new Vec2(2,2), 20, 20, 0f);
        assertTrue(contour.vertices().size() > 0, "Expected vertices");
        assertTrue(contour.segments().size() > 0, "Expected segments");
    }

    @Test void marchingSquaresNoContourOutside() {
        ScalarField2D outside = (x, y) -> 10f;
        ContourOutput contour = MarchingSquares.extract(outside, new Vec2(0,0), new Vec2(1,1), 5, 5, 0f);
        assertEquals(0, contour.vertices().size());
    }

    @Test void surfaceNetsProducesMesh() {
        MeshOutput mesh = SurfaceNets.extract(SPHERE, new Vec3(-2,-2,-2), new Vec3(2,2,2), 10, 10, 10, 0f);
        assertTrue(mesh.vertexCount() > 0, "Expected vertices");
        // Surface nets may produce fewer triangles than marching cubes
    }

    @Test void dualContouringProducesMesh() {
        MeshOutput mesh = DualContouring.extract(SPHERE, new Vec3(-2,-2,-2), new Vec3(2,2,2), 10, 10, 10, 0f);
        assertTrue(mesh.vertexCount() > 0, "Expected vertices");
    }

    @Test void marchingTetrahedraProducesMesh() {
        MeshOutput mesh = MarchingTetrahedra.extract(SPHERE, new Vec3(-2,-2,-2), new Vec3(2,2,2), 10, 10, 10, 0f);
        assertTrue(mesh.vertexCount() > 0, "Expected vertices");
        assertTrue(mesh.indexCount() > 0, "Expected indices");
        assertEquals(0, mesh.indexCount() % 3, "Indices should be multiple of 3");
    }

    @Test void meshOutputByteSize() {
        MeshOutput mesh = MarchingCubes.extract(SPHERE, new Vec3(-2,-2,-2), new Vec3(2,2,2), 5, 5, 5, 0f);
        assertEquals(mesh.vertexCount() * 12 + mesh.indexCount() * 4, mesh.gpuByteSize());
        assertEquals(mesh.vertexCount() * 12, mesh.vertexByteSize());
        assertEquals(mesh.indexCount() * 4, mesh.indexByteSize());
    }

    @Test void meshOutputWriteTo() {
        MeshOutput mesh = MarchingCubes.extract(SPHERE, new Vec3(-2,-2,-2), new Vec3(2,2,2), 5, 5, 5, 0f);
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment dst = arena.allocate(mesh.gpuByteSize());
            mesh.writeTo(dst, 0);
            // First vertex position round-trips into the head of the segment.
            Vec3 first = mesh.vertices().get(0);
            assertEquals(first.x, dst.get(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, 0));
            assertEquals(first.y, dst.get(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, 4));
            assertEquals(first.z, dst.get(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, 8));
            // Indices follow the vertex block.
            assertEquals(mesh.indices().get(0).intValue(),
                    dst.get(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, mesh.vertexByteSize()));
        }
    }

    @Test void meshOutputWriteIndicesWithBase() {
        MeshOutput mesh = MarchingCubes.extract(SPHERE, new Vec3(-2,-2,-2), new Vec3(2,2,2), 5, 5, 5, 0f);
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment dst = arena.allocate(mesh.indexByteSize());
            mesh.writeIndices(dst, 0, 1000);
            assertEquals(mesh.indices().get(0) + 1000,
                    dst.get(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, 0));
        }
    }

    @Test void meshOutputWriteVerticesStrided() {
        MeshOutput mesh = MarchingCubes.extract(SPHERE, new Vec3(-2,-2,-2), new Vec3(2,2,2), 5, 5, 5, 0f);
        long stride = 32; // position plus 20 bytes of other interleaved attributes
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment dst = arena.allocate(stride * mesh.vertexCount());
            mesh.writeVertices(dst, 0, stride);
            Vec3 second = mesh.vertices().get(1);
            assertEquals(second.x, dst.get(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, stride));
        }
    }
}
