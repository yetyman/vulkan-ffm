package io.github.yetyman.vulkan.queue;

import java.lang.foreign.MemorySegment;

/**
 * Abstraction over {@code vkQueueSubmit} strategies.
 *
 * <p>Implementations determine how and when submitted work reaches the GPU:
 * <ul>
 *   <li>{@link DirectSubmitter} — calls {@code vkQueueSubmit} immediately on the calling thread.</li>
 *   <li>{@link MutexSubmitter} — same as direct but serialized with a lock; for shared queue handles.</li>
 *   <li>{@link OpportunisticBatchingSubmitter} — accumulates submits and flushes when the caller
 *       drains or when the timeout checker fires.</li>
 *   <li>{@link MailboxSubmitter} — posts to a dedicated submission thread via a lock-free queue.</li>
 * </ul>
 *
 * <p>Install on a {@link io.github.yetyman.vulkan.VkQueue} via
 * {@code queue.setSubmitter(submitter)}. The default is {@link DirectSubmitter}.
 */
public interface IQueueSubmitter {

    /**
     * Submits the given {@code VkSubmitInfo} to the queue with the given fence.
     * The fence may be {@link MemorySegment#NULL} if no CPU-side completion signal is needed.
     *
     * @param submitInfo a {@code VkSubmitInfo} struct allocated in a caller-managed arena
     * @param fence      a {@code VkFence} handle or {@link MemorySegment#NULL}
     */
    void submit(MemorySegment submitInfo, MemorySegment fence);

    /**
     * Flushes any pending batched submits immediately.
     * No-op for {@link DirectSubmitter} and {@link MutexSubmitter}.
     */
    default void flush() {}

    /**
     * Returns the number of submits currently pending (not yet dispatched to the GPU).
     * Always 0 for {@link DirectSubmitter} and {@link MutexSubmitter}.
     */
    default int pendingCount() { return 0; }

    /**
     * Called by the application-wide timeout checker thread to flush stale pending work.
     * Implementations should flush if {@code pendingCount() > 0} and the oldest pending
     * submit has been waiting longer than their configured threshold.
     */
    default void checkTimeout() { flush(); }
}
