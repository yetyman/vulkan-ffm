package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;

/**
 * Lifecycle interface for Vulkan subsystems that can be started, stopped, and resized
 * independently of the main application loop.
 *
 * <p>Each lifecycle event is split into a before and after phase so that implementations
 * can participate in ordered teardown and rebuild sequences. For example, a compute loop
 * must stop before {@code vkDeviceWaitIdle} is called during resize, and restart after
 * the swapchain is rebuilt.
 *
 * <p>Typical implementation order for resize:
 * <ol>
 *   <li>{@link #beforeStop()} on all registered dependents</li>
 *   <li>{@link #stop()} on all registered dependents</li>
 *   <li>{@link #afterStop()} on all registered dependents</li>
 *   <li>{@code vkDeviceWaitIdle}</li>
 *   <li>{@link #beforeResize(int, int)} on all registered dependents</li>
 *   <li>Swapchain rebuild</li>
 *   <li>{@link #afterResize(int, int)} on all registered dependents</li>
 *   <li>{@link #beforeStart()} on all registered dependents</li>
 *   <li>{@link #start()} on all registered dependents</li>
 *   <li>{@link #afterStart()} on all registered dependents</li>
 * </ol>
 */
public interface ILifecycle extends AutoCloseable {

    /**
     * Allocates Vulkan resources. Called once before {@link #start()}.
     */
    default void create(Arena arena) {
    }

    /**
     * Called immediately before {@link #stop()}.
     */
    default void beforeStop() {
    }

    /**
     * Signals this component to stop. Non-blocking — returns before the component
     * has necessarily finished. Follow with {@link #awaitStopped()} to block.
     */
    default void stop() {
    }

    /**
     * Blocks until this component has fully stopped and all in-flight GPU work
     * submitted by it has completed.
     */
    default void awaitStopped() {
    }

    /**
     * Called immediately after {@link #awaitStopped()} confirms the component is idle.
     */
    default void afterStop() {
    }

    /**
     * Called before the swapchain is rebuilt during a resize. Component is already stopped.
     */
    default void beforeResize(int width, int height) {
    }

    /**
     * Called after the swapchain is rebuilt during a resize. Component has not yet restarted.
     */
    default void afterResize(int width, int height) {
    }

    /**
     * Called immediately before {@link #start()}.
     */
    default void beforeStart() {
    }

    /**
     * Starts or restarts this component.
     */
    default void start() {
    }

    /**
     * Called immediately after {@link #start()}.
     */
    default void afterStart() {
    }

    /**
     * Destroys Vulkan resources. Always called after {@link #awaitStopped()}.
     */
    @Override
    void close();
}
