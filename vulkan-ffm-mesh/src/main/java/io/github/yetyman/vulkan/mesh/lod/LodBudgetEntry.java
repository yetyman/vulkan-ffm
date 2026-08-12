package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.helpers.math.geometry.AABB;

/**
 * One entry in the list passed to {@link LodPolicy#arbitrate}. Represents a single mesh
 * instance that needs LOD selection this frame. The policy reads its properties and writes
 * back adjusted thresholds/budgets that the selector will use.
 *
 * <p>Mutable: the policy modifies {@link #effectiveErrorThreshold} and {@link #triangleBudget}
 * in place. This avoids allocation of a result list per frame.
 */
public final class LodBudgetEntry {

    private final RepresentationStructure representations;
    private final AABB worldBounds;
    private final float distanceToCamera;
    private final float screenCoverage;
    private final long currentTriangleCount;
    private final int currentNodeIndex;

    // Mutable fields the policy adjusts:
    private float effectiveErrorThreshold;
    private long triangleBudget;

    public LodBudgetEntry(RepresentationStructure representations,
                          AABB worldBounds,
                          float distanceToCamera,
                          float screenCoverage,
                          long currentTriangleCount,
                          int currentNodeIndex,
                          float baseErrorThreshold) {
        this.representations = representations;
        this.worldBounds = worldBounds;
        this.distanceToCamera = distanceToCamera;
        this.screenCoverage = screenCoverage;
        this.currentTriangleCount = currentTriangleCount;
        this.currentNodeIndex = currentNodeIndex;
        this.effectiveErrorThreshold = baseErrorThreshold;
        this.triangleBudget = Long.MAX_VALUE;
    }

    // -------------------------------------------------------------------------
    // Read-only properties
    // -------------------------------------------------------------------------

    /** The representation structure of this mesh. */
    public RepresentationStructure representations() { return representations; }

    /** World-space bounds (transformed). */
    public AABB worldBounds() { return worldBounds; }

    /** Distance from camera to nearest point of bounds. */
    public float distanceToCamera() { return distanceToCamera; }

    /**
     * Approximate fraction of the screen this mesh covers (0..1). Used by policies
     * that allocate budget proportional to screen presence.
     */
    public float screenCoverage() { return screenCoverage; }

    /** Triangle count currently being rendered for this mesh. */
    public long currentTriangleCount() { return currentTriangleCount; }

    /** The node index currently selected (-1 if first frame or GPU-driven). */
    public int currentNodeIndex() { return currentNodeIndex; }

    // -------------------------------------------------------------------------
    // Mutable fields (written by policy, read by selector)
    // -------------------------------------------------------------------------

    /**
     * The error threshold the selector should use, potentially adjusted by the policy.
     * Higher = coarser (more aggressive culling). Lower = finer (more triangles).
     */
    public float effectiveErrorThreshold() { return effectiveErrorThreshold; }
    public void setEffectiveErrorThreshold(float t) { this.effectiveErrorThreshold = t; }

    /**
     * Per-mesh triangle budget assigned by the policy. Long.MAX_VALUE = unconstrained.
     */
    public long triangleBudget() { return triangleBudget; }
    public void setTriangleBudget(long budget) { this.triangleBudget = budget; }
}
