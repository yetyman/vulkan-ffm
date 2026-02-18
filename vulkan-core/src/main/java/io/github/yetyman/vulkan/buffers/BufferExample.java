package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkApplicationInfo;
import io.github.yetyman.vulkan.generated.VkInstanceCreateInfo;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

public class BufferExample {
    static { VulkanLibrary.load(); }

    static final long SIZE = 4096;

    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            // Instance
            MemorySegment appInfo = VkApplicationInfo.allocate(arena);
            VkApplicationInfo.sType(appInfo, VkStructureType.VK_STRUCTURE_TYPE_APPLICATION_INFO.value());
            VkApplicationInfo.pNext(appInfo, MemorySegment.NULL);
            VkApplicationInfo.pApplicationName(appInfo, arena.allocateFrom("BufferExample"));
            VkApplicationInfo.applicationVersion(appInfo, 1);
            VkApplicationInfo.pEngineName(appInfo, arena.allocateFrom("NoEngine"));
            VkApplicationInfo.engineVersion(appInfo, 0);
            VkApplicationInfo.apiVersion(appInfo, Vulkan.VK_API_VERSION_1_0);

            MemorySegment createInfo = VkInstanceCreateInfo.allocate(arena);
            VkInstanceCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO.value());
            VkInstanceCreateInfo.pNext(createInfo, MemorySegment.NULL);
            VkInstanceCreateInfo.flags(createInfo, 0);
            VkInstanceCreateInfo.pApplicationInfo(createInfo, appInfo);
            MemorySegment layerName = arena.allocateFrom("VK_LAYER_KHRONOS_validation");
            MemorySegment layerArray = arena.allocate(ValueLayout.ADDRESS);
            layerArray.set(ValueLayout.ADDRESS, 0, layerName);
            VkInstanceCreateInfo.enabledLayerCount(createInfo, 1);
            VkInstanceCreateInfo.ppEnabledLayerNames(createInfo, layerArray);
            MemorySegment extName = arena.allocateFrom("VK_EXT_debug_utils");
            MemorySegment extArray = arena.allocate(ValueLayout.ADDRESS);
            extArray.set(ValueLayout.ADDRESS, 0, extName);
            VkInstanceCreateInfo.enabledExtensionCount(createInfo, 1);
            VkInstanceCreateInfo.ppEnabledExtensionNames(createInfo, extArray);

            MemorySegment instancePtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createInstance(createInfo, instancePtr).check();
            MemorySegment instance = instancePtr.get(ValueLayout.ADDRESS, 0);

            // Physical + logical device
            MemorySegment physHandle = VkPhysicalDeviceOps.enumerate(instance).first(arena);
            VkPhysicalDevice physicalDevice = VkPhysicalDevice.wrap(physHandle);
            int queueFamily = VkQueueFamily.findGraphics(physicalDevice, arena);
            int sparseFamily = -1;
            try { sparseFamily = VkQueueFamily.findSparseBinding(physicalDevice, arena); } catch (VulkanException ignored) {}

            VkDevice device = VkDevice.builder()
                .physicalDevice(physicalDevice)
                .queueFamily(queueFamily)
                .enableSparseBinding()
                .build(arena);
            if (sparseFamily >= 0 && sparseFamily != queueFamily) {
                device.close();
                device = VkDevice.builder()
                    .physicalDevice(physicalDevice)
                    .queueFamily(queueFamily)
                    .queueFamily(sparseFamily)
                    .enableSparseBinding()
                    .build(arena);
            }

            VkQueue queue = VkQueue.builder().device(device).familyIndex(queueFamily).build(arena);
            VkQueue sparseQueue = sparseFamily >= 0
                ? VkQueue.builder().device(device).familyIndex(sparseFamily).build(arena)
                : queue;
            VkCommandPool commandPool = VkCommandPool.create(arena, device, queueFamily);

            ByteBuffer data = ByteBuffer.allocate((int) SIZE);
            data.putInt(0, 0xDEADBEEF);

            // MAPPED
            try (ManagedBuffer buf = BufferFactory.create(MemoryStrategy.MAPPED, null, SIZE, BufferUsage.UNIFORM, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                System.out.println("MAPPED read: 0x" + Integer.toHexString(buf.read(0, SIZE).getInt(0)));
            }

            // MAPPED_CACHED
            try (ManagedBuffer buf = BufferFactory.create(MemoryStrategy.MAPPED_CACHED, null, SIZE, BufferUsage.UNIFORM, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                System.out.println("MAPPED_CACHED read: 0x" + Integer.toHexString(buf.read(0, SIZE).getInt(0)));
            }

            // DEVICE_LOCAL
            try (ManagedBuffer buf = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, SIZE, BufferUsage.STORAGE, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                System.out.println("DEVICE_LOCAL read: 0x" + Integer.toHexString(buf.read(0, SIZE).getInt(0)));
            }

            // STAGING (persistent staging buffer)
            try (ManagedBuffer buf = BufferFactory.create(MemoryStrategy.STAGING, null, SIZE, BufferUsage.STORAGE, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                System.out.println("STAGING read: 0x" + Integer.toHexString(buf.read(0, SIZE).getInt(0)));
            }

            // RING_BUFFER
            try (RingBuffer buf = (RingBuffer) BufferFactory.create(MemoryStrategy.RING_BUFFER, MemoryStrategy.MAPPED, SIZE, BufferUsage.UNIFORM, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                System.out.println("RING_BUFFER[0] read: 0x" + Integer.toHexString(buf.read(0, SIZE).getInt(0)));
                buf.nextFrame();
                buf.write(data.rewind(), 0);
                System.out.println("RING_BUFFER[1] read: 0x" + Integer.toHexString(buf.read(0, SIZE).getInt(0)));
            }

            // SUBALLOCATOR
            try (SuballocatorBuffer buf = (SuballocatorBuffer) BufferFactory.create(MemoryStrategy.SUBALLOCATOR, MemoryStrategy.MAPPED, SIZE, BufferUsage.UNIFORM, device, queue, commandPool, arena)) {
                SuballocatorBuffer.Suballocation sub = buf.allocate(SIZE);
                sub.write(data.rewind());
                System.out.println("SUBALLOCATOR read: 0x" + Integer.toHexString(sub.read().getInt(0)));
                sub.free();
            }

            // SPARSE (requires sparse binding support)
            if (physicalDevice.supportsSparseResidencyBuffer()) {
                try (ManagedBuffer buf = new SparseBuffer(device, arena, SIZE * 16, BufferUsage.STORAGE, MemoryStrategy.DEVICE_LOCAL, sparseQueue, queue, commandPool)) {
                    buf.write(data.rewind(), 0);
                    System.out.println("SPARSE read: 0x" + Integer.toHexString(buf.read(0, SIZE).getInt(0)));
                }
            } else {
                System.out.println("SPARSE: skipped (device does not support sparse binding)");
            }

            commandPool.close();
            device.close();
            Vulkan.destroyInstance(instance);
        }
    }
}
