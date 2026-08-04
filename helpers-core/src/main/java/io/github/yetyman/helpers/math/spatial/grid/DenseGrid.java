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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Dense spatial grid — flat array of cells, O(1) access by coordinate.
 * Fixed bounds and resolution. Good for small bounded worlds.
 *
 * @param <T> the type of items stored
 */
public class DenseGrid<T> implements SpatialStructure<T> {

    private final AABB bounds;
    private final int resX, resY, resZ;
    private final float cellSizeX, cellSizeY, cellSizeZ;
    private final List<List<Entry<T>>> cells;
    private final Map<T, Entry<T>> itemMap = new HashMap<>();
    private final DirtyTrackerImpl dirtyTracker = new DirtyTrackerImpl();

    public DenseGrid(AABB bounds, int resX, int resY, int resZ) {
        this.bounds = bounds;
        this.resX = resX; this.resY = resY; this.resZ = resZ;
        this.cellSizeX = (bounds.max.x - bounds.min.x) / resX;
        this.cellSizeY = (bounds.max.y - bounds.min.y) / resY;
        this.cellSizeZ = (bounds.max.z - bounds.min.z) / resZ;
        int total = resX * resY * resZ;
        this.cells = new ArrayList<>(total);
        for (int i = 0; i < total; i++) cells.add(new ArrayList<>());
    }

    @Override
    public void insert(T item, AABB itemBounds) {
        if (itemMap.containsKey(item)) { update(item, itemBounds); return; }
        Entry<T> entry = new Entry<>(item, itemBounds);
        itemMap.put(item, entry);
        int[] min = cellCoords(itemBounds.min);
        int[] max = cellCoords(itemBounds.max);
        for (int x = min[0]; x <= max[0]; x++)
            for (int y = min[1]; y <= max[1]; y++)
                for (int z = min[2]; z <= max[2]; z++) {
                    int idx = cellIndex(x, y, z);
                    if (idx >= 0 && idx < cells.size()) {
                        cells.get(idx).add(entry);
                        entry.cellIndices.add(idx);
                    }
                }
        dirtyTracker.markDirty(0);
    }

    @Override public void insertAll(Iterable<T> items, Function<T, AABB> bp) { for (T i : items) insert(i, bp.apply(i)); dirtyTracker.markFullRebuild(); }
    @Override public void insertAll(Map<T, AABB> ib) { for (var e : ib.entrySet()) insert(e.getKey(), e.getValue()); dirtyTracker.markFullRebuild(); }

    @Override
    public void remove(T item) {
        Entry<T> entry = itemMap.remove(item);
        if (entry == null) return;
        for (int idx : entry.cellIndices) cells.get(idx).remove(entry);
        dirtyTracker.markDirty(0);
    }

    @Override public void update(T item, AABB newBounds) { remove(item); insert(item, newBounds); }
    @Override public void rebuild() { dirtyTracker.markFullRebuild(); }

    @Override
    public void clear() {
        for (List<Entry<T>> cell : cells) cell.clear();
        itemMap.clear();
        dirtyTracker.markFullRebuild();
    }

    @Override public int size() { return itemMap.size(); }
    @Override public AABB worldBounds() { return bounds; }
    @Override public DirtyTracker dirtyTracker() { return dirtyTracker; }

    // --- BufferWritable ---
    @Override public int byteSize() { return itemMap.size() * 24; }
    @Override public void writeTo(ByteBuffer buf) { for (Entry<T> e : itemMap.values()) { buf.putFloat(e.bounds.min.x); buf.putFloat(e.bounds.min.y); buf.putFloat(e.bounds.min.z); buf.putFloat(e.bounds.max.x); buf.putFloat(e.bounds.max.y); buf.putFloat(e.bounds.max.z); } }
    @Override public void readFrom(ByteBuffer buf) { throw new UnsupportedOperationException(); }

    // --- SpatialQuery ---
    @Override public List<T> query(AABB range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public int query(AABB range, List<T> out) {
        java.util.Set<T> seen = new java.util.HashSet<>();
        int[] min = cellCoords(range.min); int[] max = cellCoords(range.max);
        int before = out.size();
        for (int x = min[0]; x <= max[0]; x++) for (int y = min[1]; y <= max[1]; y++) for (int z = min[2]; z <= max[2]; z++) {
            int idx = cellIndex(x, y, z);
            if (idx >= 0 && idx < cells.size()) for (Entry<T> e : cells.get(idx)) if (seen.add(e.item) && e.bounds.intersects(range)) out.add(e.item);
        }
        return out.size() - before;
    }

    @Override public List<T> query(Sphere range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public int query(Sphere range, List<T> out) {
        AABB sa = new AABB(new Vec3(range.center.x-range.radius, range.center.y-range.radius, range.center.z-range.radius),
                new Vec3(range.center.x+range.radius, range.center.y+range.radius, range.center.z+range.radius));
        java.util.Set<T> seen = new java.util.HashSet<>(); int before = out.size();
        int[] min = cellCoords(sa.min); int[] max = cellCoords(sa.max);
        for (int x = min[0]; x <= max[0]; x++) for (int y = min[1]; y <= max[1]; y++) for (int z = min[2]; z <= max[2]; z++) {
            int idx = cellIndex(x, y, z);
            if (idx >= 0 && idx < cells.size()) for (Entry<T> e : cells.get(idx)) if (seen.add(e.item) && aabbIntersectsSphere(e.bounds, range)) out.add(e.item);
        }
        return out.size() - before;
    }

    @Override public List<T> query(Ray ray, float maxDist) { List<T> out = new ArrayList<>(); for (Entry<T> e : itemMap.values()) { float t = Intersections.rayAABB(ray, e.bounds); if (t >= 0 && t <= maxDist) out.add(e.item); } return out; }
    @Override public List<T> queryFrustum(Frustum f) { List<T> out = new ArrayList<>(); queryFrustum(f, out); return out; }
    @Override public int queryFrustum(Frustum f, List<T> out) { int b = out.size(); for (Entry<T> e : itemMap.values()) if (f.testAABB(e.bounds) != ContainmentResult.OUTSIDE) out.add(e.item); return out.size() - b; }

    @Override public Stream<T> queryStream(AABB range) { return query(range).stream(); }
    @Override public Stream<T> queryStream(Sphere range) { return query(range).stream(); }
    @Override public Stream<T> queryStream(Ray ray, float maxDist) { return query(ray, maxDist).stream(); }
    @Override public Stream<T> queryFrustumStream(Frustum f) { return queryFrustum(f).stream(); }

    @Override public boolean contains(Vec3 point) { int[] c = cellCoords(point); int idx = cellIndex(c[0], c[1], c[2]); if (idx < 0 || idx >= cells.size()) return false; for (Entry<T> e : cells.get(idx)) if (e.bounds.contains(point)) return true; return false; }
    @Override public T nearest(Vec3 point) { T best = null; float bd = Float.MAX_VALUE; for (Entry<T> e : itemMap.values()) { Vec3 c = clampToAABB(point, e.bounds); float d = point.distanceSquared(c); if (d < bd) { bd = d; best = e.item; } } return best; }
    @Override public int count(AABB range) { return query(range).size(); }
    @Override public int count(Sphere range) { return query(range).size(); }
    @Override public int countFrustum(Frustum f) { return queryFrustum(f).size(); }

    // --- Helpers ---
    private int[] cellCoords(Vec3 point) {
        return new int[]{
                Math.max(0, Math.min(resX - 1, (int)((point.x - bounds.min.x) / cellSizeX))),
                Math.max(0, Math.min(resY - 1, (int)((point.y - bounds.min.y) / cellSizeY))),
                Math.max(0, Math.min(resZ - 1, (int)((point.z - bounds.min.z) / cellSizeZ)))
        };
    }
    private int cellIndex(int x, int y, int z) {
        if (x < 0 || x >= resX || y < 0 || y >= resY || z < 0 || z >= resZ) return -1;
        return x + y * resX + z * resX * resY;
    }
    private static boolean aabbIntersectsSphere(AABB a, Sphere s) { Vec3 c = clampToAABB(s.center, a); return s.center.distanceSquared(c) <= s.radius * s.radius; }
    private static Vec3 clampToAABB(Vec3 p, AABB a) { return new Vec3(Math.max(a.min.x,Math.min(p.x,a.max.x)),Math.max(a.min.y,Math.min(p.y,a.max.y)),Math.max(a.min.z,Math.min(p.z,a.max.z))); }

    private static class Entry<T> { final T item; AABB bounds; final List<Integer> cellIndices = new ArrayList<>(); Entry(T item, AABB bounds) { this.item = item; this.bounds = bounds; } }
    private static class DirtyTrackerImpl implements DirtyTracker {
        private boolean dirty, fullRebuild;
        void markDirty(int i) { dirty = true; } void markFullRebuild() { fullRebuild = true; }
        @Override public boolean isDirty() { return dirty||fullRebuild; } @Override public boolean isFullRebuild() { return fullRebuild; }
        @Override public int dirtyNodeCount() { return 0; } @Override public IntStream dirtyNodeIndices() { return IntStream.empty(); }
        @Override public void clearDirty() { dirty = false; fullRebuild = false; }
    }
}
