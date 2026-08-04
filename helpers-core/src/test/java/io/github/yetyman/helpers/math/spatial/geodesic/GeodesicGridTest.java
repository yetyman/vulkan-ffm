package io.github.yetyman.helpers.math.spatial.geodesic;

import io.github.yetyman.helpers.math.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeodesicGridTest {
    @Test void level0Has12Vertices() {
        GeodesicGrid grid = new GeodesicGrid(0);
        assertEquals(12, grid.vertexCount());
        assertEquals(20, grid.faceCount());
        assertEquals(12, grid.cellCount());
    }
    @Test void level1VertexCount() {
        GeodesicGrid grid = new GeodesicGrid(1);
        assertEquals(42, grid.vertexCount()); // 10*4+2 = 42
    }
    @Test void level2VertexCount() {
        GeodesicGrid grid = new GeodesicGrid(2);
        assertEquals(162, grid.vertexCount()); // 10*16+2
    }
    @Test void verticesOnUnitSphere() {
        GeodesicGrid grid = new GeodesicGrid(2);
        for (int i = 0; i < grid.vertexCount(); i++) {
            float len = grid.vertex(i).length();
            assertEquals(1f, len, 0.001f, "Vertex " + i + " not on unit sphere");
        }
    }
    @Test void has12Pentagons() {
        GeodesicGrid grid = new GeodesicGrid(2);
        int pentCount = 0;
        for (int i = 0; i < grid.cellCount(); i++) {
            if (grid.cell(i).isPentagon()) pentCount++;
        }
        assertEquals(12, pentCount);
    }
    @Test void findCellNearPole() {
        GeodesicGrid grid = new GeodesicGrid(2);
        int cell = grid.findCell(new Vec3(0, 1, 0));
        Vec3 center = grid.cell(cell).center();
        // Should be close to +Y pole
        assertTrue(center.y > 0.8f, "Expected cell near +Y pole, got " + center);
    }
    @Test void neighborsConnected() {
        GeodesicGrid grid = new GeodesicGrid(1);
        for (int i = 0; i < grid.cellCount(); i++) {
            GeodesicCell cell = grid.cell(i);
            assertTrue(cell.neighborCount() >= 5 && cell.neighborCount() <= 6,
                    "Cell " + i + " has " + cell.neighborCount() + " neighbors");
        }
    }
}
