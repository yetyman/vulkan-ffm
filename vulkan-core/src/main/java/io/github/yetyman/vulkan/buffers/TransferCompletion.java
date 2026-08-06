package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link GpuCompletion} backed by a {@link TransferBatch} submission.
 *
 * <p>One instance is a caller-facing view onto a shared {@link BatchTransferCompletion}; many
 * views may reference the same batch, and the batch's owned resources (staging buffers, arena)
 * are released once every view has been closed.
 *
 * <p>Consumers should generally type against {@link GpuCompletion} rather than this class, so
 * that the same code works for work submitted by an external scheduler.
 */
public class TransferCompletion implements GpuCompletion {

    private final BatchTransferCompletion batch;
    private final VkDevice device;
    private final VkQueue queue;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    TransferCompletion(BatchTransferCompletion batch, VkDevice device, VkQueue queue) {
        this.batch = batch;
        this.device = device;
        this.queue = queue;
    }

    @Override
    public void await() {
        if (batch == null) return;
        if (!batch.isSubmitted()) {
            // Auto-flush defensively if not yet submitted
            throw new IllegalStateException("Batch not submitted - call flush() first or use synchronous write()");
        }
        batch.await();
    }

    @Override
    public boolean isComplete() {
        return batch == null || batch.isComplete();
    }

    @Override
    public void onComplete(Runnable callback) {
        if (batch == null) {
            callback.run();
            return;
        }
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
        if (batch == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(this::await);
    }

    /**
     * Submits the per-thread batch this completion belongs to, so that {@link #await()} can
     * make progress.
     */
    @Override
    public void flush() {
        if (batch == null || device == null || queue == null) return;
        TransferBatchManager.flush(device, queue);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (batch != null) batch.release();
    }
}
