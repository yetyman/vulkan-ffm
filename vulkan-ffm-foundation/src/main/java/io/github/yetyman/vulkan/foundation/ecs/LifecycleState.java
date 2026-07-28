package io.github.yetyman.vulkan.foundation.ecs;

/**
 * Lifecycle states for nodes and components within the ECS tree.
 *
 * State machine:
 *   UNCONSTRUCTED -> CONSTRUCTED -> INITIALIZED -> READY -> CLOSING -> CLOSED
 *
 * See plans/ecs-node-tree/01-lifecycle-and-tree.md for full state machine documentation.
 */
public enum LifecycleState {
    /** Initial state before any lifecycle methods have been called. */
    UNCONSTRUCTED,

    /** Component has been constructed and added to its node. */
    CONSTRUCTED,

    /** onInit(node) has been called. */
    INITIALIZED,

    /** resolveDependencies and afterResolve have completed; steady state. */
    READY,

    /** Two-pass teardown in progress (beforeClose pass then close pass). */
    CLOSING,

    /** Fully torn down. */
    CLOSED
}
