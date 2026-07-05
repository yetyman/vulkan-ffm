# Array Backing and Render Integration

## The three levels, precisely

**Level 1 - the tree.** The single real `Node` parent/child structure (see
`01-lifecycle-and-tree.md`). Mutated immediately, synchronously. No dirty tracking lives here.
Assumed single-logical-thread (or externally synchronized) - see "Thread safety model" below.

**Level 2 - per-component-type traversal lists.** NOT part of the tree itself - a genuinely
separate, sparse structure, one per `(componentType, key)` registration (see keying below).
Conceptually a plain linked list of "nodes that currently have this component, in some
traversal order," incrementally maintained (O(1) splice) as the tree mutates. Dirty
tracking/marking lives HERE, paired with the linked-list modifications themselves - i.e. when a
node with this component type is added/removed/moved, that edit is both applied to the linked
list AND recorded as dirty state at the same time, immediately, synchronously.

**Level 3 - the dense backing array.** Owned and allocated by the CALLER (implementor), not the
framework - could be a Vulkan-mapped arena, a plain Java primitive array, anything. Updated only
via an explicit call to `applyPatches()` ON the registered traversal-view INSTANCE (i.e.
`view.applyPatches()` - an instance method invoked on a specific `ComponentTreeTraversalView`
object the caller is holding, NOT a static/class-level operation), which consumes whatever dirty
state Level 2 has accumulated since the last call and reports it back to the caller. Nothing
calls `applyPatches()` automatically - it is called explicitly, whenever the owner needs the
array current (typically render time, but not necessarily).

## `ComponentTreeTraversalView<C>` - the registration object

```java
public class ComponentTreeTraversalView<C extends Component> {
    private final Class<C> componentType;
    private final TraversalOrder order;
    private int liveCount = 0;

    // Level 2 backing structure - conceptual, exact storage TBD:
    // a linked structure of (Node, C instance) pairs in traversal order, plus whatever dirty
    // tracking representation is chosen (see "Dirty tracking - OPEN, unresolved" below).

    public Class<C> componentType() { return componentType; }
    public int liveCount() { return liveCount; }

    /** Consumes accumulated dirty state since the last call and reports it to the caller.
     *  Exact return type is the central open question of this document - see below. */
    public DirtyReport<C> applyPatches() { ... }

    /** Iterates the current (Node, C) pairs in traversal order. For callers that want to
     *  rebuild their own array from scratch rather than patch it incrementally. */
    public void forEach(BiConsumer<Node, C> visitor) { ... }
}
```

Registration:

```java
public class Tree {
    public <C extends Component> ComponentTreeTraversalView<C> getOrCreateComponentTraversal(
            Class<C> componentType, TraversalOrder order, Object key) { ... }

    public void releaseComponentTraversal(Class<?> componentType, Object key) { ... }
}
```

- Returns the SAME `ComponentTreeTraversalView` instance on repeat calls with the same
  `(componentType, key)` pair.
- `key` defaults to a shared/sentinel constant so the common case (one traversal view per
  component type per tree) needs no explicit key. A component wanting a second, differently
  ordered view of the same component type supplies its own key (any `Object` - registration map
  is off the hot path, see performance note below).
- `TraversalOrder` - traversal ordering strategy (exact enum/interface shape TBD at
  implementation time - e.g. depth-first pre-order, some custom comparator, etc).
- Registration keying performance note: the registration map
  (`Map<Class<?>, Map<Object, ComponentTreeTraversalView<?>>>` or similar) is only touched at
  registration time (rare - once per type/key combination per tree), never during steady-state
  traversal or patch application, which hold a direct reference to the resolved view instance.
  So a generic `Object` key is fine performance-wise despite `Object`-keyed maps generally being
  a smell - the map itself is never on the hot path.

## Dirty tracking representation - OPEN, UNRESOLVED, needs a follow-up design session

This is explicitly NOT decided. What follows is the tension as articulated in discussion,
recorded precisely so it can be picked up without re-deriving it, NOT a final answer.

**The core tension:** a naive per-change "patch object" list (one object per
add/remove/move/property-change) is a common ECS pattern, but risks unbounded allocation/list
growth if churn is high relative to how often `applyPatches()` is actually called - "even a
million patches could just be the same update over and over" (i.e. redundant patches for the
same index are common and wasteful to enumerate individually).

**Leading candidate discussed:** a plain `boolean[]` dirty-flag array (one flag per array slot)
plus a running dirty COUNT (incremented/decremented as flags are set/cleared), NOT a list of
patch objects. Rationale: marking a flag is O(1) and idempotent (re-marking an already-dirty
slot is a no-op, unlike appending another patch object) - this directly solves the "redundant
patches" problem the patch-list approach has. `applyPatches()` would then need to either:
  (a) return/iterate just the set flags (still needs some way to enumerate "which indices are
      dirty" without scanning the whole array every time - possibly a sparse set/bitset with
      popcount tricks, or an auxiliary dirty-index list maintained alongside the boolean array
      specifically to make iteration cheap while keeping the flag array for idempotent
      marking), or
  (b) expose a policy where, if `dirtyCount * K > totalCount` for some threshold constant K
      (discussed informally as roughly 3), the caller is told "just do a full rewrite" instead
      of being hand-fed individual dirty indices - since above that ratio, a full linear rewrite
      is cheaper than iterating and patching each dirty index individually.

**Alternative discussed and explicitly NOT preferred without more thought:** pool (reuse, don't
allocate per-change) `Patch` objects with no generic parameter (a patch describes "this slot
changed," not what it changed TO, since the array's actual data representation is caller-owned
and opaque to the traversal view anyway) - but this reintroduces the "list can grow large for no
real gain" problem the boolean-array approach avoids, unless also paired with the same
full-rewrite threshold fallback.

**Decision needed at implementation time (do not guess at this without a dedicated design pass):**
- Exact `DirtyReport<C>` shape - likely something like a sealed interface with two cases,
  `PartialReport(int[] dirtyIndices)` and `FullRewriteRecommended()`, chosen via the threshold
  policy above - but this is illustrative, not decided.
- Whether the threshold constant K is configurable per-view or a fixed framework default.
- How moves/removals interact with a flat boolean-flag model, given the swap-remove/free-list
  strategy described below (a removal causes a DIFFERENT slot's content to move into the freed
  slot - does that count as the freed slot being "dirty," the moved-into slot being "dirty," or
  both? Needs explicit resolution, not left implicit.)

## Tree-scoped components (singletons on the tree, not a node)

Symmetric lifecycle to node-scoped components (construct -> init -> resolveDependencies ->
afterResolve -> ready -> beforeClose -> close), just scoped to the `Tree` object itself rather
than to a `Node` (see `TreeComponent` interface sketch in `01-lifecycle-and-tree.md`).
Registration:

```java
tree.getOrRegisterTreeComponent(new MyBulkRendererComponent());
```

- A tree-scoped component (e.g. a bulk Vulkan renderer for all rectangles in the tree) can, in
  its own `resolveDependencies(Tree)` phase, call
  `tree.getOrCreateComponentTraversal(RectangleComponent.class, order, key)` to get DI-style
  access to the `ComponentTreeTraversalView` it needs, AND/OR call
  `tree.getOrRegisterTreeComponent(...)` again to depend on OTHER tree-scoped singletons (e.g. a
  shared descriptor pool manager component). This unifies "register a traversal view" and
  "register a singleton component that happens to use traversal views internally" into one
  consistent registration surface and lifecycle, rather than two disjoint concepts.
- Ordinary node-scoped components are equally free to call
  `tree.getOrCreateComponentTraversal(...)` directly if they want a traversal view without going
  through a tree-scoped component wrapper - both usage patterns are supported.

## Driving render from registered traversal views

Neither `Node` nor `Tree` "owns" the concept of rendering or drives a render pass itself - this
would violate the base module's render-agnosticism. Resolution: a render-system-specific driver
(living in whatever rendering layer consumes this module - e.g. a Vulkan UI renderer, NOT the
base ECS module) is responsible for:

1. Asking the tree for each `ComponentTreeTraversalView` it cares about (via
   `getOrCreateComponentTraversal`, using whatever type/key combinations that renderer needs).
2. Calling `view.applyPatches()` on each, at whatever point in its own frame sequencing it needs
   an up-to-date view (a "pre-pass" over its own render components, each choosing to call
   `applyPatches()` on its own view(s) as needed - not a single global "apply all patches now"
   framework-provided barrier).
3. Using the returned dirty report (see open section above) plus its own cached array to do
   bulk work (e.g. one big Vulkan draw call after patching or rewriting the vertex buffer).

## Release policy for traversal views whose last component was removed

Explicit decision: traversal views are NOT auto-destroyed when their live-component count
reaches zero. `ComponentTreeTraversalView.liveCount()` lets callers make a release decision
(e.g. "release if count == 0 for N frames"), but the framework imposes no default policy beyond
providing an explicit `Tree.releaseComponentTraversal(type, key)` call. Rationale: the right
behavior (keep it alive for fast add/remove churn vs. release immediately to free bookkeeping)
depends entirely on usage patterns the framework cannot predict - left to whichever system
registered the view (typically the render system, which knows its own churn characteristics) to
decide via the explicit release call.

## Thread safety model

- **Level 1 (tree) and Level 2 (per-type traversal lists): mutated immediately, synchronously,
  assumed single-logical-thread** (or externally synchronized by the caller - the framework does
  not attempt to detect or prevent concurrent misuse). Rationale: attempting to make raw tree
  mutation lock-free/concurrent would cost significant performance for a guarantee the framework
  can't fully provide anyway (it cannot stop a caller from doing genuinely unsafe concurrent
  overlapping updates) - the point of the traversal views' `applyPatches()` is clear separation;
  trying to lock everything all the time only serves to warn about things the user already
  shouldn't do.
- **Level 3 (array) via `view.applyPatches()`**: this is the one place cross-thread handoff is
  expected to matter in practice (e.g. tree mutated on a logic thread, array consumed on a render
  thread). The framework does NOT force any particular buffering strategy (no mandatory double
  buffering) - `applyPatches()` is just an instance method the view's owner calls explicitly. If
  an implementor needs safe cross-thread handoff, the RECOMMENDED pattern (not enforced) is: the
  view's owner maintains its own double buffer of the backing array (write to buffer A while a
  render thread consumes buffer B, swap pointers - never contents - between frames), entirely as
  that owner's own strategy, invisible to the base framework. A component needing more elaborate
  staggering can register multiple independent traversal views and control when each calls
  `applyPatches()` relative to the others.
- No `synchronized`, no locks, no atomics anywhere in the base tree/component/traversal-view
  machinery itself. This is a deliberate performance choice, not an oversight.

## Bulk render example this is designed for

Motivating example from discussion: a `RectangleRenderComponent` type across potentially many
nodes, all wanting their vertex data in ONE shared `FloatBuffer`/Vulkan buffer for one bulk draw
call (not one draw call per node). Each such component:

- Gets a `ComponentTreeTraversalView<RectangleComponent>` over the DATA component it renders
  (e.g. `RectangleComponent`), plus resolves its own slot in a caller-owned backing array during
  `afterResolve(Node)`.
- Caches its own slot index once (at `afterResolve()` time), never re-looking-up its slot by
  type/hashmap on the hot path.
- On its data dependency's property change (enum-keyed, see `03-events-and-input.md`), performs
  a targeted write into its cached slot - not a full rebuild.
- Add/remove uses a classic swap-remove/free-list strategy to keep the array dense without
  shifting - the standard ECS trick, with the explicit refinement that this cost should be paid
  ONLY on the level-3 array side (which is allowed to be O(1..n) at patch-apply time), since
  level 1/2 are already deterministically O(1) via the tree/list structure - i.e. don't let
  array-side swap-remove bookkeeping leak back into making tree mutation itself more expensive
  than O(1). See the "Dirty tracking representation" section above for the still-unresolved
  question of how swap-remove interacts with the dirty-flag model.
