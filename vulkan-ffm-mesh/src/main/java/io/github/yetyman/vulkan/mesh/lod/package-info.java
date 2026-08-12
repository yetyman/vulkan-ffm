/**
 * Level of Detail (LOD) representation, selection, and transition tracking.
 *
 * <h2>Design intent</h2>
 * <p>This package provides the structural types and interfaces that make any LOD system
 * composable with the mesh module. It is deliberately unbiased toward any specific LOD
 * technique: discrete chains, cluster DAGs, progressive meshes, parametric tessellation,
 * and GPU-driven selection are all first-class citizens with no preferred path.
 *
 * <h2>Four orthogonal concerns</h2>
 * <p>Every LOD technique is a specific combination of:
 * <ol>
 *   <li><b>Representation Structure</b> ({@link io.github.yetyman.vulkan.mesh.lod.RepresentationStructure}):
 *       what LOD data exists (Flat, Chain, Graph, Parametric)</li>
 *   <li><b>Selection Policy</b> ({@link io.github.yetyman.vulkan.mesh.lod.LodSelector}):
 *       which representation to use, decided by error thresholds, budgets, hysteresis</li>
 *   <li><b>Selection Execution</b> (encoded in {@link io.github.yetyman.vulkan.mesh.lod.LodSelection}):
 *       where the decision happens (CPU explicit, GPU indirect, hardware parametric)</li>
 *   <li><b>Transition Strategy</b> ({@link io.github.yetyman.vulkan.mesh.lod.TransitionMode}):
 *       how the switch appears (hard cut, dither, geomorph, cross-fade, continuous)</li>
 * </ol>
 *
 * <p>Each concern has its own type. Composing them freely produces any LOD technique without
 * special cases.
 *
 * <h2>What lives here vs. elsewhere</h2>
 * <ul>
 *   <li><b>Here (vulkan-ffm-mesh)</b>: structural types, interfaces, context, metadata channels,
 *       transition state tracking. Everything unbiased and composable.</li>
 *   <li><b>vulkan-ffm-mesh-processing</b>: concrete LOD generation algorithms (building DAGs from
 *       simplifiers, building chains, cluster grouping). These are approach-specific.</li>
 *   <li><b>sample-app / app code</b>: concrete selectors, concrete policies, GPU LOD shaders.
 *       These are paradigm-specific.</li>
 * </ul>
 *
 * <h2>Residency interaction</h2>
 * <p>A representation that is not resident cannot be selected. Selectors consult residency via
 * {@link io.github.yetyman.vulkan.mesh.lod.LodContext#residencyOf} and may return a degraded
 * selection plus residency requests, rather than blocking or returning something unusable.
 *
 * @see io.github.yetyman.vulkan.mesh.lod.RepresentationSet
 * @see io.github.yetyman.vulkan.mesh.lod.LodSelector
 * @see io.github.yetyman.vulkan.mesh.lod.LodSelection
 */
package io.github.yetyman.vulkan.mesh.lod;
