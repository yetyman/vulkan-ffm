package io.github.yetyman.helpers.math.spatial.quadtree;

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
 * Pointer-based quadtree with configurable bucket strategy.
 * Partitions in X and Z dimensions (4 children per node). Y is used for queries but not partitioning.
 *
 * @param <T> the type of items stored
 */
public class LinkedQuadtree<T> implements SpatialStructure<T> {

    private final QuadtreeConfig config;
    private final QuadNode root;
    private final Map<T, ItemEntry<T>> itemMap = new HashMap<>();
    private final DirtyTrackerImpl dirtyTracker = new DirtyTrackerImpl();
    private int nodeCount = 0;

    public LinkedQuadtree(QuadtreeConfig config) {
        this.config = config;
        this.root = new QuadNode(config.worldBounds(), 0, nodeCount++);
    }

    // --- SpatialStructure ---

    @Override
    public void insert(T item, AABB bounds) {
        if (itemMap.containsKey(item)) {
            update(item, bounds);
            return;
        }
        ItemEntry<T> entry = new ItemEntry<>(item, bounds);
        itemMap.put(item, entry);
        insertIntoNode(root, entry);
        dirtyTracker.markDirty(root.index);
    }

    @Override
    public void insertAll(Iterable<T> items, Function<T, AABB> boundsProvider) {
        for (T item : items) {
            AABB bounds = boundsProvider.apply(item);
            ItemEntry<T> entry = new ItemEntry<>(item, bounds);
            itemMap.put(item, entry);
            insertIntoNode(root, entry);
        }
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void insertAll(Map<T, AABB> itemBounds) {
        for (var e : itemBounds.entrySet()) {
            ItemEntry<T> entry = new ItemEntry<>(e.getKey(), e.getValue());
            itemMap.put(e.getKey(), entry);
            insertIntoNode(root, entry);
        }
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void remove(T item) {
        ItemEntry<T> entry = itemMap.remove(item);
        if (entry == null) return;
        QuadNode node = entry.node;
        if (node != null) {
            node.<T>typedItems().remove(entry);
            dirtyTracker.markDirty(node.index);
            tryMerge(node);
        }
    }

    @Override
    public void update(T item, AABB newBounds) {
        ItemEntry<T> entry = itemMap.get(item);
        if (entry == null) {
            insert(item, newBounds);
            return;
        }
        QuadNode oldNode = entry.node;
        entry.bounds = newBounds;
        if (oldNode != null && containsBoundsXZ(oldNode.bounds, newBounds)) {
            dirtyTracker.markDirty(oldNode.index);
            return;
        }
        if (oldNode != null) {
            oldNode.<T>typedItems().remove(entry);
            dirtyTracker.markDirty(oldNode.index);
            tryMerge(oldNode);
        }
        insertIntoNode(root, entry);
        dirtyTracker.markDirty(entry.node.index);
    }

    @Override
    public void rebuild() {
        List<ItemEntry<T>> allItems = new ArrayList<>(itemMap.values());
        root.<T>typedItems().clear();
        root.children = null;
        nodeCount = 0;
        root.index = nodeCount++;
        for (ItemEntry<T> entry : allItems) {
            entry.node = null;
            insertIntoNode(root, entry);
        }
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void clear() {
        itemMap.clear();
        root.<T>typedItems().clear();
        root.children = null;
        nodeCount = 0;
        root.index = nodeCount++;
        dirtyTracker.markFullRebuild();
    }

    @Override
    public int size() { return itemMap.size(); }

    @Override
    public AABB worldBounds() { return config.worldBounds(); }

    @Override
    public DirtyTracker dirtyTracker() { return dirtyTracker; }

    // --- GPU layout ---

    /** Default layout: DFS-ordered flat node AABBs (24 bytes per node). */
    public static final GpuLayout<LinkedQuadtree<?>> DEFAULT_LAYOUT = new DfsAabbLayout();

    /** @return total serialized size in the default layout, which depends on node count. */
    public int gpuByteSize() {
        return nodeCount * 24;
    }

    /** Writes this tree into {@code dst} at {@code offset} using the default layout. */
    @SuppressWarnings("unchecked")
    public void writeTo(java.lang.foreign.MemorySegment dst, long offset) {
        ((GpuLayout<LinkedQuadtree<T>>) (GpuLayout<?>) DEFAULT_LAYOUT).writeTo(this, dst, offset);
    }

    /** Writes this tree into {@code dst} at {@code offset} using an alternative layout. */
    public void writeTo(java.lang.foreign.MemorySegment dst, long offset, GpuLayout<LinkedQuadtree<T>> layout) {
        layout.writeTo(this, dst, offset);
    }

    // Package-private for layout access
    QuadNode root() { return root; }
    int nodeCount() { return nodeCount; }

    @Override
    public void visitNodes(io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        visitNodeRecursive(root, visitor);
    }

    private void visitNodeRecursive(QuadNode node, io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        boolean isLeaf = node.children == null;
        visitor.visit(node.bounds, node.depth, isLeaf, node.<T>typedItems().size());
        if (node.children != null) {
            for (QuadNode child : node.children) visitNodeRecursive(child, visitor);
        }
    }

    private static class DfsAabbLayout implements GpuLayout<LinkedQuadtree<?>> {
        /** Variable size: depends on node count. Use {@link LinkedQuadtree#gpuByteSize()} for the total. */
        @Override public int byteSize() { return -1; }
        @Override public void writeTo(LinkedQuadtree<?> tree, java.lang.foreign.MemorySegment dst, long offset) {
            writeDfs(tree.root(), dst, offset);
        }
        @Override public void readFrom(LinkedQuadtree<?> tree, java.lang.foreign.MemorySegment src, long offset) {
            throw new UnsupportedOperationException("Quadtree cannot be deserialized -- rebuild from items instead.");
        }
        private long writeDfs(QuadNode node, java.lang.foreign.MemorySegment dst, long o) {
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o, node.bounds.min.x);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 4, node.bounds.min.y);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 8, node.bounds.min.z);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 12, node.bounds.max.x);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 16, node.bounds.max.y);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 20, node.bounds.max.z);
            o += 24;
            if (node.children != null) {
                for (QuadNode child : node.children) o = writeDfs(child, dst, o);
            }
            return o;
        }
    }

    // --- SpatialQuery: List ---

    @Override
    public List<T> query(AABB range) {
        List<T> results = new ArrayList<>();
        query(range, results);
        return results;
    }

    @Override
    public int query(AABB range, List<T> out) {
        int before = out.size();
        queryNode(root, range, out);
        return out.size() - before;
    }

    @Override
    public List<T> query(Sphere range) {
        List<T> results = new ArrayList<>();
        query(range, results);
        return results;
    }

    @Override
    public int query(Sphere range, List<T> out) {
        int before = out.size();
        querySphereNode(root, range, out);
        return out.size() - before;
    }

    @Override
    public List<T> query(Ray ray, float maxDistance) {
        List<T> results = new ArrayList<>();
        queryRayNode(root, ray, maxDistance, results);
        return results;
    }

    @Override
    public List<T> queryFrustum(Frustum frustum) {
        List<T> results = new ArrayList<>();
        queryFrustum(frustum, results);
        return results;
    }

    @Override
    public int queryFrustum(Frustum frustum, List<T> out) {
        int before = out.size();
        queryFrustumNode(root, frustum, out);
        return out.size() - before;
    }

    // --- SpatialQuery: Stream ---

    @Override
    public Stream<T> queryStream(AABB range) { return query(range).stream(); }

    @Override
    public Stream<T> queryStream(Sphere range) { return query(range).stream(); }

    @Override
    public Stream<T> queryStream(Ray ray, float maxDistance) { return query(ray, maxDistance).stream(); }

    @Override
    public Stream<T> queryFrustumStream(Frustum frustum) { return queryFrustum(frustum).stream(); }

    // --- SpatialQuery: Point ---

    @Override
    public boolean contains(Vec3 point) {
        return containsPointNode(root, point);
    }

    @Override
    public T nearest(Vec3 point) {
        @SuppressWarnings("unchecked")
        T[] best = (T[]) new Object[1];
        float[] bestDistSq = {Float.MAX_VALUE};
        nearestNode(root, point, best, bestDistSq);
        return best[0];
    }

    // --- SpatialQuery: Count ---

    @Override
    public int count(AABB range) { return countAABBNode(root, range); }

    @Override
    public int count(Sphere range) { return countSphereNode(root, range); }

    @Override
    public int countFrustum(Frustum frustum) { return countFrustumNode(root, frustum); }

    // --- Internal: insertion ---

    private void insertIntoNode(QuadNode node, ItemEntry<T> entry) {
        Vec3 center = entry.bounds.center();
        QuadNode target = findNode(node, center, 0);
        target.<T>typedItems().add(entry);
        entry.node = target;

        if (target.children == null && target.<T>typedItems().size() > config.splitThreshold() && target.depth < config.maxDepth()) {
            split(target);
        }
    }

    private QuadNode findNode(QuadNode node, Vec3 center, int depth) {
        if (node.children == null || depth >= config.maxDepth()) return node;
        int quadrant = getQuadrant(node.bounds, center);
        return findNode(node.children[quadrant], center, depth + 1);
    }

    // --- Internal: split/merge ---

    private void split(QuadNode node) {
        node.children = new QuadNode[4];
        AABB b = node.bounds;
        Vec3 mid = b.center();

        for (int i = 0; i < 4; i++) {
            AABB childBounds = computeChildBounds(b, mid, i);
            node.children[i] = new QuadNode(childBounds, node.depth + 1, nodeCount++);
        }

        List<ItemEntry<T>> items = new ArrayList<>(node.<T>typedItems());
        node.<T>typedItems().clear();
        for (ItemEntry<T> entry : items) {
            entry.node = null;
            insertIntoNode(node, entry);
        }
        dirtyTracker.markDirty(node.index);
    }

    private void tryMerge(QuadNode node) {
        if (node.children == null) return;
        int totalItems = countItemsRecursive(node);
        if (totalItems <= config.mergeThreshold()) {
            List<ItemEntry<T>> collected = new ArrayList<>();
            collectItemsRecursive(node, collected);
            node.children = null;
            node.<T>typedItems().clear();
            for (ItemEntry<T> entry : collected) {
                node.<T>typedItems().add(entry);
                entry.node = node;
            }
            dirtyTracker.markDirty(node.index);
        }
    }

    private int countItemsRecursive(QuadNode node) {
        int count = node.<T>typedItems().size();
        if (node.children != null) {
            for (QuadNode child : node.children) count += countItemsRecursive(child);
        }
        return count;
    }

    private void collectItemsRecursive(QuadNode node, List<ItemEntry<T>> out) {
        out.addAll(node.<T>typedItems());
        if (node.children != null) {
            for (QuadNode child : node.children) collectItemsRecursive(child, out);
        }
    }

    // --- Internal: queries ---

    private void queryNode(QuadNode node, AABB range, List<T> out) {
        if (!node.bounds.intersects(range)) return;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (entry.bounds.intersects(range)) out.add(entry.item);
        }
        if (node.children != null) {
            for (QuadNode child : node.children) queryNode(child, range, out);
        }
    }

    private void querySphereNode(QuadNode node, Sphere sphere, List<T> out) {
        if (!aabbIntersectsSphere(node.bounds, sphere)) return;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (aabbIntersectsSphere(entry.bounds, sphere)) out.add(entry.item);
        }
        if (node.children != null) {
            for (QuadNode child : node.children) querySphereNode(child, sphere, out);
        }
    }

    private void queryRayNode(QuadNode node, Ray ray, float maxDist, List<T> out) {
        if (!rayIntersectsWithin(ray, node.bounds, maxDist)) return;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            float et = Intersections.rayAABB(ray, entry.bounds);
            if (et >= 0 && et <= maxDist) out.add(entry.item);
        }
        if (node.children != null) {
            for (QuadNode child : node.children) queryRayNode(child, ray, maxDist, out);
        }
    }

    private void queryFrustumNode(QuadNode node, Frustum frustum, List<T> out) {
        ContainmentResult r = frustum.testAABB(node.bounds);
        if (r == ContainmentResult.OUTSIDE) return;
        if (r == ContainmentResult.INSIDE) {
            collectAllItems(node, out);
            return;
        }
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (frustum.testAABB(entry.bounds) != ContainmentResult.OUTSIDE) out.add(entry.item);
        }
        if (node.children != null) {
            for (QuadNode child : node.children) queryFrustumNode(child, frustum, out);
        }
    }

    private boolean containsPointNode(QuadNode node, Vec3 point) {
        if (!node.bounds.contains(point)) return false;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (entry.bounds.contains(point)) return true;
        }
        if (node.children != null) {
            for (QuadNode child : node.children) {
                if (containsPointNode(child, point)) return true;
            }
        }
        return false;
    }

    private void nearestNode(QuadNode node, Vec3 point, T[] best, float[] bestDistSq) {
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            Vec3 closest = clampToAABB(point, entry.bounds);
            float distSq = point.distanceSquared(closest);
            if (distSq < bestDistSq[0]) {
                bestDistSq[0] = distSq;
                best[0] = entry.item;
            }
        }
        if (node.children != null) {
            for (QuadNode child : node.children) {
                Vec3 closestInChild = clampToAABB(point, child.bounds);
                if (point.distanceSquared(closestInChild) < bestDistSq[0]) {
                    nearestNode(child, point, best, bestDistSq);
                }
            }
        }
    }

    private void collectAllItems(QuadNode node, List<T> out) {
        for (ItemEntry<T> entry : node.<T>typedItems()) out.add(entry.item);
        if (node.children != null) {
            for (QuadNode child : node.children) collectAllItems(child, out);
        }
    }

    // --- Internal: count ---

    private int countAABBNode(QuadNode node, AABB range) {
        if (!node.bounds.intersects(range)) return 0;
        int count = 0;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (entry.bounds.intersects(range)) count++;
        }
        if (node.children != null) {
            for (QuadNode child : node.children) count += countAABBNode(child, range);
        }
        return count;
    }

    private int countSphereNode(QuadNode node, Sphere sphere) {
        if (!aabbIntersectsSphere(node.bounds, sphere)) return 0;
        int count = 0;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (aabbIntersectsSphere(entry.bounds, sphere)) count++;
        }
        if (node.children != null) {
            for (QuadNode child : node.children) count += countSphereNode(child, sphere);
        }
        return count;
    }

    private int countFrustumNode(QuadNode node, Frustum frustum) {
        ContainmentResult r = frustum.testAABB(node.bounds);
        if (r == ContainmentResult.OUTSIDE) return 0;
        if (r == ContainmentResult.INSIDE) return countAllItems(node);
        int count = 0;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (frustum.testAABB(entry.bounds) != ContainmentResult.OUTSIDE) count++;
        }
        if (node.children != null) {
            for (QuadNode child : node.children) count += countFrustumNode(child, frustum);
        }
        return count;
    }

    private int countAllItems(QuadNode node) {
        int count = node.<T>typedItems().size();
        if (node.children != null) {
            for (QuadNode child : node.children) count += countAllItems(child);
        }
        return count;
    }

    // --- Internal: geometry helpers ---

    /** Quadrant: 0=(-X,-Z), 1=(+X,-Z), 2=(-X,+Z), 3=(+X,+Z) */
    private static int getQuadrant(AABB parentBounds, Vec3 point) {
        Vec3 mid = parentBounds.center();
        int quadrant = 0;
        if (point.x >= mid.x) quadrant |= 1;
        if (point.z >= mid.z) quadrant |= 2;
        return quadrant;
    }

    private static AABB computeChildBounds(AABB parent, Vec3 mid, int quadrant) {
        float minX = (quadrant & 1) == 0 ? parent.min.x : mid.x;
        float maxX = (quadrant & 1) == 0 ? mid.x : parent.max.x;
        float minZ = (quadrant & 2) == 0 ? parent.min.z : mid.z;
        float maxZ = (quadrant & 2) == 0 ? mid.z : parent.max.z;
        // Y spans the full range (quadtree doesn't partition Y)
        return new AABB(new Vec3(minX, parent.min.y, minZ), new Vec3(maxX, parent.max.y, maxZ));
    }

    private static boolean containsBoundsXZ(AABB outer, AABB inner) {
        return inner.min.x >= outer.min.x && inner.max.x <= outer.max.x
            && inner.min.z >= outer.min.z && inner.max.z <= outer.max.z;
    }

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

    // --- Internal types ---

    private static class QuadNode {
        AABB bounds;
        int depth;
        int index;
        QuadNode[] children; // null if leaf, length 4 if split
        final List<ItemEntry<?>> items = new ArrayList<>();

        QuadNode(AABB bounds, int depth, int index) {
            this.bounds = bounds;
            this.depth = depth;
            this.index = index;
        }

        @SuppressWarnings("unchecked")
        <T> List<ItemEntry<T>> typedItems() {
            return (List<ItemEntry<T>>) (List<?>) items;
        }
    }

    private static class ItemEntry<T> {
        final T item;
        AABB bounds;
        QuadNode node;

        ItemEntry(T item, AABB bounds) {
            this.item = item;
            this.bounds = bounds;
        }
    }

    private static class DirtyTrackerImpl implements DirtyTracker {
        private final Set<Integer> dirtyNodes = new HashSet<>();
        private boolean fullRebuild = false;

        void markDirty(int nodeIndex) {
            if (!fullRebuild) dirtyNodes.add(nodeIndex);
        }

        void markFullRebuild() {
            fullRebuild = true;
            dirtyNodes.clear();
        }

        @Override public boolean isDirty() { return fullRebuild || !dirtyNodes.isEmpty(); }
        @Override public boolean isFullRebuild() { return fullRebuild; }
        @Override public int dirtyNodeCount() { return fullRebuild ? 0 : dirtyNodes.size(); }
        @Override public IntStream dirtyNodeIndices() { return fullRebuild ? IntStream.empty() : dirtyNodes.stream().mapToInt(Integer::intValue); }
        @Override public void clearDirty() { fullRebuild = false; dirtyNodes.clear(); }
    }
}
