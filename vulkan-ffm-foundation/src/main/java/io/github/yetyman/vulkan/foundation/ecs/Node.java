package io.github.yetyman.vulkan.foundation.ecs;

import java.util.*;
import java.util.function.Consumer;

/**
 * A node in the ECS tree. Nodes have parent/child relationships and hold components.
 *
 * A node's capabilities are determined entirely by its attached components.
 * The node itself has no spatial, visual, or behavioral properties.
 *
 * Thread safety: assumed single-logical-thread (or externally synchronized).
 * See plans/ecs-node-tree/04-array-backing-and-render-integration.md.
 */
public class Node implements AutoCloseable {

    private final Tree tree;
    private Node parent;
    private List<Node> children = List.of(); // zero-alloc until first child add
    private final LinkedHashMap<Class<?>, Component> components = new LinkedHashMap<>();
    private LifecycleState state = LifecycleState.UNCONSTRUCTED;

    // Claim tracking: maps (claimed component instance) -> (requester type -> claim style)
    // Used by the DI resolution engine to enforce claim style rules.
    private static final Map<Component, Map<Class<?>, ClaimStyle>> claimRegistry = new IdentityHashMap<>();

    /**
     * Package-private constructor. Use Tree.root() or parent.createChild() to obtain nodes.
     */
    Node(Tree tree, Node parent) {
        this.tree = tree;
        this.parent = parent;
        this.state = LifecycleState.CONSTRUCTED;
    }

    // --- Accessors ---

    /** @return the tree this node belongs to. */
    public Tree tree() { return tree; }

    /** @return this node's parent, or null if this is the root. */
    public Node parent() { return parent; }

    /** @return unmodifiable view of this node's children. */
    public List<Node> children() {
        return children == List.<Node>of() ? children : Collections.unmodifiableList(children);
    }

    /** @return current lifecycle state. */
    public LifecycleState state() { return state; }

    /** @return the number of children. */
    public int childCount() { return children.size(); }

    /** @return true if this node has no children. */
    public boolean isLeaf() { return children.isEmpty(); }

    /** @return true if this is the root node (parent is null). */
    public boolean isRoot() { return parent == null; }

    // --- Child management ---

    /**
     * Creates a new child node under this node.
     * The child is immediately added to this node's children list.
     *
     * @return the newly created child node
     */
    public Node createChild() {
        Node child = new Node(tree, this);
        addChildInternal(child);
        return child;
    }

    /**
     * Creates a new child node at the specified index.
     *
     * @param index the index at which to insert the child
     * @return the newly created child node
     */
    public Node createChild(int index) {
        Node child = new Node(tree, this);
        addChildInternal(child, index);
        return child;
    }

    private void addChildInternal(Node child) {
        if (children == List.<Node>of()) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    private void addChildInternal(Node child, int index) {
        if (children == List.<Node>of()) {
            children = new ArrayList<>();
        }
        children.add(index, child);
    }

    private void removeChildInternal(Node child) {
        children.remove(child);
        if (children.isEmpty()) {
            children = List.of();
        }
    }

    // --- Component management ---

    /**
     * Adds a component to this node. If the node is in READY state (late registration),
     * the component immediately runs through its full lifecycle (onInit -> resolveDependencies
     * -> afterResolve) and siblings are notified.
     *
     * @param component the component to add
     * @param <C> the component type
     * @return the added component (for fluent chaining)
     * @throws IllegalStateException if a component of this type is already present
     */
    public <C extends Component> C addComponent(C component) {
        Class<?> type = component.getClass();
        if (components.containsKey(type)) {
            throw new IllegalStateException("Component of type " + type.getSimpleName() +
                    " already exists on this node");
        }
        components.put(type, component);
        int index = components.size() - 1;

        if (state == LifecycleState.READY) {
            // Late registration: run full lifecycle for the new component
            component.onInit(this);
            component.resolveDependencies(this);
            component.afterResolve(this);

            // Notify existing siblings
            for (Map.Entry<Class<?>, Component> entry : components.entrySet()) {
                Component sibling = entry.getValue();
                if (sibling != component) {
                    sibling.onSiblingComponentAdded(component, index);
                }
            }

            // Trigger ancestor-scope re-assessment for descendants
            reassessAncestorDependencies(type);

            // Notify traversal views
            tree.notifyComponentAdded(this, component);
        }

        return component;
    }

    /**
     * Removes a component by type from this node.
     *
     * @param type the component class to remove
     * @throws IllegalStateException if no component of this type exists
     */
    public void removeComponent(Class<? extends Component> type) {
        Component removed = components.get(type);
        if (removed == null) {
            throw new IllegalStateException("No component of type " + type.getSimpleName() +
                    " on this node");
        }

        int index = indexOf(removed);
        components.remove(type);

        if (state == LifecycleState.READY) {
            // Fire onDetach on the removed component
            removed.onDetach(this);

            // Notify remaining siblings
            for (Component sibling : components.values()) {
                sibling.onSiblingComponentRemoved(removed, index);
            }

            // Trigger ancestor-scope re-assessment for descendants
            reassessAncestorDependencies(type);

            // Notify traversal views
            tree.notifyComponentRemoved(this, removed);
        }

        // Clear claims held by/on this component
        claimRegistry.remove(removed);
    }

    /**
     * Gets a component by its exact type.
     *
     * @param type the component class
     * @param <C> the component type
     * @return the component instance, or null if absent
     */
    @SuppressWarnings("unchecked")
    public <C extends Component> C getComponent(Class<C> type) {
        return (C) components.get(type);
    }

    /**
     * Gets a component by type, searching supertypes and interfaces if exact match fails.
     *
     * @param type the component class or interface
     * @param <C> the component type
     * @return the first matching component instance, or null if absent
     */
    @SuppressWarnings("unchecked")
    public <C extends Component> C findComponent(Class<C> type) {
        // Try exact match first
        Component exact = components.get(type);
        if (exact != null) return (C) exact;

        // Fall back to assignability check
        for (Component c : components.values()) {
            if (type.isInstance(c)) return (C) c;
        }
        return null;
    }

    /** @return true if this node has a component of the given type. */
    public boolean hasComponent(Class<? extends Component> type) {
        return components.containsKey(type);
    }

    /** @return unmodifiable collection of all components on this node. */
    public Collection<Component> components() {
        return Collections.unmodifiableCollection(components.values());
    }

    /** @return the number of components on this node. */
    public int componentCount() { return components.size(); }

    // --- Reparenting ---

    /**
     * Moves this node to a new parent.
     *
     * Semantics (see plans/ecs-node-tree/01-lifecycle-and-tree.md):
     *   1. onDetach(oldParent) fires on all components of this node
     *   2. Structural move: remove from old parent, add to new parent
     *   3. Component lifecycles do NOT re-run for this node's own components
     *   4. Ancestor-scope DI re-assessment walks descendants
     *
     * @param newParent the new parent node, or null to detach from the tree
     */
    public void setParent(Node newParent) {
        if (newParent == this) {
            throw new IllegalArgumentException("A node cannot be its own parent");
        }
        if (newParent != null && isAncestorOf(newParent)) {
            throw new IllegalArgumentException("Cannot reparent a node under one of its own descendants");
        }

        Node oldParent = this.parent;
        if (oldParent == newParent) return; // no-op

        // 1. Fire onDetach on all components
        for (Component c : components.values()) {
            c.onDetach(oldParent);
        }

        // 2. Structural move
        if (oldParent != null) {
            oldParent.removeChildInternal(this);
        }
        this.parent = newParent;
        if (newParent != null) {
            newParent.addChildInternal(this);
        }

        // 3. Ancestor-scope DI re-assessment on descendants (not this node itself)
        reassessAllAncestorDependenciesOnDescendants();

        // 4. Notify traversal views of structural change
        tree.notifyNodeMoved(this);
    }

    /** Detaches this node from its parent (shorthand for setParent(null)). */
    public void detach() { setParent(null); }

    // --- Event dispatch ---

    /**
     * Fires an event through this node using capture/bubble dispatch.
     *
     * Capture: root -> this node (ancestors get first look)
     * Bubble: this node -> root (target and ancestors react)
     *
     * @param event the event to dispatch
     */
    public void fireEvent(Event event) {
        // Build path from root to this node
        List<Node> path = pathFromRoot();

        // CAPTURE phase: root to target
        event.setPhase(Event.Phase.CAPTURE);
        for (Node node : path) {
            if (event.isStopped()) break;
            node.dispatchToComponents(event);
        }

        // Transition to BUBBLE phase - reset stopped flag (follows existing precedent)
        event.resetForBubble();
        event.setPhase(Event.Phase.BUBBLE);

        // BUBBLE phase: target to root
        for (int i = path.size() - 1; i >= 0; i--) {
            if (event.isStopped()) break;
            path.get(i).dispatchToComponents(event);
        }
    }

    /**
     * Creates and fires an event using an EventType factory.
     *
     * @param type the event type / factory
     * @param data construction arguments for the event
     * @param <E> the concrete event type
     * @return the created and dispatched event
     */
    public <E extends Event> E fireEvent(EventType<E> type, Object... data) {
        E event = type.create(data);
        fireEvent(event);
        return event;
    }

    // --- Traversal utilities ---

    /**
     * Depth-first pre-order traversal of this subtree (this node first, then children).
     */
    public void traverseDepthFirst(Consumer<Node> visitor) {
        visitor.accept(this);
        for (Node child : children) {
            child.traverseDepthFirst(visitor);
        }
    }

    /**
     * Depth-first post-order traversal (children first, then this node).
     */
    public void traverseDepthFirstPostOrder(Consumer<Node> visitor) {
        for (Node child : children) {
            child.traverseDepthFirstPostOrder(visitor);
        }
        visitor.accept(this);
    }

    /**
     * Breadth-first traversal of this subtree.
     */
    public void traverseBreadthFirst(Consumer<Node> visitor) {
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(this);
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            visitor.accept(current);
            for (Node child : current.children) {
                queue.add(child);
            }
        }
    }

    /** @return true if this node is an ancestor of the given node. */
    public boolean isAncestorOf(Node other) {
        Node current = other.parent;
        while (current != null) {
            if (current == this) return true;
            current = current.parent;
        }
        return false;
    }

    /** @return the path from the tree root to this node (inclusive of both endpoints). */
    public List<Node> pathFromRoot() {
        List<Node> path = new ArrayList<>();
        Node current = this;
        while (current != null) {
            path.add(current);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    /** @return the depth of this node in the tree (root = 0). */
    public int depth() {
        int d = 0;
        Node current = this.parent;
        while (current != null) {
            d++;
            current = current.parent;
        }
        return d;
    }

    // --- Lifecycle: initialization ---

    /**
     * Runs the full initialization lifecycle for all components currently on this node.
     * Called by Tree after all initial components have been added.
     *
     * Order: onInit in declaration order, then resolveDependencies in topological order,
     * then afterResolve in dependency-respecting order.
     */
    void initialize() {
        if (state != LifecycleState.CONSTRUCTED) {
            throw new IllegalStateException("Node already initialized, state: " + state);
        }

        state = LifecycleState.INITIALIZED;

        // Phase 1: onInit in declaration order
        for (Component c : components.values()) {
            c.onInit(this);
        }

        // Phase 2: resolveDependencies in topological order
        List<Component> resolved = topologicalSort();
        for (Component c : resolved) {
            c.resolveDependencies(this);
        }

        // Phase 3: afterResolve in the same topological order
        for (Component c : resolved) {
            c.afterResolve(this);
        }

        state = LifecycleState.READY;

        // Register with traversal views
        for (Component c : components.values()) {
            tree.notifyComponentAdded(this, c);
        }
    }

    // --- Lifecycle: close ---

    /**
     * Two-pass teardown of this node and all descendants.
     *
     * Pass 1 (beforeClose, top-down): parent's components before children's.
     * Pass 2 (close, bottom-up): children's components before parent's.
     */
    @Override
    public void close() {
        if (state == LifecycleState.CLOSED || state == LifecycleState.CLOSING) return;
        state = LifecycleState.CLOSING;

        // Pass 1: beforeClose, top-down (this node, then children)
        beforeClosePass();

        // Pass 2: close, bottom-up (children first, then this node)
        closePass();

        state = LifecycleState.CLOSED;

        // Remove from parent's children list
        if (parent != null) {
            parent.removeChildInternal(this);
            parent = null;
        }
    }

    private void beforeClosePass() {
        // This node's components first
        for (Component c : components.values()) {
            c.beforeClose(this);
        }
        // Then children
        for (Node child : children) {
            child.beforeClosePass();
        }
    }

    private void closePass() {
        // Children first (bottom-up)
        // Copy list to avoid ConcurrentModificationException during close
        List<Node> childrenCopy = new ArrayList<>(children);
        for (Node child : childrenCopy) {
            child.closePass();
            child.state = LifecycleState.CLOSED;
        }
        children = List.of();

        // Then this node's components
        for (Component c : components.values()) {
            tree.notifyComponentRemoved(this, c);
            c.close(this);
        }

        // Clear claims
        for (Component c : components.values()) {
            claimRegistry.remove(c);
        }
        components.clear();
    }

    // --- DI resolution internals ---

    /**
     * Resolves a dependency for a requesting component.
     * Searches according to the dependency's scope and applies claim rules.
     *
     * @param dependency the dependency to resolve
     * @param requester the component requesting the dependency
     * @param <T> the dependency type
     * @return the resolved component, or a fallback-created one, or null
     */
    @SuppressWarnings("unchecked")
    <T extends Component> T resolveDependency(Dependency<T> dependency, Component requester) {
        T found = null;

        switch (dependency.scope()) {
            case SELF -> found = findComponent(dependency.type());
            case NEAREST_ANCESTOR -> {
                Node current = this.parent;
                while (current != null) {
                    found = current.findComponent(dependency.type());
                    if (found != null) break;
                    current = current.parent;
                }
            }
        }

        if (found == null) {
            // Try fallback
            found = dependency.fallback().createDefault(this);
            if (found != null) {
                // Add the fallback-created component to this node
                addComponent(found);
            }
            return found;
        }

        // Apply claim rules
        tryClaim(found, requester.getClass(), dependency.claim());
        return found;
    }

    /**
     * Attempts to record a claim on a component instance.
     * Throws if the claim conflicts with existing claims.
     */
    static void tryClaim(Component target, Class<?> requesterType, ClaimStyle style) {
        if (style == ClaimStyle.PERMISSIVE) {
            // Permissive never conflicts with anything
            Map<Class<?>, ClaimStyle> claims = claimRegistry.computeIfAbsent(target, k -> new HashMap<>());
            claims.put(requesterType, style);
            return;
        }

        Map<Class<?>, ClaimStyle> claims = claimRegistry.computeIfAbsent(target, k -> new HashMap<>());

        if (style == ClaimStyle.SELF_EXCLUSIVE) {
            // Fail if any prior claim exists with the SAME requester type
            if (claims.containsKey(requesterType)) {
                ClaimStyle existing = claims.get(requesterType);
                if (existing != ClaimStyle.PERMISSIVE) {
                    throw new IllegalStateException(
                            "SELF_EXCLUSIVE claim violation: " + requesterType.getSimpleName() +
                                    " already claims " + target.getClass().getSimpleName());
                }
            }
            claims.put(requesterType, style);
        } else if (style == ClaimStyle.EXCLUSIVE) {
            // Fail if ANY prior non-permissive claim exists
            for (Map.Entry<Class<?>, ClaimStyle> entry : claims.entrySet()) {
                if (entry.getValue() != ClaimStyle.PERMISSIVE) {
                    throw new IllegalStateException(
                            "EXCLUSIVE claim violation: " + target.getClass().getSimpleName() +
                                    " already claimed by " + entry.getKey().getSimpleName());
                }
            }
            if (!claims.isEmpty()) {
                // Check if any claim exists at all (exclusive blocks everything)
                for (ClaimStyle existingStyle : claims.values()) {
                    if (existingStyle == ClaimStyle.EXCLUSIVE) {
                        throw new IllegalStateException(
                                "EXCLUSIVE claim violation: " + target.getClass().getSimpleName() +
                                        " already exclusively claimed");
                    }
                }
            }
            claims.put(requesterType, style);
        }
    }

    /**
     * Topological sort of components based on their declared dependencies.
     * Falls back to declaration order for components with no ordering constraint.
     */
    private List<Component> topologicalSort() {
        List<Component> result = new ArrayList<>();
        Set<Component> visited = new HashSet<>();
        Set<Component> visiting = new HashSet<>(); // cycle detection

        for (Component c : components.values()) {
            if (!visited.contains(c)) {
                topologicalVisit(c, visited, visiting, result);
            }
        }
        return result;
    }

    private void topologicalVisit(Component c, Set<Component> visited, Set<Component> visiting,
                                  List<Component> result) {
        if (visited.contains(c)) return;
        if (visiting.contains(c)) {
            throw new IllegalStateException("Circular dependency detected involving " +
                    c.getClass().getSimpleName());
        }

        visiting.add(c);

        // Visit dependencies first
        for (Dependency<?> dep : c.requires()) {
            if (dep.scope() == LookupScope.SELF) {
                Component dependency = findComponent(dep.type());
                if (dependency != null && !visited.contains(dependency)) {
                    topologicalVisit(dependency, visited, visiting, result);
                }
            }
        }

        visiting.remove(c);
        visited.add(c);
        result.add(c);
    }

    /**
     * Re-assesses NEAREST_ANCESTOR dependencies for descendants when a component of the
     * given type is added to or removed from this node.
     */
    private void reassessAncestorDependencies(Class<?> changedType) {
        for (Node child : children) {
            child.reassessAncestorDependenciesRecursive(changedType);
        }
    }

    private void reassessAncestorDependenciesRecursive(Class<?> changedType) {
        for (Component c : components.values()) {
            for (Dependency<?> dep : c.requires()) {
                if (dep.scope() == LookupScope.NEAREST_ANCESTOR &&
                        dep.type().isAssignableFrom(changedType)) {
                    c.resolveDependencies(this);
                    break; // Only re-resolve once per component
                }
            }
        }
        // Continue into descendants
        for (Node child : children) {
            child.reassessAncestorDependenciesRecursive(changedType);
        }
    }

    /**
     * Re-assesses ALL NEAREST_ANCESTOR dependencies for descendants after reparenting.
     */
    private void reassessAllAncestorDependenciesOnDescendants() {
        for (Node child : children) {
            child.reassessAllAncestorDependenciesRecursive();
        }
    }

    private void reassessAllAncestorDependenciesRecursive() {
        for (Component c : components.values()) {
            for (Dependency<?> dep : c.requires()) {
                if (dep.scope() == LookupScope.NEAREST_ANCESTOR) {
                    c.resolveDependencies(this);
                    break;
                }
            }
        }
        for (Node child : children) {
            child.reassessAllAncestorDependenciesRecursive();
        }
    }

    // --- Internal helpers ---

    private void dispatchToComponents(Event event) {
        for (Component c : components.values()) {
            if (event.isStopped()) break;
            c.handleEvent(event);
        }
    }

    private int indexOf(Component component) {
        int i = 0;
        for (Component c : components.values()) {
            if (c == component) return i;
            i++;
        }
        return -1;
    }

    @Override
    public String toString() {
        return "Node{depth=" + depth() + ", components=" + components.size() +
                ", children=" + children.size() + ", state=" + state + "}";
    }
}
