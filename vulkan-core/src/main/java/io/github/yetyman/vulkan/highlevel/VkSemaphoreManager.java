package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.VulkanExtensions;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.generated.*;
import java.lang.foreign.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages timeline and binary semaphores for Vulkan synchronization.
 * 
 * Example usage:
 * ```java
 * VkSemaphoreManager semMgr = VkSemaphoreManager.builder()
 *     .device(device)
 *     .build(arena);
 * 
 * // Timeline semaphore for complex dependencies
 * TimelineSemaphore timeline = semMgr.getTimelineSemaphore("render");
 * long value = timeline.getNextValue();
 * 
 * // Binary semaphore for simple signaling
 * MemorySegment binary = semMgr.createBinarySemaphore();
 * ```
 */
public class VkSemaphoreManager implements AutoCloseable {
    private final MemorySegment device;
    private final Arena arena;
    private final Map<String, TimelineSemaphore> semaphores = new ConcurrentHashMap<>();
    
    private VkSemaphoreManager(MemorySegment device, Arena arena) {
        this.device = device;
        this.arena = arena;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates or gets a timeline semaphore by name.
     */
    public TimelineSemaphore getTimelineSemaphore(String name) {
        return semaphores.computeIfAbsent(name, k -> createTimelineSemaphore());
    }
    
    /**
     * Creates a binary semaphore for one-time synchronization.
     */
    public MemorySegment createBinarySemaphore() {
        MemorySegment semaphoreInfo = VkSemaphoreCreateInfo.allocate(arena);
        VkSemaphoreCreateInfo.sType(semaphoreInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        
        MemorySegment semaphorePtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createSemaphore(device, semaphoreInfo, semaphorePtr).check();
        return semaphorePtr.get(ValueLayout.ADDRESS, 0);
    }
    
    private TimelineSemaphore createTimelineSemaphore() {
        MemorySegment typeInfo = VkSemaphoreTypeCreateInfo.allocate(arena);
        VkSemaphoreTypeCreateInfo.sType(typeInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO);
        VkSemaphoreTypeCreateInfo.semaphoreType(typeInfo, VkSemaphoreType.VK_SEMAPHORE_TYPE_TIMELINE);
        VkSemaphoreTypeCreateInfo.initialValue(typeInfo, 0);
        
        MemorySegment semaphoreInfo = VkSemaphoreCreateInfo.allocate(arena);
        VkSemaphoreCreateInfo.sType(semaphoreInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        VkSemaphoreCreateInfo.pNext(semaphoreInfo, typeInfo);
        
        MemorySegment semaphorePtr = arena.allocate(ValueLayout.ADDRESS);
        VulkanExtensions.createSemaphore(device, semaphoreInfo, semaphorePtr).check();
        
        return new TimelineSemaphore(semaphorePtr.get(ValueLayout.ADDRESS, 0), device, arena);
    }
    
    @Override
    public void close() {
        for (TimelineSemaphore semaphore : semaphores.values()) {
            semaphore.close();
        }
        semaphores.clear();
    }
    
    /**
     * Timeline semaphore wrapper with value tracking.
     */
    public static class TimelineSemaphore implements AutoCloseable {
        private final MemorySegment handle;
        private final MemorySegment device;
        private final Arena arena;
        private volatile long currentValue = 0;
        
        TimelineSemaphore(MemorySegment handle, MemorySegment device, Arena arena) {
            this.handle = handle;
            this.device = device;
            this.arena = arena;
        }
        
        public MemorySegment handle() { return handle; }
        
        public long getCurrentValue() {
            MemorySegment valuePtr = arena.allocate(ValueLayout.JAVA_LONG);
            VulkanExtensions.getSemaphoreCounterValue(device, handle, valuePtr).check();
            return valuePtr.get(ValueLayout.JAVA_LONG, 0);
        }
        
        public long getNextValue() {
            return ++currentValue;
        }
        
        public void waitForValue(long value, long timeoutNs) {
            MemorySegment waitInfo = VkSemaphoreWaitInfo.allocate(arena);
            VkSemaphoreWaitInfo.sType(waitInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO);
            VkSemaphoreWaitInfo.semaphoreCount(waitInfo, 1);
            
            MemorySegment semaphoreArray = arena.allocate(ValueLayout.ADDRESS);
            semaphoreArray.set(ValueLayout.ADDRESS, 0, handle);
            VkSemaphoreWaitInfo.pSemaphores(waitInfo, semaphoreArray);
            
            MemorySegment valueArray = arena.allocate(ValueLayout.JAVA_LONG);
            valueArray.set(ValueLayout.JAVA_LONG, 0, value);
            VkSemaphoreWaitInfo.pValues(waitInfo, valueArray);
            
            VulkanExtensions.waitSemaphores(device, waitInfo, timeoutNs).check();
        }
        
        public void signalValue(long value) {
            MemorySegment signalInfo = VkSemaphoreSignalInfo.allocate(arena);
            VkSemaphoreSignalInfo.sType(signalInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO);
            VkSemaphoreSignalInfo.semaphore(signalInfo, handle);
            VkSemaphoreSignalInfo.value(signalInfo, value);
            
            VulkanExtensions.signalSemaphore(device, signalInfo).check();
        }
        
        @Override
        public void close() {
            VulkanExtensions.destroySemaphore(device, handle);
        }
    }
    
    public static class Builder {
        private MemorySegment device;
        
        public Builder device(MemorySegment device) {
            this.device = device;
            return this;
        }
        
        public VkSemaphoreManager build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            return new VkSemaphoreManager(device, arena);
        }
    }
}