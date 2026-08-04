# vulkan-ffm-ui-2d

A 2D UI scene graph built on the `vulkan-ffm-node-trees` node tree.

## Purpose

Provides a hierarchical, interactive 2D UI system with:

- **Nine-slice rectangle rendering** — bulk-rendered via a single draw call using the
  node treeTraversalView + dense vertex buffer pattern
- **Spatial hit-testing** — axis-aligned grid acceleration structure for O(1) average
  point queries, with lazy rebuild on element movement
- **Tree-based input dispatch** — capture/bubble event flow through the node hierarchy,
  with hit-test determining the target node for pointer events
- **Optimized bulk property notification** — tree-scoped renderers receive direct
  (component, property, slotIndex) callbacks without per-instance lambda overhead

## Architecture

This module uses `vulkan-ffm-node-trees`'s Node Tree (`Tree`, `Node`, `Component`, `TraversalView`)
as its structural backbone. It adds:

| Component | Type | Role |
|-----------|------|------|
| `RectangleComponent` | Data (per-node) | Position, size, color, nine-slice config |
| `NineSliceRenderer` | Tree-scoped | Bulk vertex buffer, single draw call for all rects |
| `SpatialGrid` | Tree-scoped | AABB acceleration for hit-testing |
| `HoverStateComponent` | Data (per-node) | Tracks hovered/pressed state |
| `FocusModelComponent` | Data (per-node) | Focus tracking |

## Design Principles

- **Rendering is separate from data.** `RectangleComponent` knows nothing about Vulkan.
  `NineSliceRenderer` does all GPU work.
- **Spatial is replaceable.** The `SpatialGrid` is a concrete implementation. A future
  BVH or GPU-resident structure could replace it without touching the rest.
- **No GLFW dependency.** This module provides the scene graph and rendering logic.
  Example apps that need windowing/input depend on GLFW in the sample-app module.

## Module Dependencies

```
vulkan-ffm-sample-ui-layers
  └── vulkan-ffm-node-trees (Node Tree, UILayer, vulkan-core)
```
