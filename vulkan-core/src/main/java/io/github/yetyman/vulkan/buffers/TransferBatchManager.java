package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkTimelineSemaphore;
import io.github.yetyman.vulkan.commands.CommandPoolRegistry;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of per-thread per-queue {@link TransferBatch} instances.
 *
 * <p>The hot path ({@link #getOrCreate}) is a thread-local lookup with no synchronization,
 * no ConcurrentHashMap, and no boxing. Each thread owns a small flat array of batch entries
 * keyed by queue handle address, searched linearly (typically 1-2 entries per thread).
 *
 * <p>A global tracking set registers each thread's local state for device-wide teardown.
 * This set is only touched on first batch creation per thread and on {@link #destroyAll}.
 *
 * <p>Call {@link #destroyAll(VkDevice)} from {@link VkDevice#close()} before destroying the device.
 * Call {@link #destroyThread(VkDevice)} at dedicated transfer thread exit.
 */
public class TransferBatchManager {

    /**
     * Per-thread batch registry. Small flat array — linear scan is faster than any map
     * for the typical 1-3 queues a thread uses.
     */
    private static final class ThreadBatches {
        private static final int INITIAL_CAPACITY = 4;

        long deviceAddress;
        long[] queueKeys = new long[INITIAL_CAPACITY];
        TransferBatch[] batches = new TransferBatch[INITIAL_CAPACITY];
        int count = 0;

        TransferBatch find(long queueKey) {
            for (int i = 0; i < count; i++) {
                if (queueKeys[i] == queueKey) return batches[i];
            }
            return null;
        }

        void put(long queueKey, TransferBatch batch) {
            if (count == queueKeys.length) {
                int newLen = queueKeys.length * 2;
                long[] newKeys = new long[newLen];
                TransferBatch[] newBatches = new TransferBatch[newLen];
                System.arraycopy(queueKeys, 0, newKeys, 0, count);
                System.arraycopy(batches, 0, newBatches, 0, count);
                queueKeys = newKeys;
                batches = newBatches;
            }
            queueKeys[count] = queueKey;
            batches[count] = batch;
            count++;
        }

        void destroyAll() {
            for (int i = 0; i < count; i++) {
                batches[i].destroy();
                batches[i] = null;
            }
            count = 0;
        }
    }

    private static final ThreadLocal<ThreadBatches> LOCAL = ThreadLocal.withInitial(ThreadBatches::new);

    // Global tracking: all ThreadBatches instances that have been created, for device-wide teardown.
    // Only written on first batch creation per thread (rare), read on destroyAll (rare).
    private static final Set<ThreadBatches> ALL_INSTANCES = ConcurrentHashMap.newKeySet();

    static TransferBatch getOrCreate(VkDevice device, VkQueue queue) {
        ThreadBatches tb = LOCAL.get();
        long queueKey = queue.handle().address();

        // Fast path: check if device matches and scan the flat array
        long deviceAddr = device.handle().address();
        if (tb.deviceAddress != deviceAddr) {
            // Thread switched devices (rare) or first use — reset
            if (tb.count > 0 && tb.deviceAddress != 0) {
                tb.destroyAll();
            }
            tb.deviceAddress = deviceAddr;
            ALL_INSTANCES.add(tb);
        }

        TransferBatch batch = tb.find(queueKey);
        if (batch != null) return batch;

        // Cold path: create new batch
        batch = new TransferBatch(device, queue,
                CommandPoolRegistry.getOrCreate(device, queue.familyIndex()));
        tb.put(queueKey, batch);
        return batch;
    }

    /**
     * Attaches a timeline semaphore signal to the current batch for this thread+queue.
     */
    public static void signalOn(VkDevice device, VkQueue queue, VkTimelineSemaphore semaphore, long value) {
        getOrCreate(device, queue).signalOn(semaphore, value);
    }

    /**
     * Flushes the current batch for this thread+queue, submitting all pending transfers.
     */
    public static GpuCompletion flush(VkDevice device, VkQueue queue) {
        ThreadBatches tb = LOCAL.get();
        long queueKey = queue.handle().address();
        TransferBatch batch = tb.find(queueKey);
        if (batch == null) return GpuCompletion.completed();
        return batch.flush();
    }

    /**
     * Destroys all batches for the current thread on the given device. Call at thread exit.
     */
    public static void destroyThread(VkDevice device) {
        ThreadBatches tb = LOCAL.get();
        if (tb.deviceAddress != device.handle().address()) return;
        tb.destroyAll();
        ALL_INSTANCES.remove(tb);
    }

    /**
     * Destroys all batches across all threads for the given device. Called from VkDevice.close().
     * Safe to call after vkDeviceWaitIdle when all threads are quiescent.
     */
    public static void destroyAll(VkDevice device) {
        long deviceAddr = device.handle().address();
        for (ThreadBatches tb : ALL_INSTANCES) {
            if (tb.deviceAddress == deviceAddr) {
                tb.destroyAll();
            }
        }
        ALL_INSTANCES.removeIf(tb -> tb.deviceAddress == deviceAddr);
    }
}
