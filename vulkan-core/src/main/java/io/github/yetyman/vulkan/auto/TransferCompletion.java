package io.github.yetyman.vulkan.auto;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkFence;
import io.github.yetyman.vulkan.VkFenceOps;

import java.lang.foreign.Arena;
import java.util.concurrent.CompletableFuture;

/**
 * Handle for async buffer transfer operations.
 * Allows waiting, polling, or callback-based completion.
 */
public class TransferCompletion {
    private final VkDevice device;
    private final VkFence fence;
    private final Arena arena;
    
    public TransferCompletion(VkDevice device, VkFence fence, Arena arena) {
        this.device = device;
        this.fence = fence;
        this.arena = arena;
    }
    
    /**
     * Blocks current thread until transfer completes.
     * Safe to call from any thread.
     */
    public void await() {
        VkFenceOps.wait(device, fence, Long.MAX_VALUE, arena).check();
    }
    
    /**
     * Non-blocking check if transfer is complete.
     * Safe to call from any thread, typically used in game loop.
     */
    public boolean isComplete() {
        return VkFenceOps.getStatus(device, fence).isSuccess();
    }
    
    /**
     * Executes callback when transfer completes.
     * Spawns virtual thread that waits on fence - completely safe.
     * Fence is automatically cleaned up after callback.
     */
    public void onComplete(Runnable callback) {
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
        return CompletableFuture.runAsync(() -> await());
    }
}
