package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.queue.DirectSubmitter;
import io.github.yetyman.vulkan.queue.IQueueSubmitter;
import io.github.yetyman.vulkan.queue.MutexSubmitter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.yetyman.vulkan.generated.VulkanFFM.vkGetDeviceQueue;

/**
 * Wrapper for a Vulkan queue handle with an associated {@link IQueueSubmitter} strategy.
 *
 * <p>Obtained via {@link VkDevice#getQueue(int, int)} which caches instances by family+index,
 * ensuring one instance per physical queue (multiton pattern).
 *
 * <p>The default submitter is {@link DirectSubmitter}. Replace via
 * {@link #setSubmitter(IQueueSubmitter)} or atomically upgrade via {@link #compareAndSetSubmitter}.
 */
public record VkQueue(VkDevice device, MemorySegment handle, int familyIndex,
                      AtomicReference<IQueueSubmitter> submitterRef) {

    /** Canonical constructor — initialises the submitter to MutexSubmitter (safe default for shared access). */
    public VkQueue(VkDevice device, MemorySegment handle, int familyIndex) {
        this(device, handle, familyIndex, new AtomicReference<>(new MutexSubmitter(handle)));
    }

    /** @return the current submission strategy */
    public IQueueSubmitter submitter() { return submitterRef.get(); }

    /** Replaces the submission strategy. Thread-safe. */
    public void setSubmitter(IQueueSubmitter submitter) { submitterRef.set(submitter); }

    /**
     * Atomically replaces the submitter only if it is currently {@code expected}.
     * Useful for lock-free strategy upgrades, e.g. Direct → Mutex when a second
     * queue user is registered.
     *
     * @return true if the swap occurred
     */
    public boolean compareAndSetSubmitter(IQueueSubmitter expected, IQueueSubmitter replacement) {
        return submitterRef.compareAndSet(expected, replacement);
    }

    /**
     * Submits work via the installed {@link IQueueSubmitter}.
     *
     * @param submitInfo a {@code VkSubmitInfo} struct
     * @param fence      a {@code VkFence} handle, or {@link MemorySegment#NULL}
     */
    public void submit(MemorySegment submitInfo, MemorySegment fence) {
        submitterRef.get().submit(submitInfo, fence);
    }

    /** Convenience overload — submits with no fence. */
    public void submit(MemorySegment submitInfo) {
        submitterRef.get().submit(submitInfo, MemorySegment.NULL);
    }

    /** Flushes any pending batched submits. No-op for Direct and Mutex submitters. */
    public void flush() { submitterRef.get().flush(); }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private VkDevice device;
        private int queueFamilyIndex;
        private int queueIndex = 0;
        private IQueueSubmitter submitter = null; // null = create DirectSubmitter from resolved handle

        public Builder device(VkDevice device) { this.device = device; return this; }
        public Builder familyIndex(int queueFamilyIndex) { this.queueFamilyIndex = queueFamilyIndex; return this; }
        public Builder queueIndex(int queueIndex) { this.queueIndex = queueIndex; return this; }
        /** Sets a custom submitter strategy. If not set, defaults to {@link DirectSubmitter}. */
        public Builder submitter(IQueueSubmitter submitter) { this.submitter = submitter; return this; }

        public VkQueue build(Arena arena) {
            MemorySegment queuePtr = arena.allocate(ValueLayout.ADDRESS);
            vkGetDeviceQueue(device.handle(), queueFamilyIndex, queueIndex, queuePtr);
            MemorySegment handle = queuePtr.get(ValueLayout.ADDRESS, 0);
            IQueueSubmitter s = submitter != null ? submitter : new MutexSubmitter(handle);
            return new VkQueue(device, handle, queueFamilyIndex, new AtomicReference<>(s));
        }
    }
}
