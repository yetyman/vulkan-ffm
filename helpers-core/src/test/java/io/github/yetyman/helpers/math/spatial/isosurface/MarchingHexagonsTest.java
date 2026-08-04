package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarchingHexagonsTest {
    private static final ScalarField2D CIRCLE = (x, y) -> (float) Math.sqrt(x*x + y*y) - 1f;

    @Test void producesContour() {
        ContourOutput contour = MarchingHexagons.extract(CIRCLE, new Vec2(-2, -2), new Vec2(2, 2), 5, 0.3f, 0f);
        assertTrue(contour.vertices().size() > 0, "Expected vertices from marching hexagons");
        assertTrue(contour.segments().size() > 0, "Expected segments from marching hexagons");
    }

    @Test void noContourOutside() {
        ScalarField2D outside = (x, y) -> 10f;
        ContourOutput contour = MarchingHexagons.extract(outside, new Vec2(0, 0), new Vec2(1, 1), 3, 0.2f, 0f);
        assertEquals(0, contour.segments().size());
    }
}
