package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
import java.lang.foreign.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages command pools and buffers, with optional thread-local support.
 */
public class VulkanCommandManager implements AutoCloseable {
    private final Arena arena;
    private final MemorySegment device;
    private final int queueFamilyIndex;
    private final boolean threaded;
    
    private VkCommandPool mainPool;
    private final ConcurrentHashMap<Thread, VkCommandPool> threadPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Thread, Arena> threadArenas = new ConcurrentHashMap<>();
    
    private VulkanCommandManager(Arena arena, MemorySegment device, int queueFamilyIndex, boolean threaded) {
        this.arena = arena;
        this.device = device;
        this.queueFamilyIndex = queueFamilyIndex;
        this.threaded = threaded;
        
        if (!threaded) {
            mainPool = VkCommandPool.builder()
                .device(device)
                .queueFamilyIndex(queueFamilyIndex)
                .build(arena);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public MemorySegment[] allocateBuffers(int count, Arena bufferArena) {
        VkCommandPool pool = threaded ? getThreadPool() : mainPool;
        return VkCommandBufferAlloc.builder()
            .device(device)
            .commandPool(pool.handle())
            .primary()
            .count(count)
            .allocate(bufferArena);
    }
    
    public MemorySegment allocateBuffer(Arena bufferArena) {
        return allocateBuffers(1, bufferArena)[0];
    }
    
    private VkCommandPool getThreadPool() {
        Thread currentThread = Thread.currentThread();
        return threadPools.computeIfAbsent(currentThread, k -> {
            Arena threadArena = getThreadArena(k);
            return VkCommandPool.builder()
                .device(device)
                .queueFamilyIndex(queueFamilyIndex)
                .build(threadArena);
        });
    }
    
    private Arena getThreadArena(Thread thread) {
        return threadArenas.computeIfAbsent(thread, k -> Arena.ofShared());
    }
    
    public MemorySegment getCommandPool() {
        VkCommandPool pool = threaded ? getThreadPool() : mainPool;
        return pool.handle();
    }
    
    @Override
    public void close() {
        threadPools.values().forEach(VkCommandPool::close);
        threadArenas.values().forEach(Arena::close);
        if (mainPool != null) {
            mainPool.close();
        }
    }
    
    public static class Builder {
        private Arena arena;
        private MemorySegment device;
        private int queueFamilyIndex = 0;
        private boolean threaded = false;
        
        public Builder arena(Arena arena) {
            this.arena = arena;
            return this;
        }
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public Builder context(VulkanContext context) {
            this.arena = context.arena();
            this.device = context.device().handle();
            return this;
        }
        
        public Builder queueFamilyIndex(int index) {
            this.queueFamilyIndex = index;
            return this;
        }
        
        public Builder threaded(boolean threaded) {
            this.threaded = threaded;
            return this;
        }
        
        public VulkanCommandManager build() {
            if (arena == null) throw new IllegalStateException("arena not set");
            if (device == null) throw new IllegalStateException("device not set");
            return new VulkanCommandManager(arena, device, queueFamilyIndex, threaded);
        }
    }
}