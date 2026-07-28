# Component Dependency Injection and Claims

## DI is a separate phase from construction

Components are constructed and added to a node in declaration order, but dependencies may not
exist yet at construction time. Resolution is a distinct second phase (`resolveDependencies`),
run once all of a node's initial components have completed `onInit`. `requires()` is a METHOD
(not static/annotation-based) so dependency declarations can depend on instance configuration.

## Declaring dependencies

```java
public record Dependency<T extends Component>(
    Class<T> type,
    ClaimStyle claim,
    LookupScope scope,
    FallbackPolicy<T> fallback
) {
    // Convenience factories:
    public static <T extends Component> Dependency<T> selfRequired(Class<T> type);
    public static <T extends Component> Dependency<T> ancestorRequired(Class<T> type);
    public static <T extends Component> Dependency<T> selfOptional(Class<T> type);
    public static <T extends Component> Dependency<T> ancestorOptional(Class<T> type);
}
```

```java
default List<Dependency<?>> requires() { return List.of(); }
```

Resolution order: dependency-respecting topological sort (a component depended on by another
resolves first), falling back to declaration order among components with no ordering constraint.

## Claim Styles

```java
public enum ClaimStyle {
    PERMISSIVE,      // any number of components may bind to the same instance
    SELF_EXCLUSIVE,  // only one instance of the requester's type may claim this
    EXCLUSIVE        // once claimed, no other component may also claim this
}
```

### Claim tracking

Claims are tracked per-tree in an `IdentityHashMap<Component, Map<Class<?>, ClaimStyle>>` on
the `Tree` instance. Not global static state — cleaned up when the tree is closed.

**Violation behavior (resolved):** claim violations THROW `IllegalStateException` and LOG via
`Logger.warn()`. This is appropriate because violations indicate assembly bugs detected at
startup, not recoverable runtime conditions.

Algorithm for claim checking:
- PERMISSIVE: always succeeds, always records.
- SELF_EXCLUSIVE: fails if same requester type already has a non-permissive claim.
- EXCLUSIVE: fails if ANY prior non-permissive claim exists.

## Lookup Scope

```java
public enum LookupScope {
    SELF,             // same node only
    NEAREST_ANCESTOR  // walk parent chain (exclusive of self)
}
```

**Escape hatch:** for lookup scopes richer than SELF/NEAREST_ANCESTOR, do the lookup manually
inside `resolveDependencies(Node)` which has full tree access via `node.parent()` chains.

## Fallback Policy

```java
@FunctionalInterface
public interface FallbackPolicy<T extends Component> {
    T createDefault(Node requestingNode);

    static <T extends Component> FallbackPolicy<T> required();  // throws
    static <T extends Component> FallbackPolicy<T> optional();  // returns null
}
```

If resolution finds no instance and fallback creates one, it's added to the requesting node.

## Node.resolveDependency (package-private)

```java
<T extends Component> T resolveDependency(Dependency<T> dependency, Component requester) {
    // 1. Search per scope (SELF or NEAREST_ANCESTOR)
    // 2. If not found, try fallback
    // 3. If found, apply claim rules (tryClaim)
    // 4. Return result
}
```

## Sibling component add/remove notification

When a component is added/removed from a READY node:
- Every OTHER component on the same node has `onSiblingComponentAdded`/`Removed` invoked.
- This is a direct method call, NOT an event object.
- Default no-op — components that don't care never override these.
- A component wanting full re-resolution can call its own `resolveDependencies(node)` from
  inside the callback.

## Reparent invalidation for ancestor-scope dependencies

Two triggers, same mechanism:

1. **Component add/remove on node N:** walk N's descendants. For every component whose
   `requires()` declares a `NEAREST_ANCESTOR` dependency on the type that was added/removed,
   re-run `resolveDependencies(node)`.

2. **Reparenting node N:** walk N's descendants (not N itself). For every component with any
   `NEAREST_ANCESTOR` dependency, re-run `resolveDependencies(node)`.

Both are targeted subtree walks filtered by static `requires()` metadata. No listener
registration, no deregistration bookkeeping.

## Worked examples (sketches for future vulkan-ffm-ui-2d module)

### TextLabel (convenience wrapper pattern)

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

    public String text() { return text.get(); }
    public void setText(String value) { text.set(value); }
}
```

This is the "convenience wrapper" pattern: a small per-element class wraps a Node plus
direct references to the specific components it created, and exposes plain getters/setters.
No generic "wrapper of everything" abstraction needed.

### Textless Button

```java
public class Button {
    public final Node node;
    public final RectangleComponent rect;
    public final TouchStateComponent touch;

    public Button(Tree tree) {
        this.node = tree.root().createChild();
        this.rect = node.addComponent(new RectangleComponent());
        this.touch = node.addComponent(new TouchStateComponent());
        node.addComponent(new ButtonRenderComponent()); // requires() RectangleComponent + TouchStateComponent
    }
}
```

### Collapsible widget with shared FocusModel (NEAREST_ANCESTOR + PERMISSIVE)

A collapsible section's root node carries one `FocusModelComponent`. Its header's
`ColorComponent` and its subtitle's `ColorComponent` — on different descendant nodes —
both declare:

```java
new Dependency<>(FocusModelComponent.class, ClaimStyle.PERMISSIVE, LookupScope.NEAREST_ANCESTOR, FallbackPolicy.required())
```

Both resolve to the SAME `FocusModelComponent` instance on the collapsible's root node,
without either descendant needing its own. `PERMISSIVE` claim style is appropriate since
multiple unrelated components legitimately share one FocusModel.
