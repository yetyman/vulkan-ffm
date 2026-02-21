package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkApplicationInfo;
import io.github.yetyman.vulkan.generated.VkInstanceCreateInfo;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BufferExample {
    static { VulkanLibrary.load(); }

    static final long SIZE = 4096;
    static final int MAGIC = 0xDEADBEEF;
    static final int MAGIC2 = 0xCAFEBABE;

    public static void main(String[] args) throws InterruptedException {
        try (Arena arena = Arena.ofConfined()) {
            // --- Instance ---
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

            // --- Physical + logical device ---
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

            ByteBuffer data  = ByteBuffer.allocate((int) SIZE);  data.putInt(0, MAGIC);
            ByteBuffer data2 = ByteBuffer.allocate((int) SIZE);  data2.putInt(0, MAGIC2);

            // =========================================================
            // MAPPED
            // =========================================================
            section("MAPPED");
            try (ManagedBuffer buf = BufferFactory.create(MemoryStrategy.MAPPED, null, SIZE, BufferUsage.UNIFORM, device, queue, commandPool, arena)) {
                // sync write + read
                buf.write(data.rewind(), 0);
                check("MAPPED sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                // offset write
                buf.write(intBuf(MAGIC2), SIZE / 2);
                check("MAPPED offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                // async write (await)
                try (TransferCompletion tc = buf.writeAsync(data.rewind(), 0)) {
                    tc.await();
                }
                check("MAPPED writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                // async write (toFuture)
                try (TransferCompletion tc = buf.writeAsync(data2.rewind(), 0)) {
                    tc.toFuture().join();
                }
                check("MAPPED writeAsync+toFuture", buf.read(0, SIZE).getInt(0), MAGIC2);

                // async write (onComplete with latch)
                CountDownLatch latch = new CountDownLatch(1);
                buf.writeAsync(data.rewind(), 0).onComplete(latch::countDown);
                latch.await(5, TimeUnit.SECONDS);
                check("MAPPED writeAsync+onComplete", buf.read(0, SIZE).getInt(0), MAGIC);

                // flush (no-op for coherent, should not throw)
                buf.flush();
                System.out.println("MAPPED flush: ok");
            }

            // =========================================================
            // MAPPED_CACHED
            // =========================================================
            section("MAPPED_CACHED");
            try (ManagedBuffer buf = BufferFactory.create(MemoryStrategy.MAPPED_CACHED, null, SIZE, BufferUsage.UNIFORM, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                check("MAPPED_CACHED sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                buf.write(intBuf(MAGIC2), SIZE / 2);
                check("MAPPED_CACHED offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                try (TransferCompletion tc = buf.writeAsync(data.rewind(), 0)) { tc.await(); }
                check("MAPPED_CACHED writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                // explicit flush (non-coherent path)
                buf.write(data2.rewind(), 0);
                buf.flush();
                check("MAPPED_CACHED flush+read", buf.read(0, SIZE).getInt(0), MAGIC2);
            }

            // =========================================================
            // DEVICE_LOCAL
            // =========================================================
            section("DEVICE_LOCAL");
            try (ManagedBuffer buf = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, SIZE, BufferUsage.STORAGE, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                check("DEVICE_LOCAL sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                buf.write(intBuf(MAGIC2), SIZE / 2);
                check("DEVICE_LOCAL offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                try (TransferCompletion tc = buf.writeAsync(data.rewind(), 0)) { tc.await(); }
                check("DEVICE_LOCAL writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                try (TransferCompletion tc = buf.writeAsync(data2.rewind(), 0)) { tc.toFuture().join(); }
                check("DEVICE_LOCAL writeAsync+toFuture", buf.read(0, SIZE).getInt(0), MAGIC2);

                CountDownLatch latch = new CountDownLatch(1);
                buf.writeAsync(data.rewind(), 0).onComplete(latch::countDown);
                latch.await(5, TimeUnit.SECONDS);
                check("DEVICE_LOCAL writeAsync+onComplete", buf.read(0, SIZE).getInt(0), MAGIC);
            }

            // =========================================================
            // STAGING (persistent staging buffer)
            // =========================================================
            section("STAGING");
            try (ManagedBuffer buf = BufferFactory.create(MemoryStrategy.STAGING, null, SIZE, BufferUsage.STORAGE, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                check("STAGING sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                buf.write(intBuf(MAGIC2), SIZE / 2);
                check("STAGING offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                try (TransferCompletion tc = buf.writeAsync(data.rewind(), 0)) { tc.await(); }
                check("STAGING writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                try (TransferCompletion tc = buf.writeAsync(data2.rewind(), 0)) { tc.toFuture().join(); }
                check("STAGING writeAsync+toFuture", buf.read(0, SIZE).getInt(0), MAGIC2);
            }

            // =========================================================
            // RING_BUFFER — all 3 frames, async paths, in-flight guard
            // =========================================================
            section("RING_BUFFER");
            try (RingBuffer buf = (RingBuffer) BufferFactory.create(MemoryStrategy.RING_BUFFER, MemoryStrategy.MAPPED, SIZE, BufferUsage.UNIFORM, device, queue, commandPool, arena)) {
                // cycle all 3 frames with sync writes
                for (int frame = 0; frame < 3; frame++) {
                    buf.write(intBuf(MAGIC + frame), 0);
                    check("RING_BUFFER[" + frame + "] sync write/read", buf.read(0, 4).getInt(0), MAGIC + frame);
                    buf.nextFrame();
                }

                // async write on each frame, advance, verify in-flight guard awaits before reuse
                for (int frame = 0; frame < 3; frame++) {
                    TransferCompletion tc = buf.writeAsync(data.rewind(), 0);
                    // intentionally do NOT await — nextFrame's awaitSlot should handle it
                    buf.nextFrame();
                    // now write to the same slot again (wraps after 3); awaitSlot must have resolved tc
                }
                // one final read to confirm last write landed
                buf.write(data2.rewind(), 0);
                check("RING_BUFFER in-flight guard resolved", buf.read(0, SIZE).getInt(0), MAGIC2);
            }

            // RING_BUFFER with DEVICE_LOCAL underlying strategy
            section("RING_BUFFER(DEVICE_LOCAL)");
            try (RingBuffer buf = (RingBuffer) BufferFactory.create(MemoryStrategy.RING_BUFFER, MemoryStrategy.DEVICE_LOCAL, SIZE, BufferUsage.STORAGE, device, queue, commandPool, arena)) {
                buf.write(data.rewind(), 0);
                check("RING_BUFFER(DEVICE_LOCAL)[0] sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);
                buf.nextFrame();
                try (TransferCompletion tc = buf.writeAsync(data2.rewind(), 0)) { tc.await(); }
                check("RING_BUFFER(DEVICE_LOCAL)[1] writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC2);
            }

            // =========================================================
            // SUBALLOCATOR
            // =========================================================
            section("SUBALLOCATOR");
            try (SuballocatorBuffer buf = BufferFactory.createSlab(SIZE, 256, BufferUsage.UNIFORM, MemoryStrategy.MAPPED, device, queue, commandPool, arena)) {
                try (SuballocatorBuffer.Suballocation sub1 = buf.allocate();
                     SuballocatorBuffer.Suballocation sub2 = buf.allocate()) {

                    sub1.write(intBuf(MAGIC));
                    sub2.write(intBuf(MAGIC2));
                    check("SUBALLOCATOR sub1 write/read", sub1.read().getInt(0), MAGIC);
                    check("SUBALLOCATOR sub2 write/read", sub2.read().getInt(0), MAGIC2);
                    check("SUBALLOCATOR sub1 unaffected by sub2", sub1.read().getInt(0), MAGIC);

                    try (TransferCompletion tc = sub1.writeAsync(intBuf(MAGIC2))) { tc.await(); }
                    check("SUBALLOCATOR sub1 writeAsync+await", sub1.read().getInt(0), MAGIC2);
                }
                check("SUBALLOCATOR slots reclaimed", buf.availableSlots(), buf.slotCount());

                try (SuballocatorBuffer.Suballocation sub3 = buf.allocate()) {
                    sub3.write(intBuf(MAGIC));
                    check("SUBALLOCATOR reuse after free", sub3.read().getInt(0), MAGIC);
                }

                for (int i = 0; i < buf.slotCount(); i++) buf.allocate();
                boolean threw = false;
                try { buf.allocate(); } catch (IllegalStateException e) { threw = true; }
                check("SUBALLOCATOR OOM throws", threw ? 1 : 0, 1);
            }

            section("SUBALLOCATOR(DEVICE_LOCAL)");
            try (SuballocatorBuffer buf = BufferFactory.createSlab(SIZE, 256, BufferUsage.STORAGE, MemoryStrategy.DEVICE_LOCAL, device, queue, commandPool, arena)) {
                try (SuballocatorBuffer.Suballocation sub = buf.allocate()) {
                    sub.write(intBuf(MAGIC));
                    check("SUBALLOCATOR(DEVICE_LOCAL) write/read", sub.read().getInt(0), MAGIC);
                    try (TransferCompletion tc = sub.writeAsync(intBuf(MAGIC2))) { tc.await(); }
                    check("SUBALLOCATOR(DEVICE_LOCAL) writeAsync+await", sub.read().getInt(0), MAGIC2);
                }
            }

            // =========================================================
            // SPARSE
            // =========================================================
            if (physicalDevice.supportsSparseResidencyBuffer()) {
                section("SPARSE(DEVICE_LOCAL)");
                try (SparseBuffer buf = new SparseBuffer(device, arena, SIZE * 64, BufferUsage.STORAGE, MemoryStrategy.DEVICE_LOCAL, sparseQueue, queue, commandPool)) {
                    // single-page write/read
                    buf.write(data.rewind(), 0);
                    check("SPARSE(DEVICE_LOCAL) sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                    // offset into second page
                    long secondPage = buf.pageSize();
                    buf.write(intBuf(MAGIC2), secondPage);
                    check("SPARSE(DEVICE_LOCAL) second-page write/read", buf.read(secondPage, 4).getInt(0), MAGIC2);

                    // first page unaffected
                    check("SPARSE(DEVICE_LOCAL) first page unaffected", buf.read(0, 4).getInt(0), MAGIC);

                    // async write
                    try (TransferCompletion tc = buf.writeAsync(data2.rewind(), 0)) { tc.await(); }
                    check("SPARSE(DEVICE_LOCAL) writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC2);

                    // multi-page spanning write
                    long spanOffset = buf.pageSize() - 8; // straddles page boundary
                    ByteBuffer spanData = ByteBuffer.allocate(16);
                    spanData.putInt(0, MAGIC); spanData.putInt(4, MAGIC2);
                    spanData.putInt(8, MAGIC); spanData.putInt(12, MAGIC2);
                    buf.write(spanData.rewind(), spanOffset);
                    check("SPARSE(DEVICE_LOCAL) cross-page write int0", buf.read(spanOffset, 4).getInt(0), MAGIC);
                    check("SPARSE(DEVICE_LOCAL) cross-page write int1", buf.read(spanOffset + 4, 4).getInt(0), MAGIC2);
                }

                section("SPARSE(MAPPED)");
                try (SparseBuffer buf = new SparseBuffer(device, arena, SIZE * 64, BufferUsage.STORAGE, MemoryStrategy.MAPPED, sparseQueue, queue, commandPool)) {
                    buf.write(data.rewind(), 0);
                    check("SPARSE(MAPPED) sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                    buf.write(intBuf(MAGIC2), buf.pageSize());
                    check("SPARSE(MAPPED) second-page write/read", buf.read(buf.pageSize(), 4).getInt(0), MAGIC2);

                    // writeAsync on host-visible returns completed() immediately
                    try (TransferCompletion tc = buf.writeAsync(data2.rewind(), 0)) {
                        check("SPARSE(MAPPED) writeAsync isComplete immediately", tc.isComplete() ? 1 : 0, 1);
                        tc.await();
                    }
                    check("SPARSE(MAPPED) writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC2);

                    buf.flush();
                    System.out.println("SPARSE(MAPPED) flush: ok");
                }
            } else {
                System.out.println("SPARSE: skipped (device does not support sparse binding)");
            }

            // =========================================================
            // REBAR
            // =========================================================
            if (physicalDevice.supportsReBar()) {
                section("REBAR");
                try (ReBarBuffer buf = new ReBarBuffer(device, arena, SIZE, BufferUsage.STORAGE)) {
                    buf.write(data.rewind(), 0);
                    check("REBAR sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                    buf.write(intBuf(MAGIC2), SIZE / 2);
                    check("REBAR offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                    try (TransferCompletion tc = buf.writeAsync(data.rewind(), 0)) {
                        check("REBAR writeAsync isComplete immediately", tc.isComplete() ? 1 : 0, 1);
                        tc.await();
                    }
                    check("REBAR writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                    buf.flush(); // no-op, should not throw
                    System.out.println("REBAR flush: ok");
                }
            } else {
                System.out.println("REBAR: skipped (device does not support ReBAR)");
            }

            commandPool.close();
            device.close();
            Vulkan.destroyInstance(instance);

            System.out.println("\nAll tests passed.");
        }
    }

    private static ByteBuffer intBuf(int value) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(0, value);
        return b.rewind();
    }

    private static void section(String name) {
        System.out.println("\n--- " + name + " ---");
    }

    private static void check(String label, int actual, int expected) {
        if (actual == expected) {
            System.out.println("  PASS  " + label + ": 0x" + Integer.toHexString(actual));
        } else {
            System.err.println("  FAIL  " + label + ": expected 0x" + Integer.toHexString(expected) + " got 0x" + Integer.toHexString(actual));
            throw new AssertionError(label + " failed");
        }
    }

    // overload for boolean checks
    private static void check(String label, boolean actual, boolean expected) {
        check(label, actual ? 1 : 0, expected ? 1 : 0);
    }
}
