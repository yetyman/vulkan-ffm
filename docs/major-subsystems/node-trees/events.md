# Events and Notifications

The Node Treesystem has three separate notification mechanisms, each serving a distinct purpose:

1. **Structural lifecycle notifications** — fixed Component interface methods (onInit, resolveDependencies, etc.). Direct method calls during state transitions.
2. **User/simulated input events** — open-ended EventType instances, dispatched via Node.fireEvent (node-targeted) or CaptureBubbleTraversal (view-wide). Typed handler registration on nodes.
3. **Property change notifications** — PropertyNotifier, enum-keyed, per component instance. Direct observer registration. Never tree-propagated.

---

## EventType (Identity Token)

EventType is the type key for event dispatch. It uses identity equality (not name-based), and any module can define new event types without touching the core:

```java
public final class EventType<E extends Event> {
    private final int id;       // unique, auto-incremented
    private final String name;  // human-readable debug name

    public static <E extends Event> EventType<E> create(String name) { ... }
}
```

Event types are created as static constants:

```java
public static final EventType<ClickEvent> CLICK = EventType.create("click");
public static final EventType<ScrollEvent> SCROLL = EventType.create("scroll");
public static final EventType<FocusEvent> FOCUS = EventType.create("focus");
```

This is deliberately NOT class-based dispatch. Multiple EventType instances can share the same Event subclass (e.g. FOCUS_GAINED and FOCUS_LOST could both use FocusEvent). The type token IS the identity.

---

## Event Base Class

```java
public abstract class Event {
    private final EventType<?> eventType;
    private Phase phase = Phase.CAPTURE;
    private boolean stopped = false;

    protected Event(EventType<?> eventType) { ... }

    public EventType<?> eventType() { ... }
    public Phase phase() { ... }
    public void stopPropagation() { ... }
    public boolean isStopped() { ... }

    // Package-private, set by dispatch machinery
    void setPhase(Phase phase) { ... }
    void resetForBubble() { stopped = false; }

    public enum Phase { CAPTURE, BUBBLE }
}
```

`stopPropagation()` resets between capture and bubble phases — an event stopped during capture still runs the bubble phase (matching the existing `PropagationState.resetForBubble()` pattern in the UILayer input system).

---

## Typed Event Handler Registration

Handlers are registered per-node, typed by EventType:

```java
// On Node:
public <E extends Event> void addEventHandler(EventType<E> type, EventHandler<E> handler);
public <E extends Event> void removeEventHandler(EventType<E> type, EventHandler<E> handler);
public void addReceiveAllHandler(EventHandler<Event> handler);
public void removeReceiveAllHandler(EventHandler<Event> handler);
```

```java
@FunctionalInterface
public interface EventHandler<E extends Event> {
    void handle(E event);
}
```

Design:
- Typed handlers only receive matching event types — O(1) lookup by EventType identity.
- Receive-all handlers get everything (useful for logging/debugging).
- Handler lists are lazily allocated (no cost for nodes with no handlers).
- Dispatch invokes typed handlers first, then receive-all handlers.

---

## Node.fireEvent (Per-Node Targeted Dispatch)

`Node.fireEvent(event)` dispatches through the path from root to target node:

```
CAPTURE: root -> ... -> parent -> target
BUBBLE:  target -> parent -> ... -> root
```

This is for events where the caller has already determined a specific target node (e.g. a click on a known widget). The path is computed from `node.pathFromRoot()`.

**Zero allocation per fire** — uses a static scratch buffer sized to tree depth. Safe under the single-logical-thread assumption.

---

## CaptureBubbleTraversal (View-Based Dispatch)

For tree-wide or subtree-wide dispatch (not targeted at a specific node), use `CaptureBubbleTraversal` which walks a `TraversalView`:

```java
public class CaptureBubbleTraversal {
    public CaptureBubbleTraversal(TraversalView<Component> view);
    public CaptureBubbleTraversal(Tree tree); // uses tree.allNodes()

    public boolean handleEvent(Event event);         // full capture + bubble
    public void handleEventCapture(Event event);     // capture only (head -> tail)
    public void handleEventBubble(Event event);      // bubble only (tail -> head)
}
```

### Key design point: separate capture/bubble methods

`handleEventCapture` and `handleEventBubble` are separate methods so that layer-level and tree-level capture/bubble compose naturally:

- A UILayer receiving the layer-stack capture phase calls `handleEventCapture` on its tree.
- On the layer-stack bubble phase, it calls `handleEventBubble`.
- No special bridging needed.

Trees are organizational — not event handlers in themselves. Whether and how events flow through a tree is entirely up to the owning code.

---

## Relationship to the UILayer Input System

The relationship is **compositional, not bridging**:

- The existing `UIInputDispatcher` dispatches across layers (highest-to-lowest capture, lowest-to-highest bubble).
- A layer that uses a tree internally decides if/how to propagate events through its tree.
- `CaptureBubbleTraversal` provides the utility for layers that want capture/bubble within their tree, composable with layer-level capture/bubble.
- A layer that just uses its tree for render organization does not dispatch events through it.
- No "bridge" layer is needed — the layer IS the integration point, and it calls whatever traversal methods it needs.

```
Layer Stack (UIInputDispatcher)         Tree within a Layer
================================        ====================

CAPTURE: highest order -> lowest        handleEventCapture: head -> tail
         (frontmost first)              (DFS pre-order forward)

BUBBLE:  lowest order -> highest        handleEventBubble: tail -> head
         (backmost first)               (DFS pre-order reverse)
```

The mechanism is identical at both levels — "walk an ordered list forward, walk it backward." The layer stack is conceptually just another traversal view (a flat one, ordered by layer priority). This is a recognized future unification opportunity but not yet implemented.

---

## PropertyNotifier (Enum-Keyed Change Notification)

A per-component-instance notification mechanism for property changes. Each component type defines its own small nested enum of mutable properties:

```java
public class TextComponent implements Component {
    public enum Prop { TEXT, FONT_SIZE, COLOR }

    private final PropertyNotifier<Prop> notifier = new PropertyNotifier<>(Prop.class);
    private String text = "";

    public String text() { return text; }
    public void setText(String value) {
        this.text = value;
        notifier.fire(Prop.TEXT);
    }

    public PropertyNotifier<Prop> properties() { return notifier; }
}
```

Observers (typically render components) subscribe per-property on a specific instance:

```java
@Override
public void afterResolve(Node node) {
    data.properties().observe(TextComponent.Prop.TEXT, () -> {
        // text changed, update my cached vertex data
    });
}
```

### Performance

- `fire()`: O(listeners for that property) — typically 1-2. Zero allocation.
- Storage: one `List<Runnable>` per enum value, lazily allocated on first observe.
- Not tree-propagated. Not broadcast. Direct per-instance registration.

### When to use PropertyNotifier vs Events

| Concern | Mechanism | Propagation |
|---------|-----------|-------------|
| "Component X's text property changed" | PropertyNotifier | Direct: observers of that instance only |
| "User clicked this node" | Event + EventType | Tree path: root-to-target capture, target-to-root bubble |
| "A sibling component was added" | Component.onSiblingComponentAdded | Direct: same-node siblings only |

PropertyNotifier is for fine-grained data-change tracking between components that have a direct relationship (typically a data component and its render component on the same node, or a parent's component observed by a child's component). It is never the right choice for user input or structural lifecycle events.
