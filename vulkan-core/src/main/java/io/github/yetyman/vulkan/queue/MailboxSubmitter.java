package io.github.yetyman.vulkan.queue;

import io.github.yetyman.vulkan.VkSubmit;
import io.github.yetyman.vulkan.Vulkan;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Posts {@code VkSubmitInfo} packets to a dedicated submission thread via a lock-free MPSC queue.
 * The submission thread owns the queue handle exclusively — no other thread ever calls
 * {@code vkQueueSubmit} on it.
 *
 * <p>Use when strict submission ordering guarantees are required, or when the submission thread
 * also performs other queue-adjacent work (fence recycling, semaphore pool management, etc.).
 *
 * <p>Latency is one MPSC enqueue plus one thread wakeup (~1–10µs depending on OS scheduler).
 * For most workloads this is negligible. For ultra-high-frequency compute loops on dedicated
 * queues, prefer {@link DirectSubmitter}.
 *
 * <p>Call {@link #close()} to shut down the submission thread cleanly.
 */
public final class MailboxSubmitter implements IQueueSubmitter, AutoCloseable {

    private record Pending(byte[] submitInfoBytes, MemorySegment fence) {}

    private static byte[] copyStruct(MemorySegment submitInfo) {
        return submitInfo.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
    }

    private final ConcurrentLinkedQueue<Pending> mailbox = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread submissionThread;

    public MailboxSubmitter(MemorySegment queueHandle, String threadName) {
        submissionThread = Thread.ofPlatform().name(threadName).start(() -> {
            while (running.get() || !mailbox.isEmpty()) {
                Pending p = mailbox.poll();
                if (p != null) {
                    try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
                        MemorySegment seg = arena.allocateFrom(java.lang.foreign.ValueLayout.JAVA_BYTE, p.submitInfoBytes());
                        VkSubmit.queueSubmit(queueHandle, 1, seg, p.fence()).check();
                    }
                } else {
                    Thread.onSpinWait();
                }
            }
        });
    }

    public MailboxSubmitter(MemorySegment queueHandle) {
        this(queueHandle, "vk-queue-submitter");
    }

    @Override
    public void submit(MemorySegment submitInfo, MemorySegment fence) {
        mailbox.add(new Pending(copyStruct(submitInfo), fence));
    }

    @Override
    public int pendingCount() { return mailbox.size(); }

    /** Signals the submission thread to stop after draining remaining work and waits for it. */
    @Override
    public void close() {
        running.set(false);
        try { submissionThread.join(5000); } catch (InterruptedException ignored) {}
    }
}
