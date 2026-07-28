package io.github.yetyman.vulkan.foundation.ecs;

import java.util.*;

/**
 * Root container for the ECS node tree.
 *
 * Owns the root Node, tree-scoped component registration, and per-component-type
 * traversal view management.
 *
 * Thread safety: assumed single-logical-thread (or externally synchronized).
 * See plans/ecs-node-tree/04-array-backing-and-render-integration.md.
 */
public class Tree implements AutoCloseable {

    private final Node root;
    private final Map<Class<?>, TreeComponent> treeComponents = new LinkedHashMap<>();
    private final Map<Class<?>, Map<Object, ComponentTreeTraversalView<?>>> traversalViews = new HashMap<>();

    /** Sentinel key for the common case of one traversal view per component type. */
    private static final Object DEFAULT_KEY = new Object();

    public Tree() {
        this.root = new Node(this, null);
    }

    /** @return the root node of this tree. */
    public Node root() { return root; }

    // --- Tree-scoped component management ---

    /**
     * Registers a tree-scoped component (singleton on the tree).
     * If a component of this type is already registered, returns the existing one.
     *
     * @param component the tree component to register
     * @param <C> the tree component type
     * @return the registered component (may be the existing one if already registered)
     */
    @SuppressWarnings("unchecked")
    public <C extends TreeComponent> C getOrRegisterTreeComponent(C component) {
        Class<?> type = component.getClass();
        TreeComponent existing = treeComponents.get(type);
        if (existing != null) {
            return (C) existing;
        }
        treeComponents.put(type, component);

        // Run lifecycle
        component.onInit(this);
        component.resolveDependencies(this);
        component.afterResolve(this);

        return component;
    }

    /**
     * Gets a registered tree component by type.
     *
     * @param type the tree component class
     * @param <C> the tree component type
     * @return the component, or null if not registered
     */
    @SuppressWarnings("unchecked")
    public <C extends TreeComponent> C getTreeComponent(Class<C> type) {
        return (C) treeComponents.get(type);
    }

    // --- Traversal view management ---

    /**
     * Gets or creates a traversal view for the given component type.
     * Returns the SAME instance on repeat calls with the same (componentType, key) pair.
     *
     * @param componentType the component type to track
     * @param order the traversal ordering
     * @param key a key to distinguish multiple views of the same type (use null for default)
     * @param <C> the component type
     * @return the traversal view
     */
    @SuppressWarnings("unchecked")
    public <C extends Component> ComponentTreeTraversalView<C> getOrCreateComponentTraversal(
            Class<C> componentType, TraversalOrder order, Object key) {
        Object effectiveKey = (key != null) ? key : DEFAULT_KEY;
        Map<Object, ComponentTreeTraversalView<?>> viewsByKey =
                traversalViews.computeIfAbsent(componentType, k -> new HashMap<>());

        ComponentTreeTraversalView<?> existing = viewsByKey.get(effectiveKey);
        if (existing != null) {
            return (ComponentTreeTraversalView<C>) existing;
        }

        ComponentTreeTraversalView<C> view = new ComponentTreeTraversalView<>(componentType, order);
        viewsByKey.put(effectiveKey, view);

        // Populate with existing components in the tree
        root.traverseDepthFirst(node -> {
            C component = node.findComponent(componentType);
            if (component != null) {
                view.addEntry(node, component);
            }
        });

        return view;
    }

    /**
     * Gets or creates a traversal view with the default key.
     */
    public <C extends Component> ComponentTreeTraversalView<C> getOrCreateComponentTraversal(
            Class<C> componentType, TraversalOrder order) {
        return getOrCreateComponentTraversal(componentType, order, null);
    }

    /**
     * Releases a traversal view. The view is no longer maintained.
     *
     * @param componentType the component type
     * @param key the key (use null for default)
     */
    public void releaseComponentTraversal(Class<?> componentType, Object key) {
        Object effectiveKey = (key != null) ? key : DEFAULT_KEY;
        Map<Object, ComponentTreeTraversalView<?>> viewsByKey = traversalViews.get(componentType);
        if (viewsByKey != null) {
            viewsByKey.remove(effectiveKey);
            if (viewsByKey.isEmpty()) {
                traversalViews.remove(componentType);
            }
        }
    }

    // --- Node building utilities ---

    /**
     * Initializes the root node and all of its initial children/components.
     * Call this after assembling the initial tree structure.
     */
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

    /**
     * Closes the entire tree: tree components, then root (cascading to all descendants).
     */
    @Override
    public void close() {
        // Close tree components in reverse order
        List<TreeComponent> treeComponentList = new ArrayList<>(treeComponents.values());
        for (int i = treeComponentList.size() - 1; i >= 0; i--) {
            TreeComponent tc = treeComponentList.get(i);
            tc.beforeClose(this);
        }
        for (int i = treeComponentList.size() - 1; i >= 0; i--) {
            TreeComponent tc = treeComponentList.get(i);
            tc.close(this);
        }
        treeComponents.clear();

        // Close the node tree
        root.close();

        // Clear traversal views
        traversalViews.clear();
    }

    // --- Internal notifications from Node operations ---

    /**
     * Called by Node when a component is added. Updates all registered traversal views
     * that track this component's type.
     */
    void notifyComponentAdded(Node node, Component component) {
        notifyTraversalViews(node, component, true);
    }

    /**
     * Called by Node when a component is removed. Updates all registered traversal views
     * that track this component's type.
     */
    void notifyComponentRemoved(Node node, Component component) {
        notifyTraversalViews(node, component, false);
    }

    /**
     * Called by Node when a node is moved (reparented). Notifies traversal views to
     * reorder their entries if needed.
     */
    void notifyNodeMoved(Node node) {
        // For each component on the moved node and its descendants, notify views
        node.traverseDepthFirst(n -> {
            for (Component c : n.components()) {
                Map<Object, ComponentTreeTraversalView<?>> viewsByKey = traversalViews.get(c.getClass());
                if (viewsByKey != null) {
                    for (ComponentTreeTraversalView<?> view : viewsByKey.values()) {
                        view.markDirty(n);
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void notifyTraversalViews(Node node, Component component, boolean added) {
        Map<Object, ComponentTreeTraversalView<?>> viewsByKey = traversalViews.get(component.getClass());
        if (viewsByKey == null) return;

        for (ComponentTreeTraversalView<?> rawView : viewsByKey.values()) {
            ComponentTreeTraversalView<Component> view = (ComponentTreeTraversalView<Component>) rawView;
            if (added) {
                view.addEntry(node, component);
            } else {
                view.removeEntry(node, component);
            }
        }
    }

    @Override
    public String toString() {
        int nodeCount = countNodes(root);
        return "Tree{nodes=" + nodeCount + ", treeComponents=" + treeComponents.size() +
                ", traversalViews=" + traversalViews.size() + "}";
    }

    private int countNodes(Node node) {
        int count = 1;
        for (Node child : node.children()) {
            count += countNodes(child);
        }
        return count;
    }
}
