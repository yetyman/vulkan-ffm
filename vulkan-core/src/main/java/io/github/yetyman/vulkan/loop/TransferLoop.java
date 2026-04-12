package io.github.yetyman.vulkan.loop;

import io.github.yetyman.vulkan.ILifecycle;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.TransferBatchManager;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background loop that periodically flushes pending {@link io.github.yetyman.vulkan.buffers.TransferBatch}
 * work for a given device and queue.
 *
 * <p>Async writes via {@link io.github.yetyman.vulkan.buffers.ManagedBuffer#writeAsync} accumulate
 * in a per-thread {@code TransferBatch}. Under normal load the batch is flushed immediately by the
 * calling thread when it reaches the auto-flush threshold. {@code TransferLoop} acts as a safety net:
 * it wakes up at a configurable interval and flushes any work that has been sitting in a batch
 * longer than the timeout, preventing stale transfers from never being submitted.
 *
 * <p>Implements {@link ILifecycle} for automatic stop/start on resize and shutdown.
 *
 * <pre>{@code
 * TransferLoop transferLoop = TransferLoop.builder()
 *     .device(device)
 *     .queue(transferQueue)
 *     .checkIntervalMs(1)   // check every 1ms
 *     .build();
 *
 * app.registerLifecycleDependency(transferLoop);
 * transferLoop.start();
 * }</pre>
 */
public class TransferLoop implements ILifecycle {

    private final VkDevice device;
    private final VkQueue queue;
    private final long checkIntervalMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    private TransferLoop(VkDevice device, VkQueue queue, long checkIntervalMs) {
        this.device = device;
        this.queue = queue;
        this.checkIntervalMs = checkIntervalMs;
    }

    @Override
    public synchronized void start() {
        if (running.get()) return;
        running.set(true);
        thread = Thread.ofPlatform().name("transfer-loop").daemon(true).start(() -> {
            while (running.get()) {
                TransferBatchManager.flush(device, queue);
                try { Thread.sleep(checkIntervalMs); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            // Final flush on exit
            TransferBatchManager.flush(device, queue);
        });
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        if (thread != null) thread.interrupt();
    }

    @Override
    public void awaitStopped() {
        Thread t = thread;
        if (t != null) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }
    }

    @Override
    public void close() {
        stop();
        awaitStopped();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private VkDevice device;
        private VkQueue queue;
        private long checkIntervalMs = 1;

        private Builder() {}

        /** Sets the logical device. */
        public Builder device(VkDevice device) { this.device = device; return this; }

        /** Sets the transfer queue to flush. */
        public Builder queue(VkQueue queue) { this.queue = queue; return this; }

        /**
         * Sets how often the loop checks for pending transfers, in milliseconds.
         * Lower values reduce maximum transfer latency at the cost of more CPU wakeups.
         * Default: 1ms.
         */
        public Builder checkIntervalMs(long ms) { this.checkIntervalMs = ms; return this; }

        public TransferLoop build() {
            if (device == null) throw new IllegalStateException("device not set");
            if (queue == null)  throw new IllegalStateException("queue not set");
            return new TransferLoop(device, queue, checkIntervalMs);
        }
    }
}
