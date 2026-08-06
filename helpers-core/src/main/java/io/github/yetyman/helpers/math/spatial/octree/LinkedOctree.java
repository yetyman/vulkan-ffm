package io.github.yetyman.helpers.math.spatial.octree;

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
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Pointer-based octree with configurable bucket strategy.
 * Supports incremental insert/remove/update with automatic split/merge.
 *
 * @param <T> the type of items stored
 */
public class LinkedOctree<T> implements SpatialStructure<T> {

    private final OctreeConfig config;
    private final OctreeNode root;
    private final Map<T, ItemEntry<T>> itemMap = new HashMap<>();
    private final DirtyTrackerImpl dirtyTracker = new DirtyTrackerImpl();
    private int nodeCount = 0;

    public LinkedOctree(OctreeConfig config) {
        this.config = config;
        this.root = new OctreeNode(config.worldBounds(), 0, nodeCount++);
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
    public void insertAll(Iterable<T> items, java.util.function.Function<T, AABB> boundsProvider) {
        for (T item : items) {
            AABB bounds = boundsProvider.apply(item);
            ItemEntry<T> entry = new ItemEntry<>(item, bounds);
            itemMap.put(item, entry);
            insertIntoNode(root, entry);
        }
        dirtyTracker.markFullRebuild();
    }

    @Override
    public void insertAll(java.util.Map<T, AABB> itemBounds) {
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
        OctreeNode node = entry.node;
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
        OctreeNode oldNode = entry.node;
        entry.bounds = newBounds;

        // If still fits in the same node, just update bounds
        if (oldNode != null && containsBounds(oldNode.bounds, newBounds)) {
            dirtyTracker.markDirty(oldNode.index);
            return;
        }

        // Remove from old location, reinsert
        if (oldNode != null) {
            oldNode.items.remove(entry);
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
    public static final GpuLayout<LinkedOctree<?>> DEFAULT_LAYOUT = new DfsAabbLayout();

    /** @return total serialized size in the default layout, which depends on node count. */
    public int gpuByteSize() {
        return nodeCount * 24;
    }

    /** Writes this tree into {@code dst} at {@code offset} using the default layout. */
    @SuppressWarnings("unchecked")
    public void writeTo(java.lang.foreign.MemorySegment dst, long offset) {
        ((GpuLayout<LinkedOctree<T>>) (GpuLayout<?>) DEFAULT_LAYOUT).writeTo(this, dst, offset);
    }

    /** Writes this tree into {@code dst} at {@code offset} using an alternative layout. */
    public void writeTo(java.lang.foreign.MemorySegment dst, long offset, GpuLayout<LinkedOctree<T>> layout) {
        layout.writeTo(this, dst, offset);
    }

    // Package-private for layout access
    OctreeNode root() { return root; }
    int nodeCount() { return nodeCount; }

    /**
     * Enforces 2:1 balance — no two adjacent leaf cells can differ by more than 1 level of depth.
     * Subdivides shallow cells adjacent to deep cells until the constraint is satisfied.
     * Call after all insertions and before marching/serialization.
     */
    public void balance() {
        boolean changed = true;
        while (changed) {
            changed = balancePass(root);
        }
        dirtyTracker.markFullRebuild();
    }

    private boolean balancePass(OctreeNode node) {
        if (node.children == null) return false; // leaf — nothing to balance internally

        boolean changed = false;
        for (OctreeNode child : node.children) {
            if (child.children != null) {
                // Recurse into subdivided children
                if (balancePass(child)) changed = true;
            }
        }

        // For each leaf child, check if any of its face-adjacent neighbors (within this parent)
        // are subdivided more than 1 level deeper. If so, subdivide this leaf.
        for (int i = 0; i < 8; i++) {
            OctreeNode child = node.children[i];
            if (child.children != null) continue; // already subdivided

            // Check face-adjacent siblings (share a face in the parent's octants)
            int[][] adjacentOctants = getAdjacentOctants(i);
            for (int[] adj : adjacentOctants) {
                int sibIdx = adj[0];
                if (sibIdx < 0 || sibIdx >= 8) continue;
                OctreeNode sibling = node.children[sibIdx];
                if (sibling.children != null && hasSubdividedChildren(sibling)) {
                    // Sibling is 2+ levels deeper — force subdivide this child
                    forceSubdivide(child);
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    private boolean hasSubdividedChildren(OctreeNode node) {
        if (node.children == null) return false;
        for (OctreeNode child : node.children) {
            if (child.children != null) return true;
        }
        return false;
    }

    private void forceSubdivide(OctreeNode node) {
        if (node.children != null) return;
        node.children = new OctreeNode[8];
        AABB b = node.bounds;
        Vec3 mid = b.center();
        for (int i = 0; i < 8; i++) {
            AABB childBounds = computeChildBounds(b, mid, i);
            node.children[i] = new OctreeNode(childBounds, node.depth + 1, nodeCount++);
        }
        // Move items to appropriate children
        List<ItemEntry<T>> items = new ArrayList<>(node.<T>typedItems());
        node.<T>typedItems().clear();
        for (ItemEntry<T> entry : items) {
            entry.node = null;
            insertIntoNode(node, entry);
        }
    }

    /**
     * Returns which octant indices are face-adjacent to the given octant.
     * Each octant has 3 face-adjacent siblings (sharing X, Y, or Z face).
     */
    private static int[][] getAdjacentOctants(int octant) {
        // Octant bits: X=bit0, Y=bit1, Z=bit2
        // Face-adjacent = flip one bit
        return new int[][]{
                {octant ^ 1}, // flip X
                {octant ^ 2}, // flip Y
                {octant ^ 4}  // flip Z
        };
    }

    @Override
    public void visitNodes(io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        visitNodeRecursive(root, visitor);
    }

    private void visitNodeRecursive(OctreeNode node, io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        boolean isLeaf = node.children == null;
        visitor.visit(node.bounds, node.depth, isLeaf, node.<T>typedItems().size());
        if (node.children != null) {
            for (OctreeNode child : node.children) visitNodeRecursive(child, visitor);
        }
    }

    private static class DfsAabbLayout implements GpuLayout<LinkedOctree<?>> {
        /** Variable size: depends on node count. Use {@link LinkedOctree#gpuByteSize()} for the total. */
        @Override public int byteSize() { return -1; }
        @Override public void writeTo(LinkedOctree<?> tree, java.lang.foreign.MemorySegment dst, long offset) {
            writeDfs(tree.root(), dst, offset);
        }
        @Override public void readFrom(LinkedOctree<?> tree, java.lang.foreign.MemorySegment src, long offset) {
            throw new UnsupportedOperationException("Octree cannot be deserialized -- rebuild from items instead.");
        }
        private long writeDfs(OctreeNode node, java.lang.foreign.MemorySegment dst, long o) {
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o, node.bounds.min.x);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 4, node.bounds.min.y);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 8, node.bounds.min.z);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 12, node.bounds.max.x);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 16, node.bounds.max.y);
            dst.set(java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED, o + 20, node.bounds.max.z);
            o += 24;
            if (node.children != null) {
                for (OctreeNode child : node.children) o = writeDfs(child, dst, o);
            }
            return o;
        }
    }

    // --- SpatialQuery ---

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

    // --- Stream queries ---

    @Override
    public Stream<T> queryStream(AABB range) {
        return query(range).stream();
    }

    @Override
    public Stream<T> queryStream(Sphere range) {
        return query(range).stream();
    }

    @Override
    public Stream<T> queryStream(Ray ray, float maxDistance) {
        return query(ray, maxDistance).stream();
    }

    @Override
    public Stream<T> queryFrustumStream(Frustum frustum) {
        return queryFrustum(frustum).stream();
    }

    @Override
    public boolean contains(Vec3 point) {
        return containsPointNode(root, point);
    }

    @Override
    public T nearest(Vec3 point) {
        T[] best = (T[]) new Object[1];
        float[] bestDistSq = {Float.MAX_VALUE};
        nearestNode(root, point, best, bestDistSq);
        return best[0];
    }

    // --- Count queries ---

    @Override
    public int count(AABB range) {
        return countAABBNode(root, range);
    }

    @Override
    public int count(Sphere range) {
        return countSphereNode(root, range);
    }

    @Override
    public int countFrustum(Frustum frustum) {
        return countFrustumNode(root, frustum);
    }

    // --- Internal: insertion ---

    private void insertIntoNode(OctreeNode node, ItemEntry<T> entry) {
        if (config.strategy() == OctreeConfig.BucketStrategy.LOOSE) {
            insertLoose(node, entry);
        } else {
            insertPointRegion(node, entry);
        }
    }

    private void insertLoose(OctreeNode node, ItemEntry<T> entry) {
        // In loose mode, item goes into the node whose original bounds contain its center
        Vec3 center = entry.bounds.center();
        OctreeNode target = findLooseNode(node, center, 0);
        target.<T>typedItems().add(entry);
        entry.node = target;

        if (target.children == null && target.<T>typedItems().size() > config.splitThreshold() && target.depth < config.maxDepth()) {
            split(target);
        }
    }

    private void insertPointRegion(OctreeNode node, ItemEntry<T> entry) {
        Vec3 center = entry.bounds.center();
        OctreeNode target = findPointRegionNode(node, center, 0);
        target.<T>typedItems().add(entry);
        entry.node = target;

        if (target.children == null && target.<T>typedItems().size() > config.splitThreshold() && target.depth < config.maxDepth()) {
            split(target);
        }
    }

    private OctreeNode findLooseNode(OctreeNode node, Vec3 center, int depth) {
        if (node.children == null || depth >= config.maxDepth()) return node;
        int octant = getOctant(node.bounds, center);
        return findLooseNode(node.children[octant], center, depth + 1);
    }

    private OctreeNode findPointRegionNode(OctreeNode node, Vec3 center, int depth) {
        if (node.children == null || depth >= config.maxDepth()) return node;
        int octant = getOctant(node.bounds, center);
        return findPointRegionNode(node.children[octant], center, depth + 1);
    }

    // --- Internal: split/merge ---

    private void split(OctreeNode node) {
        node.children = new OctreeNode[8];
        AABB b = node.bounds;
        Vec3 mid = b.center();

        for (int i = 0; i < 8; i++) {
            AABB childBounds = computeChildBounds(b, mid, i);
            node.children[i] = new OctreeNode(childBounds, node.depth + 1, nodeCount++);
        }

        // Redistribute items
        List<ItemEntry<T>> items = new ArrayList<>(node.<T>typedItems());
        node.<T>typedItems().clear();
        for (ItemEntry<T> entry : items) {
            entry.node = null;
            insertIntoNode(node, entry);
        }
        dirtyTracker.markDirty(node.index);
    }

    private void tryMerge(OctreeNode node) {
        if (node.children == null) return;
        int totalItems = countItemsRecursive(node);
        if (totalItems <= config.mergeThreshold()) {
            // Collect all items from children into this node
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

    private int countItemsRecursive(OctreeNode node) {
        int count = node.<T>typedItems().size();
        if (node.children != null) {
            for (OctreeNode child : node.children) {
                count += countItemsRecursive(child);
            }
        }
        return count;
    }

    private void collectItemsRecursive(OctreeNode node, List<ItemEntry<T>> out) {
        out.addAll(node.<T>typedItems());
        if (node.children != null) {
            for (OctreeNode child : node.children) {
                collectItemsRecursive(child, out);
            }
        }
    }

    // --- Internal: queries ---

    private void queryNode(OctreeNode node, AABB range, List<T> out) {
        if (!node.bounds.intersects(range)) return;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (entry.bounds.intersects(range)) out.add(entry.item);
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) {
                queryNode(child, range, out);
            }
        }
    }

    private void querySphereNode(OctreeNode node, Sphere sphere, List<T> out) {
        // Quick reject: if node AABB doesn't intersect sphere
        if (!aabbIntersectsSphere(node.bounds, sphere)) return;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (aabbIntersectsSphere(entry.bounds, sphere)) out.add(entry.item);
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) {
                querySphereNode(child, sphere, out);
            }
        }
    }

    private void queryRayNode(OctreeNode node, Ray ray, float maxDist, List<T> out) {
        // If ray origin is inside the node, t will be the exit distance (which may exceed maxDist).
        // We should still traverse this node since the ray starts inside it.
        if (!rayIntersectsWithin(ray, node.bounds, maxDist)) return;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            float et = Intersections.rayAABB(ray, entry.bounds);
            if (et >= 0 && et <= maxDist) out.add(entry.item);
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) {
                queryRayNode(child, ray, maxDist, out);
            }
        }
    }

    /**
     * Returns true if the ray intersects the AABB and the entry point is within maxDist,
     * OR if the ray origin is inside the AABB.
     */
    private static boolean rayIntersectsWithin(Ray ray, AABB aabb, float maxDist) {
        // Quick check: is origin inside?
        if (aabb.contains(ray.origin)) return true;
        float t = Intersections.rayAABB(ray, aabb);
        return t >= 0 && t <= maxDist;
    }

    private void queryFrustumNode(OctreeNode node, Frustum frustum, List<T> out) {
        ContainmentResult r = frustum.testAABB(node.bounds);
        if (r == ContainmentResult.OUTSIDE) return;
        if (r == ContainmentResult.INSIDE) {
            // All items in this subtree are visible
            collectAllItems(node, out);
            return;
        }
        // INTERSECT — test individual items
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (frustum.testAABB(entry.bounds) != ContainmentResult.OUTSIDE) {
                out.add(entry.item);
            }
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) {
                queryFrustumNode(child, frustum, out);
            }
        }
    }

    private boolean containsPointNode(OctreeNode node, Vec3 point) {
        if (!node.bounds.contains(point)) return false;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (entry.bounds.contains(point)) return true;
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) {
                if (containsPointNode(child, point)) return true;
            }
        }
        return false;
    }

    private void nearestNode(OctreeNode node, Vec3 point, T[] best, float[] bestDistSq) {
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            Vec3 closest = clampToAABB(point, entry.bounds);
            float distSq = point.distanceSquared(closest);
            if (distSq < bestDistSq[0]) {
                bestDistSq[0] = distSq;
                best[0] = entry.item;
            }
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) {
                // Only descend if the child could contain something closer
                Vec3 closestInChild = clampToAABB(point, child.bounds);
                if (point.distanceSquared(closestInChild) < bestDistSq[0]) {
                    nearestNode(child, point, best, bestDistSq);
                }
            }
        }
    }

    private void collectAllItems(OctreeNode node, List<T> out) {
        for (ItemEntry<T> entry : node.<T>typedItems()) out.add(entry.item);
        if (node.children != null) {
            for (OctreeNode child : node.children) collectAllItems(child, out);
        }
    }

    private int countAABBNode(OctreeNode node, AABB range) {
        if (!node.bounds.intersects(range)) return 0;
        int count = 0;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (entry.bounds.intersects(range)) count++;
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) count += countAABBNode(child, range);
        }
        return count;
    }

    private int countSphereNode(OctreeNode node, Sphere sphere) {
        if (!aabbIntersectsSphere(node.bounds, sphere)) return 0;
        int count = 0;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (aabbIntersectsSphere(entry.bounds, sphere)) count++;
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) count += countSphereNode(child, sphere);
        }
        return count;
    }

    private int countFrustumNode(OctreeNode node, Frustum frustum) {
        ContainmentResult r = frustum.testAABB(node.bounds);
        if (r == ContainmentResult.OUTSIDE) return 0;
        if (r == ContainmentResult.INSIDE) return countAllItems(node);
        int count = 0;
        for (ItemEntry<T> entry : node.<T>typedItems()) {
            if (frustum.testAABB(entry.bounds) != ContainmentResult.OUTSIDE) count++;
        }
        if (node.children != null) {
            for (OctreeNode child : node.children) count += countFrustumNode(child, frustum);
        }
        return count;
    }

    private int countAllItems(OctreeNode node) {
        int count = node.<T>typedItems().size();
        if (node.children != null) {
            for (OctreeNode child : node.children) count += countAllItems(child);
        }
        return count;
    }

    // --- Internal: geometry helpers ---

    private static int getOctant(AABB parentBounds, Vec3 point) {
        Vec3 mid = parentBounds.center();
        int octant = 0;
        if (point.x >= mid.x) octant |= 1;
        if (point.y >= mid.y) octant |= 2;
        if (point.z >= mid.z) octant |= 4;
        return octant;
    }

    private static AABB computeChildBounds(AABB parent, Vec3 mid, int octant) {
        float minX = (octant & 1) == 0 ? parent.min.x : mid.x;
        float minY = (octant & 2) == 0 ? parent.min.y : mid.y;
        float minZ = (octant & 4) == 0 ? parent.min.z : mid.z;
        float maxX = (octant & 1) == 0 ? mid.x : parent.max.x;
        float maxY = (octant & 2) == 0 ? mid.y : parent.max.y;
        float maxZ = (octant & 4) == 0 ? mid.z : parent.max.z;
        return new AABB(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    private static boolean containsBounds(AABB outer, AABB inner) {
        return inner.min.x >= outer.min.x && inner.max.x <= outer.max.x
            && inner.min.y >= outer.min.y && inner.max.y <= outer.max.y
            && inner.min.z >= outer.min.z && inner.max.z <= outer.max.z;
    }

    private static boolean aabbIntersectsSphere(AABB aabb, Sphere sphere) {
        Vec3 closest = clampToAABB(sphere.center, aabb);
        return sphere.center.distanceSquared(closest) <= sphere.radius * sphere.radius;
    }

    private static Vec3 clampToAABB(Vec3 point, AABB aabb) {
        return new Vec3(
                Math.max(aabb.min.x, Math.min(point.x, aabb.max.x)),
                Math.max(aabb.min.y, Math.min(point.y, aabb.max.y)),
                Math.max(aabb.min.z, Math.min(point.z, aabb.max.z))
        );
    }

    // --- Internal types ---

    private static class OctreeNode {
        AABB bounds;
        int depth;
        int index;
        OctreeNode[] children; // null if leaf
        final List<ItemEntry<?>> items = new ArrayList<>();

        OctreeNode(AABB bounds, int depth, int index) {
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
        OctreeNode node;

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
