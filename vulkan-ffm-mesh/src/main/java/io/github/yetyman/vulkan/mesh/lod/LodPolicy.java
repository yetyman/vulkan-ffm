package io.github.yetyman.vulkan.mesh.lod;

import java.util.List;

/**
 * Global budget arbitration across many meshes. Called once per frame before individual
 * {@link LodSelector}s run, to divide a fixed triangle/memory/time budget across all meshes
 * that need selection.
 *
 * <p>This is the global optimization step. A single mesh's selector decides "which level for
 * this mesh". A policy decides "how much budget does each mesh get" so the total stays within
 * a frame budget. The two are separate because:
 * <ul>
 *   <li>Per-mesh selection is embarrassingly parallel and stateless per mesh</li>
 *   <li>Global arbitration is inherently serial and needs visibility of all meshes</li>
 *   <li>Many applications do not need global arbitration at all (fixed thresholds, no budget)</li>
 * </ul>
 *
 * <p>Implementations range from trivial (no arbitration, each mesh uses its own threshold) to
 * complex (sort by screen contribution, allocate budget proportionally, bias toward recently
 * loaded or player-focused meshes).
 *
 * <p>The interface is deliberately minimal. Complex policies that need history, prediction, or
 * feedback hold that state internally. The interface is the per-frame entry point only.
 */
public interface LodPolicy {

    /**
     * Arbitrates budget across all entries. Implementations mutate each entry's
     * {@link LodBudgetEntry#effectiveErrorThreshold} or other per-entry adjustments.
     *
     * <p>Called once per frame before selectors run. Implementations may sort, filter, or
     * adjust entries in place.
     *
     * @param entries     all meshes that will have LOD selection this frame (mutable list)
     * @param totalBudget the global budget constraints for this frame
     */
    void arbitrate(List<LodBudgetEntry> entries, LodBudget totalBudget);

    /**
     * Default implementation: no arbitration. Each mesh uses its base error threshold unchanged.
     * This is the correct default for applications with fixed quality settings and no budget.
     */
    LodPolicy PASSTHROUGH = (entries, budget) -> {};
}
