package io.github.yetyman.helpers.math.spatial.hex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sparse hex grid — stores values at hex coordinates.
 * Only occupied cells consume memory.
 *
 * @param <T> the type of value stored at each hex cell
 */
public class HexGrid<T> {

    private final Map<Long, T> cells = new HashMap<>();

    public HexGrid() {}

    public void set(HexCoord coord, T value) { cells.put(key(coord), value); }
    public void set(int q, int r, T value) { cells.put(key(q, r), value); }
    public T get(HexCoord coord) { return cells.get(key(coord)); }
    public T get(int q, int r) { return cells.get(key(q, r)); }
    public boolean has(HexCoord coord) { return cells.containsKey(key(coord)); }
    public boolean has(int q, int r) { return cells.containsKey(key(q, r)); }
    public void remove(HexCoord coord) { cells.remove(key(coord)); }
    public void remove(int q, int r) { cells.remove(key(q, r)); }
    public void clear() { cells.clear(); }
    public int size() { return cells.size(); }

    /** Returns all values within the given hex radius of center. */
    public List<T> queryRange(HexCoord center, int radius) {
        List<T> result = new ArrayList<>();
        HexCoord[] coords = center.range(radius);
        for (HexCoord c : coords) {
            T val = get(c);
            if (val != null) result.add(val);
        }
        return result;
    }

    /** Returns all values on the ring at the given radius from center. */
    public List<T> queryRing(HexCoord center, int radius) {
        List<T> result = new ArrayList<>();
        HexCoord[] coords = center.ring(radius);
        for (HexCoord c : coords) {
            T val = get(c);
            if (val != null) result.add(val);
        }
        return result;
    }

    /** Returns all occupied coordinates. */
    public List<HexCoord> occupiedCoords() {
        List<HexCoord> result = new ArrayList<>(cells.size());
        for (long k : cells.keySet()) result.add(fromKey(k));
        return result;
    }

    private static long key(HexCoord c) { return key(c.q, c.r); }
    private static long key(int q, int r) { return ((long) q << 32) | (r & 0xFFFFFFFFL); }
    private static HexCoord fromKey(long k) { return new HexCoord((int) (k >> 32), (int) k); }
}
