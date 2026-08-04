package io.github.yetyman.helpers.math.spatial.kdtree;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Ray;
import io.github.yetyman.helpers.math.geometry.Sphere;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KDTreeTest {
    @Test void insertAndQuery() {
        KDTree<String> tree = new KDTree<>();
        tree.insert("a", new AABB(new Vec3(0,0,0), new Vec3(1,1,1)));
        tree.insert("b", new AABB(new Vec3(50,50,50), new Vec3(51,51,51)));
        assertEquals(2, tree.size());
        List<String> near = tree.query(new AABB(new Vec3(-1,-1,-1), new Vec3(2,2,2)));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }
    @Test void remove() {
        KDTree<String> tree = new KDTree<>();
        tree.insert("a", new AABB(new Vec3(0,0,0), new Vec3(1,1,1)));
        tree.remove("a");
        assertEquals(0, tree.size());
    }
    @Test void sphereQuery() {
        KDTree<String> tree = new KDTree<>();
        tree.insert("a", new AABB(new Vec3(0,0,0), new Vec3(1,1,1)));
        tree.insert("b", new AABB(new Vec3(50,50,50), new Vec3(51,51,51)));
        List<String> near = tree.query(new Sphere(new Vec3(0,0,0), 5f));
        assertTrue(near.contains("a"));
        assertFalse(near.contains("b"));
    }
    @Test void rayQuery() {
        KDTree<String> tree = new KDTree<>();
        tree.insert("a", new AABB(new Vec3(0,0,0), new Vec3(1,1,1)));
        tree.insert("b", new AABB(new Vec3(50,50,50), new Vec3(51,51,51)));
        Ray ray = new Ray(new Vec3(-5, 0.5f, 0.5f), new Vec3(1, 0, 0));
        List<String> hits = tree.query(ray, 100f);
        assertTrue(hits.contains("a"));
        assertFalse(hits.contains("b"));
    }
    @Test void nearest() {
        KDTree<String> tree = new KDTree<>();
        tree.insert("close", new AABB(new Vec3(1,1,1), new Vec3(2,2,2)));
        tree.insert("far", new AABB(new Vec3(50,50,50), new Vec3(51,51,51)));
        assertEquals("close", tree.nearest(new Vec3(0,0,0)));
    }
    @Test void manyItems() {
        KDTree<String> tree = new KDTree<>();
        for (int i = 0; i < 100; i++) { float x = i * 3f; tree.insert("i" + i, new AABB(new Vec3(x,0,0), new Vec3(x+1,1,1))); }
        assertEquals(100, tree.size());
        List<String> near = tree.query(new AABB(new Vec3(-1,-1,-1), new Vec3(5,2,2)));
        assertTrue(near.size() >= 1);
    }
}
