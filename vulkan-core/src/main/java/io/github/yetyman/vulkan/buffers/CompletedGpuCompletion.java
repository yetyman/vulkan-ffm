package io.github.yetyman.vulkan.buffers;

import java.util.concurrent.CompletableFuture;

/**
 * Shared no-op {@link GpuCompletion} for work that required no GPU submission at all
 * (for example a write straight into persistently-mapped coherent memory).
 */
final class CompletedGpuCompletion implements GpuCompletion {
    static final CompletedGpuCompletion INSTANCE = new CompletedGpuCompletion();

    private CompletedGpuCompletion() {
    }

    @Override
    public void await() {
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void onComplete(Runnable callback) {
        callback.run();
    }

    @Override
    public CompletableFuture<Void> toFuture() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
    }
}
