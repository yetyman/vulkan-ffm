/**
 * Node Tree.
 *
 * A generic, retained, hierarchical node tree for building UI (and non-UI) object trees.
 * The base system knows nothing about rendering, spatial position, or visibility -
 * those are concerns of specific component implementations.
 *
 * <h2>Core types</h2>
 * <ul>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.Tree} - root container, owns the root Node</li>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.Node} - tree node, holds components</li>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.Component} - attachable behavior unit</li>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.TreeComponent} - tree-scoped singleton</li>
 * </ul>
 *
 * <h2>Dependency Injection</h2>
 * <ul>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.Dependency} - dependency declaration</li>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.ClaimStyle} - sharing rules</li>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.LookupScope} - resolution scope</li>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.FallbackPolicy} - missing dependency handling</li>
 * </ul>
 *
 * <h2>Events</h2>
 * <ul>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.Event} - base event with capture/bubble</li>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.EventType} - type token + factory</li>
 * </ul>
 *
 * <h2>Bulk Render Integration</h2>
 * <ul>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.TraversalView} - per-type traversal list</li>
 *   <li>{@link io.github.yetyman.vulkan.nodetree.TraversalOrder} - traversal ordering</li>
 * </ul>
 *
 * <h2>Three-level model</h2>
 * <ol>
 *   <li>Level 1: The tree (Node parent/child structure)</li>
 *   <li>Level 2: Per-component-type traversal lists (TraversalView)</li>
 *   <li>Level 3: Dense backing arrays (caller-owned, updated via applyPatches())</li>
 * </ol>
 *
 * @see io.github.yetyman.vulkan.nodetree.Tree
 * @see io.github.yetyman.vulkan.nodetree.Node
 * @see io.github.yetyman.vulkan.nodetree.Component
 */
package io.github.yetyman.vulkan.nodetree;
