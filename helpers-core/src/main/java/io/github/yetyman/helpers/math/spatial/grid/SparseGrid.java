package io.github.yetyman.helpers.math.spatial.grid;

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
 * Sparse spatial grid — only occupied cells are stored (hash map).
 * O(1) insert/remove/cell-lookup, O(N) clear where N = number of entries (not grid size).
 * Good for large worlds where most cells are empty.
 *
 * @param <T> the type of items stored
 */
public class SparseGrid<T> implements SpatialStructure<T> {

    private final float cellSize;
    private final Map<Long, List<Entry<T>>> cells = new HashMap<>();
    private final Map<T, Entry<T>> itemMap = new HashMap<>();
    private final DirtyTrackerImpl dirtyTracker = new DirtyTrackerImpl();

    public SparseGrid(float cellSize) {
        this.cellSize = cellSize;
    }

    // --- SpatialStructure ---

    @Override
    public void insert(T item, AABB bounds) {
        if (itemMap.containsKey(item)) { update(item, bounds); return; }
        Entry<T> entry = new Entry<>(item, bounds);
        itemMap.put(item, entry);
        int minCX = cellCoord(bounds.min.x), minCY = cellCoord(bounds.min.y), minCZ = cellCoord(bounds.min.z);
        int maxCX = cellCoord(bounds.max.x), maxCY = cellCoord(bounds.max.y), maxCZ = cellCoord(bounds.max.z);
        for (int cx = minCX; cx <= maxCX; cx++)
            for (int cy = minCY; cy <= maxCY; cy++)
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    long key = cellKey(cx, cy, cz);
                    cells.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
                    entry.cellKeys.add(key);
                }
        dirtyTracker.markDirty(0);
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
        Entry<T> entry = itemMap.remove(item);
        if (entry == null) return;
        for (long key : entry.cellKeys) {
            List<Entry<T>> cell = cells.get(key);
            if (cell != null) {
                cell.remove(entry);
                if (cell.isEmpty()) cells.remove(key);
            }
        }
        dirtyTracker.markDirty(0);
    }

    @Override
    public void update(T item, AABB newBounds) {
        remove(item);
        insert(item, newBounds);
    }

    @Override
    public void rebuild() { dirtyTracker.markFullRebuild(); }

    @Override
    public void clear() {
        cells.clear();
        itemMap.clear();
        dirtyTracker.markFullRebuild();
    }

    @Override
    public int size() { return itemMap.size(); }

    @Override
    public AABB worldBounds() {
        if (itemMap.isEmpty()) return new AABB(new Vec3(), new Vec3());
        Vec3 min = new Vec3(Float.POSITIVE_INFINITY);
        Vec3 max = new Vec3(Float.NEGATIVE_INFINITY);
        for (Entry<T> e : itemMap.values()) { min.min(e.bounds.min); max.max(e.bounds.max); }
        return new AABB(min, max);
    }

    @Override
    public DirtyTracker dirtyTracker() { return dirtyTracker; }

    // --- BufferWritable ---

    public static final GpuLayout<SparseGrid<?>> DEFAULT_LAYOUT = new GpuLayout<>() {
        @Override public int byteSize() { return -1; }
        @Override public void writeTo(SparseGrid<?> grid, ByteBuffer buf) {
            for (var entry : grid.itemMap.values()) {
                buf.putFloat(entry.bounds.min.x); buf.putFloat(entry.bounds.min.y); buf.putFloat(entry.bounds.min.z);
                buf.putFloat(entry.bounds.max.x); buf.putFloat(entry.bounds.max.y); buf.putFloat(entry.bounds.max.z);
            }
        }
        @Override public void readFrom(SparseGrid<?> grid, ByteBuffer buf) { throw new UnsupportedOperationException(); }
    };

    @Override public int byteSize() { return itemMap.size() * 24; }

    @SuppressWarnings("unchecked")
    @Override public void writeTo(ByteBuffer buf) { ((GpuLayout<SparseGrid<T>>) (GpuLayout<?>) DEFAULT_LAYOUT).writeTo(this, buf); }
    @Override public void readFrom(ByteBuffer buf) { throw new UnsupportedOperationException(); }

    // --- SpatialQuery ---

    @Override public List<T> query(AABB range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public int query(AABB range, List<T> out) {
        Set<T> seen = new HashSet<>();
        int minCX = cellCoord(range.min.x), minCY = cellCoord(range.min.y), minCZ = cellCoord(range.min.z);
        int maxCX = cellCoord(range.max.x), maxCY = cellCoord(range.max.y), maxCZ = cellCoord(range.max.z);
        for (int cx = minCX; cx <= maxCX; cx++)
            for (int cy = minCY; cy <= maxCY; cy++)
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    List<Entry<T>> cell = cells.get(cellKey(cx, cy, cz));
                    if (cell != null) for (Entry<T> e : cell)
                        if (seen.add(e.item) && e.bounds.intersects(range)) out.add(e.item);
                }
        return out.size();
    }

    @Override public List<T> query(Sphere range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public int query(Sphere range, List<T> out) {
        AABB sphereAABB = new AABB(
                new Vec3(range.center.x - range.radius, range.center.y - range.radius, range.center.z - range.radius),
                new Vec3(range.center.x + range.radius, range.center.y + range.radius, range.center.z + range.radius));
        Set<T> seen = new HashSet<>();
        int minCX = cellCoord(sphereAABB.min.x), minCY = cellCoord(sphereAABB.min.y), minCZ = cellCoord(sphereAABB.min.z);
        int maxCX = cellCoord(sphereAABB.max.x), maxCY = cellCoord(sphereAABB.max.y), maxCZ = cellCoord(sphereAABB.max.z);
        int before = out.size();
        for (int cx = minCX; cx <= maxCX; cx++)
            for (int cy = minCY; cy <= maxCY; cy++)
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    List<Entry<T>> cell = cells.get(cellKey(cx, cy, cz));
                    if (cell != null) for (Entry<T> e : cell)
                        if (seen.add(e.item) && aabbIntersectsSphere(e.bounds, range)) out.add(e.item);
                }
        return out.size() - before;
    }

    @Override public List<T> query(Ray ray, float maxDistance) {
        List<T> out = new ArrayList<>();
        // Brute force over all entries (grid ray march would be an optimization)
        for (Entry<T> e : itemMap.values()) {
            float t = Intersections.rayAABB(ray, e.bounds);
            if (t >= 0 && t <= maxDistance) out.add(e.item);
        }
        return out;
    }

    @Override public List<T> queryFrustum(Frustum frustum) { List<T> out = new ArrayList<>(); queryFrustum(frustum, out); return out; }
    @Override public int queryFrustum(Frustum frustum, List<T> out) {
        int before = out.size();
        for (Entry<T> e : itemMap.values())
            if (frustum.testAABB(e.bounds) != ContainmentResult.OUTSIDE) out.add(e.item);
        return out.size() - before;
    }

    @Override public Stream<T> queryStream(AABB range) { return query(range).stream(); }
    @Override public Stream<T> queryStream(Sphere range) { return query(range).stream(); }
    @Override public Stream<T> queryStream(Ray ray, float maxDistance) { return query(ray, maxDistance).stream(); }
    @Override public Stream<T> queryFrustumStream(Frustum frustum) { return queryFrustum(frustum).stream(); }

    @Override public boolean contains(Vec3 point) {
        long key = cellKey(cellCoord(point.x), cellCoord(point.y), cellCoord(point.z));
        List<Entry<T>> cell = cells.get(key);
        if (cell == null) return false;
        for (Entry<T> e : cell) if (e.bounds.contains(point)) return true;
        return false;
    }

    @Override public T nearest(Vec3 point) {
        T best = null; float bestDist = Float.MAX_VALUE;
        for (Entry<T> e : itemMap.values()) {
            Vec3 c = clampToAABB(point, e.bounds);
            float d = point.distanceSquared(c);
            if (d < bestDist) { bestDist = d; best = e.item; }
        }
        return best;
    }

    @Override public int count(AABB range) { return query(range).size(); }
    @Override public int count(Sphere range) { return query(range).size(); }
    @Override public int countFrustum(Frustum frustum) { return queryFrustum(frustum).size(); }

    // --- Helpers ---

    private int cellCoord(float v) { return (int) Math.floor(v / cellSize); }
    private long cellKey(int cx, int cy, int cz) { return ((long)(cx & 0x1FFFFF)) | ((long)(cy & 0x1FFFFF) << 21) | ((long)(cz & 0x1FFFFF) << 42); }

    private static boolean aabbIntersectsSphere(AABB aabb, Sphere sphere) {
        Vec3 c = clampToAABB(sphere.center, aabb);
        return sphere.center.distanceSquared(c) <= sphere.radius * sphere.radius;
    }

    private static Vec3 clampToAABB(Vec3 point, AABB aabb) {
        return new Vec3(Math.max(aabb.min.x, Math.min(point.x, aabb.max.x)),
                Math.max(aabb.min.y, Math.min(point.y, aabb.max.y)),
                Math.max(aabb.min.z, Math.min(point.z, aabb.max.z)));
    }

    private static class Entry<T> {
        final T item;
        AABB bounds;
        final List<Long> cellKeys = new ArrayList<>();
        Entry(T item, AABB bounds) { this.item = item; this.bounds = bounds; }
    }

    private static class DirtyTrackerImpl implements DirtyTracker {
        private boolean dirty = false, fullRebuild = false;
        void markDirty(int idx) { dirty = true; }
        void markFullRebuild() { fullRebuild = true; }
        @Override public boolean isDirty() { return dirty || fullRebuild; }
        @Override public boolean isFullRebuild() { return fullRebuild; }
        @Override public int dirtyNodeCount() { return 0; }
        @Override public IntStream dirtyNodeIndices() { return IntStream.empty(); }
        @Override public void clearDirty() { dirty = false; fullRebuild = false; }
    }
}
