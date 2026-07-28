package io.github.yetyman.vulkan.foundation.ecs;

/**
 * Handler for a specific event type dispatched through the ECS node tree.
 *
 * @param <E> the event type this handler processes
 */
@FunctionalInterface
public interface EventHandler<E extends Event> {

    /**
     * Handles an event.
     *
     * @param event the event to handle
     */
    void handle(E event);
}
