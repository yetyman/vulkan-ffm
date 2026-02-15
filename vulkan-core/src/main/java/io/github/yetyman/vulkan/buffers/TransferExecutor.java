package io.github.yetyman.vulkan.buffers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared executor for async buffer transfer cleanup operations.
 */
public class TransferExecutor {
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    
    public static void executeAsync(Runnable task) {
        EXECUTOR.execute(task);
    }
    
    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}
