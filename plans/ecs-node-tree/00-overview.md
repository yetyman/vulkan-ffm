# ECS Node Tree - Overview

## Purpose

A new module (tentatively `vulkan-ffm-elements`, name not finalized) providing a generic,
retained, hierarchical entity-component-system (ECS) for building UI (and non-UI - see below)
object trees. Built above `vulkan-ffm-foundation`, depended on by sample apps and eventually
possibly by pre-canned renderer implementations.

This is a substantial, multi-session design effort. This folder documents the full conceptual
design agreed upon in discussion BEFORE any implementation begins, so the work can be resumed
without re-deriving decisions already made. Read all four detail documents before writing code:

- `01-lifecycle-and-tree.md` - Node/Component lifecycle states, tree structure, attach/detach,
  close semantics, before-close pass
- `02-component-di-and-claims.md` - dependency declaration (`requires()`), DI resolution phase,
  claim styles, lookup scope, reparent invalidation
- `03-events-and-input.md` - event firing/simulation, capture/bubble semantics, relationship to
  the existing `vulkan-ffm-foundation` input system
- `04-array-backing-and-render-integration.md` - the tree -> list -> array three-level model,
  `ComponentTraversal`, tree-scoped components, thread safety model

## Core Philosophy (do not lose this while implementing)

- **The base system is not spatial and knows nothing about rendering.** No node/component base
  class has a position, rectangle, or "visible" concept. Those are concerns of specific
  component implementations (e.g. a `RectangleComponent`, a `VisibilityComponent`), never baked
  into `Node` or `Component`. Hit-testing, layout, and drawing are entirely out of scope for the
  base module - they are things specific components choose to implement.
- **Composition over inheritance, all the way down.** There is no `UIElement` base class with
  accumulated properties. A "text label" is a `Node` with a `TextComponent`, a
  `RectangleComponent`, a font component, etc. attached. Adding a new capability to one kind of
  element never requires touching a shared base class.
- **This is explicitly NOT UI-specific.** The same tree/component/DI/event system must be usable
  for non-UI purposes - 3D scene graphs, hardware sensor hierarchies, or anything else with a
  hierarchical structure and cross-cutting behaviors. UI is the first and primary consumer, not
  the only one.
- **Renderers are just components.** There is no separate parallel "render tree." A node that
  needs to be drawn has a render component attached alongside its data components, in the same
  tree, at the same node (owned by one `Tree`, see `01-lifecycle-and-tree.md`). Render
  components declare dependencies on data components via the same DI mechanism everything else
  uses. Data components must never reference render components.
- **Delta-carrying direct method calls over full re-passes or event objects, where the
  framework itself needs to notify components of structural changes.** Component add/remove on a
  node invokes hardcoded `Component` interface methods (`onSiblingComponentAdded`/`Removed`,
  default no-op) directly on siblings, carrying the exact delta (which component, what index) -
  NOT a full automatic `resolveDependencies()` re-pass, and NOT a dispatched event object. A
  component that wants the heavier guarantee can trigger its own re-resolution from inside these
  callbacks. See `02-component-di-and-claims.md`.
- **Enum-keyed change notification per component type, via direct observer registration.** Each
  component class defines its own small nested enum of "properties that can change" (e.g.
  `TextComponent.Prop.TEXT`). Property changes are observed via direct registration on the
  specific component instance - no string-based property lookup, and NOT tree-propagated.
- **Bulk-friendly by construction.** The tree -> per-type traversal list -> dense array
  three-level model exists specifically so that renderers (or any other system needing dense,
  ordered data - e.g. one big Vulkan buffer of rectangle vertices) can maintain that dense
  representation with incremental updates instead of full rebuilds, while the retained tree
  itself stays simple and immediate. The exact dirty-tracking representation for level 3 is
  explicitly UNRESOLVED - see `04-array-backing-and-render-integration.md`.

## Not Yet Decided / Explicitly Deferred

- Final module name and package layout.
- Whether `Node`/`Component` ship any default components at all, or the module is 100% BYO -
  current leaning is the base module ships only the tree/component/DI/event machinery, and a
  *separate* "default elements" layer (possibly in `vulkan-ffm-foundation` or its own module)
  provides common components like `RectangleComponent`, `TextComponent`, `TouchStateComponent`,
  `FocusModelComponent`, etc. This keeps the base ECS module usable for non-UI purposes without
  UI concepts leaking in.
- Concrete example components (text label, textless button) are sketched at a high level in
  `02-component-di-and-claims.md` (short illustrative code, not full implementations, per
  explicit instruction that this is docs-only work for now).
- Exact `Object` key type conventions for `getOrCreateComponentTraversal` (raw `Object`, or a
  small `TraversalKey` wrapper) - leaning `Object` since the registration map is off the hot path
  (see `04-array-backing-and-render-integration.md`).
- **The dirty-tracking/patch representation for `ComponentTreeTraversalView.applyPatches()` is
  explicitly UNRESOLVED** - a real, non-trivial open design problem (boolean dirty-flag array +
  count vs. pooled patch objects vs. a full-rewrite threshold fallback), documented in detail in
  `04-array-backing-and-render-integration.md` and requiring a dedicated follow-up discussion
  before implementation, not a default assumption.
- Exact dispatch mechanism for "does this component care about this event type" in
  `Node.fireEvent` (generic `handleEvent(Event)` override vs. typed per-`EventType`
  registration) - see `03-events-and-input.md`.
- Whether `stopPropagation()` persists or resets across the CAPTURE -> BUBBLE phase transition -
  see `03-events-and-input.md`.
- Bridging strategy between this module's `Node.fireEvent` and the existing
  `vulkan-ffm-foundation` `UIInputEvent`/`UIInputDispatcher`/`UILayer` system - see
  `03-events-and-input.md`.
- Exact claim-violation failure mode (throw vs. return-false) for `ClaimStyle` conflicts - see
  `02-component-di-and-claims.md`.

## IP Note

Confirmed in discussion: ECS as an architectural pattern predates Unity substantially and is not
patentable/copyrightable as a general concept. This design is an independent implementation, not
derived from any specific engine's source. No IP concern identified.
