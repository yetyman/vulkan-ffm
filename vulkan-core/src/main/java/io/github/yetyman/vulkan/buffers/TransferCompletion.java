package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkFence;
import io.github.yetyman.vulkan.VkFenceOps;
import io.github.yetyman.vulkan.VkResult;

import java.lang.foreign.Arena;
import java.util.concurrent.CompletableFuture;

/**
 * Handle for async buffer transfer operations.
 * Allows waiting, polling, or callback-based completion.
 * Implements AutoCloseable to ensure fence cleanup.
 */
public class TransferCompletion implements AutoCloseable {
    private static final TransferCompletion COMPLETED = new TransferCompletion(null, null, null);
    
    private final VkDevice device;
    private final VkFence fence;
    private final Arena arena;
    
    public TransferCompletion(VkDevice device, VkFence fence, Arena arena) {
        this.device = device;
        this.fence = fence;
        this.arena = arena;
    }
    
    /**
     * Returns a pre-completed TransferCompletion for synchronous operations.
     */
    public static TransferCompletion completed() {
        return COMPLETED;
    }
    
    /**
     * Blocks current thread until transfer completes.
     * Safe to call from any thread.
     */
    public void await() {
        if (fence == null) return; // Already completed
        VkFenceOps.wait(device, fence, Long.MAX_VALUE, arena).check();
    }
    
    /**
     * Non-blocking check if transfer is complete.
     * Safe to call from any thread, typically used in game loop.
     */
    public boolean isComplete() {
        if (fence == null) return true; // Already completed
        return VkFenceOps.getStatus(device, fence) == VkResult.VK_SUCCESS;
    }
    
    /**
     * Executes callback when transfer completes.
     * Spawns virtual thread that waits on fence - completely safe.
     * Fence is automatically cleaned up after callback.
     */
    public void onComplete(Runnable callback) {
        if (fence == null) {
            callback.run();
            return;
        }
        Thread.ofVirtual().start(() -> {
            await();
            try {
                callback.run();
            } finally {
                fence.close();
            }
        });
    }
    
    /**
     * Returns CompletableFuture for integration with async Java APIs.
     */
    public CompletableFuture<Void> toFuture() {
        if (fence == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> await());
    }
    
    @Override
    public void close() {
        if (fence != null) {
            fence.close();
        }
    }
}
