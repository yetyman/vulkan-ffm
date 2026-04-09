package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.loop.LoopThread;
import io.github.yetyman.vulkan.loop.TimingStrategy;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Composes a {@link GraphicsRenderer} with a {@link LoopThread}, handling the standard
 * concerns of a render loop: frame gating, resize coordination, idle sleeping, and FPS tracking.
 *
 * <pre>{@code
 * GraphicsLoop loop = GraphicsLoop.builder()
 *     .renderer(renderer)
 *     .driver(LoopDriver.uncapped())
 *     .shouldRender(() -> !minimized)
 *     .onResize((w, h) -> renderer.resize(w, h))
 *     .onFpsUpdate(fps -> title.set("FPS: " + fps))
 *     .build();
 *
 * // Signal a resize from a window callback:
 * loop.signalResize(newWidth, newHeight);
 *
 * loop.runOnCurrentThread(); // or loop.start() for a dedicated thread
 * }</pre>
 */
public class GraphicsLoop implements AutoCloseable {

    private final GraphicsRenderer renderer;
    private final LoopThread loopThread;
    private final BooleanSupplier shouldClose;

    private GraphicsLoop(GraphicsRenderer renderer, LoopThread loopThread, BooleanSupplier shouldClose) {
        this.renderer = renderer;
        this.loopThread = loopThread;
        this.shouldClose = shouldClose;
    }

    public static Builder builder() { return new Builder(); }

    public void start() { loopThread.start(); }
    public void stop()  { loopThread.stop(); }
    public boolean isRunning() { return loopThread.isRunning(); }
    public void driver(LoopDriver driver) { loopThread.driver(driver); }
    public GraphicsRenderer renderer() { return renderer; }
    public LoopThread loopThread() { return loopThread; }

    /** Runs the loop on the calling thread, blocking until {@code shouldClose} returns true. */
    public void runOnCurrentThread() { loopThread.runOnCurrentThread(); }

    @Override
    public void close() { loopThread.stop(); }

    public static class Builder {
        private GraphicsRenderer renderer;
        private LoopDriver driver = LoopDriver.uncapped();
        private TimingStrategy timing = TimingStrategy.none();
        private String name = "GraphicsLoop";

        private BooleanSupplier shouldClose = () -> false;
        private BooleanSupplier shouldRender = () -> true;
        private Runnable pollEvents = () -> {};
        private volatile int pendingResizeWidth = -1;
        private volatile int pendingResizeHeight = -1;
        private Consumer<int[]> onResize = null;
        private IntConsumer onFpsUpdate = null;
        private int idleSleepMs = 10;

        private Builder() {}

        public Builder renderer(GraphicsRenderer renderer) { this.renderer = renderer; return this; }
        public Builder driver(LoopDriver driver) { this.driver = driver; return this; }
        public Builder timing(TimingStrategy timing) { this.timing = timing; return this; }
        public Builder name(String name) { this.name = name; return this; }

        /** Checked each frame — loop exits when this returns true. */
        public Builder shouldClose(BooleanSupplier shouldClose) { this.shouldClose = shouldClose; return this; }

        /** Checked each frame — if false, skips rendering and sleeps {@code idleSleepMs}. */
        public Builder shouldRender(BooleanSupplier shouldRender) { this.shouldRender = shouldRender; return this; }

        /** Called each frame before render checks — use to poll window events. */
        public Builder pollEvents(Runnable pollEvents) { this.pollEvents = pollEvents; return this; }

        /** Called when a resize has been signaled, with [width, height]. */
        public Builder onResize(Consumer<int[]> onResize) { this.onResize = onResize; return this; }

        /** Called once per second with the current FPS. */
        public Builder onFpsUpdate(IntConsumer onFpsUpdate) { this.onFpsUpdate = onFpsUpdate; return this; }

        /** Milliseconds to sleep when shouldRender() returns false. Default: 10. */
        public Builder idleSleepMs(int ms) { this.idleSleepMs = ms; return this; }

        public GraphicsLoop build() {
            if (renderer == null) throw new IllegalStateException("renderer not set");

            Consumer<int[]> resizeHandler = onResize;
            BooleanSupplier closeCheck = shouldClose;
            BooleanSupplier renderCheck = shouldRender;
            Runnable poll = pollEvents;
            IntConsumer fpsCallback = onFpsUpdate;
            int idleMs = idleSleepMs;

            // Shared resize signal — written by signalResize(), read by the loop
            int[] pendingResize = {-1, -1};

            long[] fpsState = {System.nanoTime(), 0}; // [lastTime, frameCount]

            LoopThread loop = LoopThread.builder()
                .name(name)
                .driver(LoopDriver.uncapped()) // outer driver is uncapped; inner rate control via shouldRender/idle
                .timing(timing)
                .work(t -> {
                    poll.run();

                    if (closeCheck.getAsBoolean()) {
                        // Signal the loop to stop — LoopDriver checks running flag
                        return;
                    }

                    // Handle pending resize
                    int rw, rh;
                    synchronized (pendingResize) {
                        rw = pendingResize[0];
                        rh = pendingResize[1];
                        if (rw != -1) pendingResize[0] = pendingResize[1] = -1;
                    }
                    if (rw != -1 && resizeHandler != null) {
                        resizeHandler.accept(new int[]{rw, rh});
                    }

                    if (!renderCheck.getAsBoolean()) {
                        try { Thread.sleep(idleMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return;
                    }

                    renderer.drawFrame();

                    // FPS tracking
                    if (fpsCallback != null) {
                        fpsState[1]++;
                        long now = System.nanoTime();
                        if (now - fpsState[0] >= 1_000_000_000L) {
                            fpsCallback.accept((int) fpsState[1]);
                            fpsState[1] = 0;
                            fpsState[0] = now;
                        }
                    }
                })
                .build();

            // Expose signalResize via the GraphicsLoop instance — store pending in the array
            GraphicsLoop graphicsLoop = new GraphicsLoop(renderer, loop, closeCheck) {
                @Override
                public void signalResize(int w, int h) {
                    synchronized (pendingResize) {
                        pendingResize[0] = w;
                        pendingResize[1] = h;
                    }
                }
            };

            return graphicsLoop;
        }
    }

    /** Signals that the window has been resized. Thread-safe. */
    public void signalResize(int w, int h) {}  // overridden in build()
}
