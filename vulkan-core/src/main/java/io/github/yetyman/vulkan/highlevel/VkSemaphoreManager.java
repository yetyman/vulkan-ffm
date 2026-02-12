package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.*;
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
 * VkSemaphore binary = semMgr.createBinarySemaphore();
 * ```
 */
public class VkSemaphoreManager implements AutoCloseable {
    private final VkDevice device;
    private final Arena arena;
    private final Map<String, TimelineSemaphore> semaphores = new ConcurrentHashMap<>();
    
    private VkSemaphoreManager(VkDevice device, Arena arena) {
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
    public VkSemaphore createBinarySemaphore() {
        return VkSemaphore.builder()
            .device(device)
            .build(arena);
    }
    
    private TimelineSemaphore createTimelineSemaphore() {
        VkSemaphore semaphore = VkSemaphore.builder()
            .device(device)
            .timeline(0)
            .build(arena);
        return new TimelineSemaphore(semaphore, device, arena);
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
        private final VkSemaphore semaphore;
        private final VkDevice device;
        private final Arena arena;
        private volatile long currentValue = 0;
        
        TimelineSemaphore(VkSemaphore semaphore, VkDevice device, Arena arena) {
            this.semaphore = semaphore;
            this.device = device;
            this.arena = arena;
        }
        
        public VkSemaphore semaphore() { return semaphore; }
        public MemorySegment handle() { return semaphore.handle(); }
        
        public long getCurrentValue() {
            MemorySegment valuePtr = arena.allocate(ValueLayout.JAVA_LONG);
            Vulkan.getSemaphoreCounterValue(device.handle(), semaphore.handle(), valuePtr).check();
            return valuePtr.get(ValueLayout.JAVA_LONG, 0);
        }
        
        public long getNextValue() {
            return ++currentValue;
        }
        
        public void waitForValue(long value, long timeoutNs) {
            MemorySegment waitInfo = VkSemaphoreWaitInfo.allocate(arena);
            VkSemaphoreWaitInfo.sType(waitInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO.value());
            VkSemaphoreWaitInfo.semaphoreCount(waitInfo, 1);
            
            MemorySegment semaphoreArray = arena.allocate(ValueLayout.ADDRESS);
            semaphoreArray.set(ValueLayout.ADDRESS, 0, semaphore.handle());
            VkSemaphoreWaitInfo.pSemaphores(waitInfo, semaphoreArray);
            
            MemorySegment valueArray = arena.allocate(ValueLayout.JAVA_LONG);
            valueArray.set(ValueLayout.JAVA_LONG, 0, value);
            VkSemaphoreWaitInfo.pValues(waitInfo, valueArray);
            
            Vulkan.waitSemaphores(device.handle(), waitInfo, timeoutNs).check();
        }
        
        public void signalValue(long value) {
            MemorySegment signalInfo = VkSemaphoreSignalInfo.allocate(arena);
            VkSemaphoreSignalInfo.sType(signalInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO.value());
            VkSemaphoreSignalInfo.semaphore(signalInfo, semaphore.handle());
            VkSemaphoreSignalInfo.value(signalInfo, value);
            
            Vulkan.signalSemaphore(device.handle(), signalInfo).check();
        }
        
        @Override
        public void close() {
            semaphore.close();
        }
    }
    
    public static class Builder {
        private VkDevice device;
        
        public Builder device(VkDevice device) {
            this.device = device;
            return this;
        }
        
        public VkSemaphoreManager build(Arena arena) {
            if (device == null) throw new IllegalStateException("device not set");
            return new VkSemaphoreManager(device, arena);
        }
    }
}