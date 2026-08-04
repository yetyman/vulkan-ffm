package io.github.yetyman.helpers.math.spatial.rtree;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Ray;
import io.github.yetyman.helpers.math.geometry.Sphere;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RTreeTest {

    private RTree<String> tree;

    @BeforeEach
    void setUp() {
        tree = new RTree<>(RTreeConfig.builder().maxChildren(4).minChildren(2).build());
    }

    @Test
    void insertAndSize() {
        assertEquals(0, tree.size());
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        assertEquals(1, tree.size());
        tree.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        assertEquals(2, tree.size());
    }

    @Test
    void remove() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        tree.remove("a");
        assertEquals(1, tree.size());
        assertFalse(tree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2))).contains("a"));
    }

    @Test
    void update() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.update("a", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        assertFalse(tree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2))).contains("a"));
        assertTrue(tree.query(new AABB(new Vec3(49, 49, 49), new Vec3(52, 52, 52))).contains("a"));
    }

    @Test
    void queryAABB() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        tree.insert("c", new AABB(new Vec3(0.5f, 0.5f, 0.5f), new Vec3(1.5f, 1.5f, 1.5f)));

        List<String> near = tree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(2, 2, 2)));
        assertTrue(near.contains("a"));
        assertTrue(near.contains("c"));
        assertFalse(near.contains("b"));
    }

    @Test
    void querySphere() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        List<String> near = tree.query(new Sphere(new Vec3(0, 0, 0), 5f));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }

    @Test
    void queryRay() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));

        Ray ray = new Ray(new Vec3(-5, 0.5f, 0.5f), new Vec3(1, 0, 0));
        List<String> hits = tree.query(ray, 100f);
        assertTrue(hits.contains("a"));
        assertFalse(hits.contains("b"));
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
        tree.insert("far", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        assertEquals("close", tree.nearest(new Vec3(0, 0, 0)));
    }

    @Test
    void count() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(0, 0, 0), new Vec3(2, 2, 2)));
        tree.insert("c", new AABB(new Vec3(50, 50, 50), new Vec3(51, 51, 51)));
        assertEquals(2, tree.count(new AABB(new Vec3(-1, -1, -1), new Vec3(3, 3, 3))));
    }

    @Test
    void clear() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.clear();
        assertEquals(0, tree.size());
    }

    @Test
    void splitOnOverflow() {
        // maxChildren=4, so 5 inserts should trigger a split
        for (int i = 0; i < 5; i++) {
            tree.insert("item" + i, new AABB(new Vec3(i * 10, 0, 0), new Vec3(i * 10 + 1, 1, 1)));
        }
        assertEquals(5, tree.size());
        // All still queryable
        List<String> all = tree.query(new AABB(new Vec3(-100, -100, -100), new Vec3(100, 100, 100)));
        assertEquals(5, all.size());
    }

    @Test
    void writeTo() {
        tree.insert("a", new AABB(new Vec3(0, 0, 0), new Vec3(1, 1, 1)));
        tree.insert("b", new AABB(new Vec3(5, 5, 5), new Vec3(6, 6, 6)));
        ByteBuffer buf = ByteBuffer.allocate(tree.byteSize());
        tree.writeTo(buf);
        assertTrue(buf.position() > 0);
    }

    @Test
    void manyItems() {
        for (int i = 0; i < 50; i++) {
            float x = (i % 10) * 5f;
            float y = (i / 10) * 5f;
            tree.insert("item" + i, new AABB(new Vec3(x, y, 0), new Vec3(x + 1, y + 1, 1)));
        }
        assertEquals(50, tree.size());
        List<String> hits = tree.query(new AABB(new Vec3(-1, -1, -1), new Vec3(6, 2, 2)));
        assertTrue(hits.size() >= 2); // at least first two items in row 0
    }
}
