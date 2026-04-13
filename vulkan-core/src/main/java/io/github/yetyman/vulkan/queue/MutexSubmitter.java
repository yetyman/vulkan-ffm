package io.github.yetyman.vulkan.queue;

import io.github.yetyman.vulkan.VkSubmit;
import io.github.yetyman.vulkan.Vulkan;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes {@code vkQueueSubmit} with a {@link ReentrantLock}.
 * Use when multiple threads share the same queue handle and a dedicated queue is unavailable.
 * Prefer {@link DirectSubmitter} on dedicated queues — this adds lock overhead on every submit.
 */
public final class MutexSubmitter implements IQueueSubmitter {

    private final MemorySegment queueHandle;
    private final ReentrantLock lock;

    /**
     * Creates a MutexSubmitter with its own private lock.
     */
    public MutexSubmitter(MemorySegment queueHandle) {
        this.queueHandle = queueHandle;
        this.lock = new ReentrantLock();
    }

    /**
     * Creates a MutexSubmitter sharing an existing lock.
     * Use this when multiple submitters must be mutually exclusive on the same queue handle
     * (e.g. a compute submitter and a graphics submitter sharing one queue).
     */
    public MutexSubmitter(MemorySegment queueHandle, ReentrantLock sharedLock) {
        this.queueHandle = queueHandle;
        this.lock = sharedLock;
    }

    /**
     * @return the lock used by this submitter, for sharing with other submitters on the same queue.
     */
    public ReentrantLock lock() {
        return lock;
    }

    @Override
    public void submit(MemorySegment submitInfo, MemorySegment fence) {
        lock.lock();
        try {
            VkSubmit.queueSubmit(queueHandle, 1, submitInfo, fence).check();
        } finally {
            lock.unlock();
        }
    }
}
