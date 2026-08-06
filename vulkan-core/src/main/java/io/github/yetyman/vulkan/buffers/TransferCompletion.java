package io.github.yetyman.vulkan.buffers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link GpuCompletion} backed by a {@link TransferBatch} generation.
 *
 * <p>One instance is a caller-facing view onto a shared {@link BatchTransferCompletion}; many views
 * may reference the same generation, and that generation's owned resources (staging buffers, command
 * buffer arena) are released once every view has been closed.
 *
 * <p>The view holds its owning batch, so {@link #flush()} submits the generation this completion
 * actually belongs to. It previously looked the batch up from a thread-local registry, which meant
 * that flushing from any thread other than the one that recorded the transfer silently found no
 * batch and did nothing, leaving {@link #await()} to block on work that had never been submitted.
 *
 * <p>Consumers should generally type against {@link GpuCompletion} rather than this class, so that
 * the same code works for work submitted by an external scheduler.
 */
public class TransferCompletion implements GpuCompletion {

    private final BatchTransferCompletion batch;
    private final TransferBatch owner;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    TransferCompletion(BatchTransferCompletion batch, TransferBatch owner) {
        this.batch = batch;
        this.owner = owner;
    }

    /**
     * Submits the batch generation this completion belongs to, if it has not been submitted already.
     *
     * <p>Only the thread that recorded the transfer may do this, because a batch records into a
     * single command buffer. Calling this from another thread on an unsubmitted generation throws
     * rather than silently doing nothing.
     */
    @Override
    public void flush() {
        if (batch == null || owner == null) return;
        if (batch.isSubmitted()) return;
        owner.flush();
    }

    /**
     * Blocks until this generation's work has completed on the GPU, submitting it first if needed.
     */
    @Override
    public void await() {
        if (batch == null) return;
        if (!batch.isSubmitted()) flush();
        batch.await();
    }

    @Override
    public boolean isComplete() {
        return batch == null || batch.isComplete();
    }

    /**
     * Submits this generation on the calling thread, then waits on a virtual thread and runs
     * {@code callback}. Submission happens on the caller precisely because the caller is the batch's
     * owning thread and the virtual thread is not.
     */
    @Override
    public void onComplete(Runnable callback) {
        if (batch == null) {
            callback.run();
            return;
        }
        flush();
        Thread.ofVirtual().start(() -> {
            try {
                batch.await();
                callback.run();
            } finally {
                close();
            }
        });
    }

    @Override
    public CompletableFuture<Void> toFuture() {
        if (batch == null) return CompletableFuture.completedFuture(null);
        flush();
        return CompletableFuture.runAsync(batch::await);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (batch != null) batch.release();
    }
}
