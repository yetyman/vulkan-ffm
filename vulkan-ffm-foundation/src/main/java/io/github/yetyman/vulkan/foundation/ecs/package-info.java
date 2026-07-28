/**
 * Entity-Component-System (ECS) Node Tree.
 *
 * A generic, retained, hierarchical ECS for building UI (and non-UI) object trees.
 * The base system knows nothing about rendering, spatial position, or visibility -
 * those are concerns of specific component implementations.
 *
 * <h2>Core types</h2>
 * <ul>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.Tree} - root container, owns the root Node</li>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.Node} - tree node, holds components</li>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.Component} - attachable behavior unit</li>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.TreeComponent} - tree-scoped singleton</li>
 * </ul>
 *
 * <h2>Dependency Injection</h2>
 * <ul>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.Dependency} - dependency declaration</li>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.ClaimStyle} - sharing rules</li>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.LookupScope} - resolution scope</li>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.FallbackPolicy} - missing dependency handling</li>
 * </ul>
 *
 * <h2>Events</h2>
 * <ul>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.Event} - base event with capture/bubble</li>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.EventType} - type token + factory</li>
 * </ul>
 *
 * <h2>Bulk Render Integration</h2>
 * <ul>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.TraversalView} - per-type traversal list</li>
 *   <li>{@link io.github.yetyman.vulkan.foundation.ecs.TraversalOrder} - traversal ordering</li>
 * </ul>
 *
 * <h2>Three-level model</h2>
 * <ol>
 *   <li>Level 1: The tree (Node parent/child structure)</li>
 *   <li>Level 2: Per-component-type traversal lists (TraversalView)</li>
 *   <li>Level 3: Dense backing arrays (caller-owned, updated via applyPatches())</li>
 * </ol>
 *
 * @see io.github.yetyman.vulkan.foundation.ecs.Tree
 * @see io.github.yetyman.vulkan.foundation.ecs.Node
 * @see io.github.yetyman.vulkan.foundation.ecs.Component
 */
package io.github.yetyman.vulkan.foundation.ecs;
