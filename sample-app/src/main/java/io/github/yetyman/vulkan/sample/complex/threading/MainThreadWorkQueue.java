package io.github.yetyman.vulkan.sample.complex.threading;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Queues work to be executed on the main rendering thread during spare frame time
 */
public class MainThreadWorkQueue {
    private final ConcurrentLinkedQueue<WorkItem<?>> workQueue = new ConcurrentLinkedQueue<>();
    private final long targetFrameTimeNanos;
    private final long maxWorkTimeNanos;
    
    public MainThreadWorkQueue(double targetFPS) {
        this.targetFrameTimeNanos = (long)(1_000_000_000.0 / targetFPS);
        this.maxWorkTimeNanos = targetFrameTimeNanos / 4; // Use 25% of frame time for background work
    }
    
    /**
     * Queue work to be executed on main thread
     */
    public <T> CompletableFuture<T> enqueue(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        workQueue.offer(new WorkItem<>(work, future));
        return future;
    }
    
    /**
     * Process queued work until time budget is exhausted
     * @param frameStartTime When the current frame started
     * @return Number of work items processed
     */
    public int processWork(long frameStartTime) {
        long workStartTime = System.nanoTime();
        long maxEndTime = frameStartTime + targetFrameTimeNanos - maxWorkTimeNanos;
        int processed = 0;
        
        while (!workQueue.isEmpty() && System.nanoTime() < maxEndTime) {
            WorkItem<?> item = workQueue.poll();
            if (item != null) {
                try {
                    Object result = item.work.get();
                    @SuppressWarnings("unchecked")
                    CompletableFuture<Object> typedFuture = (CompletableFuture<Object>) item.future;
                    typedFuture.complete(result);
                    processed++;
                } catch (Exception e) {
                    item.future.completeExceptionally(e);
                }
            }
        }
        
        return processed;
    }
    
    public int getQueueSize() {
        return workQueue.size();
    }
    
    private static class WorkItem<T> {
        final Supplier<T> work;
        final CompletableFuture<T> future;
        
        WorkItem(Supplier<T> work, CompletableFuture<T> future) {
            this.work = work;
            this.future = future;
        }
    }
}