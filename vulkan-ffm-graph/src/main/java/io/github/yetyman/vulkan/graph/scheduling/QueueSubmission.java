package io.github.yetyman.vulkan.graph.scheduling;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkSubmit;
import io.github.yetyman.vulkan.VkTimelineSemaphore;
import io.github.yetyman.vulkan.graph.edges.SemaphoreEdge;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single queue's submission unit within a frame. Accumulates command buffers,
 * wait/signal semaphores (both binary and timeline), and handles the actual vkQueueSubmit.
 *
 * Each distinct queue family that participates in a frame gets one QueueSubmission.
 * The executor builds these up during recording and submits them in dependency order.
 */
public class QueueSubmission {

    private final QueueAssignment queue;
    private final List<VkCommandBuffer> commandBuffers = new ArrayList<>();
    private final List<WaitEntry> waits = new ArrayList<>();
    private final List<SignalEntry> signals = new ArrayList<>();
    private boolean submitted = false;

    public QueueSubmission(QueueAssignment queue) {
        this.queue = queue;
    }

    /** Adds a primary command buffer to this submission */
    public void addCommandBuffer(VkCommandBuffer cmd) {
        commandBuffers.add(cmd);
    }

    /** Adds a binary semaphore wait */
    public void waitBinary(MemorySegment semaphore, int stageMask) {
        waits.add(new WaitEntry(semaphore, 0, stageMask, false));
    }

    /** Adds a timeline semaphore wait */
    public void waitTimeline(MemorySegment semaphore, long value, int stageMask) {
        waits.add(new WaitEntry(semaphore, value, stageMask, true));
    }

    /** Adds a timeline semaphore wait from a VkTimelineSemaphore */
    public void waitTimeline(VkTimelineSemaphore semaphore, long value, int stageMask) {
        waits.add(new WaitEntry(semaphore.handle(), value, stageMask, true));
    }

    /** Adds a binary semaphore signal */
    public void signalBinary(MemorySegment semaphore) {
        signals.add(new SignalEntry(semaphore, 0, false));
    }

    /** Adds a timeline semaphore signal */
    public void signalTimeline(MemorySegment semaphore, long value) {
        signals.add(new SignalEntry(semaphore, value, true));
    }

    /** Adds a timeline semaphore signal from a VkTimelineSemaphore */
    public void signalTimeline(VkTimelineSemaphore semaphore, long value) {
        signals.add(new SignalEntry(semaphore.handle(), value, true));
    }

    /** Adds an external semaphore wait edge */
    public void addExternalWait(SemaphoreEdge edge) {
        if (edge.isTimeline()) {
            waitTimeline(edge.semaphore(), edge.waitValue(), edge.stageMask());
        } else {
            waitBinary(edge.semaphore(), edge.stageMask());
        }
    }

    /** Adds an external semaphore signal edge */
    public void addExternalSignal(SemaphoreEdge edge) {
        if (edge.isTimeline()) {
            signalTimeline(edge.semaphore(), edge.waitValue());
        } else {
            signalBinary(edge.semaphore());
        }
    }

    /**
     * Submits all accumulated command buffers with the configured semaphore dependencies.
     *
     * @param fence fence to signal on completion, or MemorySegment.NULL
     * @param arena arena for submit struct allocation
     */
    public void submit(MemorySegment fence, SegmentAllocator allocator) {
        if (commandBuffers.isEmpty()) return;
        if (submitted) throw new IllegalStateException("QueueSubmission already submitted");
        submitted = true;

        VkSubmit.Builder builder = VkSubmit.builder();

        for (VkCommandBuffer cmd : commandBuffers) {
            builder.commandBuffer(cmd);
        }

        for (WaitEntry wait : waits) {
            if (wait.timeline) {
                builder.waitTimelineSemaphore(wait.semaphore, wait.value, wait.stageMask);
            } else {
                builder.waitSemaphore(wait.semaphore, wait.stageMask);
            }
        }

        for (SignalEntry signal : signals) {
            if (signal.timeline) {
                builder.signalTimelineSemaphore(signal.semaphore, signal.value);
            } else {
                builder.signalSemaphore(signal.semaphore);
            }
        }

        builder.submit(queue.queueHandle(), fence, allocator);
    }

    /** @return the queue assignment this submission targets */
    public QueueAssignment queue() { return queue; }

    /** @return the queue family index */
    public int queueFamilyIndex() { return queue.queueFamilyIndex(); }

    /** @return true if this submission has any command buffers recorded */
    public boolean hasWork() { return !commandBuffers.isEmpty(); }

    /** @return true if this submission has already been submitted */
    public boolean isSubmitted() { return submitted; }

    /** @return number of command buffers accumulated */
    public int commandBufferCount() { return commandBuffers.size(); }

    /** Resets for reuse in the next frame */
    public void reset() {
        commandBuffers.clear();
        waits.clear();
        signals.clear();
        submitted = false;
    }

    private record WaitEntry(MemorySegment semaphore, long value, int stageMask, boolean timeline) {}
    private record SignalEntry(MemorySegment semaphore, long value, boolean timeline) {}
}
