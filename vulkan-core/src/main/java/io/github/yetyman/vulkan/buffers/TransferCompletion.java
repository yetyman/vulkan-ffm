package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransferCompletion implements AutoCloseable {
    private static final TransferCompletion COMPLETED = new TransferCompletion(null);

    private final BatchTransferCompletion batch;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    TransferCompletion(BatchTransferCompletion batch) {
        this.batch = batch;
    }

    public static TransferCompletion completed() { return COMPLETED; }

    public void await() {
        if (batch == null) return;
        if (!batch.isSubmitted()) {
            // Auto-flush defensively if not yet submitted
            throw new IllegalStateException("Batch not submitted - call flush() first or use synchronous write()");
        }
        batch.await();
    }

    public boolean isComplete() {
        return batch == null || batch.isComplete();
    }

    public void onComplete(Runnable callback) {
        if (batch == null) { callback.run(); return; }
        Thread.ofVirtual().start(() -> { try { await(); callback.run(); } finally { close(); } });
    }

    public CompletableFuture<Void> toFuture() {
        if (batch == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(this::await);
    }

    public void flush(VkDevice dev, VkQueue queue) {
        TransferBatchManager.flush(dev, queue);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (batch != null) batch.release();
    }
}
