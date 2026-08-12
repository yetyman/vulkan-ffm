package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.vulkan.mesh.residency.PartitionRef;
import io.github.yetyman.vulkan.mesh.source.Residency;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The input to {@link LodSelector#select}: everything a selector needs to make its decision.
 *
 * <p>Carries camera state, projection parameters, per-instance transform, budget signals,
 * residency queries, previous-frame feedback, and a typed side channel for research/custom data.
 *
 * <p>Designed for zero allocation on the hot path: construct one context per frame via
 * {@link Builder}, reuse across all meshes by mutating only the per-instance fields
 * ({@link Builder#objectTransform}, {@link Builder#objectBounds}).
 *
 * <p>The typed side channel follows the {@code AssetRegistry/AssetType} pattern: a research
 * LOD scheme can supply its own inputs without changing this class.
 */
public final class LodContext {

    private final Vec3 cameraPosition;
    private final Mat4 viewProjection;
    private final Mat4 projectionMatrix;
    private final float screenHeight;
    private final float errorThreshold;
    private final Mat4 objectTransform;
    private final AABB objectBounds;

    // Budget signals
    private final long triangleBudgetRemaining;
    private final long memoryBudgetRemaining;
    private final float timeBudgetRemainingMs;

    // Residency function (non-blocking, non-allocating)
    private final ResidencyQuery residencyQuery;

    // Feedback
    private final LodSelection previousSelection;
    private final float deltaTimeSeconds;

    // Typed side channel
    private final Map<ContextKey<?>, Object> sideChannel;

    private LodContext(Builder b) {
        this.cameraPosition = b.cameraPosition;
        this.viewProjection = b.viewProjection;
        this.projectionMatrix = b.projectionMatrix;
        this.screenHeight = b.screenHeight;
        this.errorThreshold = b.errorThreshold;
        this.objectTransform = b.objectTransform;
        this.objectBounds = b.objectBounds;
        this.triangleBudgetRemaining = b.triangleBudgetRemaining;
        this.memoryBudgetRemaining = b.memoryBudgetRemaining;
        this.timeBudgetRemainingMs = b.timeBudgetRemainingMs;
        this.residencyQuery = b.residencyQuery;
        this.previousSelection = b.previousSelection;
        this.deltaTimeSeconds = b.deltaTimeSeconds;
        this.sideChannel = b.sideChannel.isEmpty() ? Map.of() : Map.copyOf(b.sideChannel);
    }

    // -------------------------------------------------------------------------
    // Camera and projection
    // -------------------------------------------------------------------------

    /** Camera position in world space. */
    public Vec3 cameraPosition() { return cameraPosition; }

    /** Combined view-projection matrix. */
    public Mat4 viewProjection() { return viewProjection; }

    /** Screen height in pixels (for screen-space error projection). */
    public float screenHeight() { return screenHeight; }

    /**
     * Target screen-space error threshold in pixels. A representation whose projected error
     * is below this threshold is "good enough". Typical values: 1.0 (pixel-perfect) to 4.0
     * (aggressive culling).
     */
    public float errorThreshold() { return errorThreshold; }

    // -------------------------------------------------------------------------
    // Per-instance state (changes per mesh within a frame)
    // -------------------------------------------------------------------------

    /** World transform of the mesh instance being selected for. */
    public Mat4 objectTransform() { return objectTransform; }

    /** World-space bounds of the mesh instance (transformed). */
    public AABB objectBounds() { return objectBounds; }

    // -------------------------------------------------------------------------
    // Budget signals
    // -------------------------------------------------------------------------

    /**
     * Approximate triangle budget remaining for this frame. Long.MAX_VALUE if no budget.
     * Selectors should prefer coarser representations when this is low.
     */
    public long triangleBudgetRemaining() { return triangleBudgetRemaining; }

    /**
     * Approximate GPU memory budget remaining in bytes. Long.MAX_VALUE if no budget.
     * Affects whether to request residency of finer representations.
     */
    public long memoryBudgetRemaining() { return memoryBudgetRemaining; }

    /**
     * Approximate time budget remaining for LOD selection in milliseconds.
     * Float.MAX_VALUE if no budget. GPU selectors can ignore this; CPU selectors
     * with expensive traversal should early-out when exhausted.
     */
    public float timeBudgetRemainingMs() { return timeBudgetRemainingMs; }

    // -------------------------------------------------------------------------
    // Residency
    // -------------------------------------------------------------------------

    /**
     * Queries the residency state of a partition without blocking or allocating.
     * Delegates to the configured {@link ResidencyQuery} function.
     */
    public Residency residencyOf(PartitionRef ref) {
        return residencyQuery != null ? residencyQuery.query(ref) : Residency.DEVICE;
    }

    // -------------------------------------------------------------------------
    // Feedback
    // -------------------------------------------------------------------------

    /**
     * @return the selection produced last frame for this geometry, or null if this is the first
     * frame or no feedback is available. Used for hysteresis and transition tracking.
     */
    public LodSelection previousSelection() { return previousSelection; }

    /** Time since last frame in seconds. Used for transition advancement. */
    public float deltaTimeSeconds() { return deltaTimeSeconds; }

    // -------------------------------------------------------------------------
    // Side channel
    // -------------------------------------------------------------------------

    /**
     * Retrieves a typed value from the side channel, or empty if not present.
     * Research LOD schemes supply their own inputs via this without changing the context class.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(ContextKey<T> key) {
        return Optional.ofNullable((T) sideChannel.get(key));
    }

    // -------------------------------------------------------------------------
    // Utility: screen-space error projection
    // -------------------------------------------------------------------------

    /**
     * Projects a world-space error bound to screen-space pixels given the current camera and
     * object distance. This is the standard screen-space error formula:
     * {@code screenError = (worldError / distance) * (screenHeight / (2 * tan(fovY/2)))}
     *
     * <p>Selectors may use this directly or implement their own projection. This convenience
     * avoids every selector reimplementing the same math.
     *
     * <p>Uses the projection matrix's [1][1] element (cot(fovY/2)) when a separate projection
     * matrix is supplied. Falls back to the viewProjection matrix otherwise (which is only
     * correct for axis-aligned view matrices).
     *
     * @param worldError the error in world-space units
     * @param objectDistance distance from camera to object center
     * @return projected error in pixels
     */
    public float projectError(float worldError, float objectDistance) {
        if (objectDistance <= 0.0f) return Float.MAX_VALUE;
        // Extract vertical FOV from projection matrix: element [1][1] = 1/tan(fovY/2)
        // Mat4 uses column-major naming mColumnRow, so column 1 row 1 = m11
        float cotHalfFov = projectionMatrix != null ? projectionMatrix.m11 : viewProjection.m11;
        if (cotHalfFov < 0) cotHalfFov = -cotHalfFov; // Vulkan projection may negate Y
        return (worldError / objectDistance) * (screenHeight * 0.5f * cotHalfFov);
    }

    /**
     * Computes the distance from the camera to the nearest point on the given AABB.
     */
    public float distanceTo(AABB bounds) {
        // Clamp camera to AABB and compute distance
        float cx = Math.max(bounds.min.x, Math.min(cameraPosition.x, bounds.max.x));
        float cy = Math.max(bounds.min.y, Math.min(cameraPosition.y, bounds.max.y));
        float cz = Math.max(bounds.min.z, Math.min(cameraPosition.z, bounds.max.z));
        float dx = cameraPosition.x - cx;
        float dy = cameraPosition.y - cy;
        float dz = cameraPosition.z - cz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Vec3 cameraPosition;
        private Mat4 viewProjection;
        private Mat4 projectionMatrix;
        private float screenHeight = 1080.0f;
        private float errorThreshold = 1.0f;
        private Mat4 objectTransform;
        private AABB objectBounds;
        private long triangleBudgetRemaining = Long.MAX_VALUE;
        private long memoryBudgetRemaining = Long.MAX_VALUE;
        private float timeBudgetRemainingMs = Float.MAX_VALUE;
        private ResidencyQuery residencyQuery;
        private LodSelection previousSelection;
        private float deltaTimeSeconds;
        private final Map<ContextKey<?>, Object> sideChannel = new HashMap<>();

        private Builder() {}

        public Builder cameraPosition(Vec3 pos) { this.cameraPosition = pos; return this; }
        public Builder viewProjection(Mat4 vp) { this.viewProjection = vp; return this; }
        /** Sets the projection matrix separately for accurate FOV extraction in projectError(). */
        public Builder projectionMatrix(Mat4 proj) { this.projectionMatrix = proj; return this; }
        public Builder screenHeight(float h) { this.screenHeight = h; return this; }
        public Builder errorThreshold(float t) { this.errorThreshold = t; return this; }
        public Builder objectTransform(Mat4 m) { this.objectTransform = m; return this; }
        public Builder objectBounds(AABB b) { this.objectBounds = b; return this; }
        public Builder triangleBudget(long remaining) { this.triangleBudgetRemaining = remaining; return this; }
        public Builder memoryBudget(long remaining) { this.memoryBudgetRemaining = remaining; return this; }
        public Builder timeBudget(float remainingMs) { this.timeBudgetRemainingMs = remainingMs; return this; }
        public Builder residencyQuery(ResidencyQuery query) { this.residencyQuery = query; return this; }
        public Builder previousSelection(LodSelection prev) { this.previousSelection = prev; return this; }
        public Builder deltaTime(float seconds) { this.deltaTimeSeconds = seconds; return this; }

        public <T> Builder put(ContextKey<T> key, T value) {
            sideChannel.put(key, value);
            return this;
        }

        public LodContext build() {
            if (cameraPosition == null) throw new IllegalStateException("cameraPosition required");
            if (viewProjection == null) throw new IllegalStateException("viewProjection required");
            return new LodContext(this);
        }
    }
}
