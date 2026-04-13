package io.github.yetyman.vulkan.loop;

/**
 * Controls when and how often a loop body executes.
 * Each implementation is a self-contained tight loop with no internal branching.
 * <p>
 * Use the static factories to select a strategy:
 * <pre>{@code
 * loopThread.driver(LoopDriver.fixedRate(60));
 * loopThread.driver(LoopDriver.uncapped());
 * }</pre>
 */
public interface LoopDriver {

    /**
     * Runs the loop, calling {@code work} according to this driver's policy.
     * Returns when {@code running} becomes false.
     */
    void run(Runnable work, BooleanSupplier running);

    /**
     * Runs work as fast as possible with no rate limiting.
     */
    static LoopDriver uncapped() {
        return (work, running) -> {
            while (running.getAsBoolean()) work.run();
        };
    }

    /**
     * Runs work at a fixed rate, sleeping to maintain the target interval.
     * Does not attempt to catch up on missed frames.
     */
    static LoopDriver fixedRate(int targetHz) {
        long intervalNanos = 1_000_000_000L / targetHz;
        return (work, running) -> {
            long next = System.nanoTime();
            while (running.getAsBoolean()) {
                work.run();
                next += intervalNanos;
                long now = System.nanoTime();
                if (next > now) {
                    try {
                        Thread.sleep((next - now) / 1_000_000, (int) ((next - now) % 1_000_000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        };
    }

    /**
     * Runs work at a fixed rate, accumulating and replaying missed ticks to catch up.
     * Suitable for fixed-timestep physics or simulation.
     */
    static LoopDriver fixedRateCatchUp(int targetHz) {
        long intervalNanos = 1_000_000_000L / targetHz;
        return (work, running) -> {
            long last = System.nanoTime();
            long accumulator = 0;
            while (running.getAsBoolean()) {
                long now = System.nanoTime();
                accumulator += now - last;
                last = now;
                while (accumulator >= intervalNanos) {
                    work.run();
                    accumulator -= intervalNanos;
                }
                long remaining = intervalNanos - accumulator;
                if (remaining > 1_000_000) {
                    try {
                        Thread.sleep(remaining / 1_000_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        };
    }

    /**
     * Runs work once per vsync signal. Intended for use with a windowing system
     * that provides a vsync callback — the supplied {@code vsyncSignal} is called
     * by the windowing system and unblocks the loop.
     */
    static LoopDriver vsync(VsyncSource vsyncSource) {
        return (work, running) -> {
            while (running.getAsBoolean()) {
                vsyncSource.waitForVsync();
                work.run();
            }
        };
    }

    /**
     * Source of vsync signals, typically provided by a windowing system.
     */
    @FunctionalInterface
    interface VsyncSource {
        void waitForVsync();
    }

    /**
     * Supplies a boolean value, used to check the running state without boxing.
     */
    @FunctionalInterface
    interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
