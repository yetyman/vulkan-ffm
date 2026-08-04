package io.github.yetyman.helpers.math.spatial.grid;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DenseGridTest {
    @Test void insertAndQuery() {
        DenseGrid<String> grid = new DenseGrid<>(new AABB(new Vec3(0,0,0), new Vec3(100,100,100)), 10, 10, 10);
        grid.insert("a", new AABB(new Vec3(5,5,5), new Vec3(6,6,6)));
        grid.insert("b", new AABB(new Vec3(80,80,80), new Vec3(81,81,81)));
        assertEquals(2, grid.size());
        List<String> near = grid.query(new AABB(new Vec3(4,4,4), new Vec3(7,7,7)));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }
    @Test void removeAndClear() {
        DenseGrid<String> grid = new DenseGrid<>(new AABB(new Vec3(0,0,0), new Vec3(100,100,100)), 10, 10, 10);
        grid.insert("a", new AABB(new Vec3(5,5,5), new Vec3(6,6,6)));
        grid.remove("a");
        assertEquals(0, grid.size());
    }
    @Test void containsPoint() {
        DenseGrid<String> grid = new DenseGrid<>(new AABB(new Vec3(0,0,0), new Vec3(100,100,100)), 10, 10, 10);
        grid.insert("a", new AABB(new Vec3(5,5,5), new Vec3(15,15,15)));
        assertTrue(grid.contains(new Vec3(10,10,10)));
        assertFalse(grid.contains(new Vec3(80,80,80)));
    }
}
