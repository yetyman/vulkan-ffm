package io.github.yetyman.vulkan;

import io.github.yetyman.vulkan.generated.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Wrapper for VkPhysicalDevice with common operations as instance methods
 */
public class VkPhysicalDevice {
    private final MemorySegment handle;
    private MemorySegment cachedProperties;
    private MemorySegment cachedFeatures;
    private MemorySegment cachedMemoryProperties;

    private VkPhysicalDevice(MemorySegment handle) {
        this.handle = handle;
    }

    public static VkPhysicalDevice wrap(MemorySegment physicalDeviceHandle) {
        return new VkPhysicalDevice(physicalDeviceHandle);
    }

    public MemorySegment handle() {
        return handle;
    }

    private void ensureCached() {
        if (cachedProperties == null) {
            Arena globalArena = Arena.global();

            // Cache properties (includes limits, sparse properties, etc.)
            cachedProperties = VkPhysicalDeviceProperties.allocate(globalArena);
            VulkanFFM.vkGetPhysicalDeviceProperties(handle, cachedProperties);

            // Cache features (includes sparse residency, etc.)
            cachedFeatures = VkPhysicalDeviceFeatures.allocate(globalArena);
            VulkanFFM.vkGetPhysicalDeviceFeatures(handle, cachedFeatures);

            // Cache memory properties (memory types and heaps)
            cachedMemoryProperties = VkPhysicalDeviceMemoryProperties.allocate(globalArena);
            VulkanFFM.vkGetPhysicalDeviceMemoryProperties(handle, cachedMemoryProperties);
        }
    }

    /**
     * @return [width, height] from vkGetPhysicalDeviceSurfaceCapabilitiesKHR currentExtent.
     */
    public int[] getSurfaceExtent(MemorySegment surface, Arena arena) {
        MemorySegment caps = VkSurfaceCapabilitiesKHR.allocate(arena);
        VulkanFFM.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(handle, surface, caps);
        MemorySegment extent = VkSurfaceCapabilitiesKHR.currentExtent(caps);
        return new int[]{VkExtent2D.width(extent), VkExtent2D.height(extent)};
    }

    public void getFormatProperties(int format, MemorySegment properties) {
        VulkanFFM.vkGetPhysicalDeviceFormatProperties(handle, format, properties);
    }

    public VkFormatPropertiesWrapper getFormatProperties(int format, Arena arena) {
        MemorySegment props = arena.allocate(VkFormatProperties.sizeof());
        getFormatProperties(format, props);
        return new VkFormatPropertiesWrapper(props);
    }

    public void getMemoryProperties(MemorySegment properties) {
        VulkanFFM.vkGetPhysicalDeviceMemoryProperties(handle, properties);
    }

    public VkPhysicalDeviceMemoryPropertiesWrapper getMemoryProperties() {
        ensureCached();
        return new VkPhysicalDeviceMemoryPropertiesWrapper(cachedMemoryProperties);
    }

    public int findMemoryType(int typeBits, int properties) {
        ensureCached();

        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(cachedMemoryProperties);
        for (int i = 0; i < typeCount; i++) {
            if ((typeBits & (1 << i)) != 0) {
                MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(cachedMemoryProperties, i);
                int props = io.github.yetyman.vulkan.generated.VkMemoryType.propertyFlags(memType);
                if ((props & properties) == properties) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the sparse buffer page size (bufferImageGranularity).
     * Cached after first query.
     */
    public long getSparsePageSize() {
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.bufferImageGranularity(limits);
    }

    /**
     * Returns minimum alignment for memory map operations.
     */
    public long getMinMemoryMapAlignment() {
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.minMemoryMapAlignment(limits);
    }

    /**
     * Returns granularity for non-coherent memory flush/invalidate operations.
     */
    public long getNonCoherentAtomSize() {
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.nonCoherentAtomSize(limits);
    }

    /**
     * Returns maximum number of memory allocations.
     */
    public int getMaxMemoryAllocationCount() {
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.maxMemoryAllocationCount(limits);
    }

    /**
     * Returns maximum storage buffer range in bytes.
     */
    public long getMaxStorageBufferRange() {
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.maxStorageBufferRange(limits);
    }

    /**
     * Returns maximum uniform buffer range in bytes.
     */
    public long getMaxUniformBufferRange() {
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.maxUniformBufferRange(limits);
    }

    /**
     * Returns total size of device-local memory heaps in bytes.
     */
    public long getDeviceLocalMemorySize() {
        ensureCached();
        int heapCount = VkPhysicalDeviceMemoryProperties.memoryHeapCount(cachedMemoryProperties);
        long total = 0;
        for (int i = 0; i < heapCount; i++) {
            MemorySegment heap = VkPhysicalDeviceMemoryProperties.memoryHeaps(cachedMemoryProperties, i);
            int flags = VkMemoryHeap.flags(heap);
            if ((flags & 1) != 0) { // VK_MEMORY_HEAP_DEVICE_LOCAL_BIT
                total += VkMemoryHeap.size(heap);
            }
        }
        return total;
    }

    /**
     * Returns minimum required alignment for uniform buffer offsets.
     * Used for dynamic offsets and suballocations.
     */
    public long getMinUniformBufferOffsetAlignment() {
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.minUniformBufferOffsetAlignment(limits);
    }

    /**
     * Returns minimum required alignment for storage buffer offsets.
     * Used for dynamic offsets and suballocations.
     */
    public long getMinStorageBufferOffsetAlignment() {
        ensureCached();
        MemorySegment limits = VkPhysicalDeviceProperties.limits(cachedProperties);
        return VkPhysicalDeviceLimits.minStorageBufferOffsetAlignment(limits);
    }

    /**
     * Returns whether the device supports sparse buffer residency.
     * When true, accessing uncommitted sparse buffer regions returns zero.
     * When false, accessing uncommitted regions has undefined behavior.
     * Cached after first query.
     */
    public boolean supportsSparseResidencyBuffer() {
        ensureCached();
        return VkPhysicalDeviceFeatures.sparseResidencyBuffer(cachedFeatures) != 0;
    }

    /**
     * Returns whether Resizable BAR (ReBAR) / Smart Access Memory is available.
     * True when a DEVICE_LOCAL | HOST_VISIBLE memory type exists on a heap larger than 512MB,
     * indicating the full VRAM is CPU-accessible rather than just the small PCIe BAR window.
     */
    public boolean supportsReBar() {
        ensureCached();
        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(cachedMemoryProperties);
        int heapCount = VkPhysicalDeviceMemoryProperties.memoryHeapCount(cachedMemoryProperties);
        int deviceLocalHostVisible = io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value()
                | io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value();
        for (int i = 0; i < typeCount; i++) {
            MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(cachedMemoryProperties, i);
            int props = io.github.yetyman.vulkan.generated.VkMemoryType.propertyFlags(memType);
            if ((props & deviceLocalHostVisible) == deviceLocalHostVisible) {
                int heapIndex = io.github.yetyman.vulkan.generated.VkMemoryType.heapIndex(memType);
                if (heapIndex < heapCount) {
                    MemorySegment heap = VkPhysicalDeviceMemoryProperties.memoryHeaps(cachedMemoryProperties, heapIndex);
                    long heapSize = VkMemoryHeap.size(heap);
                    if (heapSize > 512L * 1024 * 1024) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns whether this device uses unified memory architecture (UMA).
     * On UMA devices (integrated GPUs), all memory is simultaneously DEVICE_LOCAL and HOST_VISIBLE
     * — there is no separate VRAM. Single-offset ring buffers are beneficial here since
     * HOST_VISIBLE memory is also the fastest GPU memory.
     * Detected by checking that no heap is exclusively DEVICE_LOCAL (i.e. all device-local
     * heaps are also accessible by the CPU).
     */
    public boolean isUMA() {
        ensureCached();
        int heapCount = VkPhysicalDeviceMemoryProperties.memoryHeapCount(cachedMemoryProperties);
        int typeCount = VkPhysicalDeviceMemoryProperties.memoryTypeCount(cachedMemoryProperties);
        int deviceLocalBit = io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT.value();
        int hostVisibleBit = io.github.yetyman.vulkan.enums.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT.value();

        // Find all heaps that have at least one DEVICE_LOCAL memory type
        boolean[] heapIsDeviceLocal = new boolean[heapCount];
        boolean[] heapIsHostVisible = new boolean[heapCount];
        for (int i = 0; i < typeCount; i++) {
            MemorySegment memType = VkPhysicalDeviceMemoryProperties.memoryTypes(cachedMemoryProperties, i);
            int props = io.github.yetyman.vulkan.generated.VkMemoryType.propertyFlags(memType);
            int heapIndex = io.github.yetyman.vulkan.generated.VkMemoryType.heapIndex(memType);
            if ((props & deviceLocalBit) != 0) heapIsDeviceLocal[heapIndex] = true;
            if ((props & hostVisibleBit) != 0) heapIsHostVisible[heapIndex] = true;
        }

        // UMA: every device-local heap is also host-visible
        for (int i = 0; i < heapCount; i++) {
            if (heapIsDeviceLocal[i] && !heapIsHostVisible[i]) return false;
        }
        return true;
    }

    /**
     * Returns whether single-offset ring buffers are beneficial on this device.
     * True on UMA (integrated GPU) or when ReBAR is available (discrete GPU with full BAR mapping).
     * On these devices HOST_VISIBLE memory is also optimal GPU memory, so a single
     * N-frame allocation with dynamic offsets avoids per-frame buffer rebinding.
     */
    public boolean prefersSingleOffsetRingBuffer() {
        return isUMA() || supportsReBar();
    }

    // Wrapper classes for return values
    public static class VkPhysicalDeviceMemoryPropertiesWrapper {
        private final MemorySegment segment;

        VkPhysicalDeviceMemoryPropertiesWrapper(MemorySegment segment) {
            this.segment = segment;
        }

        public int memoryTypeCount() {
            return VkPhysicalDeviceMemoryProperties.memoryTypeCount(segment);
        }

        public MemorySegment memoryTypes(int index) {
            return VkPhysicalDeviceMemoryProperties.memoryTypes(segment, index);
        }
    }

    public static class VkFormatPropertiesWrapper {
        private final MemorySegment segment;

        VkFormatPropertiesWrapper(MemorySegment segment) {
            this.segment = segment;
        }

        public int optimalTilingFeatures() {
            return VkFormatProperties.optimalTilingFeatures(segment);
        }

        public int linearTilingFeatures() {
            return VkFormatProperties.linearTilingFeatures(segment);
        }

        public int bufferFeatures() {
            return VkFormatProperties.bufferFeatures(segment);
        }
    }
}