package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkSemaphore;
import io.github.yetyman.vulkan.VkTimelineSemaphore;
import java.lang.foreign.Arena;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages timeline and binary semaphores for Vulkan synchronization.
 */
public class VkSemaphoreManager implements AutoCloseable {
    private final VkDevice device;
    private final Arena arena;
    private final Map<String, VkTimelineSemaphore> semaphores = new ConcurrentHashMap<>();

    private VkSemaphoreManager(VkDevice device, Arena arena) {
        this.device = device;
        this.arena = arena;
    }

    public static Builder builder() { return new Builder(); }

    /** Creates or gets a named timeline semaphore. */
    public VkTimelineSemaphore getTimelineSemaphore(String name) {
        return semaphores.computeIfAbsent(name, k -> VkTimelineSemaphore.create(device, 0, arena));
    }

    /** Creates a binary semaphore. */
    public VkSemaphore createBinarySemaphore() {
        return VkSemaphore.builder().device(device).build(arena);
    }

    @Override
    public void close() {
        semaphores.values().forEach(VkTimelineSemaphore::close);
        semaphores.clear();
    }

    public static class Builder {
        private VkDevice device;

        public Builder device(VkDevice device) { this.device = device; return this; }

        public VkSemaphoreManager build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            return new VkSemaphoreManager(device, arena);
        }
    }
}
