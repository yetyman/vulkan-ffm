package io.github.yetyman.vulkan.queue;

import io.github.yetyman.vulkan.VkSubmit;
import io.github.yetyman.vulkan.Vulkan;
import java.lang.foreign.MemorySegment;

/**
 * Calls {@code vkQueueSubmit} immediately on the calling thread.
 * Zero overhead. Safe only when the calling thread exclusively owns the queue handle.
 * This is the default submitter.
 */
public final class DirectSubmitter implements IQueueSubmitter {

    private final MemorySegment queueHandle;

    public DirectSubmitter(MemorySegment queueHandle) {
        this.queueHandle = queueHandle;
    }

    @Override
    public void submit(MemorySegment submitInfo, MemorySegment fence) {
        VkSubmit.queueSubmit(queueHandle, 1, submitInfo, fence).check();
    }
}
