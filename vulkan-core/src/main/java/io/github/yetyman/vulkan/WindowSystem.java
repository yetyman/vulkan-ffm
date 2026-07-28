package io.github.yetyman.vulkan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public interface WindowSystem {

    @FunctionalInterface
    interface ResizeCallback {
        void onResize(MemorySegment window, int width, int height);
    }

    // Window lifecycle
    MemorySegment createWindow(int width, int height, String title);

    void destroyWindow(MemorySegment window);

    void terminate();

    // Vulkan integration
    String[] getRequiredVulkanExtensions(Arena arena);

    MemorySegment createSurface(MemorySegment instance, MemorySegment window, Arena arena);

    // Event handling
    boolean shouldClose(MemorySegment window);

    void pollEvents();

    /**
     * Waits for events with a timeout. Blocks until an event arrives or the timeout
     * (in seconds) expires, whichever comes first. On input arrival, wakes instantly.
     * Prefer this over pollEvents() + sleep for power-efficient event loops with
     * continuous animation (use the timeout as the animation floor rate).
     */
    void waitEventsTimeout(double timeoutSeconds);

    void setResizeCallback(MemorySegment window, ResizeCallback callback, Arena arena);

    // Window properties
    void getFramebufferSize(MemorySegment window, MemorySegment widthPtr, MemorySegment heightPtr);

    void setResizable(boolean resizable);

    // --- Mobile lifecycle (no-op on desktop, critical on mobile) ---

    /** Called when the application is paused (mobile: backgrounded). */
    default void onPause() {}

    /** Called when the application resumes from pause. */
    default void onResume() {}

    /** Called when a new rendering surface becomes available. */
    default void onSurfaceCreated() {}

    /**
     * Called when the rendering surface is lost (must stop rendering immediately).
     * On mobile this happens when the app is backgrounded or the surface is reclaimed.
     */
    default void onSurfaceLost() {}

    // --- Platform-driven frame timing ---

    /**
     * @return true if this platform drives frame timing externally
     * (mobile: Choreographer/CADisplayLink, web: requestAnimationFrame).
     * When true, the application must not spin its own render loop but instead
     * register via {@link #requestFrameCallback(Runnable)}.
     */
    default boolean platformDrivesFrameTiming() { return false; }

    /**
     * Registers a callback to be invoked once per platform vsync/frame signal.
     * Only meaningful when {@link #platformDrivesFrameTiming()} returns true.
     * The platform re-registers the callback automatically each frame.
     *
     * @param onFrame the work to execute each frame (poll input, update, render)
     * @throws UnsupportedOperationException if the platform does not drive frame timing
     */
    default void requestFrameCallback(Runnable onFrame) {
        throw new UnsupportedOperationException("Platform does not drive frame timing");
    }
}
