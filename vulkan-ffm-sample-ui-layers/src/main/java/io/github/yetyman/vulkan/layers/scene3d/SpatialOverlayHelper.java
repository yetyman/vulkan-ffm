package io.github.yetyman.vulkan.layers.scene3d;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.geometry.Sphere;
import io.github.yetyman.helpers.math.spatial.SpatialStructure;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Helper that renders spatial structure contents into a Scene3DOverlayLayer.
 * Draws all objects as wireframe AABBs, highlights query results, and visualizes the query shape.
 *
 * Usage:
 *   SpatialOverlayHelper<Integer> helper = new SpatialOverlayHelper<>(structure, i -> objectBounds.get(i));
 *   overlayLayer.setFrameCallback(helper.frameCallback());
 *
 * @param <T> the type of items in the spatial structure
 */
public class SpatialOverlayHelper<T> {

    private static final float[] COLOR_OBJECT = {0.5f, 0.5f, 0.8f, 1f};
    private static final float[] COLOR_HIGHLIGHT = {0.2f, 1f, 0.3f, 1f};
    private static final float[] COLOR_QUERY_AABB = {1f, 1f, 0.2f, 0.8f};
    private static final float[] COLOR_QUERY_SPHERE = {1f, 0.6f, 0.2f, 0.8f};
    private static final float[] COLOR_WORLD_BOUNDS = {0.3f, 0.3f, 0.3f, 0.5f};

    private SpatialStructure<T> structure;
    private Function<T, AABB> boundsProvider;
    private List<T> queryResults = List.of();
    private AABB queryAABB;
    private Sphere querySphere;
    private boolean showWorldBounds = true;

    public SpatialOverlayHelper(SpatialStructure<T> structure, Function<T, AABB> boundsProvider) {
        this.structure = structure;
        this.boundsProvider = boundsProvider;
    }

    public void setStructure(SpatialStructure<T> structure, Function<T, AABB> boundsProvider) {
        this.structure = structure;
        this.boundsProvider = boundsProvider;
    }

    public void setQueryResults(List<T> results) { this.queryResults = results; }
    public void setQueryAABB(AABB aabb) { this.queryAABB = aabb; this.querySphere = null; }
    public void setQuerySphere(Sphere sphere) { this.querySphere = sphere; this.queryAABB = null; }
    public void setShowWorldBounds(boolean show) { this.showWorldBounds = show; }

    /**
     * Returns a frame callback suitable for Scene3DOverlayLayer.setFrameCallback().
     */
    public Consumer<OverlayDrawList> frameCallback() {
        return this::render;
    }

    private void render(OverlayDrawList drawList) {
        if (structure == null) return;

        // World bounds
        if (showWorldBounds) {
            AABB wb = structure.worldBounds();
            if (wb != null) {
                drawList.addWireBox(toArr(wb.min), toArr(wb.max), COLOR_WORLD_BOUNDS, DepthMode.DEPTH_TESTED);
            }
        }

        // All objects
        List<T> allItems = structure.query(structure.worldBounds());
        java.util.Set<T> highlighted = new java.util.HashSet<>(queryResults);
        for (T item : allItems) {
            AABB bounds = boundsProvider.apply(item);
            if (bounds == null) continue;
            float[] color = highlighted.contains(item) ? COLOR_HIGHLIGHT : COLOR_OBJECT;
            drawList.addWireBox(toArr(bounds.min), toArr(bounds.max), color, DepthMode.DEPTH_TESTED);
        }

        // Query shape
        if (queryAABB != null) {
            drawList.addWireBox(toArr(queryAABB.min), toArr(queryAABB.max), COLOR_QUERY_AABB, DepthMode.ALWAYS_ON_TOP);
        }
        if (querySphere != null) {
            drawList.addWireSphere(toArr(querySphere.center), querySphere.radius, COLOR_QUERY_SPHERE, 16, DepthMode.ALWAYS_ON_TOP);
        }
    }

    private static float[] toArr(Vec3 v) {
        return new float[]{v.x, v.y, v.z};
    }
}
