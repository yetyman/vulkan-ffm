# ECS Node Tree - Overview

## Location

Implemented in `vulkan-ffm-foundation` at package `io.github.yetyman.vulkan.foundation.ecs`.

Spatial components (e.g. `TransformComponent`) live OUTSIDE the foundation module — currently
as experimental examples in `sample-app/src/main/java/.../sample/spatial/`. This separation
is deliberate: any spatial system built in the infancy of this project will likely need
replacement, and the foundation module must not couple to any particular spatial representation.

## Detail Documents

- `01-lifecycle-and-tree.md` - Node/Component lifecycle states, tree structure, attach/detach,
  close semantics, before-close pass
- `02-component-di-and-claims.md` - dependency declaration (`requires()`), DI resolution phase,
  claim styles, lookup scope, reparent invalidation
- `03-events-and-input.md` - event firing/simulation, typed event registration, capture/bubble,
  property change notification, relationship to the existing UILayer input system
- `04-array-backing-and-render-integration.md` - the tree -> traversal view -> array three-level
  model, TraversalView, tree-scoped components, thread safety model

## Core Philosophy

- **The base system is not spatial and knows nothing about rendering.** No node/component base
  class has a position, rectangle, or "visible" concept. Those are concerns of specific
  component implementations, never baked into `Node` or `Component`. Hit-testing, layout, and
  drawing are entirely out of scope for the base module.
- **Composition over inheritance, all the way down.** There is no `UIElement` base class with
  accumulated properties. A "text label" is a `Node` with a `TextComponent`, a
  `RectangleComponent`, a font component, etc. attached.
- **This is explicitly NOT UI-specific.** The same tree/component/DI/event system must be usable
  for non-UI purposes - 3D scene graphs, hardware sensor hierarchies, anything hierarchical.
- **Renderers are just components.** No separate parallel "render tree." Data components must
  never reference render components.
- **Delta-carrying direct method calls over full re-passes or event objects** for structural
  change notifications (`onSiblingComponentAdded`/`Removed`).
- **Enum-keyed property change notification** per component type via `PropertyNotifier`. Each
  component defines its own small enum of mutable properties. Observers register per-property on
  a specific component instance. Not tree-propagated.
- **Traversal views are the universal iteration primitive.** Any code that needs an ordered
  iteration of nodes (input dispatch, rendering, physics) uses a `TraversalView`. Views are
  keyed by string name, optionally filtered by component type. The tree maintains a built-in
  all-nodes view (`tree.allNodes()`). No code should hardcode custom tree traversal logic when
  it could use a view.
- **Bulk-friendly by construction.** The tree → traversal view → dense array three-level model
  enables renderers to maintain dense GPU-friendly representations with incremental updates.
- **Zero-allocation hot paths.** Event dispatch, traversal view walks, and
  `CaptureBubbleTraversal` allocate nothing per invocation.
- **Trees are organizational, not event handlers.** Whether and how events flow through a tree
  is up to the owning code. `CaptureBubbleTraversal` is a utility for code that wants
  capture/bubble semantics over a view, not a required piece of the architecture.

## Implementation Status

All core systems are implemented and tested (73 unit tests):

- Tree/Node lifecycle state machine
- Component DI with `requires()`, claim styles (PERMISSIVE, SELF_EXCLUSIVE, EXCLUSIVE),
  lookup scopes (SELF, NEAREST_ANCESTOR), fallback policies
- Sibling add/remove notifications
- Ancestor-scope re-assessment on reparent and component add/remove
- Typed event system (`EventType` identity tokens, `EventHandler`, typed + receive-all)
- Enum-keyed property change notification (`PropertyNotifier`)
- `TraversalView` with O(1) add/remove/markDirty, intrusive linked list, dirty tracking
- String-keyed view registration, built-in `allNodes()` view
- `CaptureBubbleTraversal` with separate `handleEventCapture`/`handleEventBubble` methods
- `TreeLayer` as optional UILayer bridge (no input assumptions baked in)
- Tree-scoped components (`TreeComponent`)
- Per-tree claim registry (not global static state)

## Not Yet Implemented / Deferred

- `vulkan-ffm-ui-2d` module with default elements (RectangleComponent, TextComponent,
  FocusModelComponent, TouchStateComponent, etc.)
- Math package (Quaternion, Matrix4f, DualQuaternion) — planned for vulkan-core
- Dedicated dirty-tracking representation refinement (boolean-flag vs threshold, documented
  tension in 04-array-backing-and-render-integration.md)
- Cross-layer capture/bubble unification (layer stack as a view — the mechanism is the same
  at both levels, only the view differs)

## Key Classes

| Class | Role |
|-------|------|
| `Tree` | Root container. Owns root node, all-nodes view, tree components, view registry |
| `Node` | Tree node. Components, children, typed event handlers, zero-alloc fireEvent |
| `Component` | Interface for attachable behaviors with full lifecycle |
| `TreeComponent` | Interface for tree-scoped singletons |
| `TraversalView<C>` | Incrementally-maintained ordered linked list. O(1) ops. Optional component filter |
| `CaptureBubbleTraversal` | Walks a view forward/backward for capture/bubble. Zero alloc |
| `TreeLayer` | Optional UILayer bridge — owns a tree |
| `Event` / `EventType` / `EventHandler` | Typed event system with identity-token types |
| `PropertyNotifier<E>` | Enum-keyed per-instance property change notification |
| `Dependency` / `ClaimStyle` / `LookupScope` / `FallbackPolicy` | DI system |
| `NodeQuery` | Convenience query utility for finding nodes by component composition |
