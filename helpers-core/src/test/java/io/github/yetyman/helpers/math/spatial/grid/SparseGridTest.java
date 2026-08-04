package io.github.yetyman.helpers.math.spatial.grid;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Sphere;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SparseGridTest {
    @Test void insertAndQuery() {
        SparseGrid<String> grid = new SparseGrid<>(10f);
        grid.insert("a", new AABB(new Vec3(0,0,0), new Vec3(1,1,1)));
        grid.insert("b", new AABB(new Vec3(50,50,50), new Vec3(51,51,51)));
        assertEquals(2, grid.size());
        List<String> near = grid.query(new AABB(new Vec3(-1,-1,-1), new Vec3(2,2,2)));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }
    @Test void removeAndClear() {
        SparseGrid<String> grid = new SparseGrid<>(10f);
        grid.insert("a", new AABB(new Vec3(0,0,0), new Vec3(1,1,1)));
        grid.remove("a");
        assertEquals(0, grid.size());
        grid.insert("b", new AABB(new Vec3(0,0,0), new Vec3(1,1,1)));
        grid.clear();
        assertEquals(0, grid.size());
    }
    @Test void sphereQuery() {
        SparseGrid<String> grid = new SparseGrid<>(5f);
        grid.insert("a", new AABB(new Vec3(0,0,0), new Vec3(1,1,1)));
        grid.insert("b", new AABB(new Vec3(50,50,50), new Vec3(51,51,51)));
        List<String> near = grid.query(new Sphere(new Vec3(0,0,0), 5f));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }
    @Test void containsPoint() {
        SparseGrid<String> grid = new SparseGrid<>(10f);
        grid.insert("a", new AABB(new Vec3(0,0,0), new Vec3(10,10,10)));
        assertTrue(grid.contains(new Vec3(5,5,5)));
        assertFalse(grid.contains(new Vec3(50,50,50)));
    }
}
