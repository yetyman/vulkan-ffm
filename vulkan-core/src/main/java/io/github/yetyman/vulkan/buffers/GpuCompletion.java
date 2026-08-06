package io.github.yetyman.vulkan.buffers;

import java.util.concurrent.CompletableFuture;

/**
 * A handle to asynchronous GPU work that will eventually finish.
 *
 * <p>This is deliberately independent of how the work was scheduled. {@link TransferCompletion}
 * is the {@link TransferBatch}-backed implementation, {@link TimelineCompletion} is backed by a
 * timeline semaphore counter, and an external scheduler (a render graph, a custom submit path)
 * can supply its own implementation without this package knowing about it.
 *
 * <p>Consumers that need to know "when is this data usable" should depend on this interface
 * rather than on any concrete completion type, so that the same consumer code works regardless
 * of who submitted the work.
 *
 * <p>Instances are {@link AutoCloseable}: closing releases the caller's claim on any resources
 * the completion keeps alive (staging buffers, arenas). Closing does not cancel or wait for the
 * underlying GPU work.
 */
public interface GpuCompletion extends AutoCloseable {

    /**
     * Blocks until the GPU work has finished.
     */
    void await();

    /**
     * @return true if the GPU work has finished, without blocking.
     */
    boolean isComplete();

    /**
     * Runs {@code callback} once the work has finished. The callback runs on a virtual thread,
     * not on the calling thread, and this completion is closed after the callback returns.
     */
    void onComplete(Runnable callback);

    /**
     * @return a future that completes when the GPU work has finished.
     */
    CompletableFuture<Void> toFuture();

    /**
     * Ensures the work backing this completion has actually been submitted to the GPU, so that
     * {@link #await()} can make progress.
     *
     * <p>Implementations whose work is submitted by an external scheduler (a render graph, a
     * custom submit path) have nothing to do here and leave this as a no-op. Batched
     * implementations submit their pending batch.
     */
    default void flush() {
    }

    @Override
    void close();

    /**
     * @return a completion representing work that has already finished. Safe to share.
     */
    static GpuCompletion completed() {
        return CompletedGpuCompletion.INSTANCE;
    }
}
