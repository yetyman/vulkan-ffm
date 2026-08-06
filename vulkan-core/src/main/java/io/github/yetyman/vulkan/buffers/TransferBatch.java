package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.command.VkCopy;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkQueueSubmit;
import static io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT;

/**
 * Accumulates buffer copy commands into one command buffer and submits them as a single batch,
 * auto-flushing once {@link #AUTO_FLUSH_THRESHOLD} bytes have been staged.
 *
 * <p>Completion is tracked by a single monotonic timeline semaphore owned by this batch. Generation
 * N signals value N; a completion handed out for generation N waits for that value. Nothing is ever
 * reset, so handing out a completion is safe regardless of how many further generations are
 * submitted afterwards, and any number of threads may wait on or poll different values concurrently.
 * See {@link BatchTransferCompletion} for why the previous single-reused-fence design was unsound.
 *
 * <p>Because timeline semaphores are the batching mechanism, they are a hard requirement of this
 * class: the owning {@link VkDevice} must have been built with
 * {@link VkDevice.Builder#enableTimelineSemaphore()}. {@code VulkanContext} does this for every
 * device it creates.
 *
 * <h2>Thread ownership</h2>
 * A batch belongs to exactly one thread -- the one that first requested it from
 * {@link TransferBatchManager} -- because it records into a single command buffer that no other
 * thread may touch. {@link #record} and {@link #flush} therefore assert the calling thread is the
 * owner. {@link #destroy} deliberately does not, since it runs during device teardown after
 * {@code vkDeviceWaitIdle}, when other threads are quiescent by construction.
 */
class TransferBatch {
    static final long AUTO_FLUSH_THRESHOLD = 64L * 1024 * 1024;

    private final VkDevice device;
    private final VkQueue queue;
    private final VkCommandPool commandPool;
    private final long ownerThreadId;

    private final Arena timelineArena;
    private final VkTimelineSemaphore timeline;

    private final List<TimelineWait> pendingWaits = new ArrayList<>();
    private final List<TimelineWait> pendingSignals = new ArrayList<>();
    private final List<BatchTransferCompletion> liveCompletions = new ArrayList<>();

    private Arena batchArena;
    private VkCommandBuffer commandBuffer;
    private List<AutoCloseable> ownedObjects;
    private BatchTransferCompletion currentCompletion;
    private long stagedBytes;
    private int pendingCount;

    /** Highest timeline value handed to a generation so far. Only ever increases. */
    private long lastAssignedValue;

    private record TimelineWait(VkTimelineSemaphore semaphore, long value) {
    }

    TransferBatch(VkDevice device, VkQueue queue, VkCommandPool commandPool) {
        this.device = device;
        this.queue = queue;
        this.commandPool = commandPool;
        this.ownerThreadId = Thread.currentThread().threadId();
        this.timelineArena = Arena.ofShared();
        try {
            this.timeline = VkTimelineSemaphore.create(device, 0, timelineArena);
        } catch (Exception e) {
            timelineArena.close();
            throw new IllegalStateException(
                    "TransferBatch requires timeline semaphore support; build the VkDevice with enableTimelineSemaphore()", e);
        }
        open();
    }

    private void open() {
        batchArena = Arena.ofShared();
        ownedObjects = new ArrayList<>();
        VkCommandBuffer[] cmds = VkCommandBufferAlloc.builder()
                .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(batchArena);
        commandBuffer = cmds[0];
        VkCommandBuffer.begin(commandBuffer).oneTimeSubmit().execute(batchArena);
        currentCompletion = new BatchTransferCompletion(timeline, ++lastAssignedValue, batchArena, ownedObjects);
        liveCompletions.removeIf(BatchTransferCompletion::isReleased);
        liveCompletions.add(currentCompletion);
        stagedBytes = 0;
        pendingCount = 0;
    }

    GpuCompletion record(MemorySegment srcHandle, MemorySegment dstHandle,
                         long srcOffset, long dstOffset, long size,
                         AutoCloseable... toOwn) {
        checkOwner("record into");
        VkCopy.copyBuffer(commandBuffer.handle(), srcHandle, dstHandle, srcOffset, dstOffset, size);

        for (AutoCloseable obj : toOwn) if (obj != null) ownedObjects.add(obj);
        stagedBytes += size;
        pendingCount++;
        currentCompletion.retain();
        GpuCompletion view = new TransferCompletion(currentCompletion, this);

        if (stagedBytes >= AUTO_FLUSH_THRESHOLD) flush();
        return view;
    }

    GpuCompletion flush() {
        checkOwner("flush");
        return flushUnchecked();
    }

    /**
     * Flush without the ownership assertion. Only for teardown, where the caller has already
     * guaranteed quiescence.
     */
    private GpuCompletion flushUnchecked() {
        BatchTransferCompletion completing = currentCompletion;

        if (pendingCount == 0 && pendingSignals.isEmpty() && pendingWaits.isEmpty()) {
            // Nothing recorded: no submission happens, so this generation's timeline value is never
            // signalled and must not be waited on.
            completing.resolveNoWork();
            completing.retain();
            open();
            return new TransferCompletion(completing, this);
        }

        vkEndCommandBuffer(commandBuffer.handle());
        submit(completing.targetValue());
        completing.resolveSubmitted();

        completing.retain();
        GpuCompletion view = new TransferCompletion(completing, this);
        open();
        return view;
    }

    void destroy() {
        // Runs during device teardown, after vkDeviceWaitIdle, so the owner check is skipped.
        try (GpuCompletion tc = flushUnchecked()) {
            tc.await();
        }
        for (BatchTransferCompletion c : liveCompletions) c.forceClose();
        liveCompletions.clear();
        timeline.close();
        timelineArena.close();
    }

    VkQueue queue() {
        return queue;
    }

    /**
     * @return true if the calling thread owns this batch and may record into or flush it
     */
    boolean isOwnedByCurrentThread() {
        return Thread.currentThread().threadId() == ownerThreadId;
    }

    /**
     * Add a timeline semaphore wait to this batch - batch won't execute until semaphore reaches value.
     */
    public TransferBatch waitUntil(VkTimelineSemaphore semaphore, long value) {
        checkOwner("add a wait to");
        pendingWaits.add(new TimelineWait(semaphore, value));
        return this;
    }

    /**
     * Add a timeline semaphore signal to this batch - semaphore will be advanced to value on GPU completion.
     */
    public TransferBatch signalOn(VkTimelineSemaphore semaphore, long value) {
        checkOwner("add a signal to");
        pendingSignals.add(new TimelineWait(semaphore, value));
        return this;
    }

    /**
     * Submits the recorded command buffer, signalling this batch's own timeline at
     * {@code signalValue} plus any caller-requested signals. No fence is involved: completion is
     * observed through the timeline value, which needs no reset and is safe to poll or wait on from
     * any thread.
     */
    private void submit(long signalValue) {
        try (Arena tmp = Arena.ofConfined()) {
            VkSubmitBuilder builder = new VkSubmitBuilder().commandBuffer(commandBuffer);

            for (TimelineWait wait : pendingWaits) {
                wait.semaphore.addWaitTo(builder, wait.value, VK_PIPELINE_STAGE_TRANSFER_BIT.value());
            }
            builder.signal(timeline, signalValue);
            for (TimelineWait signal : pendingSignals) {
                signal.semaphore.addSignalTo(builder, signal.value);
            }

            MemorySegment submitInfo = builder.build(tmp);
            VkResult.fromInt(vkQueueSubmit(queue.handle(), 1, submitInfo, MemorySegment.NULL)).check();
            timeline.recordSignal(signalValue);
            for (TimelineWait signal : pendingSignals) {
                signal.semaphore.recordSignal(signal.value);
            }
            pendingWaits.clear();
            pendingSignals.clear();
        }
    }

    private void checkOwner(String operation) {
        if (Thread.currentThread().threadId() != ownerThreadId) {
            throw new IllegalStateException("Cannot " + operation + " a TransferBatch owned by thread "
                    + ownerThreadId + " from thread " + Thread.currentThread().threadId()
                    + "; batches are per-thread because they record into a single command buffer. "
                    + "Flush on the owning thread, or perform the transfer on this thread so it gets its own batch.");
        }
    }
}
