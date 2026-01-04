package io.github.yetyman.vulkan.highlevel;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.Vulkan;
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
    public MemorySegment createBinarySemaphore() {
        MemorySegment semaphoreInfo = VkSemaphoreCreateInfo.allocate(arena);
        VkSemaphoreCreateInfo.sType(semaphoreInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO.value());
        
        MemorySegment semaphorePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createSemaphore(device.handle(), semaphoreInfo, semaphorePtr).check();
        return semaphorePtr.get(ValueLayout.ADDRESS, 0);
    }
    
    private TimelineSemaphore createTimelineSemaphore() {
        MemorySegment typeInfo = VkSemaphoreTypeCreateInfo.allocate(arena);
        VkSemaphoreTypeCreateInfo.sType(typeInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO.value());
        VkSemaphoreTypeCreateInfo.semaphoreType(typeInfo, VkSemaphoreType.VK_SEMAPHORE_TYPE_TIMELINE.value());
        VkSemaphoreTypeCreateInfo.initialValue(typeInfo, 0);
        
        MemorySegment semaphoreInfo = VkSemaphoreCreateInfo.allocate(arena);
        VkSemaphoreCreateInfo.sType(semaphoreInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO.value());
        VkSemaphoreCreateInfo.pNext(semaphoreInfo, typeInfo);
        
        MemorySegment semaphorePtr = arena.allocate(ValueLayout.ADDRESS);
        Vulkan.createSemaphore(device.handle(), semaphoreInfo, semaphorePtr).check();
        
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
        private final VkDevice device;
        private final Arena arena;
        private volatile long currentValue = 0;
        
        TimelineSemaphore(MemorySegment handle, VkDevice device, Arena arena) {
            this.handle = handle;
            this.device = device;
            this.arena = arena;
        }
        
        public MemorySegment handle() { return handle; }
        
        public long getCurrentValue() {
            MemorySegment valuePtr = arena.allocate(ValueLayout.JAVA_LONG);
            Vulkan.getSemaphoreCounterValue(device.handle(), handle, valuePtr).check();
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
            semaphoreArray.set(ValueLayout.ADDRESS, 0, handle);
            VkSemaphoreWaitInfo.pSemaphores(waitInfo, semaphoreArray);
            
            MemorySegment valueArray = arena.allocate(ValueLayout.JAVA_LONG);
            valueArray.set(ValueLayout.JAVA_LONG, 0, value);
            VkSemaphoreWaitInfo.pValues(waitInfo, valueArray);
            
            Vulkan.waitSemaphores(device.handle(), waitInfo, timeoutNs).check();
        }
        
        public void signalValue(long value) {
            MemorySegment signalInfo = VkSemaphoreSignalInfo.allocate(arena);
            VkSemaphoreSignalInfo.sType(signalInfo, VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO.value());
            VkSemaphoreSignalInfo.semaphore(signalInfo, handle);
            VkSemaphoreSignalInfo.value(signalInfo, value);
            
            Vulkan.signalSemaphore(device.handle(), signalInfo).check();
        }
        
        @Override
        public void close() {
            Vulkan.destroySemaphore(device.handle(), handle);
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