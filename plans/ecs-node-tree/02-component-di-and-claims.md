# Component Dependency Injection and Claims

## Why DI is a separate phase from construction

Components are constructed and added to a node in declaration order, but a component's
dependencies (sibling components on the same node, or ancestor-scoped components elsewhere in
the tree) may not exist yet at construction/attach time - classic ordering problem. Resolution
is therefore a distinct second phase (`resolveDependencies(Node)`), run once all of a node's
initial components have completed `onInit(Node)`. See `01-lifecycle-and-tree.md` for the full
state machine. `requires()` is a METHOD (not a static/annotation-based attribute) per explicit
preference - this allows a component's dependency declarations to depend on its own instance
configuration (e.g. a component configured with an optional feature flag might declare an extra
dependency only when that flag is set).

## Declaring dependencies: `requires()` and `Dependency<T>`

```java
public record Dependency<T extends Component>(
    Class<T> type,
    ClaimStyle claim,
    LookupScope scope,
    FallbackPolicy<T> fallback
) {}

@FunctionalInterface
public interface FallbackPolicy<T extends Component> {
    /** Called when resolution finds no existing instance. Return a newly-constructed default,
     *  or throw if this dependency has no sensible default (required dependency). */
    T createDefault(Node requestingNode);

    static <T extends Component> FallbackPolicy<T> required() {
        return node -> { throw new IllegalStateException("no default available"); };
    }
}
```

`Component.requires()`:

```java
default List<Dependency<?>> requires() { return List.of(); }
```

Example usage inside a concrete component:

```java
public class TextLabelRenderComponent implements Component {
    private TextComponent text;
    private RectangleComponent rect;

    @Override
    public List<Dependency<?>> requires() {
        return List.of(
            new Dependency<>(TextComponent.class, ClaimStyle.PERMISSIVE, LookupScope.SELF, FallbackPolicy.required()),
            new Dependency<>(RectangleComponent.class, ClaimStyle.PERMISSIVE, LookupScope.SELF, FallbackPolicy.required())
        );
    }

    @Override
    public void resolveDependencies(Node node) {
        this.text = node.getComponent(TextComponent.class);
        this.rect = node.getComponent(RectangleComponent.class);
    }
}
```

Resolution order across a node's components: dependency-respecting topological order (a
component depended on by another resolves first), falling back to declaration order among
components with no ordering constraint relative to each other ("preserve declaration order in
grey space" - explicitly agreed this is fine, no need for a stronger tie-break rule).

## Claim Styles

```java
public enum ClaimStyle {
    /** Any number of components may bind to the same dependency instance. Default/common case. */
    PERMISSIVE,
    /** Only one instance of the REQUESTING component's own concrete type may claim a given
     *  dependency instance. (Two FocusHighlightComponents cannot both bind to the same
     *  ColorComponent; a FocusHighlightComponent and an unrelated AnimationComponent can.) */
    SELF_EXCLUSIVE,
    /** Once claimed by ANY component, no other component of any type may also bind to that
     *  same dependency instance - they must resolve/create their own separate instance. */
    EXCLUSIVE
}
```

### Claim tracking mechanism (resolved during design discussion, decision delegated to and made here)

Claims are tracked on the CLAIMED component instance itself, not on `Node` or `Tree` - this
keeps the check local to whichever component is actually being depended upon, avoiding a second
global registry:

```java
public interface Component {
    // ... other members ...

    /** Lazily-allocated by the base Component machinery (not by implementors) the first time a
     *  claim is checked/recorded against this instance. Package-private / framework-internal -
     *  implementors never touch this directly, it's exercised only by the DI resolution engine. */
    // Map<Class<? extends Component>, ClaimStyle> claimedBy;  // conceptual - exact storage TBD

    /** Called by the DI resolution engine (not by implementors) when another component attempts
     *  to bind to this instance with the given claim style. Returns true if the claim is
     *  compatible with any existing claims (and records it), false/throws if it conflicts.
     *  Exact failure mode (boolean return vs. throwing) TBD at implementation time - current
     *  leaning is throwing, since a claim violation indicates a real assembly bug rather than a
     *  recoverable runtime condition. */
    // boolean tryClaim(Class<? extends Component> requesterType, ClaimStyle style);
}
```

Conceptual algorithm for `tryClaim(requesterType, style)`, evaluated against whatever claims
already exist on this instance:

- If no prior claims exist: record `(requesterType, style)`, succeed unconditionally.
- If `style == PERMISSIVE`: succeed unconditionally regardless of prior claims (permissive never
  conflicts with anything, and never blocks anything else from claiming afterward either).
- If `style == SELF_EXCLUSIVE`: fail if any PRIOR claim exists with the SAME `requesterType`;
  otherwise record and succeed.
- If `style == EXCLUSIVE`: fail if ANY prior claim of ANY kind exists; otherwise record and
  succeed. An `EXCLUSIVE` claim also causes all FUTURE claim attempts (of any style) to fail
  until this instance is no longer referenced by the exclusive claimant (exact "no longer
  referenced" trigger - e.g. the claimant's own removal - TBD at implementation time).

This check runs during `resolveDependencies()`, at the point a component actually resolves a
`Dependency<T>` entry against a found instance - not at `requires()` declaration time (which is
just static metadata, no instance exists yet to claim).

## Lookup Scope

```java
public enum LookupScope {
    /** Dependency must be a component on the same node. */
    SELF,
    /** Walk up the parent chain from this node (EXCLUSIVE of self, since SELF already covers
     *  the same-node case) until a node carrying the dependency type is found. */
    NEAREST_ANCESTOR
}
```

Combined with claim styles, `NEAREST_ANCESTOR` is what enables e.g. a single `FocusModel`
component on a collapsible widget's root node to govern multiple `ColorComponent`s living on
descendant nodes (header, subtitle, etc.) WITHOUT requiring every descendant node to also carry
its own `FocusModel`, and without needing a node-hiding/indirection hack.

**Escape hatch**: if a component needs a lookup scope richer than SELF/NEAREST_ANCESTOR (e.g.
"nearest ancestor matching some predicate"), it does NOT extend the declarative `requires()`
system - it simply does the lookup manually inside its own `resolveDependencies(Node)` override,
which already has full tree access via `node.parent()` chains. The declarative path
intentionally covers only the common 90% case.

## Component add/remove notification (hardcoded interface methods, not a full DI re-pass, not an event)

`Component` declares two methods with default no-op implementations (see full interface sketch
in `01-lifecycle-and-tree.md`):

```java
default void onSiblingComponentAdded(Component added, int index) {}
default void onSiblingComponentRemoved(Component removed, int index) {}
```

Whenever a component is added to or removed from a node (after the node's initial construction),
EVERY OTHER component currently on that node has the corresponding method invoked directly
(a plain method call, not a dispatched/queued event object). This is deliberately NOT a full
`resolveDependencies()` re-pass by default: a full re-pass loses information a direct
delta-carrying call preserves - a component reacting to `onSiblingComponentRemoved` knows
EXACTLY which component instance was removed and at what index, with no need to diff its own old
cached reference against a freshly-recomputed one. A component that doesn't need to react to
runtime sibling changes simply never overrides these methods and is completely unaffected - this
is the intended default case, not a degraded fallback.

A component THAT DOES want the heavier guarantee (e.g. "fully re-resolve my dependencies any
time anything on this node changes") is free to call its own `resolveDependencies(node)` again
from inside its `onSiblingComponentAdded`/`Removed` override - the framework does not prevent
this, it simply does not do it automatically for every component by default.

This notification is node-local only (fired to siblings on the SAME node) and is entirely
separate from the ancestor-scope re-assessment walk below, which is a different mechanism
triggered by the same two structural operations (component add/remove, and reparenting) but
serves a different purpose (keeping `NEAREST_ANCESTOR` resolutions correct across the tree, not
notifying siblings on one node).

## Reparent invalidation for ancestor-scope dependencies

**Unified rule:** whenever the set of components visible via an ancestor-walk from some subtree
could have changed, re-assess `NEAREST_ANCESTOR` dependencies within that subtree. Two triggers,
both reducing to this same invariant. This walk is entirely independent of, and in addition to,
the node-local `onSiblingComponentAdded`/`Removed` notification above - that notification is
node-local only and carries no automatic re-resolution; THIS walk is the mechanism that keeps
ancestor-scope resolutions elsewhere in the tree correct.

1. **Component add/remove on node N**: walk N's descendants and, for every component whose
   `requires()` declares a `NEAREST_ANCESTOR` dependency on the type that was just added/removed
   on N, re-run that component's `resolveDependencies(node)`. This is necessary because N is an
   ancestor of everything beneath it, so changing N's own component set can change what "nearest
   ancestor of type X" resolves to for any descendant. (N's OWN components do NOT automatically
   re-run `resolveDependencies()` as part of this - see the add/remove notification section
   above; N's siblings only get the lightweight direct-method notification unless a component
   chooses to re-resolve itself in response to it.)
2. **Reparenting node N** (`setParent`): walk N's descendants (NOT N itself - N's own components
   are untouched by reparenting) and, for every component whose `requires()` declares a
   `NEAREST_ANCESTOR` dependency, re-run that component's `resolveDependencies(node)`. This is
   necessary because N's ancestor chain changed, which can change what "nearest ancestor" means
   for anything beneath N.

Both cases are implemented as a targeted subtree walk filtered by static `requires()` metadata
(does this component declare a `NEAREST_ANCESTOR` dependency, optionally on this specific
type?) - NOT a dynamic listener/registration list. No listener registration, no deregistration
bookkeeping, no live counting anywhere in this mechanism. This was an explicit, deliberate
correction during design discussion: an earlier draft proposed ancestor-scope dependents
registering themselves as listeners on their resolved ancestor node, which was rejected because
listener lifetime/deregistration correctness (ensuring a listener is removed on every possible
removal/close/reparent-away path) was judged too fragile relative to a plain subtree walk
triggered directly by the two structural operations that can actually invalidate the result.

Cost model (clarified during discussion - the cost is paid at TREE-MUTATION time, not at
traversal-consumption time, and is bounded by how many components in the affected subtree
actually declare `NEAREST_ANCESTOR` on the relevant type via static `requires()` metadata, not
by total tree size or total registered dependency count app-wide). This is the same shape of
cost/fan-out argument used for `ComponentTreeTraversalView` splice costs in
`04-array-backing-and-render-integration.md` - both are "cost proportional to how many things
actually care about this specific type, not proportional to everything registered."

## Worked examples (sketches only, not full implementations)

### `TextLabel` (from discussion - motivated the DI/claim design)

```java
public class TextLabel {
    public final Node node;
    public final TextComponent text;
    public final RectangleComponent rect;

    public TextLabel(Tree tree) {
        this.node = tree.root().createChild();
        this.text = node.addComponent(new TextComponent());
        this.rect = node.addComponent(new RectangleComponent());
        node.addComponent(new TouchStateComponent());
        node.addComponent(new FontComponent());
        node.addComponent(new FormattingComponent());
        node.addComponent(new TextLabelRenderComponent()); // requires() TextComponent + RectangleComponent
    }

    // Convenience accessors avoiding node.getComponent(...) ceremony at call sites -
    // this is the "wrapper" pattern discussed for the data/render access pain point.
    public String text() { return text.get(); }
    public void setText(String value) { text.set(value); }
}
```

### Textless button (sketch only, per explicit instruction - short, not a full implementation)

```java
public class Button {
    public final Node node;
    public final RectangleComponent rect;
    public final TouchStateComponent touch;

    public Button(Tree tree) {
        this.node = tree.root().createChild();
        this.rect = node.addComponent(new RectangleComponent());
        this.touch = node.addComponent(new TouchStateComponent());
        node.addComponent(new ButtonRenderComponent()); // requires() RectangleComponent + TouchStateComponent,
                                                          // e.g. draws a flat-colored rect that changes shade
                                                          // based on touch.isPressed()/isHovered()
    }

    public void onClick(Runnable handler) {
        // touch or a small ActionComponent could expose this - exact shape not designed yet,
        // this is illustrative only.
    }
}
```

Note both examples above show the "convenience wrapper" pattern discussed for avoiding
`((ButtonRender) button1.renderer()).setColor(...)`-style downcasting pain: a small per-element
class wraps a `Node` plus direct references to the specific components it knows it created, and
exposes plain getters/setters that delegate to those components - no generic "wrapper of
everything" abstraction needed, since each concrete element type already knows exactly which
components it assembled.

### The collapsible-widget-with-shared-FocusModel example (motivating NEAREST_ANCESTOR + claims together)

A collapsible section's root node carries one `FocusModelComponent`. Its header's
`ColorComponent` and its (optional) subtitle's `ColorComponent` - potentially on different
descendant nodes - both declare:

```java
new Dependency<>(FocusModelComponent.class, ClaimStyle.PERMISSIVE, LookupScope.NEAREST_ANCESTOR, FallbackPolicy.required())
```

and both resolve to the SAME `FocusModelComponent` instance on the collapsible's root node,
without either descendant needing its own `FocusModelComponent` and without any node-hiding
indirection. `PERMISSIVE` claim style is appropriate here since multiple unrelated
`ColorComponent`s legitimately share one `FocusModelComponent`.
