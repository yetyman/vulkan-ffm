package io.github.yetyman.vulkan.foundation.ecs;

import java.util.List;

/**
 * Base interface for all components attached to nodes in the ECS tree.
 *
 * Components are the primary building blocks of behavior. A node's capabilities are
 * determined entirely by its attached components - there is no inheritance hierarchy
 * of node types.
 *
 * Lifecycle (see plans/ecs-node-tree/01-lifecycle-and-tree.md):
 *   1. Construction (user creates instance)
 *   2. onInit(node) - called after addComponent, before DI resolution
 *   3. resolveDependencies(node) - called in topological order respecting requires()
 *   4. afterResolve(node) - called after all dependencies are fully resolved
 *   5. [steady state - READY]
 *   6. beforeClose(node) - notification pass, top-down (parent before children)
 *   7. close(node) - actual teardown, bottom-up (children before parent)
 *
 * Detach (reparent or removeComponent) fires onDetach but is NOT a close cycle.
 */
public interface Component {

    // --- Lifecycle: construction/init/DI ---

    /**
     * Called once after this component is added to a node, before DI resolution.
     * Sibling components may not exist yet at this point.
     */
    default void onInit(Node node) {}

    /**
     * Called during the DI resolution pass. Look up and cache dependencies here.
     * Order: dependency-respecting topological order across the node's components.
     */
    default void resolveDependencies(Node node) {}

    /**
     * Called after resolveDependencies completes for this component AND all of its
     * dependencies have also completed afterResolve. Safe to use fully-initialized
     * dependency references here.
     */
    default void afterResolve(Node node) {}

    // --- Lifecycle: detach/close ---

    /**
     * Called when this component's node is reparented, or when this component is
     * individually removed. Resources stay alive - the component may be re-attached.
     */
    default void onDetach(Node oldParent) {}

    /**
     * Two-pass teardown pass 1 (top-down): notification that this subtree is about to
     * be torn down. Stop referencing resources that will become invalid.
     */
    default void beforeClose(Node node) {}

    /**
     * Two-pass teardown pass 2 (bottom-up): actual resource release.
     * Called after all descendants have already closed.
     */
    default void close(Node node) {}

    // --- Sibling notification (node-local, delta-carrying, NOT an event) ---

    /**
     * Called on all existing sibling components when a new component is added to the
     * same node (after initial construction, i.e., during READY state).
     */
    default void onSiblingComponentAdded(Component added, int index) {}

    /**
     * Called on all remaining sibling components when a component is removed from the
     * same node (after initial construction, i.e., during READY state).
     */
    default void onSiblingComponentRemoved(Component removed, int index) {}

    // --- Dependency declaration ---

    /**
     * Declares this component's dependencies. Called once during DI resolution to
     * determine resolution order and validate the dependency graph.
     *
     * This is a METHOD (not static/annotation-based) so a component's dependency
     * declarations can depend on its own instance configuration.
     */
    default List<Dependency<?>> requires() { return List.of(); }

    // --- Event handling ---

    /**
     * Called during event dispatch (capture and bubble phases) when an event passes
     * through this component's node.
     *
     * @param event the event being dispatched
     */
    default void handleEvent(Event event) {}
}
