package io.github.yetyman.vulkan.shaders;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.ManagedBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.buffers.TransferCompletion;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.commands.TransientCommandBuffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ShaderExample {
    static {
        VulkanLibrary.load();
    }

    // Shaders in sample-app resources — must be on classpath at runtime
    static final String VERT_SIMPLE = "/shaders/triangle.vert";  // push constant: float time
    static final String VERT_COMPLEX = "/shaders/gltf.vert";      // UBO: camera, push constants: visualizationMode/lodLevel/splitScreenOffset

    /**
     * Inline shader that densely exercises all specialization features:
     *   - layout(constant_id=N) spec constants: bool enableFog, float fogDensity, int maxLights
     *   - #ifdef ENABLE_SHADOWS define-based conditional
     *   - push constant: float time
     *   - UBO: camera (set 0, binding 0)
     *   - SSBO: lights (set 0, binding 1)
     *   - texture sampler: albedo (set 1, binding 0)
     *   - optional shadowMap sampler (set 0, binding 2) — only present when ENABLE_SHADOWS defined
     *
     * Compiled on-the-fly via compileGlslString() — not a classpath resource.
     */
    /**
     * Simple compute shader: reads from inputBuf, doubles each uint, writes to outputBuf.
     * Push constant 'multiplier' overrides the factor (default 2).
     */
    static final String COMPUTE_SHADER_GLSL = String.join("\n",
            "#version 450",
            "layout(local_size_x = 64) in;",
            "layout(set = 0, binding = 0) readonly  buffer InputBuf  { uint data[]; } inputBuf;",
            "layout(set = 0, binding = 1) writeonly buffer OutputBuf { uint data[]; } outputBuf;",
            "layout(push_constant) uniform PC { uint multiplier; } pc;",
            "void main() {",
            "    uint idx = gl_GlobalInvocationID.x;",
            "    outputBuf.data[idx] = inputBuf.data[idx] * pc.multiplier;",
            "}"
    );

    /**
     * Compute shader with a specialization constant controlling the multiplier at pipeline creation.
     */
    static final String COMPUTE_SPEC_SHADER_GLSL = String.join("\n",
            "#version 450",
            "layout(local_size_x = 64) in;",
            "layout(constant_id = 0) const uint MULTIPLIER = 2;",
            "layout(set = 0, binding = 0) readonly  buffer InputBuf  { uint data[]; } inputBuf;",
            "layout(set = 0, binding = 1) writeonly buffer OutputBuf { uint data[]; } outputBuf;",
            "void main() {",
            "    uint idx = gl_GlobalInvocationID.x;",
            "    outputBuf.data[idx] = inputBuf.data[idx] * MULTIPLIER;",
            "}"
    );

    static final String SPEC_SHADER_GLSL = String.join("\n",
            "#version 450",
            "#extension GL_ARB_separate_shader_objects : enable",
            "",
            "layout(constant_id = 0) const bool  enableFog  = true;",
            "layout(constant_id = 1) const float fogDensity = 0.02;",
            "layout(constant_id = 2) const int   maxLights  = 4;",
            "",
            "#ifdef ENABLE_SHADOWS",
            "layout(set = 0, binding = 2) uniform sampler2D shadowMap;",
            "#endif",
            "",
            "layout(push_constant) uniform PC { float time; } pc;",
            "",
            "layout(set = 0, binding = 0) uniform CameraUBO { mat4 viewProj; } camera;",
            "",
            "struct Light { vec4 positionAndRadius; vec4 colorAndIntensity; };",
            "layout(set = 0, binding = 1) readonly buffer LightBuffer { Light lights[]; } lightBuf;",
            "",
            "layout(set = 1, binding = 0) uniform sampler2D albedo;",
            "",
            "layout(location = 0) in  vec3 inPosition;",
            "layout(location = 0) out vec4 outColor;",
            "",
            "void main() {",
            "    vec4 color = texture(albedo, vec2(inPosition.xy));",
            "    for (int i = 0; i < maxLights; i++) {",
            "        Light l = lightBuf.lights[i];",
            "        color.rgb += l.colorAndIntensity.rgb * l.colorAndIntensity.a;",
            "    }",
            "    if (enableFog) {",
            "        float fog = exp(-fogDensity * inPosition.z);",
            "        color.rgb = mix(vec3(0.5), color.rgb, fog);",
            "    }",
            "    #ifdef ENABLE_SHADOWS",
            "    color.rgb *= texture(shadowMap, vec2(inPosition.xy)).r;",
            "    #endif",
            "    outColor = color + vec4(pc.time * 0.0001);",
            "}"
    );

    public static void main(String[] args) throws Exception {
        try (Arena arena = Arena.ofConfined()) {
            // --- Vulkan instance + device ---
            VkInstance instance = VkInstance.builder()
                    .applicationName("ShaderExample")
                    .build(arena);

            VkPhysicalDevice physicalDevice = instance.pickFirstPhysicalDevice(arena);
            int queueFamily = VkQueueFamily.findGraphics(physicalDevice, arena);

            VkDevice device = VkDevice.builder()
                    .instance(instance)
                    .physicalDevice(physicalDevice)
                    .queueFamily(queueFamily)
                    .build(arena);
            VkQueue queue = device.getQueue(queueFamily, arena);

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
            try (ShaderInstance simple = ShaderLoader.compileShader(VERT_SIMPLE).createInstance(device)) {
                check("ShaderInstance created (triangle.vert)", simple != null);
                check("compiled() not null", simple.compiled() != null);

                PushConstant<Float> time = simple.getPushConstant("time", Float.class);
                check("getPushConstant 'time' not null", time != null);
                check("'time' not dirty initially", !time.isDirty());
                time.set(1.5f);
                check("'time' dirty after set()", time.isDirty());
                System.out.println("  push constant 'time': offset=" + time.offset() + " size=" + time.size());
            }

            try (ShaderInstance complex = ShaderLoader.compileShader(VERT_COMPLEX).createInstance(device)) {
                check("ShaderInstance created (gltf.vert)", complex != null);

                PushConstant<Integer> vizMode = complex.getPushConstant("visualizationMode", Integer.class);
                PushConstant<Integer> lod = complex.getPushConstant("lodLevel", Integer.class);
                PushConstant<Float> split = complex.getPushConstant("splitScreenOffset", Float.class);
                check("getPushConstant 'visualizationMode'", vizMode != null);
                check("getPushConstant 'lodLevel'", lod != null);
                check("getPushConstant 'splitScreenOffset'", split != null);

                UniformBufferSlot cameraSlot = complex.getUniformBufferSlot("camera");
                check("getUniformBufferSlot 'camera'", cameraSlot != null);
                check("'camera' slot not dirty initially", !cameraSlot.isDirty());
                System.out.println("  descriptor sets: " + complex.descriptorSets().size());
                System.out.println("  layouts: " + complex.layouts().size());
            }

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
            // =========================================================
            section("DIRTY FLAG / ASYNC BUFFER LOAD");
            long uboSize = 64;
            try (ManagedBuffer buf0 = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, uboSize, BufferUsage.UNIFORM, device, queue);
                 ManagedBuffer buf1 = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, uboSize, BufferUsage.UNIFORM, device, queue);
                 ManagedBuffer buf2 = BufferFactory.create(MemoryStrategy.DEVICE_LOCAL, null, uboSize, BufferUsage.UNIFORM, device, queue)) {

                try (ShaderInstance inst0 = complexCompiled.createInstance(device);
                     ShaderInstance inst1 = complexCompiled.createInstance(device);
                     ShaderInstance inst2 = complexCompiled.createInstance(device)) {

                    UniformBufferSlot slot0 = inst0.getUniformBufferSlot("camera");
                    UniformBufferSlot slot1 = inst1.getUniformBufferSlot("camera");
                    UniformBufferSlot slot2 = inst2.getUniformBufferSlot("camera");

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

                    tc0.onComplete(() -> {
                        slot0.set(buf0);
                        latch.countDown();
                    });
                    tc1.onComplete(() -> {
                        slot1.set(buf1);
                        latch.countDown();
                    });
                    tc2.onComplete(() -> {
                        slot2.set(buf2);
                        latch.countDown();
                    });

                    boolean allDone = latch.await(5, TimeUnit.SECONDS);
                    check("all 3 async loads completed within 5s", allDone);
                    check("slot0 dirty after completion", slot0.isDirty());
                    check("slot1 dirty after completion", slot1.isDirty());
                    check("slot2 dirty after completion", slot2.isDirty());

                    System.out.println("  buf0 size=" + slot0.buffer().size());
                    System.out.println("  buf1 size=" + slot1.buffer().size());
                    System.out.println("  buf2 size=" + slot2.buffer().size());
                }
            }

            // =========================================================
            // 5. SPECIALIZATION CONSTANTS — raw ShaderInstance API
            //    Compile the inline shader, reflect its spec constants,
            //    then create two instances with different specializations.
            //    Each instance gets a distinct VkSpecializationInfo for
            //    use at pipeline creation time.
            // =========================================================
            section("SPECIALIZATION CONSTANTS — raw API");

            CompiledShader specCompiled = ShaderLoader.fragment()
                    .source(SPEC_SHADER_GLSL)
                    .compileShader();
            check("inline spec shader compiled", specCompiled.getSpirV().length > 0);

            List<ShaderLoader.SpecializationConstantInfo> specConstants =
                    specCompiled.getReflection().getSpecializationConstants();
            check("spec constants reflected", !specConstants.isEmpty());
            check("3 spec constants reflected", specConstants.size() == 3);
            System.out.println("  reflected spec constants: " + specConstants.size());
            for (ShaderLoader.SpecializationConstantInfo sc : specConstants) {
                System.out.println("    constant_id=" + sc.constantId()
                        + " name='" + sc.name() + "'"
                        + " isBool=" + sc.isBool()
                        + " isFloat=" + sc.isFloat()
                        + " isInt=" + sc.isInt()
                        + " default=" + (sc.isBool() ? sc.defaultBool() : sc.isFloat() ? sc.defaultFloat() : sc.defaultInt()));
            }

            // Instance A: defaults — fog on, density 0.02, maxLights 4
            try (ShaderInstance instA = specCompiled.instanceBuilder(device)
                    .specialize("enableFog", true).specialize("fogDensity", 0.02f).specialize("maxLights", 4)
                    .build()) {

                check("instA enableFog = true", Boolean.TRUE.equals(instA.getSpecializationConstant("enableFog")));
                check("instA fogDensity = 0.02f", Float.compare(0.02f, (Float) instA.getSpecializationConstant("fogDensity")) == 0);
                check("instA maxLights = 4", Integer.valueOf(4).equals(instA.getSpecializationConstant("maxLights")));
                check("instA defines empty", instA.defines().isEmpty());

                try (Arena specArena = Arena.ofConfined()) {
                    MemorySegment specInfo = instA.buildSpecializationInfo(specArena);
                    check("instA buildSpecializationInfo not NULL", !specInfo.equals(MemorySegment.NULL));
                    System.out.println("  instA VkSpecializationInfo @ " + specInfo);
                }
            }

            // Instance B: fog off, maxLights 8 — different specialization, same SPIR-V
            try (ShaderInstance instB = specCompiled.instanceBuilder(device)
                    .specialize("enableFog", false).specialize("fogDensity", 0.5f).specialize("maxLights", 8)
                    .build()) {

                check("instB enableFog = false", Boolean.FALSE.equals(instB.getSpecializationConstant("enableFog")));
                check("instB fogDensity = 0.5f", Float.compare(0.5f, (Float) instB.getSpecializationConstant("fogDensity")) == 0);
                check("instB maxLights = 8", Integer.valueOf(8).equals(instB.getSpecializationConstant("maxLights")));

                try (Arena specArena = Arena.ofConfined()) {
                    MemorySegment specInfo = instB.buildSpecializationInfo(specArena);
                    check("instB buildSpecializationInfo not NULL", !specInfo.equals(MemorySegment.NULL));
                }
            }

            // =========================================================
            // 6. DEFINE-BASED SPECIALIZATION — separate compiled variant
            //    ENABLE_SHADOWS adds a shadowMap binding to the SPIR-V.
            //    The two CompiledShaders are distinct objects with different
            //    SPIR-V; ShaderInstance wraps each independently.
            // =========================================================
            section("DEFINE-BASED SPECIALIZATION");

            CompiledShader noShadowsCompiled = ShaderLoader.fragment()
                    .source(SPEC_SHADER_GLSL)
                    .compileShader();

            CompiledShader withShadowsCompiled = ShaderLoader.fragment()
                    .source(SPEC_SHADER_GLSL)
                    .define("ENABLE_SHADOWS", "1")
                    .compileShader();

            check("distinct compiled variants", noShadowsCompiled != withShadowsCompiled);

            try (ShaderInstance noShadows = noShadowsCompiled.createInstance(device);
                 ShaderInstance withShadows = withShadowsCompiled.createInstance(device)) {

                System.out.println("  noShadows  SPIR-V bytes: " + noShadows.compiled().getSpirV().length);
                System.out.println("  withShadows SPIR-V bytes: " + withShadows.compiled().getSpirV().length);

                boolean noShadowMap = noShadows.getBindingByName("shadowMap") == null;
                boolean hasShadowMap = withShadows.getBindingByName("shadowMap") != null;
                check("noShadows has no shadowMap binding", noShadowMap);
                check("withShadows has shadowMap binding", hasShadowMap);
            }

            // =========================================================
            // 7. GENERATED SHADER CLASS
            //    Generate GltfVertShader (no spec constants, no defines) to verify
            //    the plain path. Then generate InlineSpecFragShader from specCompiled
            //    (has spec constants) and withShadowsCompiled (has defines) and print
            //    both for comparison against the expected shapes below.
            //
            // Expected shape — no spec constants (GltfVertShader):
            //
            //   public class GltfVertShader implements AutoCloseable {
            //       public final ShaderInstance shader;
            //       public final PushConstant<Integer> visualizationMode;
            //       public final PushConstant<Integer> lodLevel;
            //       public final PushConstant<Float>   splitScreenOffset;
            //       public final UniformBufferSlot<?>  camera;  // set 0
            //
            //       private GltfVertShader(ShaderInstance shader) { ... }
            //       public void flush(VkCommandBuffer cmd) { shader.flush(cmd); }
            //       public void close() { shader.close(); }
            //
            //       public static Builder builder(VkDevice device) { ... }
            //       public static class Builder {
            //           private Map<String, String> defines = Map.of();
            //           public Builder defines(Map<String, String> defines) { ... }
            //           public GltfVertShader build() { ... }
            //       }
            //   }
            //
            // Expected shape — spec constants + defines (InlineSpecFragShader, withShadows variant):
            //
            //   public class InlineSpecFragShader implements AutoCloseable {
            //       public final ShaderInstance shader;
            //
            //       // Preprocessor defines (static only — baked into SPIR-V at compile time)
            //       public final String ENABLE_SHADOWS = "1";
            //
            //       // Specialization constant defaults
            //       public static final boolean DEFAULT_ENABLE_FOG  = true;
            //       public static final float   DEFAULT_FOG_DENSITY = 0.02f;
            //       public static final int     DEFAULT_MAX_LIGHTS  = 4;
            //
            //       // Specialization constant instance values (set at build time, immutable)
            //       public final boolean enableFog;
            //       public final float   fogDensity;
            //       public final int     maxLights;
            //
            //       // Descriptor slots
            //       public final UniformBufferSlot<?> camera;   // set 0, binding 0
            //       public final StorageBufferSlot<?> lightBuf; // set 0, binding 1
            //       public final TextureSlot          shadowMap; // set 0, binding 2 (only present with ENABLE_SHADOWS)
            //       public final TextureSlot          albedo;   // set 1, binding 0
            //
            //       // Push constants
            //       public final PushConstant<Float> time;
            //
            //       public boolean isEnableFog()   { return enableFog; }
            //       public float   getFogDensity() { return fogDensity; }
            //       public int     getMaxLights()  { return maxLights; }
            //
            //       private InlineSpecFragShader(ShaderInstance shader, boolean enableFog, float fogDensity, int maxLights) { ... }
            //       public void flush(VkCommandBuffer cmd) { shader.flush(cmd); }
            //       public void close() { shader.close(); }
            //
            //       public static Builder builder(VkDevice device) { ... }
            //       public static class Builder {
            //           private Map<String, String> defines = Map.of();
            //           private boolean enableFog  = DEFAULT_ENABLE_FOG;
            //           private float   fogDensity = DEFAULT_FOG_DENSITY;
            //           private int     maxLights  = DEFAULT_MAX_LIGHTS;
            //
            //           public Builder defines(Map<String, String> defines) { ... }
            //           /** Sets specialization constant 'enableFog' (default: true). */
            //           public Builder enableFog(boolean value) { ... }
            //           /** Sets specialization constant 'fogDensity' (default: 0.02f). */
            //           public Builder fogDensity(float value) { ... }
            //           /** Sets specialization constant 'maxLights' (default: 4). */
            //           public Builder maxLights(int value) { ... }
            //
            //           public InlineSpecFragShader build() { ... }
            //       }
            //   }
            // =========================================================
            section("GENERATED SHADER CLASS");

            Path specTmpDir = Files.createTempDirectory("shader-gen-spec-test");
            ShaderGenerator.generate(VERT_COMPLEX, specTmpDir, "io.github.yetyman.vulkan.shaders.generated");
            Path gltfGenFile = specTmpDir.resolve("GltfVertShader.java");
            check("GltfVertShader.java generated", gltfGenFile.toFile().exists());
            String gltfSource = Files.readString(gltfGenFile);
            check("gltf has no spec constant defaults", !gltfSource.contains("DEFAULT_"));
            check("gltf has no defines constants", !gltfSource.contains("public final String"));
            check("gltf builder has defines() setter", gltfSource.contains("defines(Map"));
            System.out.println("  GltfVertShader.java — no spec constants, no defines");
            System.out.println(generatedSource);

            // Now generate a class from SPEC_SHADER_GLSL itself and print it for comparison.
            // Expected shape: Specializations record with enableFog/fogDensity/maxLights fields.
            Path specGenDir = Files.createTempDirectory("shader-gen-spec");
            ShaderGenerator.generate(specCompiled, "/shaders/inline-spec.frag", specGenDir, "io.github.yetyman.vulkan.shaders.generated");
            Path specGenFile = specGenDir.resolve("InlineSpecFragShader.java");
            check("spec shader class generated", specGenFile.toFile().exists());
            String specGenSource = Files.readString(specGenFile);
            check("Builder class present", specGenSource.contains("public static class Builder"));
            check("enableFog setter present", specGenSource.contains("enableFog(boolean"));
            check("fogDensity setter present", specGenSource.contains("fogDensity(float"));
            check("maxLights setter present", specGenSource.contains("maxLights(int"));
            check("DEFAULT_ENABLE_FOG present", specGenSource.contains("DEFAULT_ENABLE_FOG"));
            check("DEFAULT_FOG_DENSITY present", specGenSource.contains("DEFAULT_FOG_DENSITY"));
            check("DEFAULT_MAX_LIGHTS present", specGenSource.contains("DEFAULT_MAX_LIGHTS"));
            check("enableFog instance field", specGenSource.contains("public final boolean enableFog"));
            check("fogDensity instance field", specGenSource.contains("public final float fogDensity"));
            check("maxLights instance field", specGenSource.contains("public final int maxLights"));
            check("no defines constant (no-shadows variant)", !specGenSource.contains("public final String"));
            System.out.println("  --- InlineSpecFragShader.java (no shadows) ---");
            System.out.println(specGenSource);
            System.out.println("  --- end ---");

            // withShadows variant — verify defines are stored on the compiled shader
            // and that the generator emits them as static final String constants.
            check("withShadows has ENABLE_SHADOWS define",
                    "1".equals(withShadowsCompiled.getDefines().get("ENABLE_SHADOWS")));

            Path withShadowsGenDir = Files.createTempDirectory("shader-gen-shadows");
            ShaderGenerator.generate(withShadowsCompiled, "/shaders/inline-spec.frag", withShadowsGenDir, "io.github.yetyman.vulkan.shaders.generated");
            Path withShadowsGenFile = withShadowsGenDir.resolve("InlineSpecFragShader.java");
            String withShadowsGenSource = Files.readString(withShadowsGenFile);
            check("ENABLE_SHADOWS define constant present",
                    withShadowsGenSource.contains("public final String ENABLE_SHADOWS"));
            System.out.println("  --- InlineSpecFragShader.java (with shadows) ---");
            System.out.println(withShadowsGenSource);
            System.out.println("  --- end ---");

            // =========================================================
            // 8. BARE-BONES COMPUTE SHADER — manual pipeline + dispatch
            //    Compiles a compute shader, creates pipeline/descriptors
            //    by hand, dispatches, reads back results, and asserts.
            // =========================================================
            section("BARE-BONES COMPUTE SHADER");
            {
                int elementCount = 64;
                int bufferSize = elementCount * 4; // uint = 4 bytes
                int multiplier = 3;

                // Compile
                byte[] compSpirv = ShaderLoader.compute()
                        .source(COMPUTE_SHADER_GLSL)
                        .compile();
                check("compute shader compiled", compSpirv.length > 0);

                // Buffers: host-visible so we can write input and read output directly
                try (ManagedBuffer inputBuf = BufferFactory.create(MemoryStrategy.MAPPED, null, bufferSize, BufferUsage.STORAGE, device, queue);
                     ManagedBuffer outputBuf = BufferFactory.create(MemoryStrategy.MAPPED, null, bufferSize, BufferUsage.STORAGE, device, queue)) {

                    // Fill input: [0, 1, 2, ..., 63]
                    ByteBuffer inputData = ByteBuffer.allocate(bufferSize).order(ByteOrder.nativeOrder());
                    for (int i = 0; i < elementCount; i++) inputData.putInt(i);
                    inputBuf.write(inputData.flip(), 0, queue);

                    // Descriptor group: layout + pool + set + buffer bindings in one shot
                    try (DescriptorGroup descriptors = DescriptorGroup.builder()
                            .device(device)
                            .stageFlags(VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value())
                            .storageBuffer(0, inputBuf)
                            .storageBuffer(1, outputBuf)
                            .build(arena)) {

                        // Pipeline
                        try (VkComputePipeline pipeline = VkComputePipeline.builder()
                                .device(device)
                                .computeShader(compSpirv)
                                .descriptorSetLayouts(descriptors.layoutHandle())
                                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value(), 0, 4)
                                .build(arena)) {

                            // Record, push constant, dispatch, and wait
                            VkCommandPool cmdPool = VkCommandPool.create(arena, device, queueFamily);
                            TransientCommandBuffer tcb = TransientCommandBuffer.begin(cmdPool, queue, arena);
                            pipeline.bind(tcb.handle());
                            descriptors.set().bind(tcb.handle(), pipeline, 0, arena);
                            pipeline.pushInt(tcb.handle(), 0, multiplier);
                            VkComputePipeline.dispatch(tcb.handle(), elementCount / 64, 1, 1);
                            tcb.submitAndWait();
                            tcb.close();
                        }

                        // Readback and verify
                        ByteBuffer result = outputBuf.read(0, bufferSize).order(ByteOrder.nativeOrder());
                        boolean allCorrect = true;
                        for (int i = 0; i < elementCount; i++) {
                            int expected = i * multiplier;
                            int actual = result.getInt(i * 4);
                            if (actual != expected) {
                                System.err.println("    MISMATCH [" + i + "]: expected=" + expected + " actual=" + actual);
                                allCorrect = false;
                            }
                        }
                        check("all " + elementCount + " elements = input * " + multiplier, allCorrect);
                        System.out.println("  bare-bones compute: " + elementCount + " elements verified");
                    }
                }
            }

            // =========================================================
            // 9. SHADER INSTANCE COMPUTE — reflection-driven setup
            //    Same idea but uses CompiledShader + ShaderInstance to
            //    reflect bindings and spec constants, then builds the
            //    pipeline from reflected descriptor set layouts.
            //    Note: SPIRV-Reflect has an alignment bug with compute
            //    shaders in the current native binding, so we demonstrate
            //    the pattern with manual fallback where reflection fails.
            // =========================================================
            section("SHADER INSTANCE COMPUTE");
            {
                int elementCount = 64;
                int bufferSize = elementCount * 4;
                int specMultiplier = 5;

                CompiledShader computeShader = ShaderLoader.compute()
                        .source(COMPUTE_SPEC_SHADER_GLSL)
                        .compileShader();
                check("spec compute shader compiled", computeShader.getSpirV().length > 0);

                // Check what reflection found
                ShaderLoader.ShaderReflection shaderReflection = computeShader.getReflection();
                System.out.println("spec constants: " + shaderReflection.getSpecializationConstants().size()
                        + ", descriptor sets: " + shaderReflection.getDescriptorSets().size());

                // Buffers
                try (ManagedBuffer inputBuf = BufferFactory.create(MemoryStrategy.MAPPED, null, bufferSize, BufferUsage.STORAGE, device, queue);
                     ManagedBuffer outputBuf = BufferFactory.create(MemoryStrategy.MAPPED, null, bufferSize, BufferUsage.STORAGE, device, queue)) {

                    ByteBuffer inputData = ByteBuffer.allocate(bufferSize).order(ByteOrder.nativeOrder());
                    for (int i = 0; i < elementCount; i++) inputData.putInt(i + 10); // [10, 11, ..., 73]
                    inputBuf.write(inputData.flip(), 0, queue);

                    // Reflected layout + pool + set + buffer bindings in one shot
                    try (DescriptorGroup descriptors = computeShader.descriptorGroup(device)
                            .buffer("inputBuf", inputBuf)
                            .buffer("outputBuf", outputBuf)
                            .build(arena)) {

                        // Build pipeline with specialization constant + dispatchAndWait
                        try (VkComputePipeline pipeline = VkComputePipeline.builder()
                                .device(device)
                                .computeShader(computeShader)
                                .specialize(0, specMultiplier)
                                .build(arena)) {

                            pipeline.dispatchAndWait(queue, elementCount / 64, descriptors.set());

                            // Readback and verify
                            ByteBuffer result = outputBuf.read(0, bufferSize).order(ByteOrder.nativeOrder());
                            boolean allCorrect = true;
                            for (int i = 0; i < elementCount; i++) {
                                int expected = (i + 10) * specMultiplier;
                                int actual = result.getInt(i * 4);
                                if (actual != expected) {
                                    System.err.println("    MISMATCH [" + i + "]: expected=" + expected + " actual=" + actual);
                                    allCorrect = false;
                                }
                            }
                            check("all " + elementCount + " elements = input * " + specMultiplier + " (spec constant)", allCorrect);
                            System.out.println("  ShaderInstance compute: " + elementCount + " elements verified");
                        }
                    }
                }
            }

            // =========================================================
            // 10. SHADER INSTANCE COMPUTE — swap descriptor group only
            //     Same pipeline, same shader, same spec constant.
            //     Two independent descriptor groups bound to different
            //     buffer pairs — demonstrates re-binding descriptors
            //     without rebuilding the pipeline.
            // =========================================================
            section("SHADER INSTANCE COMPUTE — SWAP DESCRIPTOR GROUP");
            {
                int elementCount = 64;
                int bufferSize = elementCount * 4;
                int specMultiplier = 5;

                CompiledShader computeShader = ShaderLoader.compute()
                        .source(COMPUTE_SPEC_SHADER_GLSL)
                        .compileShader();

                // First pair: [10..73]
                try (ManagedBuffer inputBufA = BufferFactory.create(MemoryStrategy.MAPPED, null, bufferSize, BufferUsage.STORAGE, device, queue);
                     ManagedBuffer outputBufA = BufferFactory.create(MemoryStrategy.MAPPED, null, bufferSize, BufferUsage.STORAGE, device, queue);
                     // Second pair: [100..163]
                     ManagedBuffer inputBufB = BufferFactory.create(MemoryStrategy.MAPPED, null, bufferSize, BufferUsage.STORAGE, device, queue);
                     ManagedBuffer outputBufB = BufferFactory.create(MemoryStrategy.MAPPED, null, bufferSize, BufferUsage.STORAGE, device, queue)) {

                    ByteBuffer inputDataA = ByteBuffer.allocate(bufferSize).order(ByteOrder.nativeOrder());
                    for (int i = 0; i < elementCount; i++) inputDataA.putInt(i + 10);
                    inputBufA.write(inputDataA.flip(), 0, queue);

                    ByteBuffer inputDataB = ByteBuffer.allocate(bufferSize).order(ByteOrder.nativeOrder());
                    for (int i = 0; i < elementCount; i++) inputDataB.putInt(i + 100);
                    inputBufB.write(inputDataB.flip(), 0, queue);

                    try (DescriptorGroup descriptorsA = computeShader.descriptorGroup(device)
                            .buffer("inputBuf", inputBufA)
                            .buffer("outputBuf", outputBufA)
                            .build(arena);
                         DescriptorGroup descriptorsB = computeShader.descriptorGroup(device)
                                 .buffer("inputBuf", inputBufB)
                                 .buffer("outputBuf", outputBufB)
                                 .build(arena)) {

                        try (VkComputePipeline pipeline = VkComputePipeline.builder()
                                .device(device)
                                .computeShader(computeShader)
                                .specialize(0, specMultiplier)
                                .build(arena)) {

                            pipeline.dispatchAndWait(queue, elementCount / 64, descriptorsA.set());
                            pipeline.dispatchAndWait(queue, elementCount / 64, descriptorsB.set());
                        }
                    }

                    // Verify A: [10..73] * 5
                    ByteBuffer resultA = outputBufA.read(0, bufferSize).order(ByteOrder.nativeOrder());
                    boolean allCorrectA = true;
                    for (int i = 0; i < elementCount; i++) {
                        int expected = (i + 10) * specMultiplier;
                        int actual = resultA.getInt(i * 4);
                        if (actual != expected) {
                            System.err.println("    A MISMATCH [" + i + "]: expected=" + expected + " actual=" + actual);
                            allCorrectA = false;
                        }
                    }
                    check("descriptorA: all " + elementCount + " elements = (i+10) * " + specMultiplier, allCorrectA);

                    // Verify B: [100..163] * 5
                    ByteBuffer resultB = outputBufB.read(0, bufferSize).order(ByteOrder.nativeOrder());
                    boolean allCorrectB = true;
                    for (int i = 0; i < elementCount; i++) {
                        int expected = (i + 100) * specMultiplier;
                        int actual = resultB.getInt(i * 4);
                        if (actual != expected) {
                            System.err.println("    B MISMATCH [" + i + "]: expected=" + expected + " actual=" + actual);
                            allCorrectB = false;
                        }
                    }
                    check("descriptorB: all " + elementCount + " elements = (i+100) * " + specMultiplier, allCorrectB);
                    System.out.println("  swap descriptor group: both passes verified");
                }
            }

            instance.close(); // closes owned devices + instance
            System.out.println("\nAll shader tests passed.");
        }
    }


    /**
     * @return a 4x4 identity matrix as a little-endian float ByteBuffer (64 bytes).
     */
    private static ByteBuffer identityMat4() {
        ByteBuffer buf = ByteBuffer.allocate(64);
        float[] identity = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
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
