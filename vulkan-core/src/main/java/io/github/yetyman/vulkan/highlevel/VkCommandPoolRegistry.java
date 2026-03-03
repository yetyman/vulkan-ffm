package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.VkDevice;

import java.lang.foreign.Arena;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-local command pool registry keyed by device and queue family index.
 * Pools are created on demand and destroyed when the device is closed.
 */
public class VkCommandPoolRegistry {
    // device identity (by handle address) -> thread id -> family index -> pool
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, ConcurrentHashMap<Integer, VkCommandPool>>> REGISTRY
            = new ConcurrentHashMap<>();

    public static VkCommandPool getOrCreate(VkDevice device, int queueFamilyIndex) {
        long deviceKey = device.handle().address();
        long threadKey = Thread.currentThread().threadId();
        return REGISTRY
                .computeIfAbsent(deviceKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(threadKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(queueFamilyIndex, k ->
                        VkCommandPool.builder()
                                .device(device)
                                .queueFamilyIndex(queueFamilyIndex)
                                .resetCommandBufferBit()
                                .build(Arena.global()));
    }

    /** Destroys all pools for the current thread on the given device. Call at thread exit. */
    public static void destroyThread(VkDevice device) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Integer, VkCommandPool>> byThread =
                REGISTRY.get(device.handle().address());
        if (byThread == null) return;
        ConcurrentHashMap<Integer, VkCommandPool> byFamily = byThread.remove(Thread.currentThread().threadId());
        if (byFamily == null) return;
        for (VkCommandPool pool : byFamily.values())
            pool.close();
    }

    /** Destroys all pools for the given device. Called from VkDevice.close(). */
    public static void destroyAll(VkDevice device) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Integer, VkCommandPool>> byThread =
                REGISTRY.remove(device.handle().address());
        if (byThread == null) return;
        for (Map<Integer, VkCommandPool> byFamily : byThread.values())
            for (VkCommandPool pool : byFamily.values())
                pool.close();
    }
}
