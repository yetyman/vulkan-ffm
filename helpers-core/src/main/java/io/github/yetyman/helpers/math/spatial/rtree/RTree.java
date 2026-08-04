package io.github.yetyman.helpers.math.spatial.rtree;

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
 * Balanced R-tree for spatial queries. All leaves at the same depth.
 * Supports configurable split strategy (quadratic or linear).
 *
 * @param <T> the type of items stored
 */
public class RTree<T> implements SpatialStructure<T> {

    private final RTreeConfig config;
    private RNode root;
    private final Map<T, REntry<T>> entryMap = new HashMap<>();
    private final Map<T, RNode> leafMap = new HashMap<>();
    private final DirtyTrackerImpl dirtyTracker = new DirtyTrackerImpl();
    private int nodeCount = 0;

    public RTree() {
        this(RTreeConfig.builder().build());
    }

    public RTree(RTreeConfig config) {
        this.config = config;
        this.root = new RNode(true, nodeCount++);
    }

    // --- SpatialStructure ---

    @Override
    public void insert(T item, AABB bounds) {
        if (entryMap.containsKey(item)) { update(item, bounds); return; }
        REntry<T> entry = new REntry<>(item, bounds);
        entryMap.put(item, entry);
        RNode leaf = chooseLeaf(root, entry);
        leaf.<T>typedEntries().add(entry);
        leafMap.put(item, leaf);
        expandUpward(leaf, bounds);
        if (leaf.<T>typedEntries().size() > config.maxChildren()) {
            splitAndPropagate(leaf);
        }
        dirtyTracker.markDirty(leaf.index);
    }

    @Override
    public void insertAll(Iterable<T> items, Function<T, AABB> boundsProvider) {
        for (T item : items) insert(item, boundsProvider.apply(item));
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void insertAll(Map<T, AABB> itemBounds) {
        for (var e : itemBounds.entrySet()) insert(e.getKey(), e.getValue());
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void remove(T item) {
        REntry<T> entry = entryMap.remove(item);
        if (entry == null) return;
        RNode leaf = leafMap.remove(item);
        if (leaf != null) {
            leaf.<T>typedEntries().remove(entry);
            condenseTree(leaf);
            dirtyTracker.markDirty(leaf.index);
        }
    }

    @Override
    public void update(T item, AABB newBounds) {
        remove(item);
        insert(item, newBounds);
    }

    @Override
    public void rebuild() {
        List<REntry<T>> all = new ArrayList<>(entryMap.values());
        entryMap.clear();
        leafMap.clear();
        nodeCount = 0;
        root = new RNode(true, nodeCount++);
        for (REntry<T> e : all) {
            entryMap.put(e.item, e);
            RNode leaf = chooseLeaf(root, e);
            leaf.<T>typedEntries().add(e);
            leafMap.put(e.item, leaf);
            expandUpward(leaf, e.bounds);
            if (leaf.<T>typedEntries().size() > config.maxChildren()) splitAndPropagate(leaf);
        }
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void clear() {
        entryMap.clear();
        leafMap.clear();
        nodeCount = 0;
        root = new RNode(true, nodeCount++);
        dirtyTracker.markFullRebuild();
    }

    @Override
    public int size() { return entryMap.size(); }

    @Override
    public AABB worldBounds() { return root.bounds(); }

    @Override
    public DirtyTracker dirtyTracker() { return dirtyTracker; }

    // --- BufferWritable ---

    public static final GpuLayout<RTree<?>> DEFAULT_LAYOUT = new DfsLayout();

    @Override
    public int byteSize() { return nodeCount * 24; }

    @SuppressWarnings("unchecked")
    @Override
    public void writeTo(ByteBuffer buf) {
        ((GpuLayout<RTree<T>>) (GpuLayout<?>) DEFAULT_LAYOUT).writeTo(this, buf);
    }

    @Override
    public void readFrom(ByteBuffer buf) {
        throw new UnsupportedOperationException("RTree does not support readFrom -- rebuild from items instead.");
    }

    public void writeTo(ByteBuffer buf, GpuLayout<RTree<T>> layout) { layout.writeTo(this, buf); }

    // --- SpatialQuery ---

    @Override public List<T> query(AABB range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public List<T> query(Sphere range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public List<T> query(Ray ray, float maxDistance) { List<T> out = new ArrayList<>(); queryRayNode(root, ray, maxDistance, out); return out; }
    @Override public List<T> queryFrustum(Frustum frustum) { List<T> out = new ArrayList<>(); queryFrustum(frustum, out); return out; }

    @Override
    public int query(AABB range, List<T> out) { int b = out.size(); queryAABBNode(root, range, out); return out.size() - b; }
    @Override
    public int query(Sphere range, List<T> out) { int b = out.size(); querySphereNode(root, range, out); return out.size() - b; }
    @Override
    public int queryFrustum(Frustum frustum, List<T> out) { int b = out.size(); queryFrustumNode(root, frustum, out); return out.size() - b; }

    @Override public Stream<T> queryStream(AABB range) { return query(range).stream(); }
    @Override public Stream<T> queryStream(Sphere range) { return query(range).stream(); }
    @Override public Stream<T> queryStream(Ray ray, float maxDistance) { return query(ray, maxDistance).stream(); }
    @Override public Stream<T> queryFrustumStream(Frustum frustum) { return queryFrustum(frustum).stream(); }

    @Override
    public boolean contains(Vec3 point) { return containsNode(root, point); }

    @Override
    public T nearest(Vec3 point) {
        if (entryMap.isEmpty()) return null;
        T best = null; float bestDist = Float.MAX_VALUE;
        for (REntry<T> e : entryMap.values()) {
            Vec3 c = clampToAABB(point, e.bounds);
            float d = point.distanceSquared(c);
            if (d < bestDist) { bestDist = d; best = e.item; }
        }
        return best;
    }

    @Override public int count(AABB range) { return countAABBNode(root, range); }
    @Override public int count(Sphere range) { return countSphereNode(root, range); }
    @Override public int countFrustum(Frustum frustum) { return countFrustumNode(root, frustum); }

    // --- Internal: tree operations ---

    private RNode chooseLeaf(RNode node, REntry<T> entry) {
        if (node.isLeaf) return node;
        RNode best = null;
        float bestEnlargement = Float.POSITIVE_INFINITY;
        float bestVolume = Float.POSITIVE_INFINITY;
        for (RNode child : node.children) {
            float enlarged = enlargedVolume(child.bounds(), entry.bounds);
            float enlargement = enlarged - volume(child.bounds());
            if (enlargement < bestEnlargement || (enlargement == bestEnlargement && volume(child.bounds()) < bestVolume)) {
                best = child; bestEnlargement = enlargement; bestVolume = volume(child.bounds());
            }
        }
        return chooseLeaf(best, entry);
    }

    private void expandUpward(RNode node, AABB newBounds) {
        RNode cur = node;
        while (cur != null) {
            cur.expandBounds(newBounds);
            cur = cur.parent;
        }
    }

    private void splitAndPropagate(RNode node) {
        RNode sibling = split(node);
        if (node == root) {
            RNode newRoot = new RNode(false, nodeCount++);
            newRoot.children.add(node); node.parent = newRoot;
            newRoot.children.add(sibling); sibling.parent = newRoot;
            newRoot.recomputeBounds();
            root = newRoot;
        } else {
            RNode parent = node.parent;
            sibling.parent = parent;
            parent.children.add(sibling);
            parent.recomputeBounds();
            if (parent.children.size() > config.maxChildren()) splitAndPropagate(parent);
        }
    }

    private RNode split(RNode node) {
        RNode sibling = new RNode(node.isLeaf, nodeCount++);
        sibling.parent = node.parent;

        if (node.isLeaf) {
            List<REntry<T>> all = new ArrayList<>(node.<T>typedEntries());
            node.<T>typedEntries().clear();
            int[] seeds = pickSeeds(all);
            node.<T>typedEntries().add(all.get(seeds[0])); leafMap.put(all.get(seeds[0]).item, node);
            sibling.<T>typedEntries().add(all.get(seeds[1])); leafMap.put(all.get(seeds[1]).item, sibling);
            List<REntry<T>> remaining = new ArrayList<>(all);
            remaining.remove(all.get(seeds[0])); remaining.remove(all.get(seeds[1]));
            for (REntry<T> e : remaining) {
                RNode target = pickTarget(node, sibling, e.bounds);
                target.<T>typedEntries().add(e);
                leafMap.put(e.item, target);
            }
        } else {
            List<RNode> all = new ArrayList<>(node.children);
            node.children.clear();
            // Use first two as seeds (simplified)
            node.children.add(all.get(0)); all.get(0).parent = node;
            sibling.children.add(all.get(1)); all.get(1).parent = sibling;
            for (int i = 2; i < all.size(); i++) {
                RNode target = pickTargetNode(node, sibling, all.get(i).bounds());
                target.children.add(all.get(i)); all.get(i).parent = target;
            }
        }
        node.recomputeBounds();
        sibling.recomputeBounds();
        return sibling;
    }

    private int[] pickSeeds(List<REntry<T>> entries) {
        int i1 = 0, i2 = 1;
        float maxWaste = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                float combined = enlargedVolume(entries.get(i).bounds, entries.get(j).bounds);
                float waste = combined - volume(entries.get(i).bounds) - volume(entries.get(j).bounds);
                if (waste > maxWaste) { maxWaste = waste; i1 = i; i2 = j; }
            }
        }
        return new int[]{i1, i2};
    }

    private RNode pickTarget(RNode a, RNode b, AABB bounds) {
        float enlA = enlargedVolume(a.bounds(), bounds) - volume(a.bounds());
        float enlB = enlargedVolume(b.bounds(), bounds) - volume(b.bounds());
        if (enlA < enlB) return a;
        if (enlB < enlA) return b;
        return a.size() <= b.size() ? a : b;
    }

    private RNode pickTargetNode(RNode a, RNode b, AABB bounds) {
        return pickTarget(a, b, bounds);
    }

    private void condenseTree(RNode node) {
        List<REntry<T>> reinsert = new ArrayList<>();
        RNode cur = node;
        while (cur != root) {
            RNode parent = cur.parent;
            if (cur.size() < config.minChildren()) {
                parent.children.remove(cur);
                if (cur.isLeaf) reinsert.addAll(cur.<T>typedEntries());
                else collectAllEntries(cur, reinsert);
            } else {
                cur.recomputeBounds();
            }
            cur = parent;
        }
        root.recomputeBounds();
        for (REntry<T> e : reinsert) {
            RNode leaf = chooseLeaf(root, e);
            leaf.<T>typedEntries().add(e);
            leafMap.put(e.item, leaf);
            expandUpward(leaf, e.bounds);
            if (leaf.<T>typedEntries().size() > config.maxChildren()) splitAndPropagate(leaf);
        }
        // Shrink root if needed
        while (!root.isLeaf && root.children.size() == 1) {
            root = root.children.get(0);
            root.parent = null;
        }
    }

    private void collectAllEntries(RNode node, List<REntry<T>> out) {
        if (node.isLeaf) out.addAll(node.<T>typedEntries());
        else for (RNode c : node.children) collectAllEntries(c, out);
    }

    // --- Internal: queries ---

    private void queryAABBNode(RNode node, AABB range, List<T> out) {
        if (!node.bounds().intersects(range)) return;
        if (node.isLeaf) {
            for (REntry<T> e : node.<T>typedEntries()) if (e.bounds.intersects(range)) out.add(e.item);
        } else {
            for (RNode c : node.children) queryAABBNode(c, range, out);
        }
    }

    private void querySphereNode(RNode node, Sphere sphere, List<T> out) {
        if (!aabbIntersectsSphere(node.bounds(), sphere)) return;
        if (node.isLeaf) {
            for (REntry<T> e : node.<T>typedEntries()) if (aabbIntersectsSphere(e.bounds, sphere)) out.add(e.item);
        } else {
            for (RNode c : node.children) querySphereNode(c, sphere, out);
        }
    }

    private void queryRayNode(RNode node, Ray ray, float maxDist, List<T> out) {
        if (!rayIntersectsWithin(ray, node.bounds(), maxDist)) return;
        if (node.isLeaf) {
            for (REntry<T> e : node.<T>typedEntries()) {
                float t = Intersections.rayAABB(ray, e.bounds);
                if (t >= 0 && t <= maxDist) out.add(e.item);
            }
        } else {
            for (RNode c : node.children) queryRayNode(c, ray, maxDist, out);
        }
    }

    private void queryFrustumNode(RNode node, Frustum frustum, List<T> out) {
        ContainmentResult r = frustum.testAABB(node.bounds());
        if (r == ContainmentResult.OUTSIDE) return;
        if (node.isLeaf) {
            if (r == ContainmentResult.INSIDE) { for (REntry<T> e : node.<T>typedEntries()) out.add(e.item); }
            else { for (REntry<T> e : node.<T>typedEntries()) if (frustum.testAABB(e.bounds) != ContainmentResult.OUTSIDE) out.add(e.item); }
        } else {
            for (RNode c : node.children) queryFrustumNode(c, frustum, out);
        }
    }

    private boolean containsNode(RNode node, Vec3 point) {
        if (!node.bounds().contains(point)) return false;
        if (node.isLeaf) {
            for (REntry<T> e : node.<T>typedEntries()) if (e.bounds.contains(point)) return true;
            return false;
        }
        for (RNode c : node.children) if (containsNode(c, point)) return true;
        return false;
    }

    private int countAABBNode(RNode node, AABB range) {
        if (!node.bounds().intersects(range)) return 0;
        if (node.isLeaf) { int c = 0; for (REntry<T> e : node.<T>typedEntries()) if (e.bounds.intersects(range)) c++; return c; }
        int c = 0; for (RNode ch : node.children) c += countAABBNode(ch, range); return c;
    }

    private int countSphereNode(RNode node, Sphere sphere) {
        if (!aabbIntersectsSphere(node.bounds(), sphere)) return 0;
        if (node.isLeaf) { int c = 0; for (REntry<T> e : node.<T>typedEntries()) if (aabbIntersectsSphere(e.bounds, sphere)) c++; return c; }
        int c = 0; for (RNode ch : node.children) c += countSphereNode(ch, sphere); return c;
    }

    private int countFrustumNode(RNode node, Frustum frustum) {
        ContainmentResult r = frustum.testAABB(node.bounds());
        if (r == ContainmentResult.OUTSIDE) return 0;
        if (node.isLeaf) {
            if (r == ContainmentResult.INSIDE) return node.<T>typedEntries().size();
            int c = 0; for (REntry<T> e : node.<T>typedEntries()) if (frustum.testAABB(e.bounds) != ContainmentResult.OUTSIDE) c++; return c;
        }
        int c = 0; for (RNode ch : node.children) c += countFrustumNode(ch, frustum); return c;
    }

    // --- Geometry helpers ---

    private static float volume(AABB a) {
        if (a == null) return 0f;
        float dx = a.max.x - a.min.x, dy = a.max.y - a.min.y, dz = a.max.z - a.min.z;
        return dx * dy * dz;
    }

    private static float enlargedVolume(AABB a, AABB b) {
        if (a == null) return volume(b);
        float minX = Math.min(a.min.x, b.min.x), minY = Math.min(a.min.y, b.min.y), minZ = Math.min(a.min.z, b.min.z);
        float maxX = Math.max(a.max.x, b.max.x), maxY = Math.max(a.max.y, b.max.y), maxZ = Math.max(a.max.z, b.max.z);
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    private static boolean aabbIntersectsSphere(AABB aabb, Sphere sphere) {
        Vec3 closest = clampToAABB(sphere.center, aabb);
        return sphere.center.distanceSquared(closest) <= sphere.radius * sphere.radius;
    }

    private static boolean rayIntersectsWithin(Ray ray, AABB aabb, float maxDist) {
        if (aabb == null) return false;
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

    private static class RNode {
        boolean isLeaf;
        int index;
        RNode parent;
        final List<REntry<?>> entries = new ArrayList<>(); // leaf only
        final List<RNode> children = new ArrayList<>(); // internal only
        private AABB cachedBounds;

        RNode(boolean isLeaf, int index) { this.isLeaf = isLeaf; this.index = index; }

        @SuppressWarnings("unchecked")
        <T> List<REntry<T>> typedEntries() { return (List<REntry<T>>) (List<?>) entries; }

        int size() { return isLeaf ? entries.size() : children.size(); }

        AABB bounds() {
            if (cachedBounds == null) recomputeBounds();
            return cachedBounds;
        }

        void expandBounds(AABB b) {
            if (cachedBounds == null) { cachedBounds = new AABB(new Vec3(b.min), new Vec3(b.max)); return; }
            cachedBounds.min.min(b.min);
            cachedBounds.max.max(b.max);
        }

        void recomputeBounds() {
            cachedBounds = null;
            if (isLeaf) {
                if (entries.isEmpty()) { cachedBounds = new AABB(new Vec3(), new Vec3()); return; }
                Vec3 min = new Vec3(Float.POSITIVE_INFINITY); Vec3 max = new Vec3(Float.NEGATIVE_INFINITY);
                for (REntry<?> e : entries) { min.min(e.bounds.min); max.max(e.bounds.max); }
                cachedBounds = new AABB(min, max);
            } else {
                if (children.isEmpty()) { cachedBounds = new AABB(new Vec3(), new Vec3()); return; }
                Vec3 min = new Vec3(Float.POSITIVE_INFINITY); Vec3 max = new Vec3(Float.NEGATIVE_INFINITY);
                for (RNode c : children) { AABB cb = c.bounds(); min.min(cb.min); max.max(cb.max); }
                cachedBounds = new AABB(min, max);
            }
        }
    }

    private static class REntry<T> {
        final T item;
        AABB bounds;
        REntry(T item, AABB bounds) { this.item = item; this.bounds = bounds; }
    }

    // --- Layout ---

    private static class DfsLayout implements GpuLayout<RTree<?>> {
        @Override public int byteSize() { return -1; }
        @Override public void writeTo(RTree<?> tree, ByteBuffer buf) { writeDfs(tree.root, buf); }
        @Override public void readFrom(RTree<?> tree, ByteBuffer buf) { throw new UnsupportedOperationException(); }
        private void writeDfs(RNode node, ByteBuffer buf) {
            AABB b = node.bounds();
            buf.putFloat(b.min.x); buf.putFloat(b.min.y); buf.putFloat(b.min.z);
            buf.putFloat(b.max.x); buf.putFloat(b.max.y); buf.putFloat(b.max.z);
            if (!node.isLeaf) { for (RNode c : node.children) writeDfs(c, buf); }
        }
    }

    // --- DirtyTracker ---

    private static class DirtyTrackerImpl implements DirtyTracker {
        private final Set<Integer> dirtyNodes = new HashSet<>();
        private boolean fullRebuild = false;
        void markDirty(int idx) { if (!fullRebuild) dirtyNodes.add(idx); }
        void markFullRebuild() { fullRebuild = true; dirtyNodes.clear(); }
        @Override public boolean isDirty() { return fullRebuild || !dirtyNodes.isEmpty(); }
        @Override public boolean isFullRebuild() { return fullRebuild; }
        @Override public int dirtyNodeCount() { return fullRebuild ? 0 : dirtyNodes.size(); }
        @Override public IntStream dirtyNodeIndices() { return fullRebuild ? IntStream.empty() : dirtyNodes.stream().mapToInt(Integer::intValue); }
        @Override public void clearDirty() { fullRebuild = false; dirtyNodes.clear(); }
    }
}
