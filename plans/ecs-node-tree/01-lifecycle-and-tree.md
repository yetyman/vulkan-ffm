# Lifecycle and Tree Structure

## Tree

`Tree` owns the root `Node` and owns tree-scoped component/traversal-view registration (see
`04-array-backing-and-render-integration.md`). Every `Node` holds a back-reference to its owning
`Tree`, set once at root-creation time (or when a node is first attached under a tree, if node
creation and tree assignment are ever separate operations - TBD at implementation time, but the
common case is a `Tree` constructs and owns its root `Node` directly).

```java
public class Tree implements AutoCloseable {
    private final Node root;
    private final Map<Class<?>, TreeComponent> treeComponents = new HashMap<>();
    private final Map<Class<?>, Map<Object, ComponentTreeTraversalView<?>>> traversalViews = new HashMap<>();

    public Tree() {
        this.root = new Node(this, null);
    }

    public Node root() { return root; }

    public <C extends TreeComponent> C getOrRegisterTreeComponent(C component) { ... }

    public <C extends Component> ComponentTreeTraversalView<C> getOrCreateComponentTraversal(
            Class<C> componentType, TraversalOrder order, Object key) { ... }

    public void releaseComponentTraversal(Class<?> componentType, Object key) { ... }

    @Override
    public void close() { ... } // cascades into root.close()
}
```

## Node

```java
public class Node implements AutoCloseable {
    private final Tree tree;
    private Node parent;
    private List<Node> children = List.of(); // zero-alloc until first child add
    private final Map<Class<?>, Component> components = new HashMap<>();
    private LifecycleState state = LifecycleState.UNCONSTRUCTED;

    Node(Tree tree, Node parent) { ... } // package-private; use Tree.root() or parent.createChild()

    public Tree tree() { return tree; }
    public Node parent() { return parent; }
    public List<Node> children() { return children; } // unmodifiable view

    public Node createChild() { ... } // convenience: new Node(tree, this), added to this.children

    public <C extends Component> C addComponent(C component) { ... }
    public void removeComponent(Class<? extends Component> type) { ... }
    public <C extends Component> C getComponent(Class<C> type) { ... } // null if absent

    public void setParent(Node newParent) { ... } // see "Reparenting" below
    public void detach() { setParent(null); }

    public void fireEvent(Event event) { ... } // see 03-events-and-input.md
    public <E extends Event> E fireEvent(EventType<E> type, Object... data) { ... }

    @Override
    public void close() { ... } // two-pass teardown, see "Component" below
}
```

- **Children list defaults to `List.of()`** (zero allocation) for the common leaf-node case,
  lazily replaced with a real mutable list (e.g. `ArrayList`) on first child add.
- There is deliberately no `Node.attach(...)` method - "attach" is `Component` vocabulary
  (attaching a component to a node), not `Node` vocabulary (a node doesn't attach to a parent,
  it *sets* its parent). Keep this naming distinction consistent everywhere.

### Reparenting (`setParent`)

```
setParent(newParent):
  1. onDetach(oldParent) fires on all components of this node (via Component.onDetach, see below).
  2. Structural move: remove this node from oldParent.children, add to newParent.children
     (or from/to Tree bookkeeping if oldParent/newParent is null - a null parent means "not
     currently part of the tree's active hierarchy," which is legal, e.g. before initial
     attach or after detach()).
  3. Component attach lifecycle (onInit/resolveDependencies/afterResolve) does NOT re-run for
     THIS node's own components - reparenting is not a close/reopen cycle for the moved node.
  4. Ancestor-scope DI re-assessment: walk this node's DESCENDANTS (not this node itself) and
     re-run resolveDependencies() for every component whose requires() declares a
     NEAREST_ANCESTOR dependency. See `02-component-di-and-claims.md`, "Reparent invalidation
     for ancestor-scope dependencies" for the full rule (also covers component add/remove).
```

## Component

```java
public interface Component {
    // --- Lifecycle: construction/init/DI (see state machine below) ---
    default void onInit(Node node) {}
    default void resolveDependencies(Node node) {}
    default void afterResolve(Node node) {}

    // --- Lifecycle: detach/close ---
    default void onDetach(Node oldParent) {}
    default void beforeClose(Node node) {}
    default void close(Node node) {}

    // --- Sibling notification (node-local, delta-carrying, NOT an event/pub-sub object) ---
    default void onSiblingComponentAdded(Component added, int index) {}
    default void onSiblingComponentRemoved(Component removed, int index) {}

    // --- Dependency declaration (see 02-component-di-and-claims.md) ---
    default List<Dependency<?>> requires() { return List.of(); }
}
```

`TreeComponent` (tree-scoped components, e.g. bulk renderers) mirrors this shape but is scoped
to `Tree` instead of `Node`:

```java
public interface TreeComponent {
    default void onInit(Tree tree) {}
    default void resolveDependencies(Tree tree) {}
    default void afterResolve(Tree tree) {}
    default void beforeClose(Tree tree) {}
    default void close(Tree tree) {}
    default List<Dependency<?>> requires() { return List.of(); } // resolved against OTHER tree components
}
```

### Lifecycle state machine

```
UNCONSTRUCTED
  -> construct Node (or Tree) + construct/addComponent for initial components
     (construction order = declaration order, i.e. the order addComponent was called)
CONSTRUCTED
  -> each component's onInit(node) called, in construction/declaration order.
     No sibling component lookups yet - siblings may not exist/be initialized.
INITIALIZED
  -> DI resolution pass runs once across all initial components:
     each component's resolveDependencies(node) called.
     Order: dependency-respecting (a component whose dependency is itself resolved via DI runs
     after that dependency is resolved), falling back to declaration order for components with
     no ordering constraint between them ("preserve declaration order in grey space").
  -> each component's afterResolve(node) called once its own resolveDependencies() completes and
     all of ITS dependencies have also completed afterResolve() (so a component can trust that
     anything it depends on is fully ready, not just attached).
READY
  -> steady state: input events, draw/update events, property changes flow.
  -> addComponent() after READY (late registration): the new component runs
     onInit(node) -> resolveDependencies(node) -> afterResolve(node) immediately (a real DI
     pass, scoped to just the new component - it may discover and bind to existing siblings).
     ADDITIONALLY, every OTHER existing component on the same node has its
     onSiblingComponentAdded(Component added, int index) invoked directly (hardcoded Component
     interface method, default no-op - NOT an event/pub-sub object). This is a delta-carrying
     notification, not a DI re-pass: implementations that care about sibling changes react
     directly to exactly what was added/at what index, without needing to diff old-vs-new state.
     Implementations that don't override it are simply unaffected. See
     `02-component-di-and-claims.md`, "Component add/remove notification" for the full
     rationale (a full automatic re-pass on every sibling was considered and REJECTED as the
     default because it loses the delta information a direct method call preserves, and forces
     unnecessary diffing work on components that don't need it).
  -> removeComponent() after READY: fires onDetach(node) on the removed component, THEN every
     OTHER component on the same node has its onSiblingComponentRemoved(Component removed, int
     index) invoked directly (same hardcoded-method shape as above, not an event).
  -> Both of the above ALSO trigger the ancestor-scope re-assessment walk over the node's
     descendants described in `02-component-di-and-claims.md` ("Reparent invalidation for
     ancestor-scope dependencies" - the same rule covers attach/detach and reparenting).
CLOSING (only via explicit close(), never implicit/GC-triggered - no Cleaner, see below)
  -> two-pass teardown, always in this order:
     PASS 1 (before-close, top-down: parent's components before children's):
       walk the node and all descendants root-to-leaf; call component.beforeClose(node) on every
       component of every node in that walk order. Pure notification - "stop referencing this
       subtree's resources, they are about to become invalid" - nothing is actually torn down
       yet. This lets e.g. a render component two levels up null out a cached handle to a
       descendant's GPU buffer BEFORE that buffer is freed.
     PASS 2 (actual close, bottom-up: children's components before parent's):
       walk children first, then parent; call component.close(node) on every component in that
       walk order; the node itself transitions to CLOSED once all its components are closed.
CLOSED
```

Node-level close mirrors this exact two-pass shape (before-close pass over the whole subtree,
then bottom-up close pass) - not just component-level. Both levels need it for the same reason:
resource references must be released before the things they reference become invalid.

## No Cleaner / no GC-based finalization

Explicit decision: the base module does NOT use `java.lang.ref.Cleaner` or finalizers anywhere.
Rationale (from discussion): `Cleaner` is appropriate for large, rare, GC-root-detached native
handles (one `VulkanContext`, one `VkDevice`) where per-instance `Cleanable` registration
overhead is negligible relative to the object's lifetime/size. A UI tree may have thousands of
nodes/components; per-node cleaner registration is real, avoidable overhead, and this is too
generic a base library to bias toward any particular resource-safety strategy. `close()` is
explicit and deterministic. If a specific component implementation wants a dev-mode leak-detection
safety net, that is an opt-in decorator around that specific component, not a base-library feature.

## Detach vs Close (component level)

Two distinct notifications, not to be conflated:

- `onDetach(Node oldParent)` (fired on all of a node's components when that node is reparented;
  ALSO fired on a single component when it is individually removed via `removeComponent`) -
  resources stay alive. A component hearing this should stop actively using/relying on the thing
  it was attached to, but must not release resources it owns itself, since it may be re-attached
  elsewhere.
- `beforeClose(Node)` / `close(Node)` (two-pass, see above) - terminal, resource-releasing, only
  ever triggered by an explicit `close()` call somewhere up the chain.

Node reparenting (`setParent`) is DELIBERATELY not a close/reopen cycle - it is cheap, does not
tear down components, and only triggers the descendant ancestor-scope re-assessment walk (see 02).

## Removed-from-hierarchy vs destroyed (recap)

- Reparenting (including "removed from the renderable tree but resources kept, e.g. about to be
  moved elsewhere") = `setParent(null)` or `setParent(newParent)`. No teardown.
- Actual destruction = explicit `close()`. Two-pass teardown as above.

This distinction exists because nodes in this system are not assumed to be UI elements - they
could represent hardware sensors, 3D scene nodes, or anything else where "temporarily out of the
active tree" and "gone forever" are meaningfully different operations.
