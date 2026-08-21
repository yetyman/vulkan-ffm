package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.command.VkCopy;
import io.github.yetyman.vulkan.util.BumpAllocator;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * <h2>Command buffer reuse</h2>
 * Each generation needs its own command buffer, since a prior generation's buffer may still be
 * in flight on the GPU when the next one opens. Rather than allocating a fresh command buffer per
 * generation and never freeing it -- which grows the command pool without bound over a long-running
 * loop -- this class cycles through a small fixed-size ring of {@link #RING_SIZE} command buffers,
 * allocated once. Opening generation G reuses slot {@code G % RING_SIZE}, after waiting for whichever
 * earlier generation last held that slot to finish on the GPU (a slot cannot be more than
 * {@code RING_SIZE} generations behind, so at most one earlier generation can still be using it).
 * This bounds memory deterministically regardless of flush count and applies identically to every
 * batch, independent of what work it carries.
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

    /**
     * Command buffers held per batch. Bounds worst-case in-flight generations: a slot is only
     * reused once the generation that last held it has completed on the GPU, so a caller flushing
     * faster than the GPU retires work stalls opening the next generation rather than growing the
     * pool. Sized generously enough that ordinary usage never observes that stall.
     */
    private static final int RING_SIZE = 4;

    private final VkDevice device;
    private final VkQueue queue;
    private final VkCommandPool commandPool;
    private final long ownerThreadId;
    private final Arena ringArena;

    private final VkCommandBuffer[] commandBuffers = new VkCommandBuffer[RING_SIZE];
    /** The generation each ring slot last recorded, or -1 if never used. Used to await reuse. */
    private final long[] slotGeneration = new long[RING_SIZE];
    /** The completion for the generation each ring slot last recorded, retained until reused. */
    private final BatchTransferCompletion[] slotCompletion = new BatchTransferCompletion[RING_SIZE];

    private final Arena timelineArena;
    private final VkTimelineSemaphore timeline;

    private final List<TimelineWait> pendingWaits = new ArrayList<>();
    private final List<TimelineWait> pendingSignals = new ArrayList<>();
    private final Deque<BatchTransferCompletion> liveCompletions = new ArrayDeque<>();

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
        this.ringArena = Arena.ofShared();
        if (!device.supportsTimelineSemaphore()) {
            timelineArena.close();
            ringArena.close();
            throw new IllegalStateException(
                    "TransferBatch requires timeline semaphore support, but this VkDevice was not built with "
                    + "enableTimelineSemaphore(). Semaphore creation can succeed on some drivers even without the "
                    + "feature enabled, which is why this is checked directly rather than discovered on first wait. "
                    + "Build the device through VulkanContext, which enables this automatically, or call "
                    + "VkDevice.Builder.enableTimelineSemaphore() directly.");
        }
        try {
            this.timeline = VkTimelineSemaphore.create(device, 0, timelineArena);
        } catch (Exception e) {
            timelineArena.close();
            ringArena.close();
            throw new IllegalStateException(
                    "TransferBatch requires timeline semaphore support; build the VkDevice with enableTimelineSemaphore()", e);
        }
        VkCommandBuffer[] allocated = VkCommandBufferAlloc.builder()
                .device(device).commandPool(commandPool.handle()).primary().count(RING_SIZE).allocate(ringArena);
        System.arraycopy(allocated, 0, commandBuffers, 0, RING_SIZE);
        java.util.Arrays.fill(slotGeneration, -1L);
        open();
    }

    private void open() {
        long generation = ++lastAssignedValue;
        int slot = (int) (generation % RING_SIZE);

        // A slot is only reused once the generation that last held it has actually completed on
        // the GPU. This is the one point where the ring can stall: if the caller is flushing
        // faster than the GPU retires work, opening the next generation waits here rather than
        // growing the pool. That is a deliberate, bounded tradeoff, not a bug.
        BatchTransferCompletion previousOccupant = slotCompletion[slot];
        if (previousOccupant != null) {
            previousOccupant.await();
            previousOccupant.release();
        }

        commandBuffer = commandBuffers[slot];
        VkCommandBuffer.reset(commandBuffer).check();

        batchArena = Arena.ofShared();
        ownedObjects = new ArrayList<>();
        VkCommandBuffer.begin(commandBuffer).oneTimeSubmit().execute(batchArena);
        currentCompletion = new BatchTransferCompletion(timeline, generation, batchArena, ownedObjects);
        currentCompletion.retain(); // held by the ring slot until the slot is reused
        slotGeneration[slot] = generation;
        slotCompletion[slot] = currentCompletion;

        // Drain released completions from the front. Completions are enqueued in generation order
        // and GPU work completes roughly FIFO, so released entries cluster at the head. This is O(k)
        // where k is the number of newly-released entries rather than O(n) over the entire collection,
        // which eliminates the scaling problem of the previous removeIf scan.
        while (!liveCompletions.isEmpty() && liveCompletions.peekFirst().isReleased()) {
            liveCompletions.pollFirst();
        }
        liveCompletions.addLast(currentCompletion);
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

    /**
     * Records a multi-region buffer copy in one {@code vkCmdCopyBuffer} call.
     * Used by deferred flush to copy scattered dirty ranges efficiently.
     */
    GpuCompletion recordMultiRegion(MemorySegment srcHandle, MemorySegment dstHandle,
                                    long[] srcOffsets, long[] dstOffsets, long[] sizes, int count) {
        checkOwner("record into");
        VkCopy.copyBufferMultiRegion(commandBuffer.handle(), srcHandle, dstHandle,
                srcOffsets, dstOffsets, sizes, count);

        long totalBytes = 0;
        for (int i = 0; i < count; i++) totalBytes += sizes[i];
        stagedBytes += totalBytes;
        pendingCount++;
        currentCompletion.retain();
        GpuCompletion view = new TransferCompletion(currentCompletion, this);

        if (stagedBytes >= AUTO_FLUSH_THRESHOLD) flush();
        return view;
    }

    /**
     * Records a multi-region buffer copy directly from a {@link DirtyRegionIterator},
     * with no intermediate arrays. Source and destination offsets are identical (mirror pattern).
     */
    GpuCompletion recordMultiRegionFromIterator(MemorySegment srcHandle, MemorySegment dstHandle,
                                                DirtyRegionIterator it, int count) {
        checkOwner("record into");
        long totalBytes = VkCopy.copyBufferMultiRegionFromIterator(
                commandBuffer.handle(), srcHandle, dstHandle, it, count);

        stagedBytes += totalBytes;
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
        // Release the retain each ring slot is still holding on whatever generation it last
        // recorded, then force-close every generation regardless of ref count -- teardown means
        // no one is coming back to close their own view.
        for (int slot = 0; slot < RING_SIZE; slot++) {
            if (slotCompletion[slot] != null) slotCompletion[slot].release();
        }
        for (BatchTransferCompletion c : liveCompletions) c.forceClose();
        liveCompletions.clear();
        timeline.close();
        timelineArena.close();
        ringArena.close();
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
        BumpAllocator ba = BumpAllocator.get();
        ba.push();
        try {
            VkSubmitBuilder builder = new VkSubmitBuilder().commandBuffer(commandBuffer);

            for (TimelineWait wait : pendingWaits) {
                wait.semaphore.addWaitTo(builder, wait.value, VK_PIPELINE_STAGE_TRANSFER_BIT.value());
            }
            builder.signal(timeline, signalValue);
            for (TimelineWait signal : pendingSignals) {
                signal.semaphore.addSignalTo(builder, signal.value);
            }

            MemorySegment submitInfo = builder.build(ba);
            VkResult.fromInt(vkQueueSubmit(queue.handle(), 1, submitInfo, MemorySegment.NULL)).check();
            timeline.recordSignal(signalValue);
            for (TimelineWait signal : pendingSignals) {
                signal.semaphore.recordSignal(signal.value);
            }
            pendingWaits.clear();
            pendingSignals.clear();
        } finally {
            ba.pop();
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
