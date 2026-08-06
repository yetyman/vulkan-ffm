package io.github.yetyman.helpers.math.spatial.bvh;

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

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;

/**
 * Bounding Volume Hierarchy — binary tree of AABBs.
 * Supports pluggable construction strategies via {@link BvhBuilder}.
 * Incremental insert/remove triggers full rebuild (BVHs don't rebalance well incrementally).
 *
 * @param <T> the type of items stored
 */
public class BVH<T> implements SpatialStructure<T> {

    private final BvhBuilder builder;
    private final List<T> items = new ArrayList<>();
    private final List<AABB> bounds = new ArrayList<>();
    private BvhNode[] nodes = new BvhNode[0];
    private final DirtyTrackerImpl dirtyTracker = new DirtyTrackerImpl();

    public BVH() {
        this(new MedianSplitBuilder());
    }

    public BVH(BvhBuilder builder) {
        this.builder = builder;
    }

    // --- SpatialStructure ---

    @Override
    public void insert(T item, AABB itemBounds) {
        items.add(item);
        bounds.add(itemBounds);
        rebuildInternal();
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void insertAll(Iterable<T> items, Function<T, AABB> boundsProvider) {
        for (T item : items) {
            this.items.add(item);
            this.bounds.add(boundsProvider.apply(item));
        }
        rebuildInternal();
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void insertAll(Map<T, AABB> itemBounds) {
        for (var e : itemBounds.entrySet()) {
            items.add(e.getKey());
            bounds.add(e.getValue());
        }
        rebuildInternal();
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void remove(T item) {
        int idx = items.indexOf(item);
        if (idx < 0) return;
        items.remove(idx);
        bounds.remove(idx);
        rebuildInternal();
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void update(T item, AABB newBounds) {
        int idx = items.indexOf(item);
        if (idx < 0) {
            insert(item, newBounds);
            return;
        }
        bounds.set(idx, newBounds);
        rebuildInternal();
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void rebuild() {
        rebuildInternal();
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void clear() {
        items.clear();
        bounds.clear();
        nodes = new BvhNode[0];
        dirtyTracker.markFullRebuild();
    }

    @Override
    public int size() { return items.size(); }

    @Override
    public AABB worldBounds() {
        if (nodes.length == 0) return new AABB(new Vec3(), new Vec3());
        return nodes[0].bounds;
    }

    @Override
    public DirtyTracker dirtyTracker() { return dirtyTracker; }

    // --- GPU layout ---

    /** Default layout: DFS-ordered flat node AABBs (24 bytes per node). */
    public static final GpuLayout<BVH<?>> DEFAULT_LAYOUT = new DfsLayout();

    /** @return total serialized size in the default layout, which depends on node count. */
    public int gpuByteSize() { return nodes.length * 24; }

    /** Writes this BVH into {@code dst} at {@code offset} using the default layout. */
    @SuppressWarnings("unchecked")
    public void writeTo(MemorySegment dst, long offset) {
        ((GpuLayout<BVH<T>>) (GpuLayout<?>) DEFAULT_LAYOUT).writeTo(this, dst, offset);
    }

    /** Writes this BVH into {@code dst} at {@code offset} using an alternative layout. */
    public void writeTo(MemorySegment dst, long offset, GpuLayout<BVH<T>> layout) {
        layout.writeTo(this, dst, offset);
    }

    // Package-private for layout access
    BvhNode[] nodes() { return nodes; }

    @Override
    public void visitNodes(io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        if (nodes.length > 0) visitBvhNode(0, 0, visitor);
    }

    private void visitBvhNode(int nodeIdx, int depth, io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        BvhNode node = nodes[nodeIdx];
        visitor.visit(node.bounds, depth, node.isLeaf(), node.isLeaf() ? node.primCount : 2);
        if (!node.isLeaf()) {
            visitBvhNode(node.leftChild, depth + 1, visitor);
            visitBvhNode(node.rightChild, depth + 1, visitor);
        }
    }

    // --- SpatialQuery ---

    @Override
    public List<T> query(AABB range) {
        List<T> out = new ArrayList<>();
        query(range, out);
        return out;
    }

    @Override
    public int query(AABB range, List<T> out) {
        int before = out.size();
        if (nodes.length > 0) queryAABBNode(0, range, out);
        return out.size() - before;
    }

    @Override
    public List<T> query(Sphere range) {
        List<T> out = new ArrayList<>();
        query(range, out);
        return out;
    }

    @Override
    public int query(Sphere range, List<T> out) {
        int before = out.size();
        if (nodes.length > 0) querySphereNode(0, range, out);
        return out.size() - before;
    }

    @Override
    public List<T> query(Ray ray, float maxDistance) {
        List<T> out = new ArrayList<>();
        if (nodes.length > 0) queryRayNode(0, ray, maxDistance, out);
        return out;
    }

    @Override
    public List<T> queryFrustum(Frustum frustum) {
        List<T> out = new ArrayList<>();
        queryFrustum(frustum, out);
        return out;
    }

    @Override
    public int queryFrustum(Frustum frustum, List<T> out) {
        int before = out.size();
        if (nodes.length > 0) queryFrustumNode(0, frustum, out);
        return out.size() - before;
    }

    @Override
    public Stream<T> queryStream(AABB range) { return query(range).stream(); }
    @Override
    public Stream<T> queryStream(Sphere range) { return query(range).stream(); }
    @Override
    public Stream<T> queryStream(Ray ray, float maxDistance) { return query(ray, maxDistance).stream(); }
    @Override
    public Stream<T> queryFrustumStream(Frustum frustum) { return queryFrustum(frustum).stream(); }

    @Override
    public boolean contains(Vec3 point) {
        if (nodes.length == 0) return false;
        return containsNode(0, point);
    }

    @Override
    public T nearest(Vec3 point) {
        if (items.isEmpty()) return null;
        T best = null;
        float bestDistSq = Float.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            Vec3 closest = clampToAABB(point, bounds.get(i));
            float distSq = point.distanceSquared(closest);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = items.get(i);
            }
        }
        return best;
    }

    @Override
    public int count(AABB range) {
        if (nodes.length == 0) return 0;
        return countAABBNode(0, range);
    }

    @Override
    public int count(Sphere range) {
        if (nodes.length == 0) return 0;
        return countSphereNode(0, range);
    }

    @Override
    public int countFrustum(Frustum frustum) {
        if (nodes.length == 0) return 0;
        return countFrustumNode(0, frustum);
    }

    // --- Internal: rebuild ---

    private void rebuildInternal() {
        if (items.isEmpty()) {
            nodes = new BvhNode[0];
        } else {
            BvhBuilder.BuildResult result = builder.build(bounds);
            nodes = result.nodes();
            int[] order = result.orderedIndices();

            // Reorder items and bounds to match the builder's sorted order
            List<T> reorderedItems = new ArrayList<>(items.size());
            List<AABB> reorderedBounds = new ArrayList<>(bounds.size());
            for (int idx : order) {
                reorderedItems.add(items.get(idx));
                reorderedBounds.add(bounds.get(idx));
            }
            items.clear();
            items.addAll(reorderedItems);
            bounds.clear();
            bounds.addAll(reorderedBounds);
        }
    }

    // --- Internal: queries ---

    private void queryAABBNode(int nodeIdx, AABB range, List<T> out) {
        BvhNode node = nodes[nodeIdx];
        if (!node.bounds.intersects(range)) return;
        if (node.isLeaf()) {
            for (int i = node.primStart; i < node.primStart + node.primCount; i++) {
                if (bounds.get(i).intersects(range)) out.add(items.get(i));
            }
        } else {
            queryAABBNode(node.leftChild, range, out);
            queryAABBNode(node.rightChild, range, out);
        }
    }

    private void querySphereNode(int nodeIdx, Sphere sphere, List<T> out) {
        BvhNode node = nodes[nodeIdx];
        if (!aabbIntersectsSphere(node.bounds, sphere)) return;
        if (node.isLeaf()) {
            for (int i = node.primStart; i < node.primStart + node.primCount; i++) {
                if (aabbIntersectsSphere(bounds.get(i), sphere)) out.add(items.get(i));
            }
        } else {
            querySphereNode(node.leftChild, sphere, out);
            querySphereNode(node.rightChild, sphere, out);
        }
    }

    private void queryRayNode(int nodeIdx, Ray ray, float maxDist, List<T> out) {
        BvhNode node = nodes[nodeIdx];
        if (!rayIntersectsWithin(ray, node.bounds, maxDist)) return;
        if (node.isLeaf()) {
            for (int i = node.primStart; i < node.primStart + node.primCount; i++) {
                float t = Intersections.rayAABB(ray, bounds.get(i));
                if (t >= 0 && t <= maxDist) out.add(items.get(i));
            }
        } else {
            queryRayNode(node.leftChild, ray, maxDist, out);
            queryRayNode(node.rightChild, ray, maxDist, out);
        }
    }

    private void queryFrustumNode(int nodeIdx, Frustum frustum, List<T> out) {
        BvhNode node = nodes[nodeIdx];
        ContainmentResult r = frustum.testAABB(node.bounds);
        if (r == ContainmentResult.OUTSIDE) return;
        if (node.isLeaf()) {
            if (r == ContainmentResult.INSIDE) {
                for (int i = node.primStart; i < node.primStart + node.primCount; i++) out.add(items.get(i));
            } else {
                for (int i = node.primStart; i < node.primStart + node.primCount; i++) {
                    if (frustum.testAABB(bounds.get(i)) != ContainmentResult.OUTSIDE) out.add(items.get(i));
                }
            }
        } else {
            queryFrustumNode(node.leftChild, frustum, out);
            queryFrustumNode(node.rightChild, frustum, out);
        }
    }

    private boolean containsNode(int nodeIdx, Vec3 point) {
        BvhNode node = nodes[nodeIdx];
        if (!node.bounds.contains(point)) return false;
        if (node.isLeaf()) {
            for (int i = node.primStart; i < node.primStart + node.primCount; i++) {
                if (bounds.get(i).contains(point)) return true;
            }
            return false;
        }
        return containsNode(node.leftChild, point) || containsNode(node.rightChild, point);
    }

    private int countAABBNode(int nodeIdx, AABB range) {
        BvhNode node = nodes[nodeIdx];
        if (!node.bounds.intersects(range)) return 0;
        if (node.isLeaf()) {
            int c = 0;
            for (int i = node.primStart; i < node.primStart + node.primCount; i++) {
                if (bounds.get(i).intersects(range)) c++;
            }
            return c;
        }
        return countAABBNode(node.leftChild, range) + countAABBNode(node.rightChild, range);
    }

    private int countSphereNode(int nodeIdx, Sphere sphere) {
        BvhNode node = nodes[nodeIdx];
        if (!aabbIntersectsSphere(node.bounds, sphere)) return 0;
        if (node.isLeaf()) {
            int c = 0;
            for (int i = node.primStart; i < node.primStart + node.primCount; i++) {
                if (aabbIntersectsSphere(bounds.get(i), sphere)) c++;
            }
            return c;
        }
        return countSphereNode(node.leftChild, sphere) + countSphereNode(node.rightChild, sphere);
    }

    private int countFrustumNode(int nodeIdx, Frustum frustum) {
        BvhNode node = nodes[nodeIdx];
        ContainmentResult r = frustum.testAABB(node.bounds);
        if (r == ContainmentResult.OUTSIDE) return 0;
        if (node.isLeaf()) {
            if (r == ContainmentResult.INSIDE) return node.primCount;
            int c = 0;
            for (int i = node.primStart; i < node.primStart + node.primCount; i++) {
                if (frustum.testAABB(bounds.get(i)) != ContainmentResult.OUTSIDE) c++;
            }
            return c;
        }
        return countFrustumNode(node.leftChild, frustum) + countFrustumNode(node.rightChild, frustum);
    }

    // --- Internal: geometry helpers ---

    private static boolean aabbIntersectsSphere(AABB aabb, Sphere sphere) {
        Vec3 closest = clampToAABB(sphere.center, aabb);
        return sphere.center.distanceSquared(closest) <= sphere.radius * sphere.radius;
    }

    private static boolean rayIntersectsWithin(Ray ray, AABB aabb, float maxDist) {
        if (aabb.contains(ray.origin)) return true;
        float t = Intersections.rayAABB(ray, aabb);
        return t >= 0 && t <= maxDist;
    }

    private static Vec3 clampToAABB(Vec3 point, AABB aabb) {
        return new Vec3(
                Math.max(aabb.min.x, Math.min(point.x, aabb.max.x)),
                Math.max(aabb.min.y, Math.min(point.y, aabb.max.y)),
                Math.max(aabb.min.z, Math.min(point.z, aabb.max.z))
        );
    }

    // --- Layout ---

    private static class DfsLayout implements GpuLayout<BVH<?>> {
        /** Variable size: depends on node count. Use {@link BVH#gpuByteSize()} for the total. */
        @Override public int byteSize() { return -1; }
        @Override public void writeTo(BVH<?> bvh, MemorySegment dst, long offset) {
            long o = offset;
            for (BvhNode node : bvh.nodes()) {
                dst.set(JAVA_FLOAT_UNALIGNED, o, node.bounds.min.x);
                dst.set(JAVA_FLOAT_UNALIGNED, o + 4, node.bounds.min.y);
                dst.set(JAVA_FLOAT_UNALIGNED, o + 8, node.bounds.min.z);
                dst.set(JAVA_FLOAT_UNALIGNED, o + 12, node.bounds.max.x);
                dst.set(JAVA_FLOAT_UNALIGNED, o + 16, node.bounds.max.y);
                dst.set(JAVA_FLOAT_UNALIGNED, o + 20, node.bounds.max.z);
                o += 24;
            }
        }
        @Override public void readFrom(BVH<?> bvh, MemorySegment src, long offset) {
            throw new UnsupportedOperationException("BVH cannot be deserialized -- rebuild from items instead.");
        }
    }

    // --- DirtyTracker ---

    private static class DirtyTrackerImpl implements DirtyTracker {
        private final Set<Integer> dirtyNodes = new HashSet<>();
        private boolean fullRebuild = false;

        void markFullRebuild() { fullRebuild = true; dirtyNodes.clear(); }

        @Override public boolean isDirty() { return fullRebuild || !dirtyNodes.isEmpty(); }
        @Override public boolean isFullRebuild() { return fullRebuild; }
        @Override public int dirtyNodeCount() { return fullRebuild ? 0 : dirtyNodes.size(); }
        @Override public IntStream dirtyNodeIndices() { return fullRebuild ? IntStream.empty() : dirtyNodes.stream().mapToInt(Integer::intValue); }
        @Override public void clearDirty() { fullRebuild = false; dirtyNodes.clear(); }
    }
}
