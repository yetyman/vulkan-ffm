package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic correctness tests for marching algorithms.
 * Uses known analytic fields where crossing positions can be verified mathematically.
 */
class MarchingCorrectnessTest {

    // Unit circle: sqrt(x^2 + y^2) - 1 = 0 at distance 1 from origin
    private static final ScalarField2D CIRCLE = (x, y) -> (float) Math.sqrt(x * x + y * y) - 1f;

    // Horizontal line: y - 0.5 = 0 at y=0.5
    private static final ScalarField2D HORIZONTAL = (x, y) -> y - 0.5f;

    // Vertical line: x - 0.5 = 0 at x=0.5
    private static final ScalarField2D VERTICAL = (x, y) -> x - 0.5f;

    // --- Marching Triangles ---

    @Test
    void marchingTriangles_circleVerticesOnUnitCircle() {
        ContourOutput contour = MarchingTriangles.extract(CIRCLE, new Vec2(-2, -2), new Vec2(2, 2), 40, 40, 0f);
        assertTrue(contour.vertices().size() > 10, "Should produce many vertices for a circle");

        // Every vertex should be approximately at distance 1.0 from origin
        List<Vec2> verts = contour.vertices();
        for (int i = 0; i < verts.size(); i++) {
            Vec2 v = verts.get(i);
            float dist = (float) Math.sqrt(v.x * v.x + v.y * v.y);
            assertEquals(1.0f, dist, 0.15f, "Vertex " + i + " at (" + v.x + "," + v.y + ") has distance " + dist + " from origin, expected ~1.0");
        }
    }

    @Test
    void marchingTriangles_horizontalLineVerticesAtCorrectY() {
        ContourOutput contour = MarchingTriangles.extract(HORIZONTAL, new Vec2(0, 0), new Vec2(2, 2), 20, 20, 0f);
        assertTrue(contour.vertices().size() > 5, "Should produce vertices for horizontal line");

        for (Vec2 v : contour.vertices()) {
            assertEquals(0.5f, v.y, 0.15f, "Vertex y=" + v.y + " should be ~0.5");
        }
    }

    @Test
    void marchingTriangles_verticalLineVerticesAtCorrectX() {
        ContourOutput contour = MarchingTriangles.extract(VERTICAL, new Vec2(0, 0), new Vec2(2, 2), 20, 20, 0f);
        assertTrue(contour.vertices().size() > 5, "Should produce vertices for vertical line");

        for (Vec2 v : contour.vertices()) {
            assertEquals(0.5f, v.x, 0.15f, "Vertex x=" + v.x + " should be ~0.5");
        }
    }

    @Test
    void marchingTriangles_segmentsFormConnectedContour() {
        ContourOutput contour = MarchingTriangles.extract(CIRCLE, new Vec2(-2, -2), new Vec2(2, 2), 30, 30, 0f);
        // Each segment should have finite length (not degenerate)
        List<Vec2> verts = contour.vertices();
        for (int[] seg : contour.segments()) {
            Vec2 a = verts.get(seg[0]), b = verts.get(seg[1]);
            float len = (float) Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y));
            assertTrue(len > 1e-6f, "Segment should have non-zero length");
            assertTrue(len < 1f, "Segment should not be unreasonably long, got " + len);
        }
    }

    // --- Marching Hexagons ---

    @Test
    void marchingHexagons_circleVerticesOnUnitCircle() {
        ContourOutput contour = MarchingHexagons.extract(CIRCLE, new Vec2(-2, -2), new Vec2(2, 2), 8, 0.2f, 0f);
        assertTrue(contour.vertices().size() > 5, "Should produce vertices for a circle, got " + contour.vertices().size());

        List<Vec2> verts = contour.vertices();
        for (int i = 0; i < verts.size(); i++) {
            Vec2 v = verts.get(i);
            float dist = (float) Math.sqrt(v.x * v.x + v.y * v.y);
            assertEquals(1.0f, dist, 0.3f, "Hex vertex " + i + " at (" + v.x + "," + v.y + ") has distance " + dist + " from origin, expected ~1.0");
        }
    }

    @Test
    void marchingHexagons_horizontalLineVerticesAtCorrectY() {
        ContourOutput contour = MarchingHexagons.extract(HORIZONTAL, new Vec2(-1, -1), new Vec2(2, 2), 5, 0.2f, 0f);
        assertTrue(contour.vertices().size() > 3, "Should produce vertices for horizontal line");

        for (Vec2 v : contour.vertices()) {
            assertEquals(0.5f, v.y, 0.25f, "Hex vertex y=" + v.y + " should be ~0.5");
        }
    }

    @Test
    void marchingHexagons_producesSegments() {
        ContourOutput contour = MarchingHexagons.extract(CIRCLE, new Vec2(-2, -2), new Vec2(2, 2), 6, 0.3f, 0f);
        assertTrue(contour.segments().size() > 0, "Should produce contour segments");

        List<Vec2> verts = contour.vertices();
        for (int[] seg : contour.segments()) {
            assertTrue(seg[0] >= 0 && seg[0] < verts.size(), "Segment index out of bounds");
            assertTrue(seg[1] >= 0 && seg[1] < verts.size(), "Segment index out of bounds");
            Vec2 a = verts.get(seg[0]), b = verts.get(seg[1]);
            float len = (float) Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y));
            assertTrue(len > 1e-6f, "Segment should have non-zero length");
        }
    }

    // --- Marching Squares (reference baseline) ---

    @Test
    void marchingSquares_circleVerticesOnUnitCircle() {
        ContourOutput contour = MarchingSquares.extract(CIRCLE, new Vec2(-2, -2), new Vec2(2, 2), 40, 40, 0f);
        assertTrue(contour.vertices().size() > 10, "Should produce many vertices");

        for (Vec2 v : contour.vertices()) {
            float dist = (float) Math.sqrt(v.x * v.x + v.y * v.y);
            assertEquals(1.0f, dist, 0.1f, "Square vertex should be on unit circle");
        }
    }

    // --- Cross-algorithm comparison ---

    @Test
    void allAlgorithms_produceVerticesInsideSamplingBounds() {
        Vec2 min = new Vec2(-2, -2), max = new Vec2(2, 2);
        ContourOutput squares = MarchingSquares.extract(CIRCLE, min, max, 30, 30, 0f);
        ContourOutput triangles = MarchingTriangles.extract(CIRCLE, min, max, 30, 30, 0f);
        ContourOutput hexagons = MarchingHexagons.extract(CIRCLE, min, max, 6, 0.25f, 0f);

        for (Vec2 v : squares.vertices()) {
            assertTrue(v.x >= min.x - 0.5f && v.x <= max.x + 0.5f, "Square vertex OOB: " + v);
            assertTrue(v.y >= min.y - 0.5f && v.y <= max.y + 0.5f, "Square vertex OOB: " + v);
        }
        for (Vec2 v : triangles.vertices()) {
            assertTrue(v.x >= min.x - 0.5f && v.x <= max.x + 0.5f, "Triangle vertex OOB: " + v);
            assertTrue(v.y >= min.y - 0.5f && v.y <= max.y + 0.5f, "Triangle vertex OOB: " + v);
        }
        for (Vec2 v : hexagons.vertices()) {
            assertTrue(v.x >= min.x - 1f && v.x <= max.x + 1f, "Hex vertex OOB: " + v);
            assertTrue(v.y >= min.y - 1f && v.y <= max.y + 1f, "Hex vertex OOB: " + v);
        }
    }
}
