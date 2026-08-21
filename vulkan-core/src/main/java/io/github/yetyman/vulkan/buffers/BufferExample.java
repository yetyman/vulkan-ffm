package io.github.yetyman.vulkan.buffers;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.VkTimelineSemaphore;
import io.github.yetyman.vulkan.buffers.typed.TypedVkBuffer;
import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkApplicationInfo;
import io.github.yetyman.vulkan.generated.VkInstanceCreateInfo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BufferExample {
    static {
        VulkanLibrary.load();
    }

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

            MemorySegment[] physHandles = VkPhysicalDeviceOps.enumerate(instance).execute(arena);
            System.out.println("Found " + physHandles.length + " Vulkan device(s).");

            List<String> passed = new ArrayList<>();
            List<String> failed = new ArrayList<>();

            for (int gpuIndex = 0; gpuIndex < physHandles.length; gpuIndex++) {
                MemorySegment physHandle = physHandles[gpuIndex];
                MemorySegment propsSegment = io.github.yetyman.vulkan.generated.VkPhysicalDeviceProperties.allocate(arena);
                io.github.yetyman.vulkan.generated.VulkanFFM.vkGetPhysicalDeviceProperties(physHandle, propsSegment);
                String gpuName = io.github.yetyman.vulkan.generated.VkPhysicalDeviceProperties.deviceName(propsSegment).getString(0);

                System.out.println("\n========================================");
                System.out.println("GPU " + gpuIndex + ": " + gpuName);
                System.out.println("========================================");

                try (Arena gpuArena = Arena.ofConfined()) {
                    runTests(instance, physHandle, gpuArena);
                    passed.add(gpuName);
                } catch (Throwable t) {
                    System.err.println("FAILED on GPU " + gpuIndex + " (" + gpuName + "): " + t.getMessage());
                    t.printStackTrace(System.err);
                    failed.add(gpuName);
                }
            }

            Vulkan.destroyInstance(instance);

            System.out.println("\n========================================");
            System.out.println("SUMMARY");
            System.out.println("========================================");
            for (String name : passed) System.out.println("  PASS  " + name);
            for (String name : failed) System.err.println("  FAIL  " + name);
            if (failed.isEmpty()) System.out.println("\nAll GPUs passed.");
            else System.err.println("\n" + failed.size() + " GPU(s) failed.");
        }
    }

    private static void runTests(MemorySegment instance, MemorySegment physHandle, Arena arena) throws InterruptedException {
        VkPhysicalDevice physicalDevice = VkPhysicalDevice.wrap(physHandle);
        int queueFamily = VkQueueFamily.findGraphics(physicalDevice, arena);
        int sparseFamily = -1;
        try {
            sparseFamily = VkQueueFamily.findSparseBinding(physicalDevice, arena);
        } catch (VulkanException ignored) {
        }

        VkDevice device = VkDevice.builder()
                .physicalDevice(physicalDevice)
                .queueFamily(queueFamily)
                .enableSparseBinding()
                .enableTimelineSemaphore()
                .build(arena);
        if (sparseFamily >= 0 && sparseFamily != queueFamily) {
            device.close();
            device = VkDevice.builder()
                    .physicalDevice(physicalDevice)
                    .queueFamily(queueFamily)
                    .queueFamily(sparseFamily)
                    .enableSparseBinding()
                    .enableTimelineSemaphore()
                    .build(arena);
        }
        final VkDevice finalDevice = device;
        try {

        VkQueue queue = VkQueue.builder().device(device).familyIndex(queueFamily).build(arena);
        queue.setSubmitter(new io.github.yetyman.vulkan.queue.DirectSubmitter(queue.handle()));
        VkQueue sparseQueue = sparseFamily >= 0
                ? VkQueue.builder().device(device).familyIndex(sparseFamily).build(arena)
                : queue;

        ByteBuffer data = ByteBuffer.allocate((int) SIZE);
        data.putInt(0, MAGIC);
        ByteBuffer data2 = ByteBuffer.allocate((int) SIZE);
        data2.putInt(0, MAGIC2);

            // =========================================================
            // MAPPED
            // =========================================================
            section("MAPPED");
            try (IBuffer buf = BufferFactory.create(MemoryStrategy.MAPPED, null, SIZE, BufferUsage.UNIFORM, device, queue)) {
                // sync write + read
                buf.write(data.rewind(), 0, queue);
                check("MAPPED sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                // offset write
                buf.write(intBuf(MAGIC2), SIZE / 2, queue);
                check("MAPPED offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                // async write (await)
                try (GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue)) {
                    tc.await();
                }
                check("MAPPED writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                // async write (toFuture)
                try (GpuCompletion tc = buf.writeAsync(data2.rewind(), 0, queue)) {
                    tc.toFuture().join();
                }
                check("MAPPED writeAsync+toFuture", buf.read(0, SIZE).getInt(0), MAGIC2);

                // async write (onComplete with latch)
                CountDownLatch latch = new CountDownLatch(1);
                buf.writeAsync(data.rewind(), 0, queue).onComplete(latch::countDown);
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
            try (IBuffer buf = BufferFactory.create(MemoryStrategy.MAPPED_CACHED, null, SIZE, BufferUsage.UNIFORM, device, queue)) {
                buf.write(data.rewind(), 0, queue);
                check("MAPPED_CACHED sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                buf.write(intBuf(MAGIC2), SIZE / 2, queue);
                check("MAPPED_CACHED offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                try (GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue)) {
                    tc.flush();
                    tc.await();
                }
                check("MAPPED_CACHED writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                // explicit flush (non-coherent path)
                buf.write(data2.rewind(), 0, queue);
                buf.flush();
                check("MAPPED_CACHED flush+read", buf.read(0, SIZE).getInt(0), MAGIC2);
            }

            // =========================================================
            // DEVICE_LOCAL
            // =========================================================
            section("DEVICE_LOCAL");
            try (IBuffer buf = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, SIZE, BufferUsage.STORAGE, device, queue)) {
                buf.write(data.rewind(), 0, queue);
                check("DEVICE_LOCAL sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                buf.write(intBuf(MAGIC2), SIZE / 2, queue);
                check("DEVICE_LOCAL offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                try (GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue)) {
                    tc.flush();
                    tc.await();
                }
                check("DEVICE_LOCAL writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                try (GpuCompletion tc = buf.writeAsync(data2.rewind(), 0, queue)) {
                    tc.flush();
                    tc.toFuture().join();
                }
                check("DEVICE_LOCAL writeAsync+toFuture", buf.read(0, SIZE).getInt(0), MAGIC2);

                CountDownLatch latch = new CountDownLatch(1);
                GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue);
                tc.onComplete(latch::countDown);
                tc.flush();
                latch.await(5, TimeUnit.SECONDS);
                check("DEVICE_LOCAL writeAsync+onComplete", buf.read(0, SIZE).getInt(0), MAGIC);
            }

            // =========================================================
            // STAGING (persistent staging buffer)
            // =========================================================
            section("STAGING");
            try (IBuffer buf = BufferFactory.create(MemoryStrategy.STAGING, null, SIZE, BufferUsage.STORAGE, device, queue)) {
                buf.write(data.rewind(), 0, queue);
                check("STAGING sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                buf.write(intBuf(MAGIC2), SIZE / 2, queue);
                check("STAGING offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                try (GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue)) {
                    tc.flush();
                    tc.await();
                }
                check("STAGING writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                try (GpuCompletion tc = buf.writeAsync(data2.rewind(), 0, queue)) {
                    tc.flush();
                    tc.toFuture().join();
                }
                check("STAGING writeAsync+toFuture", buf.read(0, SIZE).getInt(0), MAGIC2);
            }

            // =========================================================
            // GPU COPY
            // =========================================================
            section("GPU COPY");
            try (IBuffer src = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, SIZE, BufferUsage.STORAGE, device, queue);
                 IBuffer dst = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, SIZE, BufferUsage.STORAGE, device, queue)) {
                src.write(data.rewind(), 0, queue);
                src.copyTo(dst, 0, 0, SIZE, queue);
                check("GPU COPY sync full", dst.read(0, SIZE).getInt(0), MAGIC);

                src.write(intBuf(MAGIC2), SIZE / 2, queue);
                src.copyTo(dst, SIZE / 2, SIZE / 2, 4, queue);
                check("GPU COPY sync offset", dst.read(SIZE / 2, 4).getInt(0), MAGIC2);

                src.write(data.rewind(), 0, queue);
                try (GpuCompletion tc = src.copyToAsync(dst, 0, 0, SIZE, queue)) {
                    tc.flush();
                    tc.await();
                }
                check("GPU COPY async+await", dst.read(0, SIZE).getInt(0), MAGIC);

                src.write(data2.rewind(), 0, queue);
                try (GpuCompletion tc = src.copyToAsync(dst, 0, 0, SIZE, queue)) {
                    tc.flush();
                    tc.toFuture().join();
                }
                check("GPU COPY async+toFuture", dst.read(0, SIZE).getInt(0), MAGIC2);
            }

            // =========================================================
            // DEVICE_LOCAL_MIRRORED
            // =========================================================
            section("DEVICE_LOCAL_MIRRORED");
            try (IBuffer buf = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL_MIRRORED, null, SIZE, BufferUsage.STORAGE, device, queue)) {
                buf.write(data.rewind(), 0, queue);
                check("MIRRORED sync write/read (mirror)", buf.read(0, SIZE).getInt(0), MAGIC);

                buf.write(intBuf(MAGIC2), SIZE / 2, queue);
                check("MIRRORED offset write/read (mirror)", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                try (GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue)) {
                    tc.flush();
                    tc.await();
                }
                check("MIRRORED writeAsync+await (mirror)", buf.read(0, SIZE).getInt(0), MAGIC);

                try (GpuCompletion tc = buf.writeAsync(data2.rewind(), 0, queue)) {
                    tc.flush();
                    tc.toFuture().join();
                }
                check("MIRRORED writeAsync+toFuture (mirror)", buf.read(0, SIZE).getInt(0), MAGIC2);
            }

            // =========================================================
            // RING_BUFFER — all 3 frames, async paths, in-flight guard
            // =========================================================
            section("RING_BUFFER");
            try (RingBuffer buf = (RingBuffer) BufferFactory.create(MemoryStrategy.RING_BUFFER, MemoryStrategy.MAPPED, SIZE, BufferUsage.UNIFORM, device, queue)) {
                // cycle all 3 frames with sync writes
                for (int frame = 0; frame < 3; frame++) {
                    buf.write(intBuf(MAGIC + frame), 0, queue);
                    check("RING_BUFFER[" + frame + "] sync write/read", buf.read(0, 4).getInt(0), MAGIC + frame);
                    buf.nextFrame();
                }

                // async write on each frame, advance, verify in-flight guard awaits before reuse
                for (int frame = 0; frame < 3; frame++) {
                    GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue);
                    // intentionally do NOT await — nextFrame's awaitSlot should handle it
                    tc.flush();
                    buf.nextFrame();
                    // now write to the same slot again (wraps after 3); awaitSlot must have resolved tc
                }
                // one final read to confirm last write landed
                buf.write(data2.rewind(), 0, queue);
                check("RING_BUFFER in-flight guard resolved", buf.read(0, SIZE).getInt(0), MAGIC2);
            }

            // RING_BUFFER with DEVICE_LOCAL underlying strategy
            section("RING_BUFFER(DEVICE_LOCAL)");
            try (RingBuffer buf = (RingBuffer) BufferFactory.create(MemoryStrategy.RING_BUFFER, MemoryStrategy.DEVICE_LOCAL, SIZE, BufferUsage.STORAGE, device, queue);
                 IBuffer copyDst = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, SIZE, BufferUsage.STORAGE, device, queue)) {
                buf.write(data.rewind(), 0, queue);
                check("RING_BUFFER(DEVICE_LOCAL)[0] sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);
                buf.nextFrame();
                try (GpuCompletion tc = buf.writeAsync(data2.rewind(), 0, queue)) {
                    tc.flush();
                    tc.await();
                }
                check("RING_BUFFER(DEVICE_LOCAL)[1] writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC2);

                // copyTo from the current (active) ring slot to an external buffer
                buf.copyTo(copyDst, 0, 0, SIZE, queue);
                check("RING_BUFFER(DEVICE_LOCAL) copyTo sync", copyDst.read(0, SIZE).getInt(0), MAGIC2);

                buf.write(data.rewind(), 0, queue);
                try (GpuCompletion tc = buf.copyToAsync(copyDst, 0, 0, SIZE, queue)) {
                    tc.flush();
                    tc.await();
                }
                check("RING_BUFFER(DEVICE_LOCAL) copyToAsync", copyDst.read(0, SIZE).getInt(0), MAGIC);
            }

            // =========================================================
            // SUBALLOCATOR
            // =========================================================
            section("SUBALLOCATOR");
            try (SuballocatorBuffer buf = BufferFactory.createSlab(SIZE, 256, BufferUsage.UNIFORM, MemoryStrategy.MAPPED, device, queue)) {
                try (SuballocatorBuffer.Suballocation sub1 = buf.allocate();
                     SuballocatorBuffer.Suballocation sub2 = buf.allocate()) {

                    sub1.write(intBuf(MAGIC), queue);
                    sub2.write(intBuf(MAGIC2), queue);
                    check("SUBALLOCATOR sub1 write/read", sub1.read().getInt(0), MAGIC);
                    check("SUBALLOCATOR sub2 write/read", sub2.read().getInt(0), MAGIC2);
                    check("SUBALLOCATOR sub1 unaffected by sub2", sub1.read().getInt(0), MAGIC);
                    check("SUBALLOCATOR sub1 vkBuffer() not null", sub1.vkBuffer() != null, true);
                    check("SUBALLOCATOR sub1/sub2 share backing vkBuffer", sub1.vkBuffer() == sub2.vkBuffer(), true);

                    try (GpuCompletion tc = sub1.writeAsync(intBuf(MAGIC2), queue)) {
                        tc.flush();
                        tc.await();
                    }
                    check("SUBALLOCATOR sub1 writeAsync+await", sub1.read().getInt(0), MAGIC2);
                }
                check("SUBALLOCATOR slots reclaimed", buf.availableSlots(), buf.slotCount());

                try (SuballocatorBuffer.Suballocation sub3 = buf.allocate()) {
                    sub3.write(intBuf(MAGIC), queue);
                    check("SUBALLOCATOR reuse after free", sub3.read().getInt(0), MAGIC);
                }

                for (int i = 0; i < buf.slotCount(); i++) buf.allocate();
                boolean threw = false;
                try {
                    buf.allocate();
                } catch (IllegalStateException e) {
                    threw = true;
                }
                check("SUBALLOCATOR OOM throws", threw ? 1 : 0, 1);
            }

            section("SUBALLOCATOR(DEVICE_LOCAL)");
            try (SuballocatorBuffer buf = BufferFactory.createSlab(SIZE, 256, BufferUsage.STORAGE, MemoryStrategy.DEVICE_LOCAL, device, queue)) {
                try (SuballocatorBuffer.Suballocation sub = buf.allocate()) {
                    sub.write(intBuf(MAGIC), queue);
                    check("SUBALLOCATOR(DEVICE_LOCAL) write/read", sub.read().getInt(0), MAGIC);
                    try (GpuCompletion tc = sub.writeAsync(intBuf(MAGIC2), queue)) {
                        tc.flush();
                        tc.await();
                    }
                    check("SUBALLOCATOR(DEVICE_LOCAL) writeAsync+await", sub.read().getInt(0), MAGIC2);
                }
            }

            // =========================================================
            // SPARSE
            // =========================================================
            if (physicalDevice.supportsSparseResidencyBuffer()) {
                section("SPARSE(DEVICE_LOCAL)");
                try (ManagedBuffer buf = BufferFactory.createSparse(SIZE * 64, BufferUsage.STORAGE, MemoryStrategy.DEVICE_LOCAL, device, sparseQueue, queue)) {
                    // single-page write/read
                    buf.write(data.rewind(), 0, queue);
                    check("SPARSE(DEVICE_LOCAL) sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                    // offset into second page
                    long secondPage = buf.pageSize();
                    buf.write(intBuf(MAGIC2), secondPage, queue);
                    check("SPARSE(DEVICE_LOCAL) second-page write/read", buf.read(secondPage, 4).getInt(0), MAGIC2);

                    // first page unaffected
                    check("SPARSE(DEVICE_LOCAL) first page unaffected", buf.read(0, 4).getInt(0), MAGIC);

                    // async write
                    try (GpuCompletion tc = buf.writeAsync(data2.rewind(), 0, queue)) {
                        tc.flush();
                        tc.await();
                    }
                    check("SPARSE(DEVICE_LOCAL) writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC2);

                    // multi-page spanning write
                    long spanOffset = buf.pageSize() - 8; // straddles page boundary
                    ByteBuffer spanData = ByteBuffer.allocate(16);
                    spanData.putInt(0, MAGIC);
                    spanData.putInt(4, MAGIC2);
                    spanData.putInt(8, MAGIC);
                    spanData.putInt(12, MAGIC2);
                    buf.write(spanData.rewind(), spanOffset, queue);
                    check("SPARSE(DEVICE_LOCAL) cross-page write int0", buf.read(spanOffset, 4).getInt(0), MAGIC);
                    check("SPARSE(DEVICE_LOCAL) cross-page write int1", buf.read(spanOffset + 4, 4).getInt(0), MAGIC2);

                    // decommit/isCommitted — page 0 fully committed by earlier writes
                    check("SPARSE(DEVICE_LOCAL) page 0 isCommitted before decommit", buf.isCommitted(0, buf.pageSize()), true);
                    buf.decommitPages(0, buf.pageSize());
                    check("SPARSE(DEVICE_LOCAL) page 0 isCommitted after decommit", buf.isCommitted(0, buf.pageSize()), false);
                    // page 1 (second page) was untouched by decommit — still committed
                    check("SPARSE(DEVICE_LOCAL) page 1 still committed after page 0 decommit", buf.isCommitted(secondPage, 4), true);
                    // re-write to page 0 re-commits it on demand
                    buf.write(intBuf(MAGIC), 0, queue);
                    check("SPARSE(DEVICE_LOCAL) page 0 recommitted by write", buf.read(0, 4).getInt(0), MAGIC);
                }

                section("SPARSE(MAPPED)");
                try (ManagedBuffer buf = BufferFactory.createSparse(SIZE * 64, BufferUsage.STORAGE, MemoryStrategy.MAPPED, device, sparseQueue, queue)) {
                    buf.write(data.rewind(), 0, queue);
                    check("SPARSE(MAPPED) sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                    buf.write(intBuf(MAGIC2), buf.pageSize(), queue);
                    check("SPARSE(MAPPED) second-page write/read", buf.read(buf.pageSize(), 4).getInt(0), MAGIC2);

                    // writeAsync on host-visible returns completed() immediately
                    try (GpuCompletion tc = buf.writeAsync(data2.rewind(), 0, queue)) {
                        check("SPARSE(MAPPED) writeAsync isComplete immediately", tc.isComplete() ? 1 : 0, 1);
                        tc.await();
                    }
                    check("SPARSE(MAPPED) writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC2);

                    buf.flush();
                    System.out.println("SPARSE(MAPPED) flush: ok");
                } catch (UnsupportedOperationException e) {
                    System.out.println("SPARSE(MAPPED): skipped (" + e.getMessage() + ")");
                }
            } else {
                System.out.println("SPARSE: skipped (device does not support sparse binding)");
            }

            // =========================================================
            // TYPED_VK_BUFFER
            // =========================================================
            section("TYPED_VK_BUFFER (mirrored)");
            int elemCount = 4;
            int elemStride = Vec4.BYTE_SIZE;
            long typedBufSize = (long) elemStride * elemCount;
            try (TypedVkBuffer<Vec4> buf = new TypedVkBuffer<>(
                    BufferFactory.create(MemoryStrategy.MAPPED, null, typedBufSize, BufferUsage.UNIFORM, device, queue),
                    Vec4.LAYOUT, elemCount, true) {
                @Override
                protected Vec4 getInstance() {
                    return new Vec4();
                }
            }) {
                Vec4 a = new Vec4(1, 2, 3, 4);
                Vec4 b = new Vec4(5, 6, 7, 8);

                buf.write(0, a, queue);
                check("TYPED mirrored write/read[0].x", Float.floatToRawIntBits(buf.read(0).x), Float.floatToRawIntBits(1f));

                try (GpuCompletion tc = buf.writeAsync(1, b, queue)) {
                    tc.await();
                }
                check("TYPED mirrored writeAsync[1].x", Float.floatToRawIntBits(buf.read(1).x), Float.floatToRawIntBits(5f));

                // bulk write
                List<Vec4> batch = List.of(new Vec4(10, 0, 0, 0), new Vec4(20, 0, 0, 0));
                buf.write(batch, 2, queue);
                check("TYPED mirrored bulk write[2].x", Float.floatToRawIntBits(buf.read(2).x), Float.floatToRawIntBits(10f));
                check("TYPED mirrored bulk write[3].x", Float.floatToRawIntBits(buf.read(3).x), Float.floatToRawIntBits(20f));

                // bulk read returns mirror sublist — zero GPU cost
                List<Vec4> slice = buf.read(0, 2);
                check("TYPED mirrored bulk read[0].x", Float.floatToRawIntBits(slice.get(0).x), Float.floatToRawIntBits(1f));
                check("TYPED mirrored bulk read[1].x", Float.floatToRawIntBits(slice.get(1).x), Float.floatToRawIntBits(5f));
            }

            section("TYPED_VK_BUFFER (non-mirrored, GPU readback)");
            try (TypedVkBuffer<Vec4> buf = new TypedVkBuffer<>(
                    BufferFactory.create(MemoryStrategy.MAPPED, null, typedBufSize, BufferUsage.UNIFORM, device, queue),
                    Vec4.LAYOUT, elemCount, false) {
                @Override
                protected Vec4 getInstance() {
                    return new Vec4();
                }
            }) {
                buf.write(0, new Vec4(99, 0, 0, 0), queue);
                Vec4 result = buf.read(0, new Vec4());
                check("TYPED non-mirrored read(target).x", Float.floatToRawIntBits(result.x), Float.floatToRawIntBits(99f));

                // bulk read into pre-populated list
                buf.write(1, new Vec4(77, 0, 0, 0), queue);
                ArrayList<Vec4> targets = new ArrayList<>(List.of(new Vec4(), new Vec4()));
                buf.read(0, 2, targets);
                check("TYPED non-mirrored bulk read[0].x", Float.floatToRawIntBits(targets.get(0).x), Float.floatToRawIntBits(99f));
                check("TYPED non-mirrored bulk read[1].x", Float.floatToRawIntBits(targets.get(1).x), Float.floatToRawIntBits(77f));
            }

            section("TYPED_VK_BUFFER GPU copy");
            try (TypedVkBuffer<Vec4> src = new TypedVkBuffer<>(
                    BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, typedBufSize, BufferUsage.STORAGE, device, queue),
                    Vec4.LAYOUT, elemCount, true) {
                @Override
                protected Vec4 getInstance() {
                    return new Vec4();
                }
            };
                 TypedVkBuffer<Vec4> dst = new TypedVkBuffer<>(
                         BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, typedBufSize, BufferUsage.STORAGE, device, queue),
                         Vec4.LAYOUT, elemCount, false) {
                     @Override
                     protected Vec4 getInstance() {
                         return new Vec4();
                     }
                 }) {
                src.write(0, new Vec4(42, 0, 0, 0), queue);
                src.copyTo(dst, 0, 0, 1, queue);
                check("TYPED GPU copy[0].x", Float.floatToRawIntBits(dst.read(0, new Vec4()).x), Float.floatToRawIntBits(42f));

                try (GpuCompletion tc = src.copyToAsync(dst, 0, 0, elemCount, queue)) {
                    tc.flush();
                    tc.await();
                }
                check("TYPED GPU copyAsync full", Float.floatToRawIntBits(dst.read(0, new Vec4()).x), Float.floatToRawIntBits(42f));
            }

            // =========================================================
            // TIMELINE SEMAPHORE
            // =========================================================
            section("TIMELINE_SEMAPHORE");
            try (Arena semArena = Arena.ofShared();
                 VkTimelineSemaphore timeline = VkTimelineSemaphore.create(device, 0, semArena);
                 IBuffer buf = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, SIZE, BufferUsage.STORAGE, device, queue)) {

                // Queue three async writes into the same batch, then attach a signal at value 1
                TransferBatchManager.signalOn(device, queue, timeline, 1);
                check("Timeline counter before writes", (int) timeline.counterValue(), 0);
                buf.writeAsync(intBuf(MAGIC), 0, queue);
                buf.writeAsync(intBuf(MAGIC2), SIZE / 2, queue);
                buf.writeAsync(intBuf(MAGIC), SIZE - 4, queue);

                check("Timeline counter after async writes", (int) timeline.counterValue(), 0);
                TransferBatchManager.flush(device, queue);

                // CPU waits for the GPU to signal value 1
                timeline.await(1);
                check("Timeline counter after flush+await", (int) timeline.counterValue(), 1);

                check("TIMELINE_SEMAPHORE write[0]", buf.read(0, 4).getInt(0), MAGIC);
                check("TIMELINE_SEMAPHORE write[SIZE/2]", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);
                check("TIMELINE_SEMAPHORE write[end]", buf.read(SIZE - 4, 4).getInt(0), MAGIC);
                System.out.println("  timeline counter after await: " + timeline.counterValue());
            }

            // =========================================================
            // REBAR
            // =========================================================
            if (physicalDevice.supportsReBar()) {
                section("REBAR");
                try (IBuffer buf = BufferFactory.create(MemoryStrategy.REBAR, null, SIZE, BufferUsage.STORAGE, device, queue)) {
                    buf.write(data.rewind(), 0, queue);
                    check("REBAR sync write/read", buf.read(0, SIZE).getInt(0), MAGIC);

                    buf.write(intBuf(MAGIC2), SIZE / 2, queue);
                    check("REBAR offset write/read", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                    try (GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue)) {
                        check("REBAR writeAsync isComplete immediately", tc.isComplete() ? 1 : 0, 1);
                        tc.await();
                    }
                    check("REBAR writeAsync+await", buf.read(0, SIZE).getInt(0), MAGIC);

                    buf.flush(); // no-op, should not throw
                    System.out.println("REBAR flush: ok");
                }

                // DEVICE_LOCAL_MIRRORED over ReBAR — verify that mirrored observability works and
                // provides zero-cost CPU reads via MirrorCapable.
                section("DEVICE_LOCAL_MIRRORED(REBAR)");
                try (ManagedBuffer buf = (ManagedBuffer) BufferFactory.create(
                        MemoryStrategy.DEVICE_LOCAL_MIRRORED, null, SIZE, BufferUsage.STORAGE, device, queue)) {
                    buf.write(data.rewind(), 0, queue);
                    check("MIRRORED sync write/read (mirror)", buf.read(0, SIZE).getInt(0), MAGIC);

                    buf.write(intBuf(MAGIC2), SIZE / 2, queue);
                    check("MIRRORED offset write/read (mirror)", buf.read(SIZE / 2, 4).getInt(0), MAGIC2);

                    try (GpuCompletion tc = buf.writeAsync(data.rewind(), 0, queue)) {
                        tc.flush();
                        tc.await();
                    }
                    check("MIRRORED writeAsync+await (mirror)", buf.read(0, SIZE).getInt(0), MAGIC);
                }

                // Mirrored buffer with GPU->CPU readDiff — verify markGpuDirty + readDiff pulls
                // GPU-side writes back into the mirror.
                section("DEVICE_LOCAL_MIRRORED readDiff");
                try (ManagedBuffer buf = (ManagedBuffer) BufferFactory.create(
                        MemoryStrategy.DEVICE_LOCAL_MIRRORED, null, SIZE, BufferUsage.STORAGE, device, queue)) {
                    buf.write(data.rewind(), 0, queue);
                    check("MIRRORED sync write/read (mirror)", buf.read(0, SIZE).getInt(0), MAGIC);

                    // Simulate an external GPU-side write by copying directly to the primary
                    // handle, bypassing the mirror
                    TransferBatch batch = TransferBatchManager.getOrCreate(device, queue);
                    IBuffer extBuf = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, SIZE, BufferUsage.STORAGE, device, queue);
                    extBuf.write(data2.rewind(), 0, queue);
                    GpuCompletion copyTc = batch.record(extBuf.handle(), buf.handle(), 0, 0, SIZE);
                    TransferBatchManager.flush(device, queue);
                    copyTc.await();
                    copyTc.close();

                    // Mirror should still be stale
                    check("MIRRORED mirror stale after external write", buf.read(0, SIZE).getInt(0), MAGIC);

                    // readDiff should pull GPU-side data back into the mirror
                    buf.markGpuDirty(0, SIZE);
                    try (GpuCompletion tc = buf.readDiff(queue)) {
                        TransferBatchManager.flush(device, queue);
                        tc.await();
                    }
                    check("MIRRORED mirror fresh after readDiff", buf.read(0, SIZE).getInt(0), MAGIC2);

                    extBuf.close();
                }
            } else {
                System.out.println("REBAR: skipped (device does not support ReBAR)");
            }

            System.out.println("\nAll tests passed on this GPU.");
        } finally {
            finalDevice.close();
        }
    }

    // Simple 4-float struct used with an explicit GpuLayout in TypedVkBuffer examples
    static class Vec4 {
        static final int BYTE_SIZE = 16;

        static final GpuLayout<Vec4> LAYOUT = new GpuLayout<>() {
            @Override public int byteSize() { return BYTE_SIZE; }
            @Override public void writeTo(Vec4 v, MemorySegment dst, long o) {
                dst.set(ValueLayout.JAVA_FLOAT_UNALIGNED, o, v.x);
                dst.set(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 4, v.y);
                dst.set(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 8, v.z);
                dst.set(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 12, v.w);
            }
            @Override public void readFrom(Vec4 v, MemorySegment src, long o) {
                v.x = src.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o);
                v.y = src.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 4);
                v.z = src.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 8);
                v.w = src.get(ValueLayout.JAVA_FLOAT_UNALIGNED, o + 12);
            }
        };

        float x, y, z, w;

        Vec4() {
        }

        Vec4(float x, float y, float z, float w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
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
