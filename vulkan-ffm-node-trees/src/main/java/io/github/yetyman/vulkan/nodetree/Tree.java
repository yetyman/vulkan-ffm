package io.github.yetyman.vulkan.nodetree;

import java.util.*;

/**
 * Root container for the node tree.
 *
 * Owns the root Node, tree-scoped component registration, and traversal view management.
 *
 * Traversal views are keyed by string name. Any code that needs an ordered iteration of
 * nodes registers a view (optionally filtered by component type). The tree maintains a
 * built-in "all nodes" view that everything (including input dispatch) can use.
 *
 * Thread safety: assumed single-logical-thread (or externally synchronized).
 */
public class Tree implements AutoCloseable {

    private final Node root;
    private final Map<Class<? extends TreeComponent>, TreeComponent> treeComponents = new LinkedHashMap<>();
    private final Map<String, TraversalView<?>> traversalViews = new HashMap<>();
    private final IdentityHashMap<Component, Map<Class<?>, ClaimStyle>> claimRegistry = new IdentityHashMap<>();

    /** The built-in all-nodes view. Always maintained, never released. */
    private final TraversalView<Component> allNodesView;

    public Tree() {
        this.root = new Node(this, null);
        this.allNodesView = TraversalView.unfiltered(TraversalOrder.DEPTH_FIRST_PRE_ORDER);
        // Root starts in the all-nodes view
        allNodesView.addEntry(root, null);
    }

    /** @return the root node of this tree. */
    public Node root() { return root; }

    /**
     * @return the built-in all-nodes traversal view (DFS pre-order, unfiltered).
     * Use this for input dispatch, tree-wide iteration, etc.
     * Zero-allocation iteration: just walk the linked list.
     */
    public TraversalView<Component> allNodes() { return allNodesView; }

    /** Package-private: the per-tree claim registry for DI claim tracking. */
    IdentityHashMap<Component, Map<Class<?>, ClaimStyle>> claimRegistry() { return claimRegistry; }

    // --- Tree-scoped component management ---

    @SuppressWarnings("unchecked")
    public <C extends TreeComponent> C getOrRegisterTreeComponent(C component) {
        Class<? extends TreeComponent> type = component.getClass();
        TreeComponent existing = treeComponents.get(type);
        if (existing != null) {
            return (C) existing;
        }
        treeComponents.put(type, component);

        component.onInit(this);
        component.resolveDependencies(this);
        component.afterResolve(this);

        return component;
    }

    @SuppressWarnings("unchecked")
    public <C extends TreeComponent> C getTreeComponent(Class<C> type) {
        return (C) treeComponents.get(type);
    }

    // --- Traversal view management ---

    /**
     * Gets or creates a traversal view by name, filtered by component type.
     * Returns the SAME instance on repeat calls with the same key.
     *
     * @param key unique string key for this view
     * @param componentType the component type to filter by
     * @param order the traversal ordering
     * @param <C> the component type
     * @return the traversal view
     */
    @SuppressWarnings("unchecked")
    public <C extends Component> TraversalView<C> getOrCreateTraversalView(
            String key, Class<C> componentType, TraversalOrder order) {
        TraversalView<?> existing = traversalViews.get(key);
        if (existing != null) {
            return (TraversalView<C>) existing;
        }

        TraversalView<C> view = new TraversalView<>(componentType, order);
        traversalViews.put(key, view);

        // Populate with existing matching nodes
        root.traverseDepthFirst(node -> {
            C component = node.findComponent(componentType);
            if (component != null) {
                view.addEntry(node, component);
            }
        });

        return view;
    }

    /**
     * Gets or creates an unfiltered traversal view (all nodes) by name.
     *
     * @param key unique string key for this view
     * @param order the traversal ordering
     * @return the traversal view containing all nodes
     */
    @SuppressWarnings("unchecked")
    public TraversalView<Component> getOrCreateTraversalView(String key, TraversalOrder order) {
        TraversalView<?> existing = traversalViews.get(key);
        if (existing != null) {
            return (TraversalView<Component>) existing;
        }

        TraversalView<Component> view = TraversalView.unfiltered(order);
        traversalViews.put(key, view);

        // Populate with all existing nodes
        root.traverseDepthFirst(node -> view.addEntry(node, null));

        return view;
    }

    /**
     * Backward-compatible: get or create a component-typed view with auto-generated key.
     */
    public <C extends Component> TraversalView<C> getOrCreateComponentTraversal(
            Class<C> componentType, TraversalOrder order, String key) {
        String effectiveKey = (key != null) ? key : ("__component__" + componentType.getName());
        return getOrCreateTraversalView(effectiveKey, componentType, order);
    }

    /**
     * Backward-compatible: get or create with default key.
     */
    public <C extends Component> TraversalView<C> getOrCreateComponentTraversal(
            Class<C> componentType, TraversalOrder order) {
        return getOrCreateComponentTraversal(componentType, order, null);
    }

    /**
     * Releases a traversal view by key.
     */
    public void releaseTraversalView(String key) {
        traversalViews.remove(key);
    }

    // --- Node building utilities ---

    public void initialize() {
        initializeSubtree(root);
    }

    private void initializeSubtree(Node node) {
        if (node.state() == LifecycleState.CONSTRUCTED) {
            node.initialize();
        }
        for (Node child : node.children()) {
            initializeSubtree(child);
        }
    }

    // --- Close ---

    @Override
    public void close() {
        List<TreeComponent> treeComponentList = new ArrayList<>(treeComponents.values());
        for (int i = treeComponentList.size() - 1; i >= 0; i--) {
            treeComponentList.get(i).beforeClose(this);
        }
        for (int i = treeComponentList.size() - 1; i >= 0; i--) {
            treeComponentList.get(i).close(this);
        }
        treeComponents.clear();

        root.close();
        traversalViews.clear();
    }

    // --- Internal notifications from Node operations ---

    /**
     * Called when a node is created and added to the tree.
     * Inserts it into the all-nodes view at the correct DFS pre-order position
     * (after the parent's last existing descendant in the list).
     */
    void notifyNodeAdded(Node node) {
        Node parent = node.parent();
        // Find the insertion point: after parent's last descendant already in the view.
        // Walk forward from parent's entry in the all-nodes view until we hit a node
        // that is NOT a descendant of parent.
        Node insertAfterNode = findLastDescendantInView(allNodesView, parent);
        allNodesView.insertAfter(node, null, insertAfterNode);

        for (TraversalView<?> view : traversalViews.values()) {
            if (view.isUnfiltered() && view.contains(parent)) {
                Node refNode = findLastDescendantInView(view, parent);
                view.insertAfter(node, null, refNode);
            }
        }
    }

    /**
     * Finds the last descendant of 'ancestor' that is currently in the given view.
     * Walks forward from ancestor's entry until we find a non-descendant.
     */
    private Node findLastDescendantInView(TraversalView<?> view, Node ancestor) {
        var entry = view.getEntry(ancestor);
        if (entry == null) return ancestor;

        var current = entry;
        while (current.next() != null && ancestor.isAncestorOf(current.next().node)) {
            current = current.next();
        }
        return current.node;
    }

    /**
     * Called when a node is removed from the tree (close).
     */
    void notifyNodeRemoved(Node node) {
        allNodesView.removeEntry(node, null);
        for (TraversalView<?> view : traversalViews.values()) {
            if (view.contains(node)) {
                view.removeEntry(node, null);
            }
        }
    }

    /**
     * Called when a component is added to a node. Updates filtered traversal views.
     */
    @SuppressWarnings("unchecked")
    void notifyComponentAdded(Node node, Component component) {
        for (TraversalView<?> view : traversalViews.values()) {
            if (view.componentType() != null && view.componentType().isInstance(component)) {
                ((TraversalView<Component>) view).addEntry(node, component);
            }
        }
    }

    /**
     * Called when a component is removed from a node. Updates filtered traversal views.
     */
    @SuppressWarnings("unchecked")
    void notifyComponentRemoved(Node node, Component component) {
        for (TraversalView<?> view : traversalViews.values()) {
            if (view.componentType() != null && view.componentType().isInstance(component)) {
                ((TraversalView<Component>) view).removeEntry(node, component);
            }
        }
    }

    /**
     * Called when a node is reparented. Marks it dirty in all views that contain it.
     */
    void notifyNodeMoved(Node node) {
        allNodesView.markDirty(node);
        for (TraversalView<?> view : traversalViews.values()) {
            view.markDirty(node);
        }
    }

    @Override
    public String toString() {
        return "Tree{nodes=" + allNodesView.liveCount() + ", treeComponents=" + treeComponents.size() +
                ", traversalViews=" + traversalViews.size() + "}";
    }
}
