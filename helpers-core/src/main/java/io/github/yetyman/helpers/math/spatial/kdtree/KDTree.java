package io.github.yetyman.helpers.math.spatial.kdtree;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.ContainmentResult;
import io.github.yetyman.helpers.math.geometry.Frustum;
import io.github.yetyman.helpers.math.geometry.Intersections;
import io.github.yetyman.helpers.math.geometry.Ray;
import io.github.yetyman.helpers.math.geometry.Sphere;
import io.github.yetyman.helpers.math.spatial.DirtyTracker;
import io.github.yetyman.helpers.math.spatial.SpatialStructure;
import io.github.yetyman.vulkan.buffers.GpuLayout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * KD-Tree — binary space partition alternating axes.
 * Excellent for static point clouds and nearest-neighbor queries.
 * Rebuild on every mutation (not optimized for dynamic scenes).
 *
 * @param <T> the type of items stored
 */
public class KDTree<T> implements SpatialStructure<T> {

    private final List<T> items = new ArrayList<>();
    private final List<AABB> bounds = new ArrayList<>();
    private KDNode root;
    private final DirtyTrackerImpl dirtyTracker = new DirtyTrackerImpl();

    public KDTree() {}

    @Override public void insert(T item, AABB b) { items.add(item); bounds.add(b); rebuildInternal(); dirtyTracker.markFullRebuild(); }
    @Override public void insertAll(Iterable<T> it, Function<T, AABB> bp) { for (T i : it) { items.add(i); bounds.add(bp.apply(i)); } rebuildInternal(); dirtyTracker.markFullRebuild(); }
    @Override public void insertAll(Map<T, AABB> ib) { for (var e : ib.entrySet()) { items.add(e.getKey()); bounds.add(e.getValue()); } rebuildInternal(); dirtyTracker.markFullRebuild(); }
    @Override public void remove(T item) { int i = items.indexOf(item); if (i >= 0) { items.remove(i); bounds.remove(i); rebuildInternal(); dirtyTracker.markFullRebuild(); } }
    @Override public void update(T item, AABB nb) { int i = items.indexOf(item); if (i >= 0) { bounds.set(i, nb); rebuildInternal(); dirtyTracker.markFullRebuild(); } else insert(item, nb); }
    @Override public void rebuild() { rebuildInternal(); dirtyTracker.markFullRebuild(); }
    @Override public void clear() { items.clear(); bounds.clear(); root = null; dirtyTracker.markFullRebuild(); }
    @Override public int size() { return items.size(); }
    @Override public AABB worldBounds() { if (root == null) return new AABB(new Vec3(), new Vec3()); return root.bounds; }
    @Override public DirtyTracker dirtyTracker() { return dirtyTracker; }

    @Override
    public void visitNodes(io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        if (root != null) visitKDNode(root, 0, visitor);
    }

    private void visitKDNode(KDNode node, int depth, io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        visitor.visit(node.bounds, depth, node.isLeaf(), node.isLeaf() ? (node.end - node.start) : 2);
        if (!node.isLeaf()) {
            if (node.left != null) visitKDNode(node.left, depth + 1, visitor);
            if (node.right != null) visitKDNode(node.right, depth + 1, visitor);
        }
    }

    // BufferWritable
    @Override public int byteSize() { return items.size() * 24; }
    @Override public void writeTo(ByteBuffer buf) { for (AABB b : bounds) { buf.putFloat(b.min.x); buf.putFloat(b.min.y); buf.putFloat(b.min.z); buf.putFloat(b.max.x); buf.putFloat(b.max.y); buf.putFloat(b.max.z); } }
    @Override public void readFrom(ByteBuffer buf) { throw new UnsupportedOperationException(); }

    // Queries
    @Override public List<T> query(AABB range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public int query(AABB range, List<T> out) { int b = out.size(); if (root != null) queryNode(root, range, out); return out.size() - b; }
    @Override public List<T> query(Sphere range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public int query(Sphere range, List<T> out) { int b = out.size(); if (root != null) querySphereNode(root, range, out); return out.size() - b; }
    @Override public List<T> query(Ray ray, float maxDist) { List<T> out = new ArrayList<>(); if (root != null) queryRayNode(root, ray, maxDist, out); return out; }
    @Override public List<T> queryFrustum(Frustum f) { List<T> out = new ArrayList<>(); queryFrustum(f, out); return out; }
    @Override public int queryFrustum(Frustum f, List<T> out) { int b = out.size(); if (root != null) queryFrustumNode(root, f, out); return out.size() - b; }
    @Override public Stream<T> queryStream(AABB range) { return query(range).stream(); }
    @Override public Stream<T> queryStream(Sphere range) { return query(range).stream(); }
    @Override public Stream<T> queryStream(Ray ray, float maxDist) { return query(ray, maxDist).stream(); }
    @Override public Stream<T> queryFrustumStream(Frustum f) { return queryFrustum(f).stream(); }
    @Override public boolean contains(Vec3 point) { for (AABB b : bounds) if (b.contains(point)) return true; return false; }
    @Override public T nearest(Vec3 point) { T best = null; float bd = Float.MAX_VALUE; for (int i = 0; i < items.size(); i++) { Vec3 c = clampToAABB(point, bounds.get(i)); float d = point.distanceSquared(c); if (d < bd) { bd = d; best = items.get(i); } } return best; }
    @Override public int count(AABB range) { return query(range).size(); }
    @Override public int count(Sphere range) { return query(range).size(); }
    @Override public int countFrustum(Frustum f) { return queryFrustum(f).size(); }

    // Internal
    private void rebuildInternal() {
        if (items.isEmpty()) { root = null; return; }
        int[] indices = new int[items.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        root = buildNode(indices, 0, indices.length, 0);
    }

    private KDNode buildNode(int[] indices, int start, int end, int depth) {
        if (start >= end) return null;
        AABB nodeBounds = computeEnclosing(indices, start, end);
        if (end - start <= 4) return new KDNode(nodeBounds, indices, start, end, null, null);
        int axis = depth % 3;
        sortByAxis(indices, start, end, axis);
        int mid = (start + end) / 2;
        KDNode left = buildNode(indices, start, mid, depth + 1);
        KDNode right = buildNode(indices, mid, end, depth + 1);
        return new KDNode(nodeBounds, null, 0, 0, left, right);
    }

    private AABB computeEnclosing(int[] indices, int start, int end) {
        Vec3 min = new Vec3(Float.POSITIVE_INFINITY); Vec3 max = new Vec3(Float.NEGATIVE_INFINITY);
        for (int i = start; i < end; i++) { AABB b = bounds.get(indices[i]); min.min(b.min); max.max(b.max); }
        return new AABB(min, max);
    }

    private void sortByAxis(int[] indices, int start, int end, int axis) {
        Integer[] boxed = new Integer[end - start];
        for (int i = 0; i < boxed.length; i++) boxed[i] = indices[start + i];
        java.util.Arrays.sort(boxed, (a, b) -> Float.compare(centroid(bounds.get(a), axis), centroid(bounds.get(b), axis)));
        for (int i = 0; i < boxed.length; i++) indices[start + i] = boxed[i];
    }

    private static float centroid(AABB a, int axis) { return switch(axis) { case 0 -> (a.min.x+a.max.x)*0.5f; case 1 -> (a.min.y+a.max.y)*0.5f; default -> (a.min.z+a.max.z)*0.5f; }; }

    private void queryNode(KDNode node, AABB range, List<T> out) {
        if (!node.bounds.intersects(range)) return;
        if (node.isLeaf()) { for (int i = node.start; i < node.end; i++) if (bounds.get(node.indices[i]).intersects(range)) out.add(items.get(node.indices[i])); }
        else { if (node.left != null) queryNode(node.left, range, out); if (node.right != null) queryNode(node.right, range, out); }
    }

    private void querySphereNode(KDNode node, Sphere sphere, List<T> out) {
        if (!aabbIntersectsSphere(node.bounds, sphere)) return;
        if (node.isLeaf()) { for (int i = node.start; i < node.end; i++) if (aabbIntersectsSphere(bounds.get(node.indices[i]), sphere)) out.add(items.get(node.indices[i])); }
        else { if (node.left != null) querySphereNode(node.left, sphere, out); if (node.right != null) querySphereNode(node.right, sphere, out); }
    }

    private void queryRayNode(KDNode node, Ray ray, float maxDist, List<T> out) {
        if (!rayIntersectsWithin(ray, node.bounds, maxDist)) return;
        if (node.isLeaf()) { for (int i = node.start; i < node.end; i++) { float t = Intersections.rayAABB(ray, bounds.get(node.indices[i])); if (t >= 0 && t <= maxDist) out.add(items.get(node.indices[i])); } }
        else { if (node.left != null) queryRayNode(node.left, ray, maxDist, out); if (node.right != null) queryRayNode(node.right, ray, maxDist, out); }
    }

    private void queryFrustumNode(KDNode node, Frustum f, List<T> out) {
        ContainmentResult r = f.testAABB(node.bounds);
        if (r == ContainmentResult.OUTSIDE) return;
        if (node.isLeaf()) { for (int i = node.start; i < node.end; i++) if (f.testAABB(bounds.get(node.indices[i])) != ContainmentResult.OUTSIDE) out.add(items.get(node.indices[i])); }
        else { if (node.left != null) queryFrustumNode(node.left, f, out); if (node.right != null) queryFrustumNode(node.right, f, out); }
    }

    private static boolean aabbIntersectsSphere(AABB a, Sphere s) { Vec3 c = clampToAABB(s.center, a); return s.center.distanceSquared(c) <= s.radius*s.radius; }
    private static boolean rayIntersectsWithin(Ray ray, AABB aabb, float maxDist) { if (aabb.contains(ray.origin)) return true; float t = Intersections.rayAABB(ray, aabb); return t >= 0 && t <= maxDist; }
    private static Vec3 clampToAABB(Vec3 p, AABB a) { return new Vec3(Math.max(a.min.x,Math.min(p.x,a.max.x)),Math.max(a.min.y,Math.min(p.y,a.max.y)),Math.max(a.min.z,Math.min(p.z,a.max.z))); }

    private static class KDNode {
        final AABB bounds;
        final int[] indices; // leaf only
        final int start, end; // range into indices
        final KDNode left, right;
        KDNode(AABB bounds, int[] indices, int start, int end, KDNode left, KDNode right) { this.bounds = bounds; this.indices = indices; this.start = start; this.end = end; this.left = left; this.right = right; }
        boolean isLeaf() { return indices != null; }
    }

    private static class DirtyTrackerImpl implements DirtyTracker {
        private boolean fullRebuild;
        void markFullRebuild() { fullRebuild = true; }
        @Override public boolean isDirty() { return fullRebuild; } @Override public boolean isFullRebuild() { return fullRebuild; }
        @Override public int dirtyNodeCount() { return 0; } @Override public IntStream dirtyNodeIndices() { return IntStream.empty(); }
        @Override public void clearDirty() { fullRebuild = false; }
    }
}
