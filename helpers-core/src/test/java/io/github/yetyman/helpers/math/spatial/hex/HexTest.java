package io.github.yetyman.helpers.math.spatial.hex;

import io.github.yetyman.helpers.math.Vec2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HexTest {
    @Test void distance() {
        HexCoord a = new HexCoord(0, 0);
        HexCoord b = new HexCoord(3, -1);
        assertEquals(3, a.distanceTo(b));
    }
    @Test void neighbors() {
        HexCoord c = new HexCoord(0, 0);
        HexCoord[] n = c.neighbors();
        assertEquals(6, n.length);
        for (HexCoord nc : n) assertEquals(1, c.distanceTo(nc));
    }
    @Test void ring() {
        HexCoord c = new HexCoord(0, 0);
        HexCoord[] ring = c.ring(2);
        assertEquals(12, ring.length);
        for (HexCoord h : ring) assertEquals(2, c.distanceTo(h));
    }
    @Test void range() {
        HexCoord c = new HexCoord(0, 0);
        HexCoord[] range = c.range(1);
        assertEquals(7, range.length); // center + 6 neighbors
    }
    @Test void lineTo() {
        HexCoord a = new HexCoord(0, 0);
        HexCoord b = new HexCoord(3, 0);
        HexCoord[] line = a.lineTo(b);
        assertEquals(4, line.length); // 0,1,2,3
    }
    @Test void rotation() {
        HexCoord c = new HexCoord(1, 0);
        HexCoord rotated = c.rotateCW();
        assertEquals(1, new HexCoord(0, 0).distanceTo(rotated));
    }
    @Test void hexToPixelAndBack() {
        HexLayout layout = new HexLayout(HexLayout.Orientation.FLAT_TOP, 10f, new Vec2(0, 0));
        HexCoord original = new HexCoord(3, -2);
        Vec2 pixel = layout.hexToPixel(original);
        HexCoord roundTrip = layout.pixelToHex(pixel);
        assertEquals(original, roundTrip);
    }
    @Test void gridSetAndGet() {
        HexGrid<String> grid = new HexGrid<>();
        grid.set(new HexCoord(1, 2), "hello");
        assertEquals("hello", grid.get(new HexCoord(1, 2)));
        assertNull(grid.get(new HexCoord(0, 0)));
    }
    @Test void gridQueryRange() {
        HexGrid<String> grid = new HexGrid<>();
        grid.set(new HexCoord(0, 0), "center");
        grid.set(new HexCoord(1, 0), "neighbor");
        grid.set(new HexCoord(5, 5), "far");
        var results = grid.queryRange(new HexCoord(0, 0), 1);
        assertEquals(2, results.size());
        assertTrue(results.contains("center"));
        assertTrue(results.contains("neighbor"));
    }
}
