package io.github.yetyman.vulkan.sample.complex.threading;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages threaded execution for Vulkan operations.
 */
public class ThreadManager implements AutoCloseable {
    public enum Mode { BEST_EFFICIENCY, BEST_PERFORMANCE, ADAPTIVE }
    
    private final ThreadPoolExecutor executor;
    private final AtomicInteger currentThreadCount = new AtomicInteger(1);
    private Mode mode = Mode.ADAPTIVE;
    
    public ThreadManager() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        executor = new ThreadPoolExecutor(1, coreCount, 60L, TimeUnit.SECONDS, 
            new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "VulkanWorker");
                t.setDaemon(true);
                return t;
            });
    }
    
    public void executeThreaded(int taskCount, Consumer<Integer> taskExecutor) {
        int threadsToUse = currentThreadCount.get();
        if (threadsToUse == 1) {
            // Single-threaded execution
            for (int i = 0; i < taskCount; i++) {
                taskExecutor.accept(i);
            }
            return;
        }
        
        // Multi-threaded execution
        CountDownLatch latch = new CountDownLatch(threadsToUse);
        int tasksPerThread = taskCount / threadsToUse;
        
        for (int t = 0; t < threadsToUse; t++) {
            final int threadIndex = t;
            final int startTask = t * tasksPerThread;
            final int endTask = (t == threadsToUse - 1) ? taskCount : (t + 1) * tasksPerThread;
            
            executor.submit(() -> {
                try {
                    for (int i = startTask; i < endTask; i++) {
                        taskExecutor.accept(i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void adjustThreadCount(double avgFrameTime) {
        double targetFrameTime = 16_666_667.0; // 60 FPS in nanoseconds
        int currentThreads = currentThreadCount.get();
        int maxThreads = Runtime.getRuntime().availableProcessors();
        
        switch (mode) {
            case BEST_EFFICIENCY:
                if (avgFrameTime < targetFrameTime * 0.8 && currentThreads > 1) {
                    setThreadCount(currentThreads - 1);
                } else if (avgFrameTime > targetFrameTime && currentThreads < maxThreads) {
                    setThreadCount(currentThreads + 1);
                }
                break;
                
            case BEST_PERFORMANCE:
                if (currentThreads < maxThreads && avgFrameTime > targetFrameTime * 0.5) {
                    setThreadCount(currentThreads + 1);
                }
                break;
                
            case ADAPTIVE:
                if (avgFrameTime > targetFrameTime && currentThreads < maxThreads) {
                    setThreadCount(currentThreads + 1);
                } else if (avgFrameTime < targetFrameTime * 0.7 && currentThreads > 1) {
                    setThreadCount(Math.max(1, currentThreads - 1));
                }
                break;
        }
    }
    
    public void setThreadCount(int count) {
        int maxThreads = Runtime.getRuntime().availableProcessors();
        count = Math.max(1, Math.min(count, maxThreads));
        
        executor.setCorePoolSize(count);
        executor.setMaximumPoolSize(count);
        currentThreadCount.set(count);
        
        System.out.println("[THREADS] Adjusted to " + count + " threads");
    }
    
    public void setMode(Mode mode) {
        this.mode = mode;
        System.out.println("[MODE] Switched to " + mode);
    }
    
    public int getActiveThreads() {
        return currentThreadCount.get();
    }
    
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}