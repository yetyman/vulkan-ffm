package io.github.yetyman.helpers.math.spatial.bvh;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Ray;
import io.github.yetyman.helpers.math.geometry.Sphere;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BVHTest {

    private BVH<String> bvh;

    @BeforeEach
    void setUp() {
        bvh = new BVH<>(new MedianSplitBuilder(1));
    }

    @Test
    void insertAndSize() {
        assertEquals(0, bvh.size());
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        assertEquals(1, bvh.size());
        bvh.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        assertEquals(2, bvh.size());
    }

    @Test
    void remove() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        bvh.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        bvh.remove("a");
        assertEquals(1, bvh.size());
        assertFalse(bvh.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2))).contains("a"));
        assertTrue(bvh.query(new AABB(new Vec3(4, 4, 4), new Vec3(7, 7, 7))).contains("b"));
    }

    @Test
    void update() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        bvh.update("a", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        assertFalse(bvh.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2))).contains("a"));
        assertTrue(bvh.query(new AABB(new Vec3(49, 49, 49), new Vec3(52, 52, 52))).contains("a"));
    }

    @Test
    void queryAABB() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        bvh.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        bvh.insert("c", new AABB(new Vec3(0.5f, 0.5f, 0.5f), new Vec3(1.5f, 1.5f, 1.5f)));

        List<String> near = bvh.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2)));
        assertTrue(near.contains("a"));
        assertTrue(near.contains("c"));
        assertFalse(near.contains("b"));
    }

    @Test
    void querySphere() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        bvh.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        List<String> near = bvh.query(new Sphere(new Vec3(0, 0, 0), 5f));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }

    @Test
    void queryRay() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        bvh.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        Ray ray = new Ray(new Vec3(-5, 0.5f, 0.5f), new Vec3(1, 0, 0));
        List<String> hits = bvh.query(ray, 100f);
        assertTrue(hits.contains("a"));
        assertFalse(hits.contains("b"));
    }

    @Test
    void containsPoint() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(10, 10, 10)));
        assertTrue(bvh.contains(new Vec3(5, 5, 5)));
        assertFalse(bvh.contains(new Vec3(50, 50, 50)));
    }

    @Test
    void nearest() {
        bvh.insert("close", new AABB(new Vec3(1, 1, 1), new Vec3(2, 2, 2)));
        bvh.insert("far", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        assertEquals("close", bvh.nearest(new Vec3(0, 0, 0)));
    }

    @Test
    void count() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        bvh.insert("b", new AABB(new Vec3(0, 0, 0), new Vec3(2, 2, 2)));
        bvh.insert("c", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        assertEquals(2, bvh.count(new AABB(new Vec3(-1, -1, -1), new Vec3(3, 3, 3))));
    }

    @Test
    void bulkInsert() {
        bvh.insertAll(List.of("a", "b", "c", "d"), item -> {
            int i = item.charAt(0) - 'a';
            float x = i * 10f;
            return new AABB(new Vec3(x, 0, 0), new Vec3(x + 1, 1, 1));
        });
        assertEquals(4, bvh.size());
        assertTrue(bvh.dirtyTracker().isFullRebuild());
    }

    @Test
    void clear() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        bvh.clear();
        assertEquals(0, bvh.size());
    }

    @Test
    void writeTo() {
        bvh.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        bvh.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            int size = bvh.gpuByteSize();
            assertTrue(size > 0);
            java.lang.foreign.MemorySegment dst = arena.allocate(size);
            bvh.writeTo(dst, 0);
            // First node is the root, whose bounds enclose both inserted items.
            assertEquals(0f, dst.get(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, 0));
            assertEquals(6f, dst.get(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, 12));
        }
    }

    @Test
    void worldBounds() {
        bvh.insert("a", new AABB(new Vec3(-5, -5, -5), new Vec3(5, 5, 5)));
        bvh.insert("b", new AABB(new Vec3(10, 10, 10), new Vec3(20, 20, 20)));
        AABB wb = bvh.worldBounds();
        assertTrue(wb.min.x <= -5);
        assertTrue(wb.max.x >= 20);
    }

    @Test
    void manyItemsStillQueryable() {
        for (int i = 0; i < 100; i++) {
            float x = (i % 10) * 5f;
            float y = (i / 10) * 5f;
            bvh.insert("item" + i, new AABB(new Vec3(x, y, 0), new Vec3(x + 1, y + 1, 1)));
        }
        assertEquals(100, bvh.size());
        // Query a region containing first row
        List<String> hits = bvh.query(new AABB(new Vec3(-1, -1, -1), new Vec3(50, 2, 2)));
        assertEquals(10, hits.size());
    }
}
