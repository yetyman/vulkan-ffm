package io.github.yetyman.vulkan.loop;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.ILifecycle;
import io.github.yetyman.vulkan.queue.MutexSubmitter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Runs a compute workload on a dedicated thread using {@link LoopThread}, with its own
 * command pool, double-buffered fences, and an optional {@link VkTimelineSemaphore} for
 * cross-queue synchronization.
 *
 * <p>Implements {@link ILifecycle} so it can be registered with
 * {@link io.github.yetyman.vulkan.highlevel.VulkanApplication#registerLifecycleDependency}
 * and stopped/restarted automatically on resize and shutdown.
 *
 * <pre>{@code
 * VkTimelineSemaphore done = VkTimelineSemaphore.create(device, 0, arena);
 *
 * ComputeLoop loop = ComputeLoop.builder()
 *     .device(device)
 *     .queue(computeQueue)
 *     .queueFamilyIndex(computeFamily)
 *     .semaphore(done)
 *     .work((cmd, generation, frameArena) -> {
 *         pipeline.bind(cmd);
 *         descriptorSet.bind(cmd, pipeline, 0, frameArena);
 *         VkComputePipeline.dispatch(cmd, groupsX, groupsY, 1);
 *     })
 *     .build();
 *
 * loop.start();
 * // ...
 * loop.stop();
 * loop.awaitStopped();
 * }</pre>
 */
public class ComputeLoop implements ILifecycle {

    /**
     * Work function called each iteration with the current command buffer handle,
     * the current generation counter, and a per-iteration arena.
     */
    @FunctionalInterface
    public interface Work {
        void record(MemorySegment commandBuffer, long generation, Arena frameArena);
    }

    private final VkDevice device;
    private final VkQueue queue;
    private final int queueFamilyIndex;
    private final VkTimelineSemaphore semaphore;
    private final LoopDriver driver;
    private final Work work;
    private final String threadName;
    private final java.util.concurrent.atomic.AtomicLong completedGen = new java.util.concurrent.atomic.AtomicLong(0);

    // Per-run resources — allocated on start(), released on awaitStopped()
    private Arena runArena;
    private VkCommandPool pool;
    private VkCommandBuffer[] cmds;
    private VkFence[] fences;
    private long gen;

    private LoopThread loopThread;

    private ComputeLoop(VkDevice device, VkQueue queue, int queueFamilyIndex,
                        VkTimelineSemaphore semaphore, LoopDriver driver, Work work, String threadName) {
        this.device = device;
        this.queue = queue;
        this.queueFamilyIndex = queueFamilyIndex;
        this.semaphore = semaphore;
        this.driver = driver;
        this.work = work;
        this.threadName = threadName;
    }

    @Override
    public synchronized void start() {
        if (loopThread != null && loopThread.isRunning()) return;

        runArena = Arena.ofShared();
        gen = semaphore != null ? semaphore.counterValue() : 0;

        pool = VkCommandPool.builder()
            .device(device)
            .queueFamilyIndex(queueFamilyIndex)
            .resetCommandBufferBit()
            .build(runArena);

        cmds = VkCommandBufferAlloc.builder()
            .device(device)
            .commandPool(pool.handle())
            .primary()
            .count(2)
            .allocate(runArena);

        fences = new VkFence[]{
            VkFence.create(runArena, device, false),
            VkFence.create(runArena, device, false)
        };

        loopThread = LoopThread.builder()
            .driver(driver)
            .name(threadName)
            .work(timing -> tick())
            .build();
        loopThread.start();
    }

    @Override
    public synchronized void stop() {
        if (loopThread != null) loopThread.stop();
    }

    @Override
    public void awaitStopped() {
        // LoopThread.stop() already joins — thread is dead by the time we get here.
        // Wait for any in-flight GPU work then release per-run resources.
        if (fences == null) return;
        try (Arena tmp = Arena.ofConfined()) {
            if (gen >= 1) VkFenceOps.waitFor(device).fence(fences[(int)((gen - 1) % 2)].handle()).execute(tmp).check();
            if (gen >= 2) VkFenceOps.waitFor(device).fence(fences[(int)((gen - 2) % 2)].handle()).execute(tmp).check();
        }
        fences[0].close();
        fences[1].close();
        pool.close();
        runArena.close();
        fences = null;
        pool = null;
        cmds = null;
        runArena = null;
    }

    @Override
    public void close() {
        stop();
        awaitStopped();
    }

    /** @return the last completed generation as a cheap CPU-side read (no kernel call). */
    public long completedGeneration() { return completedGen.get(); }

    /** @return the timeline semaphore used to signal completion, or null if none was set. */
    public VkTimelineSemaphore semaphore() { return semaphore; }

    private void tick() {
        int slot = (int)(gen % 2);
        VkCommandBuffer cmd = cmds[slot];
        VkFence fence = fences[slot];

        if (gen >= 2) {
            try (Arena tmp = Arena.ofConfined()) {
                VkFenceOps.waitFor(device).fence(fence.handle()).execute(tmp).check();
                VkFenceOps.waitFor(device).fence(fence.handle()).reset(tmp).check();
            }
        }

        try (Arena frameArena = Arena.ofConfined()) {
            VkCommandBuffer.begin(cmd).execute(frameArena);
            work.record(cmd.handle(), gen, frameArena);
            Vulkan.endCommandBuffer(cmd.handle()).check();

            gen++;
            long signalValue = gen;

            VkSubmit.Builder submitBuilder = VkSubmit.builder().commandBuffer(cmd);
            if (semaphore != null) submitBuilder.signalTimelineSemaphore(semaphore, signalValue);
            submitBuilder.submit(queue, fence.handle(), frameArena);
            completedGen.set(signalValue);
        }
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private VkDevice device;
        private VkQueue queue;
        private int queueFamilyIndex;
        private VkTimelineSemaphore semaphore;
        private LoopDriver driver = LoopDriver.uncapped();
        private Work work;
        private String threadName = "compute-loop";

        private Builder() {}

        /** Sets the logical device. */
        public Builder device(VkDevice device) { this.device = device; return this; }

        /** Sets the queue to submit compute work to. */
        public Builder queue(VkQueue queue) { this.queue = queue; return this; }

        /** Sets the queue family index for command pool creation. */
        public Builder queueFamilyIndex(int index) { this.queueFamilyIndex = index; return this; }

        /**
         * Sets the timeline semaphore to signal after each iteration.
         * Optional — if not set, no semaphore is signaled.
         */
        public Builder semaphore(VkTimelineSemaphore semaphore) { this.semaphore = semaphore; return this; }

        /** Sets the loop driver controlling execution rate. Defaults to {@link LoopDriver#uncapped()}. */
        public Builder driver(LoopDriver driver) { this.driver = driver; return this; }

        /** Sets the work function called each iteration. */
        public Builder work(Work work) { this.work = work; return this; }

        /** Sets the thread name. */
        public Builder name(String name) { this.threadName = name; return this; }

        public ComputeLoop build() {
            if (device == null) throw new IllegalStateException("device not set");
            if (queue == null)  throw new IllegalStateException("queue not set");
            if (work == null)   throw new IllegalStateException("work not set");
            return new ComputeLoop(device, queue, queueFamilyIndex, semaphore, driver, work, threadName);
        }
    }
}
