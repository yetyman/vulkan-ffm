package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.loop.ComputeLoop;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.*;
import java.util.Random;

public class NBodyGraphicsFrame extends SimpleGraphicsFrame {

    // Particle struct: vec2 pos + vec2 vel = 4 floats = 16 bytes
    private static final int PARTICLE_STRIDE = 16;
    private static final int PARTICLE_COUNT  = 4096;

    private static final float DT         = 0.0002f;
    private static final float SOFTENING  = 0.01f;
    private static final float GRAVITY    = 0.0001f;

    private VkBuffer particleBuffer;

    private VkDescriptorSetLayout computeLayout;
    private VkDescriptorPool      computePool;
    private VkDescriptorSet       computeSet;
    private VkComputePipeline     computePipeline;

    private VkDescriptorSetLayout graphicsLayout;
    private VkDescriptorPool      graphicsPool;
    private VkDescriptorSet       graphicsSet;

    private VkTimelineSemaphore computeDone;

    public NBodyGraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                              MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        long bufSize = (long) PARTICLE_COUNT * PARTICLE_STRIDE;

        particleBuffer = VkBuffer.builder()
                .device(device)
                .size(bufSize)
                .storageBuffer()
                .vertexBuffer()
                .hostVisible()
                .build(arena);

        // Seed particles in a disc with random velocities
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment mapped = particleBuffer.map(tmp);
            Random rng = new Random(42);
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                double angle  = rng.nextDouble() * 2.0 * Math.PI;
                double radius = Math.sqrt(rng.nextDouble()) * 0.6;
                float px = (float) (Math.cos(angle) * radius);
                float py = (float) (Math.sin(angle) * radius);
                // Tangential velocity for initial rotation
                float vx = (float) (-Math.sin(angle) * radius * 0.3);
                float vy = (float) ( Math.cos(angle) * radius * 0.3);
                long base = (long) i * PARTICLE_STRIDE;
                mapped.set(ValueLayout.JAVA_FLOAT, base,      px);
                mapped.set(ValueLayout.JAVA_FLOAT, base + 4,  py);
                mapped.set(ValueLayout.JAVA_FLOAT, base + 8,  vx);
                mapped.set(ValueLayout.JAVA_FLOAT, base + 12, vy);
            }
            particleBuffer.unmap();
        }

        int computeStage = VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value();

        computeLayout = VkDescriptorSetLayout.builder()
                .device(device)
                .storageBuffer(0, computeStage)
                .build(arena);

        computePool = VkDescriptorPool.builder()
                .device(device)
                .maxSets(1)
                .storageBuffers(1)
                .build(arena);

        computeSet = computePool.allocateDescriptorSet(computeLayout);
        try (Arena tmp = Arena.ofConfined()) {
            computeSet.bind(0, particleBuffer, tmp);
        }

        computePipeline = VkComputePipeline.builder()
                .device(device)
                .computeShader(ShaderLoader.builder("/shaders/nbody.comp").compile())
                .descriptorSetLayouts(computeLayout.handle())
                .pushConstantRange(computeStage, 0, 16) // int count, float dt, float softening, float gravity
                .build(arena);

        int vertStage = VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value();

        graphicsLayout = VkDescriptorSetLayout.builder()
                .device(device)
                .storageBuffer(0, vertStage)
                .build(arena);

        graphicsPool = VkDescriptorPool.builder()
                .device(device)
                .maxSets(1)
                .storageBuffers(1)
                .build(arena);

        graphicsSet = graphicsPool.allocateDescriptorSet(graphicsLayout);
        try (Arena tmp = Arena.ofConfined()) {
            graphicsSet.bind(0, particleBuffer, tmp);
        }

        computeDone = VkTimelineSemaphore.create(device, 0, arena);

        super.initializeResources(queueFamilyIndex);
    }

    @Override
    protected VkPipeline createPipeline() {
        VkPipeline.Builder pb = VkPipeline.builder()
                .device(device)
                .vertexShader(ShaderLoader.builder("/shaders/nbody.vert").compile())
                .fragmentShader(ShaderLoader.builder("/shaders/nbody.frag").compile())
                .pointTopology()
                .dynamicViewport()
                .dynamicScissor()
                .alphaBlend()
                .descriptorSetLayouts(graphicsLayout.handle());

        if (useDynamicRendering) {
            pb.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        } else {
            pb.renderPass(renderPass.handle());
        }

        return pb.build(arena);
    }

    ComputeLoop buildComputeLoop(VkQueue computeQueue) {
        addTimelineWait(computeDone, VkPipelineStageFlagBits.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT.value());
        ComputeLoop computeLoop = ComputeLoop.builder()
                .device(device)
                .queue(computeQueue)
                .queueFamilyIndex(computeQueue.familyIndex())
                .semaphore(computeDone)
                .driver(LoopDriver.uncapped())
                .name("nbody-compute")
                .work((cmd, generation, frameAllocator) -> {
                    computePipeline.bind(cmd);
                    computeSet.bind(cmd, computePipeline, 0, frameAllocator);

                    // push_constant layout: int count, float dt, float softening, float gravity
                    computePipeline.pushInt(cmd, 0, PARTICLE_COUNT);
                    computePipeline.pushFloat(cmd, 4, DT);
                    computePipeline.pushFloat(cmd, 8, SOFTENING);
                    computePipeline.pushFloat(cmd, 12, GRAVITY);

                    VkComputePipeline.dispatch(cmd, (PARTICLE_COUNT + 255) / 256, 1, 1);

                    // Release barrier on the compute queue: flush the write.
                    // Cross-queue ordering is handled by the timeline semaphore.
                    // dstStageMask must only reference stages supported by the compute queue.
                    VkMemoryBarrier.builder()
                            .srcAccess(VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value())
                            .dstAccess(0)
                            .build(frameAllocator)
                            .execute(cmd,
                                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT.value(),
                                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());
                })
                .build();
        return computeLoop;
    }

    @Override
    protected void beforeRenderPass(VkCommandBuffer commandBuffer, SegmentAllocator frameAllocator) {
        VkMemoryBarrier.builder()
                .srcAccess(VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value())
                .dstAccess(VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value())
                .build(frameAllocator)
                .execute(commandBuffer.handle(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT.value(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT.value());
    }

    @Override
    protected void onDraw(VkCommandBuffer commandBuffer, SegmentAllocator frameAllocator) {
        graphicsSet.bind(commandBuffer, pipeline, 0, frameAllocator);
    }

    @Override
    protected int vertexCount() {
        return PARTICLE_COUNT;
    }

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        if (computeDone != null)    computeDone.close();
        if (computePipeline != null) computePipeline.close();
        if (computePool != null)    computePool.close();
        if (computeLayout != null)  computeLayout.close();
        if (graphicsPool != null)   graphicsPool.close();
        if (graphicsLayout != null) graphicsLayout.close();
        if (particleBuffer != null) particleBuffer.close();
    }
}
