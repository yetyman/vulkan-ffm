package io.github.yetyman.vulkan.nodetree;

import java.util.List;

/**
 * A component scoped to the Tree itself rather than to a specific Node.
 * Used for tree-wide singletons like bulk renderers, descriptor pool managers, etc.
 *
 * Mirrors the Component lifecycle shape but receives Tree instead of Node.
 */
public interface TreeComponent {

    /** Called once after registration on the tree. */
    default void onInit(Tree tree) {}

    /** Called to resolve dependencies on other tree components. */
    default void resolveDependencies(Tree tree) {}

    /** Called after all dependencies have completed their own afterResolve. */
    default void afterResolve(Tree tree) {}

    /** Notification that the tree is about to close. Stop referencing resources. */
    default void beforeClose(Tree tree) {}

    /** Actual teardown - release resources. */
    default void close(Tree tree) {}

    /**
     * Declares dependencies on other tree-scoped components.
     * Resolved against OTHER tree components via getOrRegisterTreeComponent.
     */
    default List<Dependency<?>> requires() { return List.of(); }
}
