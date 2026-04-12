package io.github.yetyman.vulkan;

/**
 * Observer interface for objects that want to react to lifecycle events emitted by an owner
 * such as {@link io.github.yetyman.vulkan.highlevel.VulkanApplication} or
 * {@link io.github.yetyman.vulkan.highlevel.GraphicsLoop}.
 *
 * <p>Contrast with {@link ILifecycle}, which is the <em>component</em> interface — what a managed
 * thing looks like. {@code ILifecycleListener} is the <em>observer</em> interface — what a thing
 * that wants to be notified of another object's lifecycle events looks like. A {@code ComputeLoop}
 * for example implements both: it has its own lifecycle and it reacts to the application's events.
 *
 * <p>All methods are default no-ops. Implement only the events you care about.
 *
 * <p>Event ordering during resize:
 * <ol>
 *   <li>{@link #onBeforeStop()} — GPU work should be halted here, before deviceWaitIdle</li>
 *   <li>{@code vkDeviceWaitIdle}</li>
 *   <li>{@link #onBeforeResize(int, int)} — swapchain is about to be rebuilt</li>
 *   <li>Swapchain rebuild</li>
 *   <li>{@link #onAfterResize(int, int)} — swapchain is rebuilt, resources can be recreated</li>
 *   <li>{@link #onAfterStart()} — safe to resume GPU work</li>
 * </ol>
 *
 * <p>Event ordering during shutdown:
 * <ol>
 *   <li>{@link #onBeforeStop()} — GPU work should be halted here, before deviceWaitIdle</li>
 *   <li>{@code vkDeviceWaitIdle}</li>
 *   <li>{@link #onBeforeShutdown()} — Vulkan resources are about to be destroyed</li>
 * </ol>
 */
public interface ILifecycleListener {

    /** Called before GPU work must stop — before {@code vkDeviceWaitIdle} during resize or shutdown. */
    default void onBeforeStop() {}

    /** Called after GPU work has stopped and {@code vkDeviceWaitIdle} has completed. */
    default void onAfterStop() {}

    /** Called before the swapchain is rebuilt. New dimensions are provided. */
    default void onBeforeResize(int width, int height) {}

    /** Called after the swapchain is rebuilt with new dimensions. */
    default void onAfterResize(int width, int height) {}

    /** Called when it is safe to resume GPU work after a resize. */
    default void onAfterStart() {}

    /** Called before Vulkan resources are destroyed during shutdown. GPU is already idle. */
    default void onBeforeShutdown() {}
}
