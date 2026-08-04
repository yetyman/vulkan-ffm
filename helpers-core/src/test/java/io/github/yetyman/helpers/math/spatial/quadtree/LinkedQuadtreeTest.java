package io.github.yetyman.helpers.math.spatial.quadtree;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Ray;
import io.github.yetyman.helpers.math.geometry.Sphere;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LinkedQuadtreeTest {

    private LinkedQuadtree<String> tree;

    @BeforeEach
    void setUp() {
        QuadtreeConfig config = QuadtreeConfig.builder()
                .worldBounds(new AABB(new Vec3(-100, -100, -100), new Vec3(100, 100, 100)))
                .maxDepth(6)
                .splitThreshold(4)
                .mergeThreshold(2)
                .strategy(QuadtreeConfig.BucketStrategy.LOOSE)
                .build();
        tree = new LinkedQuadtree<>(config);
    }

    @Test
    void insertAndSize() {
        assertEquals(0, tree.size());
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        assertEquals(1, tree.size());
    }

    @Test
    void removeAndSize() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.remove("a");
        assertEquals(0, tree.size());
    }

    @Test
    void queryAABB() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(50, 0, 50), new Vec3(51, 1, 51)));

        List<String> near = tree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2)));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }

    @Test
    void querySphere() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(50, 0, 50), new Vec3(51, 1, 51)));

        List<String> near = tree.query(new Sphere(new Vec3(0, 0, 0), 5f));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }

    @Test
    void queryRay() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(50, 0, 50), new Vec3(51, 1, 51)));

        // Ray along +X at y=0.5, z=0.5
        Ray ray = new Ray(new Vec3(-5, 0.5f, 0.5f), new Vec3(1, 0, 0));
        List<String> hits = tree.query(ray, 100f);
        assertTrue(hits.contains("a"));
        assertFalse(hits.contains("b"));
    }

    @Test
    void update() {
        AABB original = new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        AABB moved = new AABB(new Vec3(50, 0, 50), new Vec3(51, 1, 51));
        tree.insert("a", original);
        tree.update("a", moved);
        assertEquals(1, tree.size());
        assertTrue(tree.query(moved).contains("a"));
        assertFalse(tree.query(original).contains("a"));
    }

    @Test
    void clear() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.clear();
        assertEquals(0, tree.size());
    }

    @Test
    void containsPoint() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(10, 10, 10)));
        assertTrue(tree.contains(new Vec3(5, 5, 5)));
        assertFalse(tree.contains(new Vec3(50, 50, 50)));
    }

    @Test
    void nearest() {
        tree.insert("close", new AABB(new Vec3(1, 1, 1), new Vec3(2, 2, 2)));
        tree.insert("far", new AABB(new Vec3(50, 0, 50), new Vec3(51, 1, 51)));
        assertEquals("close", tree.nearest(new Vec3(0, 0, 0)));
    }

    @Test
    void count() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(0, 0, 0), new Vec3(2, 2, 2)));
        tree.insert("c", new AABB(new Vec3(50, 0, 50), new Vec3(51, 1, 51)));
        assertEquals(2, tree.count(new AABB(new Vec3(-1, -1, -1), new Vec3(3, 3, 3))));
    }

    @Test
    void bulkInsert() {
        tree.insertAll(List.of("a", "b", "c"), item -> {
            int i = item.charAt(0) - 'a';
            float x = i * 10f;
            return new AABB(new Vec3(x, 0, 0), new Vec3(x + 1, 1, 1));
        });
        assertEquals(3, tree.size());
        assertTrue(tree.dirtyTracker().isFullRebuild());
    }

    @Test
    void splitOnThreshold() {
        for (int i = 0; i < 5; i++) {
            float x = i * 0.1f;
            tree.insert("item" + i, new AABB(new Vec3(x, 0, x), new Vec3(x + 0.05f, 0.05f, x + 0.05f)));
        }
        assertEquals(5, tree.size());
        List<String> all = tree.query(new AABB(new Vec3(-100, -100, -100), new Vec3(100, 100, 100)));
        assertEquals(5, all.size());
    }

    @Test
    void queryStream() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(50, 0, 50), new Vec3(51, 1, 51)));
        long count = tree.queryStream(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2))).count();
        assertEquals(1, count);
    }

    @Test
    void dirtyTrackerOnInsertAndClear() {
        assertFalse(tree.dirtyTracker().isDirty());
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        assertTrue(tree.dirtyTracker().isDirty());
        tree.dirtyTracker().clearDirty();
        assertFalse(tree.dirtyTracker().isDirty());
    }
}
