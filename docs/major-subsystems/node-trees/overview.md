# Node Tree System

## Overview

The node tree is a general-purpose hierarchical composition system. It provides the structural backbone for anything that organizes entities into parent/child trees with attached behaviors: UI widget hierarchies, 3D scene graphs, hardware sensor networks, or any other domain that benefits from hierarchical composition and dependency injection.

It lives in `vulkan-ffm-node-trees` at package `io.github.yetyman.vulkan.nodetree`.

---

## Data Flow (How Things Move Through the System)

```
                    STRUCTURAL LAYER
                    ================

   Tree
    |
    +-- Node (root)
         |-- Component A
         |-- Component B
         |
         +-- Node (child)
              |-- Component C  (declares: requires ComponentA from NEAREST_ANCESTOR)
              |-- Component D
              |
              +-- Node (grandchild)
                   |-- Component E

                         |
                         | DI resolution resolves cross-node
                         | dependencies at initialization time
                         |
                         v

                    EVENT LAYER
                    ===========

   EventType tokens (identity-based, open-ended)
        |
        v
   Node.fireEvent(event)  ------------>  CAPTURE: root -> target node
                                         BUBBLE:  target node -> root
                                         (handlers registered per-node, typed)

   CaptureBubbleTraversal  ----------->  Walks a TraversalView forward/backward
                                         (for subtree/tree-wide dispatch)

   PropertyNotifier<Enum>  ----------->  Per-component-instance change callbacks
                                         (direct observer, never tree-propagated)


                    ITERATION LAYER
                    ===============

   TraversalView<C>  (optimized incrementally-maintained linked list)
        |
        |-- Filtered by component type (e.g. all RectangleComponents)
        |-- Or unfiltered (all nodes, DFS pre-order)
        |-- O(1) add/remove/markDirty
        |-- Dirty chain tracks what changed since last read
        |
        v
   view.applyPatches()  -------------->  DirtyReport { dirtyEntries, fullRewriteRecommended }
        |
        v
   CALLER-OWNED DENSE ARRAY  --------->  GPU buffer, FloatBuffer, whatever
                                         (the framework never touches this directly)
```

---

## Structural Flow (Lifecycle of a Node)

```
 1. Construct Tree
 2. Create nodes (tree.root().createChild())
 3. Add components to nodes (node.addComponent(new Foo()))
      -- State: CONSTRUCTED --

 4. tree.initialize()
      |
      +-- onInit(node) on each component, declaration order
      |     -- State: INITIALIZED --
      |
      +-- resolveDependencies(node) in topological order (dependency-respecting)
      +-- afterResolve(node) in same order
            -- State: READY --

 5. Steady state: events fire, properties change, views iterate
      |
      +-- Late additions: addComponent() on a READY node runs the full
      |   init/resolve/afterResolve sequence immediately on the new component
      |
      +-- Reparenting: setParent() fires onDetach, moves the node,
          re-assesses ancestor-scope dependencies on all descendants

 6. tree.close() or node.close()
      |
      +-- Pass 1 (beforeClose, top-down): "stop referencing"
      +-- Pass 2 (close, bottom-up): "release resources"
            -- State: CLOSED --
```

---

## Core Design Rules

- **Non-spatial, non-visual base.** Node and Component know nothing about position, size, rendering, or hit-testing. Those are concerns of specific component layer implementations.
- **Composition over inheritance.** There is no `UIElement` base class. A "text label" is a Node with TextComponent + RectangleComponent + FontComponent attached.
- **Not UI-specific.** The same system works for 3D scene graphs, sensor hierarchies, or any tree-structured domain.
- **Renderers are just components.** No separate "render tree." Data components must never reference render components.
- **Zero-allocation hot paths.** Event dispatch, traversal view walks, and CaptureBubbleTraversal allocate nothing per invocation.
- **Trees are organizational, not event handlers.** Whether and how events flow through a tree is up to the owning code.
- **Bulk Processing**, to facilitate bulk data as a gpu library right should, node trees support tree scoped components in addition to node level components. These are often paired with a custom tree component representing the registry and collation of data from individual node's components

---

## Document Index

| Document | Contents |
|----------|----------|
| [trees.md](trees.md) | Tree, Node, Component, TreeComponent — lifecycle state machine, structure, API |
| [events.md](events.md) | EventType, Event, handlers, CaptureBubbleTraversal, PropertyNotifier |
| [traversal-views.md](traversal-views.md) | TraversalView, the three-level model, dirty tracking, bulk render patterns |
| [dependency-injection.md](dependency-injection.md) | Dependency, ClaimStyle, LookupScope, FallbackPolicy, resolution algorithm |
| [ui-system.md](ui-system.md) | UILayer, UIComposite, UIContext, input dispatch, AssetRegistry, render paths |

---

## Implementation Status

All core systems are implemented and tested (73 unit tests across 6 test files):

- Tree/Node lifecycle state machine
- Component DI with requires(), claim styles, lookup scopes, fallback policies
- Typed event system with identity-token EventTypes
- Enum-keyed property change notification (PropertyNotifier)
- TraversalView with O(1) operations and dirty tracking
- CaptureBubbleTraversal with separate capture/bubble methods
- TreeLayer (optional UILayer bridge)
- TreeComponent (tree-scoped singletons)
- NodeQuery (convenience query utility)

### Key Classes

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
