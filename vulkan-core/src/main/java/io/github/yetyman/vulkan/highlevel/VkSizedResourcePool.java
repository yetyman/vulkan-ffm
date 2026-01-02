package io.github.yetyman.vulkan.highlevel;

import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;

/**
 * Size-based resource pool for efficiently managing resources of different sizes.
 * Maintains separate pools for each size and finds the smallest resource that fits.
 * 
 * Example usage:
 * ```java
 * VkSizedResourcePool<VkBuffer> bufferPool = VkSizedResourcePool.builder(VkBuffer.class)
 *     .sizes(1024, 4096, 65536, 1048576) // 1KB, 4KB, 64KB, 1MB
 *     .factory((size) -> VkBuffer.builder()
 *         .device(device)
 *         .physicalDevice(physicalDevice)
 *         .size(size)
 *         .vertexBuffer()
 *         .build(arena))
 *     .sizeExtractor(VkBuffer::size)
 *     .maxPoolSizePerSize(32)
 *     .build();
 * 
 * VkBuffer buffer = bufferPool.acquire(2048); // Gets 4KB buffer
 * bufferPool.release(buffer);
 * ```
 */
public class VkSizedResourcePool<T> implements AutoCloseable {
    private final Map<Long, VkResourcePool<T>> pools = new HashMap<>();
    private final long[] sizes;
    private final Function<Long, T> factory;
    private final Function<T, Long> sizeExtractor;
    private volatile boolean closed = false;
    
    private VkSizedResourcePool(long[] sizes, Function<Long, T> factory, Function<T, Long> sizeExtractor,
                               Consumer<T> resetFunction, Consumer<T> destroyFunction, int maxPoolSizePerSize) {
        this.sizes = sizes.clone();
        Arrays.sort(this.sizes); // Ensure sorted for binary search
        this.factory = factory;
        this.sizeExtractor = sizeExtractor;
        
        // Create a pool for each size
        for (long size : this.sizes) {
            VkResourcePool<T> pool = VkResourcePool.builder((Class<T>) Object.class)
                .factory(v -> factory.apply(size))
                .resetFunction(resetFunction)
                .destroyFunction(destroyFunction)
                .maxPoolSize(maxPoolSizePerSize)
                .build();
            pools.put(size, pool);
        }
    }
    
    public static <T> Builder<T> builder(Class<T> resourceType) {
        return new Builder<>();
    }
    
    /**
     * Acquires a resource that can accommodate the requested size.
     * Returns the smallest available resource that fits.
     */
    public T acquire(long requiredSize) {
        if (closed) {
            throw new IllegalStateException("Resource pool is closed");
        }
        
        // Find smallest size that fits
        for (long size : sizes) {
            if (size >= requiredSize) {
                VkResourcePool<T> pool = pools.get(size);
                T resource = pool.acquire();
                if (resource != null) {
                    return resource;
                }
            }
        }
        
        return null; // No suitable resource available
    }
    
    /**
     * Returns a resource to the appropriate size pool.
     */
    public void release(T resource) {
        if (closed || resource == null) {
            return;
        }
        
        long resourceSize = sizeExtractor.apply(resource);
        VkResourcePool<T> pool = pools.get(resourceSize);
        if (pool != null) {
            pool.release(resource);
        }
    }
    
    /**
     * Pre-allocates resources for the specified size to avoid allocation during runtime.
     */
    public void preallocate(long size, int count) {
        VkResourcePool<T> pool = pools.get(size);
        if (pool != null) {
            // Pre-allocate by acquiring and immediately releasing resources
            List<T> resources = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                T resource = pool.acquire();
                if (resource != null) {
                    resources.add(resource);
                }
            }
            // Release them back to make them available
            for (T resource : resources) {
                pool.release(resource);
            }
        }
    }
    public Map<Long, PoolStats> getStats() {
        Map<Long, PoolStats> stats = new HashMap<>();
        for (Map.Entry<Long, VkResourcePool<T>> entry : pools.entrySet()) {
            VkResourcePool<T> pool = entry.getValue();
            stats.put(entry.getKey(), new PoolStats(
                pool.getAvailableCount(),
                pool.getTotalCount()
            ));
        }
        return stats;
    }
    
    @Override
    public void close() {
        closed = true;
        for (VkResourcePool<T> pool : pools.values()) {
            pool.close();
        }
        pools.clear();
    }
    
    public static class PoolStats {
        public final int available;
        public final int total;
        
        public PoolStats(int available, int total) {
            this.available = available;
            this.total = total;
        }
    }
    
    public static class Builder<T> {
        private long[] sizes;
        private Function<Long, T> factory;
        private Function<T, Long> sizeExtractor;
        private Consumer<T> resetFunction;
        private Consumer<T> destroyFunction;
        private int maxPoolSizePerSize = 32;
        
        public Builder<T> sizes(long... sizes) {
            this.sizes = sizes;
            return this;
        }
        
        public Builder<T> factory(Function<Long, T> factory) {
            this.factory = factory;
            return this;
        }
        
        public Builder<T> sizeExtractor(Function<T, Long> sizeExtractor) {
            this.sizeExtractor = sizeExtractor;
            return this;
        }
        
        public Builder<T> resetFunction(Consumer<T> resetFunction) {
            this.resetFunction = resetFunction;
            return this;
        }
        
        public Builder<T> destroyFunction(Consumer<T> destroyFunction) {
            this.destroyFunction = destroyFunction;
            return this;
        }
        
        public Builder<T> maxPoolSizePerSize(int maxPoolSizePerSize) {
            this.maxPoolSizePerSize = maxPoolSizePerSize;
            return this;
        }
        
        public VkSizedResourcePool<T> build() {
            if (sizes == null || sizes.length == 0) {
                throw new IllegalStateException("sizes not set");
            }
            if (factory == null) {
                throw new IllegalStateException("factory not set");
            }
            if (sizeExtractor == null) {
                throw new IllegalStateException("sizeExtractor not set");
            }
            
            return new VkSizedResourcePool<>(sizes, factory, sizeExtractor, 
                resetFunction, destroyFunction, maxPoolSizePerSize);
        }
    }
}