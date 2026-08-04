package io.github.yetyman.helpers.math.spatial.octree;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Frustum;
import io.github.yetyman.helpers.math.geometry.Ray;
import io.github.yetyman.helpers.math.geometry.Sphere;
import io.github.yetyman.helpers.math.Mat4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LinkedOctreeTest {

    private LinkedOctree<String> octree;

    @BeforeEach
    void setUp() {
        OctreeConfig config = OctreeConfig.builder()
                .worldBounds(new AABB(new Vec3(-100, -100, -100), new Vec3(100, 100, 100)))
                .maxDepth(6)
                .splitThreshold(4)
                .mergeThreshold(2)
                .strategy(OctreeConfig.BucketStrategy.LOOSE)
                .build();
        octree = new LinkedOctree<>(config);
    }

    // --- Insert / Size / Clear ---

    @Test
    void insertAndSize() {
        assertEquals(0, octree.size());
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        assertEquals(1, octree.size());
        octree.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        assertEquals(2, octree.size());
    }

    @Test
    void insertDuplicateUpdates() {
        AABB bounds1 = new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        AABB bounds2 = new AABB(new Vec3(10, 10, 10), new Vec3(11, 11, 11));
        octree.insert("a", bounds1);
        octree.insert("a", bounds2); // should update, not duplicate
        assertEquals(1, octree.size());
        // Should find it at new location
        List<String> results = octree.query(bounds2);
        assertTrue(results.contains("a"));
    }

    @Test
    void clear() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        octree.clear();
        assertEquals(0, octree.size());
        assertTrue(octree.query(new AABB(new Vec3(-100, -100, -100), new Vec3(100, 100, 100))).isEmpty());
    }

    // --- Remove ---

    @Test
    void remove() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        octree.remove("a");
        assertEquals(1, octree.size());
        assertFalse(octree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2))).contains("a"));
    }

    @Test
    void removeNonexistentIsNoOp() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.remove("nonexistent");
        assertEquals(1, octree.size());
    }

    // --- Update ---

    @Test
    void update() {
        AABB original = new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        AABB moved = new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51));
        octree.insert("a", original);
        octree.update("a", moved);
        assertEquals(1, octree.size());
        assertTrue(octree.query(moved).contains("a"));
        assertFalse(octree.query(original).contains("a"));
    }

    // --- AABB Query ---

    @Test
    void queryAABB() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        octree.insert("c", new AABB(new Vec3(0, 0, 0), new Vec3(2, 2, 2)));

        List<String> near = octree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(3, 3, 3)));
        assertTrue(near.contains("a"));
        assertTrue(near.contains("c"));
        assertFalse(near.contains("b"));
    }

    @Test
    void queryAABBAllocationFree() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        List<String> out = new ArrayList<>();
        int count = octree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2)), out);
        assertEquals(1, count);
        assertEquals("a", out.get(0));
    }

    // --- Sphere Query ---

    @Test
    void querySphere() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        List<String> near = octree.query(new Sphere(new Vec3(0, 0, 0), 5f));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }

    // --- Ray Query ---

    @Test
    void queryRay() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        Ray ray = new Ray(new Vec3(-5, 0.5f, 0.5f), new Vec3(1, 0, 0));
        List<String> hits = octree.query(ray, 100f);
        assertTrue(hits.contains("a"));
        assertFalse(hits.contains("b"));
    }

    @Test
    void queryRayMissesDistantObjects() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        Ray ray = new Ray(new Vec3(-5, 0.5f, 0.5f), new Vec3(1, 0, 0));
        List<String> hits = octree.query(ray, 3f); // maxDistance too short
        assertTrue(hits.isEmpty());
    }

    // --- Frustum Query ---

    @Test
    void queryFrustum() {
        octree.insert("a", new AABB(new Vec3(0, 0, -5), new Vec3(1, 1, -4)));
        octree.insert("b", new AABB(new Vec3(0, 0, -50), new Vec3(1, 1, -49)));
        octree.insert("outside", new AABB(new Vec3(90, 90, 90), new Vec3(91, 91, 91)));

        // Simple frustum looking down -Z
        Mat4 proj = Mat4.perspective((float) Math.toRadians(90), 1f, 0.1f, 100f);
        Mat4 view = Mat4.lookAt(new Vec3(0, 0, 0), new Vec3(0, 0, -1), new Vec3(0, 1, 0));
        Mat4 vp = view.mulNew(proj);
        Frustum frustum = Frustum.fromViewProjection(vp);

        List<String> visible = octree.queryFrustum(frustum);
        assertTrue(visible.contains("a"));
        assertTrue(visible.contains("b"));
        assertFalse(visible.contains("outside"));
    }

    // --- Contains ---

    @Test
    void containsPoint() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(10, 10, 10)));
        assertTrue(octree.contains(new Vec3(5, 5, 5)));
        assertFalse(octree.contains(new Vec3(50, 50, 50)));
    }

    // --- Nearest ---

    @Test
    void nearest() {
        octree.insert("close", new AABB(new Vec3(1, 1, 1), new Vec3(2, 2, 2)));
        octree.insert("far", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        assertEquals("close", octree.nearest(new Vec3(0, 0, 0)));
    }

    // --- Count ---

    @Test
    void countAABB() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(0, 0, 0), new Vec3(2, 2, 2)));
        octree.insert("c", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        assertEquals(2, octree.count(new AABB(new Vec3(-1, -1, -1), new Vec3(3, 3, 3))));
        assertEquals(1, octree.count(new AABB(new Vec3(49, 49, 49), new Vec3(52, 52, 52))));
    }

    @Test
    void countSphere() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        assertEquals(1, octree.count(new Sphere(new Vec3(0, 0, 0), 5f)));
        assertEquals(0, octree.count(new Sphere(new Vec3(25, 25, 25), 1f)));
    }

    // --- Bulk Insert ---

    @Test
    void insertAllWithFunction() {
        List<String> items = List.of("a", "b", "c", "d", "e");
        octree.insertAll(items, item -> {
            int i = item.charAt(0) - 'a';
            float x = i * 10f;
            return new AABB(new Vec3(x, 0, 0), new Vec3(x + 1, 1, 1));
        });
        assertEquals(5, octree.size());
        assertTrue(octree.dirtyTracker().isFullRebuild());
    }

    @Test
    void insertAllWithMap() {
        Map<String, AABB> itemBounds = Map.of(
                "a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)),
                "b", new AABB(new Vec3(10, 10, 10), new Vec3(11, 11, 11)),
                "c", new AABB(new Vec3(20, 20, 20), new Vec3(21, 21, 21))
        );
        octree.insertAll(itemBounds);
        assertEquals(3, octree.size());
    }

    // --- Dirty Tracker ---

    @Test
    void dirtyTrackerMarksOnInsert() {
        assertFalse(octree.dirtyTracker().isDirty());
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        assertTrue(octree.dirtyTracker().isDirty());
    }

    @Test
    void dirtyTrackerClear() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.dirtyTracker().clearDirty();
        assertFalse(octree.dirtyTracker().isDirty());
    }

    @Test
    void dirtyTrackerFullRebuildOnClear() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.dirtyTracker().clearDirty();
        octree.clear();
        assertTrue(octree.dirtyTracker().isFullRebuild());
    }

    // --- Split behavior ---

    @Test
    void splitOnThresholdExceeded() {
        // splitThreshold is 4, so inserting 5 items in the same region should trigger a split
        for (int i = 0; i < 5; i++) {
            float x = i * 0.1f;
            octree.insert("item" + i, new AABB(new Vec3(x, 0, 0), new Vec3(x + 0.05f, 0.05f, 0.05f)));
        }
        assertEquals(5, octree.size());
        // All items should still be queryable
        List<String> all = octree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(100, 100, 100)));
        assertEquals(5, all.size());
    }

    // --- Stream queries ---

    @Test
    void queryStream() {
        octree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        octree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        long count = octree.queryStream(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2))).count();
        assertEquals(1, count);
    }

    // --- World bounds ---

    @Test
    void worldBounds() {
        AABB wb = octree.worldBounds();
        assertEquals(-100, wb.min.x);
        assertEquals(100, wb.max.x);
    }
}
