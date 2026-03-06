package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.VkPhysicalDeviceOps;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkQueueFamily;
import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.VulkanLibrary;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.buffers.TransferCompletion;
import io.github.yetyman.vulkan.enums.VkStructureType;
import io.github.yetyman.vulkan.generated.VkApplicationInfo;
import io.github.yetyman.vulkan.generated.VkInstanceCreateInfo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ShaderExample {
    static { VulkanLibrary.load(); }

    // Shaders in sample-app resources — must be on classpath at runtime
    static final String VERT_SIMPLE  = "/shaders/triangle.vert";  // push constant: float time
    static final String VERT_COMPLEX = "/shaders/gltf.vert";      // UBO: camera, push constants: visualizationMode/lodLevel/splitScreenOffset

    public static void main(String[] args) throws Exception {
        try (Arena arena = Arena.ofConfined()) {
            // --- Vulkan instance ---
            MemorySegment appInfo = VkApplicationInfo.allocate(arena);
            VkApplicationInfo.sType(appInfo, VkStructureType.VK_STRUCTURE_TYPE_APPLICATION_INFO.value());
            VkApplicationInfo.pNext(appInfo, MemorySegment.NULL);
            VkApplicationInfo.pApplicationName(appInfo, arena.allocateFrom("ShaderExample"));
            VkApplicationInfo.applicationVersion(appInfo, 1);
            VkApplicationInfo.pEngineName(appInfo, arena.allocateFrom("NoEngine"));
            VkApplicationInfo.engineVersion(appInfo, 0);
            VkApplicationInfo.apiVersion(appInfo, Vulkan.VK_API_VERSION_1_0);

            MemorySegment createInfo = VkInstanceCreateInfo.allocate(arena);
            VkInstanceCreateInfo.sType(createInfo, VkStructureType.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO.value());
            VkInstanceCreateInfo.pNext(createInfo, MemorySegment.NULL);
            VkInstanceCreateInfo.flags(createInfo, 0);
            VkInstanceCreateInfo.pApplicationInfo(createInfo, appInfo);
            VkInstanceCreateInfo.enabledLayerCount(createInfo, 0);
            VkInstanceCreateInfo.ppEnabledLayerNames(createInfo, MemorySegment.NULL);
            VkInstanceCreateInfo.enabledExtensionCount(createInfo, 0);
            VkInstanceCreateInfo.ppEnabledExtensionNames(createInfo, MemorySegment.NULL);

            MemorySegment instancePtr = arena.allocate(ValueLayout.ADDRESS);
            Vulkan.createInstance(createInfo, instancePtr).check();
            MemorySegment instance = instancePtr.get(ValueLayout.ADDRESS, 0);

            MemorySegment physHandle = VkPhysicalDeviceOps.enumerate(instance).first(arena);
            VkPhysicalDevice physicalDevice = VkPhysicalDevice.wrap(physHandle);
            int queueFamily = VkQueueFamily.findGraphics(physicalDevice, arena);

            VkDevice device = VkDevice.builder()
                .physicalDevice(physicalDevice)
                .queueFamily(queueFamily)
                .build(arena);
            VkQueue queue = VkQueue.builder().device(device).familyIndex(queueFamily).build(arena);

            // =========================================================
            // 1. COMPILE TEST — both shaders must compile without error
            // =========================================================
            section("COMPILE TEST");
            CompiledShader simpleCompiled = ShaderLoader.compileShader(VERT_SIMPLE);
            check("triangle.vert compiled", simpleCompiled.getSpirV().length > 0);
            System.out.println("  triangle.vert SPIR-V bytes: " + simpleCompiled.getSpirV().length);

            CompiledShader complexCompiled = ShaderLoader.compileShader(VERT_COMPLEX);
            check("gltf.vert compiled", complexCompiled.getSpirV().length > 0);
            System.out.println("  gltf.vert SPIR-V bytes: " + complexCompiled.getSpirV().length);

            // =========================================================
            // 2. SHADER INSTANCE — reflection-driven slot creation
            // =========================================================
            section("SHADER INSTANCE");
            try (ShaderInstance simple = ShaderInstance.from(VERT_SIMPLE, device)) {
                check("ShaderInstance created (triangle.vert)", simple != null);
                check("compiled() not null", simple.compiled() != null);

                // triangle.vert: push constant block 'pc' with member 'time'
                PushConstant<Float> time = simple.getPushConstant("time", Float.class);
                check("getPushConstant 'time' not null", time != null);
                check("'time' not dirty initially", !time.isDirty());
                time.set(1.5f);
                check("'time' dirty after set()", time.isDirty());
                System.out.println("  push constant 'time': offset=" + time.offset() + " size=" + time.size());
            }

            try (ShaderInstance complex = ShaderInstance.from(VERT_COMPLEX, device)) {
                check("ShaderInstance created (gltf.vert)", complex != null);

                // gltf.vert has push constants: visualizationMode (int), lodLevel (int), splitScreenOffset (float)
                PushConstant<Integer> vizMode = complex.getPushConstant("visualizationMode", Integer.class);
                PushConstant<Integer> lod     = complex.getPushConstant("lodLevel", Integer.class);
                PushConstant<Float>   split   = complex.getPushConstant("splitScreenOffset", Float.class);
                check("getPushConstant 'visualizationMode'", vizMode != null);
                check("getPushConstant 'lodLevel'", lod != null);
                check("getPushConstant 'splitScreenOffset'", split != null);

                // gltf.vert has UBO 'camera' at binding 0
                UniformBufferSlot<Object> cameraSlot = complex.getUniformBufferSlot("camera", Object.class);
                check("getUniformBufferSlot 'camera'", cameraSlot != null);
                check("'camera' slot not dirty initially", !cameraSlot.isDirty());
                System.out.println("  descriptor sets: " + complex.descriptorSets().size());
                System.out.println("  layouts: " + complex.layouts().size());

                // =========================================================
                // 3. GENERATED SHADER CODE PRINTOUT
                // =========================================================
                section("GENERATED SHADER CODE (gltf.vert)");
                Path tmpDir = Files.createTempDirectory("shader-gen-test");
                ShaderGenerator.generate(VERT_COMPLEX, tmpDir, "io.github.yetyman.vulkan.shaders.generated");
                Path genFile = tmpDir.resolve("GltfVertShader.java");
                check("generated file exists", genFile.toFile().exists());
                String generatedSource = Files.readString(genFile);
                System.out.println("  --- GltfVertShader.java ---");
                System.out.println(generatedSource);
                System.out.println("  --- end ---");

                // =========================================================
                // 4. DIRTY FLAG + ASYNC BUFFER LOAD
                //    Background thread writes camera UBO data; on completion
                //    it sets the buffer into the 'camera' slot, marking it dirty.
                // =========================================================
                section("DIRTY FLAG / ASYNC BUFFER LOAD");

                // Use DEVICE_LOCAL so transfers are genuinely async (GPU staging copy).
                // Launch 3 concurrent buffer loads; each onComplete sets its slot and counts down.
                // We verify: slots are not dirty before any completion, then all dirty after all complete.
                long uboSize = 64; // mat4 = 16 floats
                try (ManagedBuffer buf0 = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, uboSize, BufferUsage.UNIFORM, device, queue);
                     ManagedBuffer buf1 = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, uboSize, BufferUsage.UNIFORM, device, queue);
                     ManagedBuffer buf2 = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, uboSize, BufferUsage.UNIFORM, device, queue)) {

                    // Three separate shader instances so each has its own independent camera slot
                    try (ShaderInstance inst0 = ShaderInstance.from(VERT_COMPLEX, device);
                         ShaderInstance inst1 = ShaderInstance.from(VERT_COMPLEX, device);
                         ShaderInstance inst2 = ShaderInstance.from(VERT_COMPLEX, device)) {

                        UniformBufferSlot<Object> slot0 = inst0.getUniformBufferSlot("camera", Object.class);
                        UniformBufferSlot<Object> slot1 = inst1.getUniformBufferSlot("camera", Object.class);
                        UniformBufferSlot<Object> slot2 = inst2.getUniformBufferSlot("camera", Object.class);

                        check("slot0 not dirty before load", !slot0.isDirty());
                        check("slot1 not dirty before load", !slot1.isDirty());
                        check("slot2 not dirty before load", !slot2.isDirty());

                        CountDownLatch latch = new CountDownLatch(3);

                        TransferCompletion tc0 = buf0.writeAsync(identityMat4(), 0, queue);
                        TransferCompletion tc1 = buf1.writeAsync(identityMat4(), 0, queue);
                        TransferCompletion tc2 = buf2.writeAsync(identityMat4(), 0, queue);

                        check("slot0 not dirty immediately after writeAsync", !slot0.isDirty());
                        check("slot1 not dirty immediately after writeAsync", !slot1.isDirty());
                        check("slot2 not dirty immediately after writeAsync", !slot2.isDirty());

                        tc0.flush(device, queue);
                        tc1.flush(device, queue);
                        tc2.flush(device, queue);

                        tc0.onComplete(() -> { slot0.set(buf0); latch.countDown(); });
                        tc1.onComplete(() -> { slot1.set(buf1); latch.countDown(); });
                        tc2.onComplete(() -> { slot2.set(buf2); latch.countDown(); });

                        boolean allDone = latch.await(5, TimeUnit.SECONDS);
                        check("all 3 async loads completed within 5s", allDone);
                        check("slot0 dirty after completion", slot0.isDirty());
                        check("slot1 dirty after completion", slot1.isDirty());
                        check("slot2 dirty after completion", slot2.isDirty());

                        System.out.println("  buf0 size=" + slot0.boundBuffer().size());
                        System.out.println("  buf1 size=" + slot1.boundBuffer().size());
                        System.out.println("  buf2 size=" + slot2.boundBuffer().size());
                    }
                }
            }

            device.close();
            Vulkan.destroyInstance(instance);
            System.out.println("\nAll shader tests passed.");
        }
    }

    /** @return a 4x4 identity matrix as a little-endian float ByteBuffer (64 bytes). */
    private static ByteBuffer identityMat4() {
        ByteBuffer buf = ByteBuffer.allocate(64);
        float[] identity = {
            1,0,0,0,
            0,1,0,0,
            0,0,1,0,
            0,0,0,1
        };
        for (float f : identity) buf.putFloat(f);
        return buf.rewind();
    }

    private static void section(String name) {
        System.out.println("\n--- " + name + " ---");
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  PASS  " + label);
        } else {
            System.err.println("  FAIL  " + label);
            throw new AssertionError(label + " failed");
        }
    }
}
