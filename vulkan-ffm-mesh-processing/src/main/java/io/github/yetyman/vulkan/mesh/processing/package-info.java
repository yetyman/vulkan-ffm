/**
 * Optimized mesh processing implementations: production-quality simplifiers, meshlet builders,
 * and other geometry processors that require external native libraries or heavy computation.
 *
 * <p>This module depends on {@code vulkan-ffm-mesh} for the interfaces
 * ({@link io.github.yetyman.vulkan.mesh.process.Simplifier},
 * {@link io.github.yetyman.vulkan.mesh.process.MeshletBuilder}) and provides optimized
 * implementations. The reference implementations in {@code vulkan-ffm-mesh} are for tests
 * and correctness validation; this module is for production use.</p>
 *
 * <p>Native library policy: discuss before adding any. Each native dependency needs its own
 * bindings module following the project's established pattern (jextract + bundled DLLs).</p>
 */
package io.github.yetyman.vulkan.mesh.processing;
