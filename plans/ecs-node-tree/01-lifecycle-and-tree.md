# Lifecycle and Tree Structure

## Tree

`Tree` owns the root `Node`, tree-scoped component registration, and traversal view
management. Every `Node` holds a back-reference to its owning `Tree`.

```java
public class Tree implements AutoCloseable {
    private final Node root;
    private final Map<Class<? extends TreeComponent>, TreeComponent> treeComponents;
    private final Map<String, TraversalView<?>> traversalViews;
    private final IdentityHashMap<Component, Map<Class<?>, ClaimStyle>> claimRegistry;
    private final TraversalView<Component> allNodesView; // built-in, always maintained

    public Tree();
    public Node root();
    public TraversalView<Component> allNodes();

    public <C extends TreeComponent> C getOrRegisterTreeComponent(C component);
    public <C extends TreeComponent> C getTreeComponent(Class<C> type);

    public <C extends Component> TraversalView<C> getOrCreateTraversalView(
            String key, Class<C> componentType, TraversalOrder order);
    public TraversalView<Component> getOrCreateTraversalView(String key, TraversalOrder order);
    public void releaseTraversalView(String key);

    public void initialize(); // initializes all CONSTRUCTED nodes in the tree
    @Override public void close();
}
```

## Node

```java
public class Node implements AutoCloseable {
    private final Tree tree;
    private Node parent;
    private List<Node> children = List.of(); // zero-alloc until first child add
    private List<Node> childrenView;         // cached unmodifiable wrapper
    private final LinkedHashMap<Class<?>, Component> components;
    private LifecycleState state;

    // Event handler registration (lazily allocated)
    private Map<EventType<?>, List<EventHandler<?>>> typedHandlers;
    private List<EventHandler<Event>> receiveAllHandlers;

    Node(Tree tree, Node parent); // package-private

    public Tree tree();
    public Node parent();
    public List<Node> children(); // cached unmodifiable view
    public LifecycleState state();

    public Node createChild();
    public Node createChild(int index);

    public <C extends Component> C addComponent(C component);
    public void removeComponent(Class<? extends Component> type);
    public <C extends Component> C getComponent(Class<C> type);  // exact type match
    public <C extends Component> C findComponent(Class<C> type); // + assignability check
    public boolean hasComponent(Class<? extends Component> type);

    public void setParent(Node newParent);
    public void detach();

    public void fireEvent(Event event);
    public <E extends Event> void addEventHandler(EventType<E> type, EventHandler<E> handler);
    public void addReceiveAllHandler(EventHandler<Event> handler);

    public void traverseDepthFirst(Consumer<Node> visitor);
    public void traverseDepthFirstPostOrder(Consumer<Node> visitor);
    public void traverseBreadthFirst(Consumer<Node> visitor);
    public boolean isAncestorOf(Node other);
    public List<Node> pathFromRoot();
    public int depth();

    @Override public void close();
}
```

- **Children list defaults to `List.of()`** (zero allocation) for the common leaf-node case,
  lazily replaced with `ArrayList` on first child add.
- **`children()` returns a cached unmodifiable wrapper**, invalidated only on structural mutation.
- **`createChild()` notifies the tree** to insert the new node into all unfiltered traversal
  views at the correct DFS pre-order position.

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

## Component

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

## TreeComponent

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

## Lifecycle state machine

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

## Two-pass close

Pass 1: Walk subtree top-down, call `component.beforeClose(node)` on every component.
"Stop referencing resources that are about to become invalid."

Pass 2: Walk subtree bottom-up, call `component.close(node)` on every component.
Notify traversal views of removal. Clear claims. Clear component map.

## No Cleaner / no GC-based finalization

The base module does NOT use `java.lang.ref.Cleaner` or finalizers. `close()` is explicit
and deterministic.

## Detach vs Close

- `onDetach(Node oldParent)` — resources stay alive. Component may be re-attached elsewhere.
- `beforeClose(Node)` / `close(Node)` — terminal, resource-releasing, only via explicit `close()`.

Reparenting (`setParent`) is NOT a close/reopen cycle. It fires `onDetach`, moves the node,
and triggers ancestor-scope DI re-assessment on descendants.
