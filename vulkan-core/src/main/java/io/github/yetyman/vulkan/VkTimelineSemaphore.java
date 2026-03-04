package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.VkSemaphoreType;
import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VulkanFFM;
import io.github.yetyman.vulkan.generated.VkSemaphoreCreateInfo;
import io.github.yetyman.vulkan.generated.VkSemaphoreTypeCreateInfo;
import io.github.yetyman.vulkan.generated.VkSemaphoreWaitInfo;
import io.github.yetyman.vulkan.generated.VkSemaphoreSignalInfo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class VkTimelineSemaphore implements AutoCloseable {
    private final MemorySegment handle;
    private final VkDevice device;

    private VkTimelineSemaphore(MemorySegment handle, VkDevice device) {
        this.handle = handle;
        this.device = device;
    }

    public static VkTimelineSemaphore create(VkDevice device, long initialValue, Arena arena) {
        MemorySegment typeInfo = VkSemaphoreTypeCreateInfo.allocate(arena);
        VkSemaphoreTypeCreateInfo.sType(typeInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO.value());
        VkSemaphoreTypeCreateInfo.pNext(typeInfo, MemorySegment.NULL);
        VkSemaphoreTypeCreateInfo.semaphoreType(typeInfo, VkSemaphoreType.VK_SEMAPHORE_TYPE_TIMELINE.value());
        VkSemaphoreTypeCreateInfo.initialValue(typeInfo, initialValue);

        MemorySegment createInfo = VkSemaphoreCreateInfo.allocate(arena);
        VkSemaphoreCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO.value());
        VkSemaphoreCreateInfo.pNext(createInfo, typeInfo);
        VkSemaphoreCreateInfo.flags(createInfo, 0);

        MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
        VkResult.fromInt(VulkanFFM.vkCreateSemaphore(device.handle(), createInfo, MemorySegment.NULL, ptr)).check();
        return new VkTimelineSemaphore(ptr.get(ValueLayout.ADDRESS, 0), device);
    }

    /** CPU-side signal — advances the counter without a queue submission. */
    public void signal(long value) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment info = VkSemaphoreSignalInfo.allocate(tmp);
            VkSemaphoreSignalInfo.sType(info, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO.value());
            VkSemaphoreSignalInfo.pNext(info, MemorySegment.NULL);
            VkSemaphoreSignalInfo.semaphore(info, handle);
            VkSemaphoreSignalInfo.value(info, value);
            device.signalSemaphore(info).check();
        }
    }

    /** Blocks until the semaphore reaches at least {@code value}. */
    public void await(long value) {
        if (counterValue() >= value) return;
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment semPtr = tmp.allocate(ValueLayout.ADDRESS);
            semPtr.set(ValueLayout.ADDRESS, 0, handle);
            MemorySegment valPtr = tmp.allocate(ValueLayout.JAVA_LONG);
            valPtr.set(ValueLayout.JAVA_LONG, 0, value);

            MemorySegment info = VkSemaphoreWaitInfo.allocate(tmp);
            VkSemaphoreWaitInfo.sType(info, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO.value());
            VkSemaphoreWaitInfo.pNext(info, MemorySegment.NULL);
            VkSemaphoreWaitInfo.flags(info, 0);
            VkSemaphoreWaitInfo.semaphoreCount(info, 1);
            VkSemaphoreWaitInfo.pSemaphores(info, semPtr);
            VkSemaphoreWaitInfo.pValues(info, valPtr);
            device.waitSemaphores(info, Long.MAX_VALUE).check();
        }
    }

    /** Returns the current counter value. */
    public long counterValue() {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment ptr = tmp.allocate(ValueLayout.JAVA_LONG);
            device.getSemaphoreCounterValue(handle, ptr).check();
            return ptr.get(ValueLayout.JAVA_LONG, 0);
        }
    }

    public MemorySegment handle() { return handle; }
    public VkDevice device() { return device; }

    /** Adds this semaphore as a wait to the submit builder. */
    public VkSubmitBuilder addWaitTo(VkSubmitBuilder builder, long value, int stageMask) {
        return builder.waitFor(this, value, stageMask);
    }

    /** Adds this semaphore as a signal to the submit builder. */
    public VkSubmitBuilder addSignalTo(VkSubmitBuilder builder, long value) {
        return builder.signal(this, value);
    }

    @Override
    public void close() {
        VulkanFFM.vkDestroySemaphore(device.handle(), handle, MemorySegment.NULL);
    }
}
