package io.github.yetyman.vulkan.queue;

import io.github.yetyman.vulkan.VkSubmit;
import io.github.yetyman.vulkan.Vulkan;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accumulates {@code VkSubmitInfo} packets in a lock-free queue and dispatches them in a single
 * {@code vkQueueSubmit} call when flushed.
 *
 * <p>Flush is triggered by:
 * <ul>
 *   <li>The calling thread when it enqueues and finds it is the sole enqueuer (opportunistic drain).</li>
 *   <li>An explicit {@link #flush()} call.</li>
 *   <li>The application-wide timeout checker via {@link #checkTimeout()}.</li>
 * </ul>
 *
 * <p>Batching reduces {@code vkQueueSubmit} kernel transitions when multiple producers submit
 * to the same queue faster than the drain point is reached. On a lightly loaded queue the
 * pending list is almost always length 1 and the opportunistic drain fires immediately —
 * overhead is one CAS per submit with no contention.
 *
 * <p><b>Fence limitation:</b> Vulkan only allows one fence per {@code vkQueueSubmit} call.
 * When batching multiple submits, only the fence from the <em>last</em> submit in the batch
 * is passed to {@code vkQueueSubmit}. Callers that require per-submit fence signaling should
 * use {@link DirectSubmitter} or {@link MutexSubmitter} instead.
 */
public final class OpportunisticBatchingSubmitter implements IQueueSubmitter {

    private record Pending(byte[] submitInfoBytes, MemorySegment fence, long enqueuedNanos) {}

    private static byte[] copyStruct(MemorySegment submitInfo) {
        return submitInfo.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
    }

    private final MemorySegment queueHandle;
    private final ConcurrentLinkedQueue<Pending> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final long timeoutNanos;

    /**
     * @param queueHandle  the VkQueue handle
     * @param timeoutNanos maximum age of a pending submit before the timeout checker forces a flush
     */
    public OpportunisticBatchingSubmitter(MemorySegment queueHandle, long timeoutNanos) {
        this.queueHandle = queueHandle;
        this.timeoutNanos = timeoutNanos;
    }

    @Override
    public void submit(MemorySegment submitInfo, MemorySegment fence) {
        pending.add(new Pending(copyStruct(submitInfo), fence, System.nanoTime()));
        int count = pendingCount.incrementAndGet();
        if (count == 1) {
            flush();
        }
    }

    @Override
    public void flush() {
        if (pendingCount.get() == 0) return;

        java.util.List<Pending> batch = new java.util.ArrayList<>();
        Pending p;
        while ((p = pending.poll()) != null) {
            batch.add(p);
            pendingCount.decrementAndGet();
        }
        if (batch.isEmpty()) return;

        try (Arena arena = Arena.ofConfined()) {
            if (batch.size() == 1) {
                Pending single = batch.get(0);
                MemorySegment seg = arena.allocateFrom(java.lang.foreign.ValueLayout.JAVA_BYTE, single.submitInfoBytes());
                VkSubmit.queueSubmit(queueHandle, 1, seg, single.fence()).check();
                return;
            }

            long structSize = io.github.yetyman.vulkan.generated.VkSubmitInfo.layout().byteSize();
            MemorySegment array = arena.allocate(structSize * batch.size());
            for (int i = 0; i < batch.size(); i++) {
                MemorySegment seg = arena.allocateFrom(java.lang.foreign.ValueLayout.JAVA_BYTE, batch.get(i).submitInfoBytes());
                MemorySegment.copy(seg, 0, array, i * structSize, structSize);
            }
            MemorySegment lastFence = batch.get(batch.size() - 1).fence();
            VkSubmit.queueSubmit(queueHandle, batch.size(), array, lastFence).check();
        }
    }

    @Override
    public int pendingCount() { return pendingCount.get(); }

    @Override
    public void checkTimeout() {
        if (pendingCount.get() == 0) return;
        Pending oldest = pending.peek();
        if (oldest != null && System.nanoTime() - oldest.enqueuedNanos() >= timeoutNanos) {
            flush();
        }
    }
}
