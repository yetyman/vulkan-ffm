package io.github.yetyman.vulkan.loop;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A managed thread that drives a work function according to a {@link LoopDriver} and
 * captures timing via a {@link TimingStrategy}.
 *
 * <pre>{@code
 * LoopThread loop = LoopThread.builder()
 *     .driver(LoopDriver.fixedRate(60))
 *     .timing(TimingStrategy.budgeted(Map.of("physics", 0.4, "render", 0.5)))
 *     .work(timing -> {
 *         timing.beginSection("physics");
 *         // ...
 *         timing.endSection("physics");
 *     })
 *     .build();
 *
 * loop.start();
 * // ...
 * loop.stop();
 * }</pre>
 */
public class LoopThread implements AutoCloseable {

    private LoopDriver driver;
    private final TimingStrategy timing;
    private final Consumer<TimingStrategy> work;
    private final String name;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    private LoopThread(LoopDriver driver, TimingStrategy timing, Consumer<TimingStrategy> work, String name) {
        this.driver = driver;
        this.timing = timing;
        this.work = work;
        this.name = name;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts the loop on a new thread. No-op if already running.
     */
    public synchronized void start() {
        if (running.get()) return;
        running.set(true);
        thread = new Thread(() -> driver.run(() -> {
            timing.beforeWork();
            work.accept(timing);
            timing.afterWork();
        }, running::get), name);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Signals the loop to stop and waits for the thread to finish.
     */
    public synchronized void stop() {
        running.set(false);
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    /**
     * Signals the loop to stop without blocking. Safe to call from within the work function.
     */
    public void signal() {
        running.set(false);
    }

    /**
     * Runs the loop on the calling thread, blocking until stopped.
     */
    public void runOnCurrentThread() {
        running.set(true);
        driver.run(() -> {
            timing.beforeWork();
            work.accept(timing);
            timing.afterWork();
        }, running::get);
    }

    public boolean isRunning() {
        return running.get();
    }

    public TimingStrategy timing() {
        return timing;
    }

    /**
     * Replaces the active driver. The current driver will finish its current iteration,
     * then the new driver takes over on the next call to its run() method.
     * Requires a stop/start cycle since the driver owns the loop structure.
     */
    public synchronized void driver(LoopDriver newDriver) {
        if (running.get()) stop();
        this.driver = newDriver;
        if (thread != null) start();
    }

    @Override
    public void close() {
        stop();
    }

    public static class Builder {
        private LoopDriver driver = LoopDriver.uncapped();
        private TimingStrategy timing = TimingStrategy.none();
        private Consumer<TimingStrategy> work = t -> {
        };
        private String name = "LoopThread";

        private Builder() {
        }

        /**
         * Sets the loop driver controlling rate and timing of work execution.
         */
        public Builder driver(LoopDriver driver) {
            this.driver = driver;
            return this;
        }

        /**
         * Sets the timing strategy for profiling and budget tracking.
         */
        public Builder timing(TimingStrategy timing) {
            this.timing = timing;
            return this;
        }

        /**
         * Sets the work function. Receives the active {@link TimingStrategy} for section instrumentation.
         */
        public Builder work(Consumer<TimingStrategy> work) {
            this.work = work;
            return this;
        }

        /**
         * Sets the thread name.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public LoopThread build() {
            return new LoopThread(driver, timing, work, name);
        }
    }
}
