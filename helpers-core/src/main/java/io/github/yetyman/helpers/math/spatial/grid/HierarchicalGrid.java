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
 * Hierarchical grid — multiple resolution levels (like a mipmap of sparse grids).
 * Large objects go in coarse levels, small objects in fine levels.
 * Avoids the "one object spans 100 cells" problem of flat grids.
 *
 * Each level has cell size = baseSize * 2^level. Objects are placed at the coarsest level
 * where they fit in at most 2x2x2 cells (8 cells max per object).
 *
 * @param <T> the type of items stored
 */
public class HierarchicalGrid<T> implements SpatialStructure<T> {

    private final float baseSize;
    private final int levelCount;
    private final Map<Long, List<Entry<T>>>[] levels;
    private final Map<T, Entry<T>> itemMap = new HashMap<>();
    private final DirtyTrackerImpl dirtyTracker = new DirtyTrackerImpl();

    @SuppressWarnings("unchecked")
    public HierarchicalGrid(float baseSize, int levelCount) {
        this.baseSize = baseSize;
        this.levelCount = levelCount;
        this.levels = new Map[levelCount];
        for (int i = 0; i < levelCount; i++) levels[i] = new HashMap<>();
    }

    public int levelCount() { return levelCount; }
    public float cellSizeAtLevel(int level) { return baseSize * (1 << level); }

    @Override
    public void insert(T item, AABB bounds) {
        if (itemMap.containsKey(item)) { update(item, bounds); return; }
        Entry<T> entry = new Entry<>(item, bounds);
        int level = chooseLevelForSize(bounds);
        entry.level = level;
        insertAtLevel(entry, level);
        itemMap.put(item, entry);
        dirtyTracker.markDirty(0);
    }

    @Override public void insertAll(Iterable<T> items, Function<T, AABB> bp) { for (T i : items) insert(i, bp.apply(i)); dirtyTracker.markFullRebuild(); }
    @Override public void insertAll(Map<T, AABB> ib) { for (var e : ib.entrySet()) insert(e.getKey(), e.getValue()); dirtyTracker.markFullRebuild(); }

    @Override
    public void remove(T item) {
        Entry<T> entry = itemMap.remove(item);
        if (entry == null) return;
        for (long key : entry.cellKeys) {
            List<Entry<T>> cell = levels[entry.level].get(key);
            if (cell != null) { cell.remove(entry); if (cell.isEmpty()) levels[entry.level].remove(key); }
        }
        dirtyTracker.markDirty(0);
    }

    @Override public void update(T item, AABB newBounds) { remove(item); insert(item, newBounds); }
    @Override public void rebuild() { dirtyTracker.markFullRebuild(); }
    @Override public void clear() { for (Map<Long, List<Entry<T>>> l : levels) l.clear(); itemMap.clear(); dirtyTracker.markFullRebuild(); }
    @Override public int size() { return itemMap.size(); }

    @Override
    public AABB worldBounds() {
        if (itemMap.isEmpty()) return new AABB(new Vec3(), new Vec3());
        Vec3 min = new Vec3(Float.POSITIVE_INFINITY); Vec3 max = new Vec3(Float.NEGATIVE_INFINITY);
        for (Entry<T> e : itemMap.values()) { min.min(e.bounds.min); max.max(e.bounds.max); }
        return new AABB(min, max);
    }

    @Override public DirtyTracker dirtyTracker() { return dirtyTracker; }

    @Override
    public void visitNodes(io.github.yetyman.helpers.math.spatial.NodeVisitor visitor) {
        java.util.Set<Long> visited = new java.util.HashSet<>();
        for (int level = 0; level < levelCount; level++) {
            float cs = cellSizeAtLevel(level);
            int lvl = level;
            for (java.util.Map.Entry<Long, List<Entry<T>>> cellEntry : levels[level].entrySet()) {
                long key = cellEntry.getKey();
                if (visited.add(key * 31 + lvl)) {
                    int cx = decodeCoord(key, 0), cy = decodeCoord(key, 21), cz = decodeCoord(key, 42);
                    float minX = cx * cs, minY = cy * cs, minZ = cz * cs;
                    visitor.visit(new AABB(new Vec3(minX, minY, minZ), new Vec3(minX + cs, minY + cs, minZ + cs)),
                            lvl, true, cellEntry.getValue().size());
                }
            }
        }
    }

    private static int decodeCoord(long key, int shift) {
        int v = (int)((key >> shift) & 0x1FFFFF);
        return (v & 0x100000) != 0 ? v | 0xFFE00000 : v;
    }

    // BufferWritable
    @Override public int byteSize() { return itemMap.size() * 24; }
    @Override public void writeTo(ByteBuffer buf) { for (Entry<T> e : itemMap.values()) { buf.putFloat(e.bounds.min.x); buf.putFloat(e.bounds.min.y); buf.putFloat(e.bounds.min.z); buf.putFloat(e.bounds.max.x); buf.putFloat(e.bounds.max.y); buf.putFloat(e.bounds.max.z); } }
    @Override public void readFrom(ByteBuffer buf) { throw new UnsupportedOperationException(); }

    // Queries
    @Override public List<T> query(AABB range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public int query(AABB range, List<T> out) {
        Set<T> seen = new HashSet<>(); int before = out.size();
        for (int level = 0; level < levelCount; level++) {
            float cs = cellSizeAtLevel(level);
            int minCX = (int) Math.floor(range.min.x / cs), minCY = (int) Math.floor(range.min.y / cs), minCZ = (int) Math.floor(range.min.z / cs);
            int maxCX = (int) Math.floor(range.max.x / cs), maxCY = (int) Math.floor(range.max.y / cs), maxCZ = (int) Math.floor(range.max.z / cs);
            for (int cx = minCX; cx <= maxCX; cx++) for (int cy = minCY; cy <= maxCY; cy++) for (int cz = minCZ; cz <= maxCZ; cz++) {
                List<Entry<T>> cell = levels[level].get(cellKey(cx, cy, cz));
                if (cell != null) for (Entry<T> e : cell) if (seen.add(e.item) && e.bounds.intersects(range)) out.add(e.item);
            }
        }
        return out.size() - before;
    }

    @Override public List<T> query(Sphere range) { List<T> out = new ArrayList<>(); query(range, out); return out; }
    @Override public int query(Sphere range, List<T> out) {
        AABB sa = new AABB(new Vec3(range.center.x-range.radius, range.center.y-range.radius, range.center.z-range.radius),
                new Vec3(range.center.x+range.radius, range.center.y+range.radius, range.center.z+range.radius));
        Set<T> seen = new HashSet<>(); int before = out.size();
        for (int level = 0; level < levelCount; level++) {
            float cs = cellSizeAtLevel(level);
            int minCX = (int) Math.floor(sa.min.x / cs), minCY = (int) Math.floor(sa.min.y / cs), minCZ = (int) Math.floor(sa.min.z / cs);
            int maxCX = (int) Math.floor(sa.max.x / cs), maxCY = (int) Math.floor(sa.max.y / cs), maxCZ = (int) Math.floor(sa.max.z / cs);
            for (int cx = minCX; cx <= maxCX; cx++) for (int cy = minCY; cy <= maxCY; cy++) for (int cz = minCZ; cz <= maxCZ; cz++) {
                List<Entry<T>> cell = levels[level].get(cellKey(cx, cy, cz));
                if (cell != null) for (Entry<T> e : cell) if (seen.add(e.item) && aabbIntersectsSphere(e.bounds, range)) out.add(e.item);
            }
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

    @Override public boolean contains(Vec3 point) { for (Entry<T> e : itemMap.values()) if (e.bounds.contains(point)) return true; return false; }
    @Override public T nearest(Vec3 point) { T best = null; float bd = Float.MAX_VALUE; for (Entry<T> e : itemMap.values()) { Vec3 c = clampToAABB(point, e.bounds); float d = point.distanceSquared(c); if (d < bd) { bd = d; best = e.item; } } return best; }
    @Override public int count(AABB range) { return query(range).size(); }
    @Override public int count(Sphere range) { return query(range).size(); }
    @Override public int countFrustum(Frustum f) { return queryFrustum(f).size(); }

    // Internal
    private int chooseLevelForSize(AABB bounds) {
        float maxExtent = Math.max(bounds.max.x - bounds.min.x, Math.max(bounds.max.y - bounds.min.y, bounds.max.z - bounds.min.z));
        for (int level = 0; level < levelCount; level++) {
            if (maxExtent <= cellSizeAtLevel(level)) return level;
        }
        return levelCount - 1;
    }

    private void insertAtLevel(Entry<T> entry, int level) {
        float cs = cellSizeAtLevel(level);
        int minCX = (int) Math.floor(entry.bounds.min.x / cs), minCY = (int) Math.floor(entry.bounds.min.y / cs), minCZ = (int) Math.floor(entry.bounds.min.z / cs);
        int maxCX = (int) Math.floor(entry.bounds.max.x / cs), maxCY = (int) Math.floor(entry.bounds.max.y / cs), maxCZ = (int) Math.floor(entry.bounds.max.z / cs);
        for (int cx = minCX; cx <= maxCX; cx++) for (int cy = minCY; cy <= maxCY; cy++) for (int cz = minCZ; cz <= maxCZ; cz++) {
            long key = cellKey(cx, cy, cz);
            levels[level].computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            entry.cellKeys.add(key);
        }
    }

    private static long cellKey(int cx, int cy, int cz) { return ((long)(cx & 0x1FFFFF)) | ((long)(cy & 0x1FFFFF) << 21) | ((long)(cz & 0x1FFFFF) << 42); }
    private static boolean aabbIntersectsSphere(AABB a, Sphere s) { Vec3 c = clampToAABB(s.center, a); return s.center.distanceSquared(c) <= s.radius*s.radius; }
    private static Vec3 clampToAABB(Vec3 p, AABB a) { return new Vec3(Math.max(a.min.x,Math.min(p.x,a.max.x)),Math.max(a.min.y,Math.min(p.y,a.max.y)),Math.max(a.min.z,Math.min(p.z,a.max.z))); }

    private static class Entry<T> { final T item; AABB bounds; int level; final List<Long> cellKeys = new ArrayList<>(); Entry(T item, AABB bounds) { this.item = item; this.bounds = bounds; } }
    private static class DirtyTrackerImpl implements DirtyTracker {
        private boolean dirty, fullRebuild;
        void markDirty(int i) { dirty = true; } void markFullRebuild() { fullRebuild = true; }
        @Override public boolean isDirty() { return dirty||fullRebuild; } @Override public boolean isFullRebuild() { return fullRebuild; }
        @Override public int dirtyNodeCount() { return 0; } @Override public IntStream dirtyNodeIndices() { return IntStream.empty(); }
        @Override public void clearDirty() { dirty = false; fullRebuild = false; }
    }
}
