package io.github.yetyman.vulkan.highlevel;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Consumer;

/**
 * Generic resource pool for efficiently managing frequently created and destroyed Vulkan resources.
 * Reduces allocation overhead by reusing objects.
 * 
 * Example usage:
 * ```java
 * // Pool for command buffers
 * VkResourcePool<MemorySegment> cmdBufferPool = VkResourcePool.builder(MemorySegment.class)
 *     .factory(v -> VkCommandBufferAlloc.allocateSingle(arena, device, commandPool))
 *     .resetFunction(cmdBuffer -> VulkanExtensions.resetCommandBuffer(cmdBuffer, 0))
 *     .maxPoolSize(16)
 *     .build();
 * 
 * // Usage
 * MemorySegment cmdBuffer = cmdBufferPool.acquire();
 * // ... record commands ...
 * cmdBufferPool.release(cmdBuffer);
 * ```
 */
public class VkResourcePool<T> implements AutoCloseable {
    private final Queue<T> availableResources = new ConcurrentLinkedQueue<>();
    private final Set<T> allResources = Collections.synchronizedSet(new HashSet<>());
    private final Function<Void, T> factory;
    private final Consumer<T> resetFunction;
    private final Consumer<T> destroyFunction;
    private final int maxPoolSize;
    private volatile boolean closed = false;
    
    private VkResourcePool(Function<Void, T> factory, Consumer<T> resetFunction, 
                          Consumer<T> destroyFunction, int maxPoolSize) {
        this.factory = factory;
        this.resetFunction = resetFunction;
        this.destroyFunction = destroyFunction;
        this.maxPoolSize = maxPoolSize;
    }
    
    /**
     * Creates a new resource pool.
     */
    public static <T> Builder<T> builder(Class<T> resourceType) {
        return new Builder<>();
    }
    
    /**
     * Acquires a resource from the pool, creating a new one if necessary.
     */
    public T acquire() {
        if (closed) {
            throw new IllegalStateException("Resource pool is closed");
        }
        
        T resource = availableResources.poll();
        if (resource == null) {
            resource = factory.apply(null);
            allResources.add(resource);
        } else if (resetFunction != null) {
            resetFunction.accept(resource);
        }
        
        return resource;
    }
    
    /**
     * Returns a resource to the pool for reuse.
     */
    public void release(T resource) {
        if (closed || resource == null || !allResources.contains(resource)) {
            return;
        }
        
        if (availableResources.size() < maxPoolSize) {
            availableResources.offer(resource);
        } else {
            // Pool is full, destroy the resource
            allResources.remove(resource);
            if (destroyFunction != null) {
                destroyFunction.accept(resource);
            }
        }
    }
    
    /**
     * Gets the current number of available resources in the pool.
     */
    public int getAvailableCount() {
        return availableResources.size();
    }
    
    /**
     * Gets the total number of resources managed by this pool.
     */
    public int getTotalCount() {
        return allResources.size();
    }
    
    /**
     * Clears all available resources from the pool without destroying them.
     */
    public void clear() {
        availableResources.clear();
    }
    
    /**
     * Shrinks the pool to the specified size by destroying excess resources.
     */
    public void shrink(int targetSize) {
        while (availableResources.size() > targetSize) {
            T resource = availableResources.poll();
            if (resource != null) {
                allResources.remove(resource);
                if (destroyFunction != null) {
                    destroyFunction.accept(resource);
                }
            }
        }
    }
    
    @Override
    public void close() {
        closed = true;
        
        // Destroy all resources
        for (T resource : allResources) {
            if (destroyFunction != null) {
                destroyFunction.accept(resource);
            }
        }
        
        allResources.clear();
        availableResources.clear();
    }
    
    public static class Builder<T> {
        private Function<Void, T> factory;
        private Consumer<T> resetFunction;
        private Consumer<T> destroyFunction;
        private int maxPoolSize = 32;
        
        /**
         * Sets the factory function for creating new resources.
         */
        public Builder<T> factory(Function<Void, T> factory) {
            this.factory = factory;
            return this;
        }
        
        /**
         * Sets the reset function called when a resource is acquired from the pool.
         */
        public Builder<T> resetFunction(Consumer<T> resetFunction) {
            this.resetFunction = resetFunction;
            return this;
        }
        
        /**
         * Sets the destroy function called when a resource is permanently removed.
         */
        public Builder<T> destroyFunction(Consumer<T> destroyFunction) {
            this.destroyFunction = destroyFunction;
            return this;
        }
        
        /**
         * Sets the maximum number of resources to keep in the pool.
         */
        public Builder<T> maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }
        
        public VkResourcePool<T> build() {
            if (factory == null) {
                throw new IllegalStateException("factory function not set");
            }
            return new VkResourcePool<>(factory, resetFunction, destroyFunction, maxPoolSize);
        }
    }
}