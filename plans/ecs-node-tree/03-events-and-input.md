# Events and Input

## The base tree is not spatial

`Node`/`Component` have zero concept of position, size, visibility, or hit-testing. The base
system only provides: given a node (or a traversal view), propagate an event through handlers.

## EventType (identity token, not Class-based)

```java
public final class EventType<E extends Event> {
    private final int id;       // unique, auto-incremented
    private final String name;  // human-readable debug name

    public static <E extends Event> EventType<E> create(String name) { ... }
}
```

EventType instances are created as static constants. Identity-based equality (not name-based).
Any module can define new EventType instances without touching the core.

```java
public static final EventType<ClickEvent> CLICK = EventType.create("click");
public static final EventType<ScrollEvent> SCROLL = EventType.create("scroll");
```

## Event base class

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

**Resolved:** `stopPropagation()` resets between capture and bubble phases (follows existing
`PropagationState.resetForBubble()` precedent in the foundation input system).

## Typed event handler registration (on Node)

**Resolved:** Components register typed handlers via `Node.addEventHandler(EventType, handler)`.
This is a typed registration system, not `Component.handleEvent(Event)` + instanceof dispatch.

```java
public <E extends Event> void addEventHandler(EventType<E> type, EventHandler<E> handler);
public <E extends Event> void removeEventHandler(EventType<E> type, EventHandler<E> handler);
public void addReceiveAllHandler(EventHandler<Event> handler);
public void removeReceiveAllHandler(EventHandler<Event> handler);
```

- Typed handlers only receive matching event types (O(1) lookup by EventType identity).
- Receive-all handlers get everything (useful for logging/debugging).
- Handler lists are lazily allocated (no cost for nodes with no handlers).
- Dispatch invokes typed handlers first, then receive-all handlers.

## Firing events (per-node capture/bubble)

`Node.fireEvent(event)` dispatches through the path from root to target node:
- CAPTURE: root → target
- BUBBLE: target → root

Zero allocation per fire (uses a static scratch buffer sized by depth).
This is for node-targeted events where the caller has already picked a specific target.

## CaptureBubbleTraversal (view-based dispatch)

For tree-wide or subtree-wide dispatch (not targeted at a specific node), use
`CaptureBubbleTraversal` which walks a `TraversalView`:

```java
public class CaptureBubbleTraversal {
    public CaptureBubbleTraversal(TraversalView<Component> view);
    public CaptureBubbleTraversal(Tree tree); // uses tree.allNodes()

    public boolean handleEvent(Event event);         // full capture + bubble
    public void handleEventCapture(Event event);     // capture only (head→tail)
    public void handleEventBubble(Event event);      // bubble only (tail→head)
}
```

**Key design point:** `handleEventCapture` and `handleEventBubble` are separate methods so
that layer-level and tree-level capture/bubble compose naturally. A UILayer receiving the
layer-stack capture phase calls `handleEventCapture` on its tree. On the layer-stack bubble
phase, it calls `handleEventBubble`. No special bridging needed.

Trees are organizational — not event handlers in themselves. Whether and how events flow
through a tree is entirely up to the owning code.

## Relationship to the existing UILayer input system

**Resolved:** The relationship is compositional, not bridging.

- The existing `UIInputDispatcher` dispatches across layers (highest→lowest capture,
  lowest→highest bubble).
- A layer that uses a tree internally decides if/how to propagate events through its tree.
- `CaptureBubbleTraversal` provides the utility for layers that want capture/bubble within
  their tree, composable with layer-level capture/bubble.
- A layer that just uses its tree for render organization doesn't dispatch events through it.
- No "bridge" layer is needed — the layer IS the integration point, and it calls whatever
  traversal methods it needs.

## Enum-keyed property change notification

**Implemented as `PropertyNotifier<E extends Enum<E>>`.**

Each component type defines its own small nested enum of properties that can change:

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

Performance:
- `fire()`: O(listeners for that property) — typically 1-2. Zero allocation.
- Storage: one `List<Runnable>` per enum value, lazily allocated on first observe.
- Not tree-propagated. Not broadcast. Direct per-instance registration.

## Three separate notification concerns

1. **Structural lifecycle notifications** — fixed set of Component interface methods
   (`onInit`, `resolveDependencies`, `afterResolve`, `onDetach`, `beforeClose`, `close`,
   `onSiblingComponentAdded`, `onSiblingComponentRemoved`). Direct method calls, not events.

2. **User/simulated input events** — open-ended EventType instances, dispatched via
   `Node.fireEvent` (node-targeted) or `CaptureBubbleTraversal` (view-wide). Typed handler
   registration on nodes.

3. **Property change notifications** — `PropertyNotifier`, enum-keyed, per component instance,
   direct observer registration. Never tree-propagated.

## Future advancement: cross-layer capture/bubble unification

Currently, capture/bubble exists at two levels:
1. **Layer level:** `UIInputDispatcher` walks layers highest→lowest (capture), lowest→highest (bubble).
2. **Tree level:** `CaptureBubbleTraversal` walks a view head→tail (capture), tail→head (bubble).

These compose (a layer calls `handleEventCapture` during the layer-stack capture phase, and
`handleEventBubble` during the layer-stack bubble phase). But architecturally, the mechanism
is identical at both levels — "walk an ordered list forward, walk it backward." The layer
stack is conceptually just another traversal view (a flat one, ordered by layer priority).

A potential future unification: represent the layer stack itself as a traversal view, making
`UIInputDispatcher` just another `CaptureBubbleTraversal` over a different view. This would
eliminate the duplication and make the dispatch mechanism fully generic. Not yet implemented,
but the current `handleEventCapture`/`handleEventBubble` split was designed with this in mind.
