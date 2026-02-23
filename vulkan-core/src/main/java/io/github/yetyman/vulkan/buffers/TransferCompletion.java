package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkFence;
import io.github.yetyman.vulkan.VkFenceOps;
import io.github.yetyman.vulkan.VkResult;

import java.lang.foreign.Arena;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle for async buffer transfer operations.
 * Owns the fence, any ephemeral Vulkan objects (e.g. staging buffers), and the native memory arena
 * for the in-flight transfer. All are destroyed on {@link #close()}.
 */
public class TransferCompletion implements AutoCloseable {
    private static final TransferCompletion COMPLETED = new TransferCompletion(null, null, null, (AutoCloseable[]) null);

    private final VkDevice device;
    private final VkFence fence;
    /** Shared arena owning native memory (structs, copy regions, etc.) for this transfer. */
    private final Arena transferArena;
    /** Additional Vulkan objects (e.g. staging VkBuffer) that must be explicitly closed after the fence signals. */
    private final AutoCloseable[] ownedObjects;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public TransferCompletion(VkDevice device, VkFence fence, Arena transferArena, AutoCloseable... ownedObjects) {
        this.device = device;
        this.fence = fence;
        this.transferArena = transferArena;
        this.ownedObjects = ownedObjects;
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
        if (fence == null) return;
        try (Arena tmp = Arena.ofConfined()) {
            VkFenceOps.wait(device, fence, Long.MAX_VALUE, tmp).check();
        }
    }

    /**
     * Non-blocking check if transfer is complete.
     * Safe to call from any thread, typically used in game loop.
     */
    public boolean isComplete() {
        if (fence == null) return true;
        return VkFenceOps.getStatus(device, fence) == VkResult.VK_SUCCESS;
    }

    /**
     * Executes callback when transfer completes, then closes this completion.
     * Spawns a virtual thread — safe to call from any thread.
     */
    public void onComplete(Runnable callback) {
        if (fence == null) {
            callback.run();
            close();
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

    /**
     * Returns CompletableFuture for integration with async Java APIs.
     */
    public CompletableFuture<Void> toFuture() {
        if (fence == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(this::await);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (fence != null) fence.close();
        if (ownedObjects != null) {
            for (AutoCloseable obj : ownedObjects) {
                if (obj != null) try { obj.close(); } catch (Exception ignored) {}
            }
        }
        if (transferArena != null) transferArena.close();
    }
}
