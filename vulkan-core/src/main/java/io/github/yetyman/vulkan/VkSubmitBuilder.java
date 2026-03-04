package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkSubmitInfo;
import io.github.yetyman.vulkan.generated.VkTimelineSemaphoreSubmitInfo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

public class VkSubmitBuilder {
    private final List<MemorySegment> waitSemaphores = new ArrayList<>();
    private final List<Integer> waitStages = new ArrayList<>();
    private final List<Long> waitValues = new ArrayList<>();
    private final List<MemorySegment> signalSemaphores = new ArrayList<>();
    private final List<Long> signalValues = new ArrayList<>();
    private final List<MemorySegment> commandBuffers = new ArrayList<>();

    public VkSubmitBuilder waitFor(VkTimelineSemaphore semaphore, long value, int stageMask) {
        waitSemaphores.add(semaphore.handle());
        waitStages.add(stageMask);
        waitValues.add(value);
        return this;
    }

    public VkSubmitBuilder signal(VkTimelineSemaphore semaphore, long value) {
        signalSemaphores.add(semaphore.handle());
        signalValues.add(value);
        return this;
    }

    public VkSubmitBuilder commandBuffer(VkCommandBuffer cmd) {
        commandBuffers.add(cmd.handle());
        return this;
    }

    public MemorySegment build(Arena arena) {
        MemorySegment submitInfo = VkSubmitInfo.allocate(arena);
        VkSubmitInfo.sType(submitInfo, VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO.value());

        // Command buffers
        if (!commandBuffers.isEmpty()) {
            MemorySegment cmdArray = arena.allocate(ValueLayout.ADDRESS, commandBuffers.size());
            for (int i = 0; i < commandBuffers.size(); i++) {
                cmdArray.setAtIndex(ValueLayout.ADDRESS, i, commandBuffers.get(i));
            }
            VkSubmitInfo.commandBufferCount(submitInfo, commandBuffers.size());
            VkSubmitInfo.pCommandBuffers(submitInfo, cmdArray);
        }

        // Wait semaphores
        if (!waitSemaphores.isEmpty()) {
            MemorySegment waitSemArray = arena.allocate(ValueLayout.ADDRESS, waitSemaphores.size());
            MemorySegment waitStageArray = arena.allocate(ValueLayout.JAVA_INT, waitStages.size());
            for (int i = 0; i < waitSemaphores.size(); i++) {
                waitSemArray.setAtIndex(ValueLayout.ADDRESS, i, waitSemaphores.get(i));
                waitStageArray.setAtIndex(ValueLayout.JAVA_INT, i, waitStages.get(i));
            }
            VkSubmitInfo.waitSemaphoreCount(submitInfo, waitSemaphores.size());
            VkSubmitInfo.pWaitSemaphores(submitInfo, waitSemArray);
            VkSubmitInfo.pWaitDstStageMask(submitInfo, waitStageArray);
        }

        // Signal semaphores
        if (!signalSemaphores.isEmpty()) {
            MemorySegment signalSemArray = arena.allocate(ValueLayout.ADDRESS, signalSemaphores.size());
            for (int i = 0; i < signalSemaphores.size(); i++) {
                signalSemArray.setAtIndex(ValueLayout.ADDRESS, i, signalSemaphores.get(i));
            }
            VkSubmitInfo.signalSemaphoreCount(submitInfo, signalSemaphores.size());
            VkSubmitInfo.pSignalSemaphores(submitInfo, signalSemArray);
        }

        // Timeline semaphore info if needed
        if (!waitValues.isEmpty() || !signalValues.isEmpty()) {
            MemorySegment timelineInfo = VkTimelineSemaphoreSubmitInfo.allocate(arena);
            VkTimelineSemaphoreSubmitInfo.sType(timelineInfo, VkStructureType.VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO.value());
            VkTimelineSemaphoreSubmitInfo.pNext(timelineInfo, MemorySegment.NULL);

            if (!waitValues.isEmpty()) {
                MemorySegment waitValueArray = arena.allocate(ValueLayout.JAVA_LONG, waitValues.size());
                for (int i = 0; i < waitValues.size(); i++) {
                    waitValueArray.setAtIndex(ValueLayout.JAVA_LONG, i, waitValues.get(i));
                }
                VkTimelineSemaphoreSubmitInfo.waitSemaphoreValueCount(timelineInfo, waitValues.size());
                VkTimelineSemaphoreSubmitInfo.pWaitSemaphoreValues(timelineInfo, waitValueArray);
            }

            if (!signalValues.isEmpty()) {
                MemorySegment signalValueArray = arena.allocate(ValueLayout.JAVA_LONG, signalValues.size());
                for (int i = 0; i < signalValues.size(); i++) {
                    signalValueArray.setAtIndex(ValueLayout.JAVA_LONG, i, signalValues.get(i));
                }
                VkTimelineSemaphoreSubmitInfo.signalSemaphoreValueCount(timelineInfo, signalValues.size());
                VkTimelineSemaphoreSubmitInfo.pSignalSemaphoreValues(timelineInfo, signalValueArray);
            }

            VkSubmitInfo.pNext(submitInfo, timelineInfo);
        }

        return submitInfo;
    }
}