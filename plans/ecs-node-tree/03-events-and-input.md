# Events and Input

## The base tree is not spatial

Critical, repeatedly-emphasized constraint: `Node`/`Component` have zero concept of position,
size, visibility, or hit-testing. Any spatial behavior (deciding "the pointer at screen
coordinate (x,y) means THIS node should receive an event") is entirely the responsibility of
specific component implementations (e.g. a rectangle/shape component performing hit-testing as
part of deciding where to originate a synthetic event) - never the base system. The base system
only provides: given a node someone else has already selected as the origin, propagate an event
through capture then bubble across the tree structure.

Capture in this system means "ancestors get first look at the event, in root-to-target order,"
full stop; it carries no implication of spatial hit-testing at any point.

## Event / EventType shape

```java
public abstract class Event {
    private Phase phase = Phase.CAPTURE;
    private boolean stopped = false;

    public Phase phase() { return phase; }
    void setPhase(Phase phase) { this.phase = phase; } // package-private, set by dispatch machinery

    public void stopPropagation() { this.stopped = true; }
    public boolean isStopped() { return stopped; }

    public enum Phase { CAPTURE, BUBBLE }
}

/** Type token + factory, parallel to the pattern used elsewhere in this codebase
 *  (see io.github.yetyman.vulkan.foundation.ui.input for a similar existing shape). */
public interface EventType<E extends Event> {
    E create(Object... data);
}
```

Concrete event example:

```java
public class ClickEvent extends Event {
    public final float x, y;
    public final int button;

    public ClickEvent(float x, float y, int button) {
        this.x = x; this.y = y; this.button = button;
    }

    public static final EventType<ClickEvent> TYPE = data ->
        new ClickEvent((float) data[0], (float) data[1], (int) data[2]);
}
```

## Firing events

```java
public class Node {
    // ... other members ...

    public void fireEvent(Event event) {
        // 1. CAPTURE: walk from tree.root() down to (and including) this node, root-to-target
        //    order. At each node, invoke every Component on that node whose type declares
        //    interest in this event (exact dispatch-to-component mechanism TBD - e.g. a
        //    Component.handleEvent(Event) method, or a typed registration - see open question
        //    below). Stop early if event.isStopped().
        // 2. event.setPhase(BUBBLE); reset stopped flag? NO - stopped should persist across the
        //    capture->bubble transition boundary is a real open question, see below.
        // 3. BUBBLE: walk from this node up to tree.root(), target-to-root order. Same dispatch.
    }

    public <E extends Event> E fireEvent(EventType<E> type, Object... data) {
        E event = type.create(data);
        fireEvent(event);
        return event;
    }
}
```

- ANY node can be the origin of ANY event type - no restriction that only certain nodes can fire
  certain events. This is explicitly required so that simulated/synthetic input (tests,
  programmatic interaction, non-pointer-device input sources) can produce indistinguishable
  results from "real" input.
- Dispatch order from a given origin node: CAPTURE (root of tree down to and including the origin
  node) then BUBBLE (origin node up to root).
- An event with `stopPropagation()` already called (or called by a capture-phase handler
  partway through) is honored normally - `fireEvent` does not force-clear or override
  propagation state before or during dispatch.
- **Open question, NOT resolved in discussion, needs a decision at implementation time**: does
  the `stopped` flag reset when transitioning from CAPTURE to BUBBLE (mirroring how the existing
  `vulkan-ffm-foundation` `PropagationState.resetForBubble()` behaves - stop flags reset,
  context/handled persist), or does a capture-phase stop also suppress the entire bubble phase?
  The existing foundation module's precedent (see `io.github.yetyman.vulkan.foundation.ui.input.
  PropagationState`) resets on phase transition - likely the right default here too for
  consistency, but not yet explicitly re-confirmed for this new system.
- **Open question**: exact mechanism for "does this component care about this event type" -
  candidates are (a) `Component` gets a generic `handleEvent(Event event)` method components
  override and internally `instanceof`/switch on event type, mirroring how
  `UILayer.handleInput(UIInputEvent)` works in the existing foundation module, or (b) components
  register typed handlers per `EventType` explicitly. (a) is simpler and more consistent with
  the rest of this design's "hardcoded interface methods over registration" preference (see
  `onSiblingComponentAdded`/`Removed` in `02-component-di-and-claims.md`) - leaning (a) but not
  finalized.
- **Open question**: does `fireEvent` require the CALLER to have already picked the correct
  origin node (current assumption - yes, since hit-testing is explicitly out of scope for the
  base system), or should there be a separate helper elsewhere (e.g. in a spatial/default-
  elements layer, NOT the base module) that does "synthesize a pointer event, hit-test from the
  root via some registered spatial component, then call fireEvent on the resolved node"? Current
  assumption: `fireEvent` itself is dumb/direct; any hit-testing convenience lives in a higher
  layer that calls it.

## Relationship to the existing `vulkan-ffm-foundation` input system

Not yet reconciled - open question for implementation time. The existing
`vulkan-ffm-foundation` `UIInputEvent`/`UIInputDispatcher`/`UILayer.handleInput()` system already
implements a capture/bubble dispatcher, but it operates over a flat list of `UILayer`s sorted by
`order()`, not a tree. Whether this new ECS node tree's event system:

(a) replaces the `UILayer` input dispatch entirely for trees built with this new module,
(b) is bridged to it (e.g. a `UILayer` implementation that owns a root `Node`/`Tree` and forwards
    `UIInputEvent`s into `Node.fireEvent` after doing its own hit-testing), or
(c) is entirely independent/unrelated (this ECS module's event system is for something else,
    and UI built with it uses its own separate input plumbing)

...is NOT decided yet and should be resolved explicitly before or during implementation, not
assumed. Likely (b) given the stated goal of building real interactive UI on top of this system,
but this needs to be a deliberate decision, not a default.

## Enum-keyed property change notification

Each component type defines its own small nested enum of properties that can change:

```java
public class TextComponent implements Component {
    public enum Prop { TEXT }

    private String text = "";
    private final List<BiConsumer<Prop, String>> observers = new ArrayList<>(); // shape illustrative only

    public String get() { return text; }

    public void set(String value) {
        this.text = value;
        notifyObservers(Prop.TEXT);
    }

    public void observe(Prop prop, Runnable listener) { ... } // exact API TBD
}
```

Changes fire through direct observer registration keyed on that enum - listeners (typically
render components, via their DI-resolved dependency) subscribe to specific enum values on a
SPECIFIC component instance, not string property names and not a tree-wide broadcast.

## Three separate notification concerns (not to be merged into one generic "event" type)

1. **Structural lifecycle notifications** (`onInit`, `resolveDependencies`, `afterResolve`,
   `onDetach`, `beforeClose`, `close`, `onSiblingComponentAdded`, `onSiblingComponentRemoved`) -
   fixed set, framework-defined hardcoded `Component` interface methods (default no-op), invoked
   as direct method calls, NOT dispatched as event objects. Node-local only for the
   sibling-add/remove pair (see `02-component-di-and-claims.md`); additionally, component
   add/remove and reparenting both trigger a separate, narrower ancestor-scope DI re-assessment
   walk over descendants (also detailed in `02-component-di-and-claims.md`) - that walk is a
   distinct mechanism from this notification list, not a fourth channel, since it doesn't notify
   anything itself, it only re-invokes `resolveDependencies()` on components that declared
   `NEAREST_ANCESTOR`.
2. **User/simulated input events** (`fireEvent`, capture/bubble) - open-ended event types,
   tree-wide propagation from a caller-chosen origin node. This is the only channel that is a
   real dispatched `Event` object traversing the tree.
3. **Property change notifications** (enum-keyed, per component type, direct observer
   registration on a specific component instance) - component-local, observed only by whoever
   explicitly registered, never tree-propagated at all.
