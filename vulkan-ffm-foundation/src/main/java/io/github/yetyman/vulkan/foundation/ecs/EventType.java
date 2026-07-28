package io.github.yetyman.vulkan.foundation.ecs;

/**
 * Type token and factory for event creation.
 * Parallel to the pattern used in io.github.yetyman.vulkan.foundation.ui.input.
 *
 * @param <E> the concrete event type
 */
@FunctionalInterface
public interface EventType<E extends Event> {

    /**
     * Creates a new event instance from the provided data.
     *
     * @param data event-type-specific construction arguments
     * @return a new event instance
     */
    E create(Object... data);
}
