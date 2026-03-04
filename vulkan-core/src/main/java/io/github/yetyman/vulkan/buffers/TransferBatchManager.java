package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkTimelineSemaphore;
import io.github.yetyman.vulkan.highlevel.VkCommandPoolRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of per-thread per-queue {@link TransferBatch} instances.
 * Batches are created on demand and flushed explicitly or automatically on size threshold.
 * Call {@link #destroyAll(VkDevice)} from {@link VkDevice#close()} before destroying the device.
 * Call {@link #destroyThread(VkDevice)} at dedicated transfer thread exit.
 */
public class TransferBatchManager {
    // device address -> thread id -> queue handle address -> batch
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, ConcurrentHashMap<Long, TransferBatch>>> REGISTRY
            = new ConcurrentHashMap<>();

    static TransferBatch getOrCreate(VkDevice device, VkQueue queue) {
        long deviceKey = device.handle().address();
        long threadKey = Thread.currentThread().threadId();
        long queueKey = queue.handle().address();
        return REGISTRY
                .computeIfAbsent(deviceKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(threadKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(queueKey, k -> new TransferBatch(
                        device, queue,
                        VkCommandPoolRegistry.getOrCreate(device, queue.familyIndex())));
    }

    /**
     * Attaches a timeline semaphore signal to the current batch for this thread+queue.
     * The semaphore will be advanced to {@code value} when the batch completes on the GPU.
     */
    public static void signalOn(VkDevice device, VkQueue queue, VkTimelineSemaphore semaphore, long value) {
        getOrCreate(device, queue).signalOn(semaphore, value);
    }

    /**
     * Flushes the current batch for this thread+queue, submitting all pending transfers.
     * @return a TransferCompletion for the submitted batch (caller must close it).
     */
    public static TransferCompletion flush(VkDevice device, VkQueue queue) {
        long deviceKey = device.handle().address();
        long threadKey = Thread.currentThread().threadId();
        long queueKey = queue.handle().address();
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, TransferBatch>> byThread =
                REGISTRY.get(deviceKey);
        if (byThread == null) return TransferCompletion.completed();
        ConcurrentHashMap<Long, TransferBatch> byQueue = byThread.get(threadKey);
        if (byQueue == null) return TransferCompletion.completed();
        TransferBatch batch = byQueue.get(queueKey);
        if (batch == null) return TransferCompletion.completed();
        return batch.flush();
    }

    /** Destroys all batches for the current thread on the given device. Call at thread exit. */
    public static void destroyThread(VkDevice device) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, TransferBatch>> byThread =
                REGISTRY.get(device.handle().address());
        if (byThread == null) return;
        ConcurrentHashMap<Long, TransferBatch> byQueue =
                byThread.remove(Thread.currentThread().threadId());
        if (byQueue == null) return;
        for (TransferBatch batch : byQueue.values()) batch.destroy();
    }

    /** Destroys all batches for the given device. Called from VkDevice.close(). */
    public static void destroyAll(VkDevice device) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, TransferBatch>> byThread =
                REGISTRY.remove(device.handle().address());
        if (byThread == null) return;
        for (Map<Long, TransferBatch> byQueue : byThread.values())
            for (TransferBatch batch : byQueue.values())
                batch.destroy();
    }
}
