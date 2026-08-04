# Trees and Nodes

## Tree

`Tree` is the root container for the entire hierarchy. It owns:
- The root `Node`
- The built-in all-nodes traversal view
- The tree-scoped component registry
- The string-keyed traversal view registry
- The per-tree claim registry for DI

```java
public class Tree implements AutoCloseable {
    private final Node root;
    private final Map<Class<? extends TreeComponent>, TreeComponent> treeComponents;
    private final Map<String, TraversalView<?>> traversalViews;
    private final IdentityHashMap<Component, Map<Class<?>, ClaimStyle>> claimRegistry;
    private final TraversalView<Component> allNodesView;

    public Tree();
    public Node root();
    public TraversalView<Component> allNodes();

    public <C extends TreeComponent> C getOrRegisterTreeComponent(C component);
    public <C extends TreeComponent> C getTreeComponent(Class<C> type);

    public <C extends Component> TraversalView<C> getOrCreateTraversalView(
            String key, Class<C> componentType, TraversalOrder order);
    public TraversalView<Component> getOrCreateTraversalView(String key, TraversalOrder order);
    public void releaseTraversalView(String key);

    // Backward-compatible convenience
    public <C extends Component> TraversalView<C> getOrCreateComponentTraversal(
            Class<C> componentType, TraversalOrder order);

    public void initialize();
    @Override public void close();
}
```

`tree.initialize()` transitions all CONSTRUCTED nodes through the full lifecycle (onInit, resolveDependencies, afterResolve) bringing them to READY state.

---

## Node

Every `Node` holds a back-reference to its owning `Tree`. Nodes form a classic parent/child tree.

```java
public class Node implements AutoCloseable {
    private final Tree tree;
    private Node parent;
    private List<Node> children = List.of();     // zero-alloc until first child
    private List<Node> childrenView;              // cached unmodifiable wrapper
    private final LinkedHashMap<Class<?>, Component> components;
    private LifecycleState state;

    // Event handler registration (lazily allocated)
    private Map<EventType<?>, List<EventHandler<?>>> typedHandlers;
    private List<EventHandler<Event>> receiveAllHandlers;

    Node(Tree tree, Node parent); // package-private

    // --- Structure ---
    public Tree tree();
    public Node parent();
    public List<Node> children();  // cached unmodifiable view
    public LifecycleState state();

    public Node createChild();
    public Node createChild(int index);

    // --- Components ---
    public <C extends Component> C addComponent(C component);
    public void removeComponent(Class<? extends Component> type);
    public <C extends Component> C getComponent(Class<C> type);   // exact type match
    public <C extends Component> C findComponent(Class<C> type);  // + assignability check
    public boolean hasComponent(Class<? extends Component> type);

    // --- Reparenting ---
    public void setParent(Node newParent);
    public void detach();

    // --- Events ---
    public void fireEvent(Event event);
    public <E extends Event> void addEventHandler(EventType<E> type, EventHandler<E> handler);
    public <E extends Event> void removeEventHandler(EventType<E> type, EventHandler<E> handler);
    public void addReceiveAllHandler(EventHandler<Event> handler);
    public void removeReceiveAllHandler(EventHandler<Event> handler);

    // --- Traversal utilities ---
    public void traverseDepthFirst(Consumer<Node> visitor);
    public void traverseDepthFirstPostOrder(Consumer<Node> visitor);
    public void traverseBreadthFirst(Consumer<Node> visitor);
    public boolean isAncestorOf(Node other);
    public List<Node> pathFromRoot();
    public int depth();

    @Override public void close();
}
```

### Key design decisions

- **Children list defaults to `List.of()`** — zero allocation for the common leaf-node case. Lazily replaced with `ArrayList` on first child add.
- **`children()` returns a cached unmodifiable wrapper**, invalidated only on structural mutation.
- **`createChild()` notifies the tree** to insert the new node into all unfiltered traversal views at the correct DFS pre-order position.

### Reparenting (`setParent`)

```
setParent(newParent):
  1. Cycle detection (cannot reparent under self or own descendant)
  2. No-op if newParent == current parent
  3. onDetach(oldParent) fires on all components of this node
  4. Structural move: remove from old parent, add to new parent
  5. Ancestor-scope DI re-assessment walks descendants (not this node itself)
  6. Notify traversal views of structural change (mark dirty)
```

### Detach vs Close

- `onDetach(Node oldParent)` — resources stay alive. Component may be re-attached elsewhere.
- `beforeClose(Node)` / `close(Node)` — terminal, resource-releasing, only via explicit `close()`.

Reparenting is NOT a close/reopen cycle. It fires onDetach, moves the node, and triggers ancestor-scope DI re-assessment on descendants.

---

## Component

The interface for attachable behaviors with a full lifecycle:

```java
public interface Component {
    default void onInit(Node node) {}
    default void resolveDependencies(Node node) {}
    default void afterResolve(Node node) {}
    default void onDetach(Node oldParent) {}
    default void beforeClose(Node node) {}
    default void close(Node node) {}

    default void onSiblingComponentAdded(Component added, int index) {}
    default void onSiblingComponentRemoved(Component removed, int index) {}

    default List<Dependency<?>> requires() { return List.of(); }
}
```

All methods have default no-op implementations. Components only override what they need.

### Sibling notifications

When a component is added/removed from a READY node:
- Every OTHER component on the same node has `onSiblingComponentAdded`/`Removed` invoked.
- Direct method call, not an event object.
- A component wanting full re-resolution can call its own `resolveDependencies(node)` from inside the callback.

---

## TreeComponent

Tree-scoped singletons. Symmetric lifecycle to node-scoped components:

```java
public interface TreeComponent {
    default void onInit(Tree tree) {}
    default void resolveDependencies(Tree tree) {}
    default void afterResolve(Tree tree) {}
    default void beforeClose(Tree tree) {}
    default void close(Tree tree) {}
    default List<Dependency<?>> requires() { return List.of(); }
}
```

Registered via:
```java
tree.getOrRegisterTreeComponent(new MyBulkRendererComponent());
```

A tree-scoped component can register traversal views in its own lifecycle and use them for bulk processing (see [layers.md](traversal-views.md) for the bulk render pattern).

---

## Lifecycle State Machine

```
UNCONSTRUCTED
  -> construct Node + addComponent for initial components
CONSTRUCTED
  -> tree.initialize() triggers:
     each component's onInit(node) in declaration order
INITIALIZED
  -> DI resolution pass:
     resolveDependencies(node) in topological order (dependency-respecting)
  -> afterResolve(node) in same order
READY
  -> steady state
  -> addComponent() after READY: new component runs onInit -> resolveDependencies ->
     afterResolve immediately. Siblings get onSiblingComponentAdded. Ancestor-scope
     re-assessment walks descendants.
  -> removeComponent(): onDetach on removed, siblings get onSiblingComponentRemoved,
     ancestor-scope re-assessment walks descendants.
CLOSING (via explicit close())
  -> Pass 1 (beforeClose, top-down): parent's components before children's
  -> Pass 2 (close, bottom-up): children's components before parent's
CLOSED
```

### Two-pass close

**Pass 1 (top-down):** Walk subtree top-down, call `component.beforeClose(node)` on every component. Purpose: "Stop referencing resources that are about to become invalid."

**Pass 2 (bottom-up):** Walk subtree bottom-up, call `component.close(node)` on every component. Notify traversal views of removal. Clear claims. Clear component map. Purpose: "Release resources, leaves before branches."

### Late additions (adding components to a READY node)

When a component is added to a node that is already in the READY state:
1. `onInit(node)` runs immediately on the new component
2. `resolveDependencies(node)` runs immediately
3. `afterResolve(node)` runs immediately
4. All sibling components receive `onSiblingComponentAdded`
5. Ancestor-scope re-assessment walks descendants if the new component type is depended upon

### No finalizers

The base module does NOT use `java.lang.ref.Cleaner` or GC-based finalization. `close()` is explicit and deterministic.
