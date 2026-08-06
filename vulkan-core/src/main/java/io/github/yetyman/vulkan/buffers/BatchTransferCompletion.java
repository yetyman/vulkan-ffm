package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkTimelineSemaphore;

import java.lang.foreign.Arena;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared completion state for one generation of a {@link TransferBatch}, identified by the timeline
 * value that generation signals.
 *
 * <p>This used to be backed by a single {@code VkFence} reused across every generation, which was
 * unsound: a fence must be reset before reuse, {@code vkResetFences} and the fence parameter of
 * {@code vkQueueSubmit} both require external synchronization, and resetting a fence while a wait on
 * it is pending is undefined. A completion handed out for generation N could therefore find itself
 * waiting on generation N+1's signal, reporting incomplete for work that had finished, or blocking
 * forever if N+1 was never submitted.
 *
 * <p>A timeline value has none of those problems because there is nothing to reset. The counter is
 * monotonic, so generation N's target stays meaningful no matter how many generations follow;
 * {@code vkWaitSemaphores} and {@code vkGetSemaphoreCounterValue} are internally synchronized, so
 * any number of threads may observe different values concurrently; and completion becomes a pure
 * comparison rather than a piece of resettable state. The race is removed rather than guarded, and
 * one fewer API call is made per submission.
 *
 * <p>Instances are reference counted. The batch's command buffer arena and any staging buffers it
 * owns are released once every handed-out {@link TransferCompletion} view has been closed.
 */
class BatchTransferCompletion {

    private final VkTimelineSemaphore timeline;
    private final long targetValue;
    private final Arena batchArena;
    private final List<AutoCloseable> ownedObjects;
    private final AtomicInteger refCount = new AtomicInteger(0);
    private final AtomicBoolean resourcesFreed = new AtomicBoolean(false);

    private volatile boolean submitted = false;
    private volatile boolean noWork = false;

    BatchTransferCompletion(VkTimelineSemaphore timeline, long targetValue,
                            Arena batchArena, List<AutoCloseable> ownedObjects) {
        this.timeline = timeline;
        this.targetValue = targetValue;
        this.batchArena = batchArena;
        this.ownedObjects = ownedObjects;
    }

    /**
     * @return the timeline value this generation signals on GPU completion
     */
    long targetValue() {
        return targetValue;
    }

    /**
     * Marks this generation as submitted to the queue, so its target value will eventually be
     * signalled.
     */
    void resolveSubmitted() {
        this.submitted = true;
    }

    /**
     * Marks this generation as finished without any submission, which happens when a flush finds
     * nothing recorded. No timeline value will ever be signalled for it, so it must not be waited on.
     */
    void resolveNoWork() {
        this.noWork = true;
        this.submitted = true;
    }

    boolean isSubmitted() {
        return submitted;
    }

    /**
     * Blocks until the GPU reaches this generation's timeline value.
     *
     * <p>Returns immediately when the generation carried no work, and also when it has not been
     * submitted yet -- callers that need submission to happen first go through
     * {@link TransferCompletion#await()}, which flushes on the owning thread before waiting.
     */
    void await() {
        if (noWork || !submitted) return;
        timeline.await(targetValue);
    }

    boolean isComplete() {
        if (noWork) return true;
        if (!submitted) return false;
        return timeline.counterValue() >= targetValue;
    }

    void retain() {
        refCount.incrementAndGet();
    }

    void release() {
        if (refCount.decrementAndGet() == 0) freeResources();
    }

    void forceClose() {
        freeResources();
    }

    /**
     * @return true once this generation's resources have been released, so the batch can drop it
     * from its bookkeeping
     */
    boolean isReleased() {
        return resourcesFreed.get();
    }

    private void freeResources() {
        if (!resourcesFreed.compareAndSet(false, true)) return;
        for (AutoCloseable obj : ownedObjects) {
            try {
                obj.close();
            } catch (Exception ignored) {
            }
        }
        batchArena.close();
    }
}
