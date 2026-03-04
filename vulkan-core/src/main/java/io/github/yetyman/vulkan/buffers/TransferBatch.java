package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.VkBufferCopy;
import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkSubmitInfo;
import io.github.yetyman.vulkan.generated.VkTimelineSemaphoreSubmitInfo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkCmdCopyBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkEndCommandBuffer;
import static io.github.yetyman.vulkan.generated.VulkanFFM.vkQueueSubmit;
import static io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT;

class TransferBatch {
    static final long AUTO_FLUSH_THRESHOLD = 64L * 1024 * 1024;

    private final VkDevice device;
    private final VkQueue queue;
    private final VkCommandPool commandPool;
    private final VkFence fence;
    private final List<TimelineWait> pendingWaits = new ArrayList<>();
    private final List<TimelineWait> pendingSignals = new ArrayList<>();
    private final List<BatchTransferCompletion> allCompletions = new ArrayList<>();

    private Arena batchArena;
    private VkCommandBuffer commandBuffer;
    private List<AutoCloseable> ownedObjects;
    private BatchTransferCompletion currentCompletion;
    private long stagedBytes;
    private int pendingCount;

    private static record TimelineWait(VkTimelineSemaphore semaphore, long value) {}

    TransferBatch(VkDevice device, VkQueue queue, VkCommandPool commandPool) {
        this.device = device;
        this.queue = queue;
        this.commandPool = commandPool;
        this.fence = VkFence.builder().device(device).build(Arena.ofShared());
        open();
    }

    private void open() {
        batchArena = Arena.ofShared();
        ownedObjects = new ArrayList<>();
        VkCommandBuffer[] cmds = VkCommandBufferAlloc.builder()
            .device(device).commandPool(commandPool.handle()).primary().count(1).allocate(batchArena);
        commandBuffer = cmds[0];
        VkCommandBuffer.begin(commandBuffer).oneTimeSubmit().execute(batchArena);
        currentCompletion = new BatchTransferCompletion(fence, batchArena, ownedObjects);
        allCompletions.add(currentCompletion);
        stagedBytes = 0;
        pendingCount = 0;
    }

    TransferCompletion record(MemorySegment srcHandle, MemorySegment dstHandle,
                              long srcOffset, long dstOffset, long size,
                              AutoCloseable... toOwn) {
        MemorySegment copyRegion = VkBufferCopy.allocate(batchArena, srcOffset, dstOffset, size);
        vkCmdCopyBuffer(commandBuffer.handle(), srcHandle, dstHandle, 1, copyRegion);

        for (AutoCloseable obj : toOwn) if (obj != null) ownedObjects.add(obj);
        stagedBytes += size;
        pendingCount++;
        currentCompletion.retain();
        TransferCompletion view = new TransferCompletion(currentCompletion);

        if (stagedBytes >= AUTO_FLUSH_THRESHOLD) flush();
        return view;
    }

    TransferCompletion flush() {
        BatchTransferCompletion completing = currentCompletion;

        if (pendingCount == 0 && pendingSignals.isEmpty() && pendingWaits.isEmpty()) {
            completing.resolve();
            completing.retain();
            open();
            return new TransferCompletion(completing);
        }

        completing.resolve();

        vkEndCommandBuffer(commandBuffer.handle());
        submitWithFence();

        completing.retain();
        TransferCompletion view = new TransferCompletion(completing);
        open();
        return view;
    }

    void destroy() {
        try (TransferCompletion tc = flush()) {
            tc.await();
        }
        for (BatchTransferCompletion c : allCompletions) c.forceClose();
        allCompletions.clear();
        fence.close();
    }

    VkQueue queue() { return queue; }

    /** Add a timeline semaphore wait to this batch - batch won't execute until semaphore reaches value. */
    public TransferBatch waitUntil(VkTimelineSemaphore semaphore, long value) {
        pendingWaits.add(new TimelineWait(semaphore, value));
        return this;
    }

    /** Add a timeline semaphore signal to this batch - semaphore will be advanced to value on GPU completion. */
    public TransferBatch signalOn(VkTimelineSemaphore semaphore, long value) {
        pendingSignals.add(new TimelineWait(semaphore, value));
        return this;
    }

    private void submitWithFence() {
        try (Arena tmp = Arena.ofConfined()) {
            // Reset fence before use
            fence.reset();
            
            VkSubmitBuilder builder = new VkSubmitBuilder().commandBuffer(commandBuffer);
            
            for (TimelineWait wait : pendingWaits) {
                wait.semaphore.addWaitTo(builder, wait.value, VK_PIPELINE_STAGE_TRANSFER_BIT.value());
            }
            for (TimelineWait signal : pendingSignals) {
                signal.semaphore.addSignalTo(builder, signal.value);
            }

            MemorySegment submitInfo = builder.build(tmp);
            VkResult.fromInt(vkQueueSubmit(queue.handle(), 1, submitInfo, fence.handle())).check();
            pendingWaits.clear();
            pendingSignals.clear();
        }
    }
}