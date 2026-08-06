package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkTimelineSemaphore;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link GpuCompletion} backed by a timeline semaphore reaching a target counter value.
 *
 * <p>This is the implementation an external scheduler uses. A caller that submits work itself
 * (a render graph transfer node, a custom submit path, a compute pass that produces data) signals
 * a timeline semaphore on completion and hands back a {@code TimelineCompletion} for the value it
 * will signal. Consumers await it exactly as they would await a batched transfer.
 *
 * <p>Unlike {@link TransferCompletion}, this owns nothing and keeps nothing alive; {@link #close()}
 * is a no-op unless an {@code onClose} action was supplied.
 */
public final class TimelineCompletion implements GpuCompletion {

    private final VkTimelineSemaphore semaphore;
    private final long targetValue;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * @param semaphore   the timeline semaphore that will be signalled
     * @param targetValue the counter value that indicates the work is finished
     */
    public TimelineCompletion(VkTimelineSemaphore semaphore, long targetValue) {
        this(semaphore, targetValue, null);
    }

    /**
     * @param onClose optional action run once when this completion is closed, for releasing
     *                resources the scheduler kept alive for the duration of the work
     */
    public TimelineCompletion(VkTimelineSemaphore semaphore, long targetValue, Runnable onClose) {
        if (semaphore == null) throw new IllegalArgumentException("semaphore required");
        this.semaphore = semaphore;
        this.targetValue = targetValue;
        this.onClose = onClose;
    }

    /**
     * @return the semaphore this completion observes
     */
    public VkTimelineSemaphore semaphore() {
        return semaphore;
    }

    /**
     * @return the counter value that indicates completion
     */
    public long targetValue() {
        return targetValue;
    }

    @Override
    public void await() {
        semaphore.await(targetValue);
    }

    @Override
    public boolean isComplete() {
        return semaphore.counterValue() >= targetValue;
    }

    @Override
    public void onComplete(Runnable callback) {
        Thread.ofVirtual().start(() -> {
            try {
                await();
                callback.run();
            } finally {
                close();
            }
        });
    }

    @Override
    public CompletableFuture<Void> toFuture() {
        return CompletableFuture.runAsync(this::await);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (onClose != null) onClose.run();
    }
}
