package io.github.yetyman.helpers.math.spatial.geodesic;

import io.github.yetyman.helpers.math.Vec3;

/**
 * A cell in the geodesic grid. Either a hexagon (6 neighbors) or pentagon (5 neighbors).
 */
public class GeodesicCell {

    private final int index;
    private final Vec3 center;
    private final int[] neighborIndices;
    private final boolean pentagon;

    public GeodesicCell(int index, Vec3 center, int[] neighborIndices, boolean pentagon) {
        this.index = index;
        this.center = center;
        this.neighborIndices = neighborIndices;
        this.pentagon = pentagon;
    }

    public int index() { return index; }
    public Vec3 center() { return center; }
    public int[] neighborIndices() { return neighborIndices; }
    public int neighborCount() { return neighborIndices.length; }
    public boolean isPentagon() { return pentagon; }
    public boolean isHexagon() { return !pentagon; }

    @Override
    public String toString() {
        return (pentagon ? "Pentagon" : "Hexagon") + "(idx=" + index + ", neighbors=" + neighborIndices.length + ")";
    }
}
