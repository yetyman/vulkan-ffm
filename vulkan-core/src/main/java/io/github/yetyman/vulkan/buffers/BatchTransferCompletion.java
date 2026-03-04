package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkFence;
import io.github.yetyman.vulkan.VkFenceOps;
import io.github.yetyman.vulkan.VkResult;

import java.lang.foreign.Arena;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared completion for a transfer batch. Resolved when the fence is submitted.
 */
class BatchTransferCompletion {
    private final VkFence fence;
    private final Arena batchArena;
    private final List<AutoCloseable> ownedObjects;
    private final AtomicInteger refCount;
    private final AtomicBoolean resourcesFreed = new AtomicBoolean(false);
    private volatile boolean submitted = false;

    BatchTransferCompletion(VkFence fence, Arena batchArena, List<AutoCloseable> ownedObjects) {
        this.fence = fence;
        this.batchArena = batchArena;
        this.ownedObjects = ownedObjects;
        this.refCount = new AtomicInteger(0);
    }

    void resolve() { this.submitted = true; }

    boolean isSubmitted() { return submitted; }

    void await() {
        if (!submitted) return;
        try (Arena tmp = Arena.ofConfined()) {
            VkFenceOps.wait(fence.device(), fence, Long.MAX_VALUE, tmp).check();
        }
    }

    boolean isComplete() {
        if (!submitted) return false;
        return VkFenceOps.getStatus(fence.device(), fence) == VkResult.VK_SUCCESS;
    }

    void retain() { refCount.incrementAndGet(); }

    void release() {
        if (refCount.decrementAndGet() == 0) freeResources();
    }

    void forceClose() {
        freeResources();
    }

    private void freeResources() {
        if (!resourcesFreed.compareAndSet(false, true)) return;
        for (AutoCloseable obj : ownedObjects) {
            try { obj.close(); } catch (Exception ignored) {}
        }
        batchArena.close();
    }
}