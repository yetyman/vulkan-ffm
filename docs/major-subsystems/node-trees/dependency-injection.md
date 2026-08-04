# Dependency Injection

## Overview

DI is a separate phase from construction. Components are constructed and added to a node in declaration order, but dependencies may not exist yet at construction time. Resolution is a distinct second phase (`resolveDependencies`), run once all of a node's initial components have completed `onInit`.

`requires()` is an instance METHOD (not static/annotation-based) so dependency declarations can depend on instance configuration.

---

## Declaring Dependencies

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

Components declare their dependencies by overriding `requires()`:

```java
public class TextLabelRenderComponent implements Component {
    @Override
    public List<Dependency<?>> requires() {
        return List.of(
            Dependency.selfRequired(TextComponent.class),
            Dependency.selfRequired(RectangleComponent.class),
            Dependency.ancestorRequired(FontComponent.class)
        );
    }

    @Override
    public void resolveDependencies(Node node) {
        // Dependencies are resolved and available here
        this.text = node.getComponent(TextComponent.class);
        this.rect = node.getComponent(RectangleComponent.class);
        // FontComponent found on an ancestor node
    }
}
```

### Resolution order

Components are resolved in dependency-respecting topological order: a component depended on by another resolves first. Components with no ordering constraint among each other fall back to declaration order.

---

## ClaimStyle

```java
public enum ClaimStyle {
    PERMISSIVE,      // any number of components may bind to the same instance
    SELF_EXCLUSIVE,  // only one instance of the requester's type may claim this
    EXCLUSIVE        // once claimed, no other component may also claim this
}
```

### Claim tracking

Claims are tracked per-tree in an `IdentityHashMap<Component, Map<Class<?>, ClaimStyle>>` on the `Tree` instance. Not global static state — cleaned up when the tree is closed.

### Violation behavior

Claim violations THROW `IllegalStateException` and LOG via `Logger.warn()`. This is appropriate because violations indicate assembly bugs detected at startup, not recoverable runtime conditions.

### Claim checking algorithm

- **PERMISSIVE**: always succeeds, always records.
- **SELF_EXCLUSIVE**: fails if same requester type already has a non-permissive claim on the target.
- **EXCLUSIVE**: fails if ANY prior non-permissive claim exists on the target.

### When to use each style

| Style | Use case |
|-------|----------|
| PERMISSIVE | Multiple unrelated components legitimately share one service (e.g. many descendants sharing one FocusModelComponent on an ancestor) |
| SELF_EXCLUSIVE | Only one renderer of a given type should bind to a data component (prevents duplicate TextRenderComponents both claiming the same TextComponent) |
| EXCLUSIVE | A component that requires sole ownership (e.g. a physics body that must not be shared) |

---

## LookupScope

```java
public enum LookupScope {
    SELF,             // same node only
    NEAREST_ANCESTOR  // walk parent chain (exclusive of self)
}
```

**SELF:** The dependency must be on the same node as the requester.

**NEAREST_ANCESTOR:** Walk up the parent chain (not including the requester's own node) and return the first matching component found.

### Escape hatch

For lookup scopes richer than SELF/NEAREST_ANCESTOR (e.g. "find the nearest sibling with X", "find any descendant with Y"), do the lookup manually inside `resolveDependencies(Node)` which has full tree access via `node.parent()` chains, `node.children()`, and traversal utilities.

---

## FallbackPolicy

```java
@FunctionalInterface
public interface FallbackPolicy<T extends Component> {
    T createDefault(Node requestingNode);

    static <T extends Component> FallbackPolicy<T> required();  // throws if not found
    static <T extends Component> FallbackPolicy<T> optional();  // returns null if not found
}
```

If resolution finds no instance and the fallback creates one, the created component is added to the requesting node (runs the full init/resolve/afterResolve sequence immediately).

Custom fallbacks can create components with specific configuration:

```java
new Dependency<>(
    FontComponent.class,
    ClaimStyle.PERMISSIVE,
    LookupScope.NEAREST_ANCESTOR,
    node -> new FontComponent("sans-serif", 14.0f)  // auto-create with defaults
)
```

---

## Resolution Algorithm (Node.resolveDependency)

```java
// Package-private
<T extends Component> T resolveDependency(Dependency<T> dependency, Component requester) {
    // 1. Search per scope
    T found = switch (dependency.scope()) {
        case SELF -> getComponent(dependency.type());
        case NEAREST_ANCESTOR -> walkAncestorsFor(dependency.type());
    };

    // 2. If not found, try fallback
    if (found == null) {
        found = dependency.fallback().createDefault(this);
        if (found != null) {
            addComponent(found);  // adds to THIS node, runs lifecycle
        }
    }

    // 3. If found, apply claim rules
    if (found != null) {
        tree().tryClaim(found, requester.getClass(), dependency.claim());
    }

    // 4. Return (may be null for optional dependencies)
    return found;
}
```

---

## Sibling Component Add/Remove Notification

When a component is added/removed from a READY node:
- Every OTHER component on the same node has `onSiblingComponentAdded`/`Removed` invoked.
- This is a direct method call, NOT an event object.
- Default no-op — components that do not care never override these.
- A component wanting full re-resolution can call its own `resolveDependencies(node)` from inside the callback.

---

## Reparent Invalidation for Ancestor-Scope Dependencies

Two triggers, same mechanism:

### 1. Component add/remove on node N

Walk N's descendants. For every component whose `requires()` declares a `NEAREST_ANCESTOR` dependency on the type that was added/removed, re-run `resolveDependencies(node)`.

### 2. Reparenting node N

Walk N's descendants (not N itself). For every component with any `NEAREST_ANCESTOR` dependency, re-run `resolveDependencies(node)`.

Both are targeted subtree walks filtered by static `requires()` metadata. No listener registration, no deregistration bookkeeping.

---

## Worked Examples

### Collapsible widget with shared FocusModel

A collapsible section's root node carries one `FocusModelComponent`. Its header's `ColorComponent` and its subtitle's `ColorComponent` — on different descendant nodes — both declare:

```java
new Dependency<>(
    FocusModelComponent.class,
    ClaimStyle.PERMISSIVE,
    LookupScope.NEAREST_ANCESTOR,
    FallbackPolicy.required()
)
```

Both resolve to the SAME `FocusModelComponent` instance on the collapsible's root node, without either descendant needing its own. `PERMISSIVE` claim style is appropriate since multiple unrelated components legitimately share one FocusModel.

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
        node.addComponent(new TextLabelRenderComponent());
        // TextLabelRenderComponent.requires() -> TextComponent + RectangleComponent (SELF)
    }

    public String text() { return text.get(); }
    public void setText(String value) { text.set(value); }
}
```

This is the "convenience wrapper" pattern: a small per-element class wraps a Node plus direct references to the specific components it created, and exposes plain getters/setters. No generic "wrapper of everything" abstraction needed.

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
        node.addComponent(new ButtonRenderComponent());
        // ButtonRenderComponent.requires() -> RectangleComponent + TouchStateComponent (SELF)
    }
}
```
