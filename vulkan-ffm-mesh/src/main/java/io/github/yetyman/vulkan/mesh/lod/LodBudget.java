package io.github.yetyman.vulkan.mesh.lod;

/**
 * Global budget constraints for a frame. Passed to {@link LodPolicy#arbitrate} so the policy
 * can divide resources across all meshes.
 *
 * <p>All budgets are soft: exceeding them degrades quality rather than causing failure. A
 * budget of {@link Long#MAX_VALUE} or {@link Float#MAX_VALUE} means "unconstrained".
 *
 * @param maxTriangles    target maximum total triangles for this frame (soft cap)
 * @param maxMemoryBytes  target maximum GPU memory for LOD-managed geometry (soft cap)
 * @param maxTimeMs       target maximum time for LOD selection computation (soft cap)
 * @param maxResidencyRequests maximum number of new residency requests to issue this frame
 *                            (prevents load spikes during rapid camera motion)
 */
public record LodBudget(
        long maxTriangles,
        long maxMemoryBytes,
        float maxTimeMs,
        int maxResidencyRequests
) {
    /**
     * Unconstrained budget: no limits on anything.
     */
    public static final LodBudget UNLIMITED = new LodBudget(
            Long.MAX_VALUE, Long.MAX_VALUE, Float.MAX_VALUE, Integer.MAX_VALUE);

    /**
     * Creates a triangle-budget-only constraint.
     */
    public static LodBudget triangleBudget(long maxTriangles) {
        return new LodBudget(maxTriangles, Long.MAX_VALUE, Float.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Creates a memory-budget-only constraint.
     */
    public static LodBudget memoryBudget(long maxBytes) {
        return new LodBudget(Long.MAX_VALUE, maxBytes, Float.MAX_VALUE, Integer.MAX_VALUE);
    }
}
