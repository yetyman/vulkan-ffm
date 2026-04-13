package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.loop.ComputeLoop;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.*;
import java.util.Random;

public class GameOfLifeGraphicsFrame extends SimpleGraphicsFrame {

    private static final int GRID_W = 512;
    private static final int GRID_H = 512;

    private VkBuffer cellsA, cellsB;

    private VkDescriptorSetLayout computeLayout;
    private VkDescriptorPool computePool;
    private VkDescriptorSet computeSetAtoB, computeSetBtoA;
    private VkComputePipeline computePipeline;

    private CompiledShader fragCompiled;
    private ShaderInstance fragShader;
    private VkDescriptorPool fragDescriptorPool;
    private VkDescriptorSet fragSetA, fragSetB;

    private VkTimelineSemaphore computeDone;
    private ComputeLoop computeLoop;

    public GameOfLifeGraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                                   MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        long cellCount = (long) GRID_W * GRID_H;
        long bufSize = cellCount * Integer.BYTES;

        cellsA = VkBuffer.builder().device(device).size(bufSize).storageBuffer().hostVisible().build(arena);
        cellsB = VkBuffer.builder().device(device).size(bufSize).storageBuffer().hostVisible().build(arena);

        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment mapped = cellsA.map(tmp);
            Random rng = new Random();
            for (int i = 0; i < cellCount; i++)
                mapped.setAtIndex(ValueLayout.JAVA_INT, i, rng.nextFloat() < 0.3f ? 1 : 0);
            cellsA.unmap();
        }

        int computeStage = VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value();
        computeLayout = VkDescriptorSetLayout.builder().device(device)
                .storageBuffer(0, computeStage)
                .storageBuffer(1, computeStage)
                .build(arena);

        computePool = VkDescriptorPool.builder().device(device).maxSets(2).storageBuffers(4).build(arena);
        computeSetAtoB = computePool.allocateDescriptorSet(computeLayout);
        computeSetBtoA = computePool.allocateDescriptorSet(computeLayout);

        try (Arena tmp = Arena.ofConfined()) {
            int st = VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value();
            computeSetAtoB.updateBuffer(0, st, cellsA.handle(), 0, bufSize, tmp);
            computeSetAtoB.updateBuffer(1, st, cellsB.handle(), 0, bufSize, tmp);
            computeSetBtoA.updateBuffer(0, st, cellsB.handle(), 0, bufSize, tmp);
            computeSetBtoA.updateBuffer(1, st, cellsA.handle(), 0, bufSize, tmp);
        }

        computePipeline = VkComputePipeline.builder()
                .device(device)
                .computeShader(ShaderLoader.builder("/shaders/gol.comp").compile())
                .descriptorSetLayouts(computeLayout.handle())
                .pushConstantRange(computeStage, 0, 8)
                .build(arena);

        fragCompiled = ShaderLoader.compileShader("/shaders/gol.frag");
        fragShader = ShaderInstance.from(fragCompiled, device);

        // Two descriptor sets from a shared pool — one bound to cellsA, one to cellsB
        fragDescriptorPool = VkDescriptorPool.builder().device(device)
                .maxSets(2)
                .storageBuffers(2)
                .build(arena);
        VkDescriptorSetLayout fragLayout = fragShader.layouts().get(0).getLayout();
        fragSetA = fragDescriptorPool.allocateDescriptorSet(fragLayout);
        fragSetB = fragDescriptorPool.allocateDescriptorSet(fragLayout);

        try (Arena tmp = Arena.ofConfined()) {
            int st = VkDescriptorType.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER.value();
            fragSetA.updateBuffer(0, st, cellsA.handle(), 0, bufSize, tmp);
            fragSetB.updateBuffer(0, st, cellsB.handle(), 0, bufSize, tmp);
        }

        computeDone = VkTimelineSemaphore.create(device, 0, arena);

        // pipeline is created by SimpleGraphicsFrame.initializeResources via createPipeline()
        super.initializeResources(queueFamilyIndex);
    }

    @Override
    protected VkPipeline createPipeline() {
        VkPipeline.Builder pb = VkPipeline.builder()
                .device(device)
                .vertexShader(ShaderLoader.builder("/shaders/fullscreen.vert").compile())
                .fragmentShader(fragCompiled.getSpirV())
                .triangleTopology()
                .dynamicViewport()
                .dynamicScissor()
                .descriptorSetLayouts(fragShader.layoutHandle(0))
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value(), 0, 8);

        if (useDynamicRendering) {
            pb.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        } else {
            pb.renderPass(renderPass.handle());
        }

        VkPipeline p = pb.build(arena);
        fragShader.pipelineLayout(p.layout());
        return p;
    }

    /**
     * Builds the ComputeLoop that drives the GOL simulation. Called after init().
     */
    ComputeLoop buildComputeLoop(VkQueue computeQueue) {
        computeLoop = ComputeLoop.builder()
                .device(device)
                .queue(computeQueue)
                .queueFamilyIndex(computeQueue.familyIndex())
                .semaphore(computeDone)
                .driver(LoopDriver.uncapped())
                .name("gol-compute")
                .work((cmd, generation, frameArena) -> {
                    int slot = (int) (generation % 2);
                    VkDescriptorSet set = (slot == 0) ? computeSetAtoB : computeSetBtoA;

                    computePipeline.bind(cmd);
                    set.bind(cmd, computePipeline, 0, frameArena);
                    computePipeline.pushInt(cmd, 0, GRID_W);
                    computePipeline.pushInt(cmd, 4, GRID_H);
                    VkComputePipeline.dispatch(cmd, (GRID_W + 15) / 16, (GRID_H + 15) / 16, 1);

                    VkMemoryBarrier.builder()
                            .srcAccess(VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value())
                            .dstAccess(VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value())
                            .build(frameArena)
                            .execute(cmd,
                                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT.value(),
                                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT.value());
                })
                .build();
        return computeLoop;
    }

    @Override
    protected void onDraw(VkCommandBuffer commandBuffer, Arena frameArena) {
        long gen = computeLoop.completedGeneration();
        VkDescriptorSet fragSet = (gen % 2 == 0 && gen > 0) ? fragSetB : fragSetA;

        fragSet.bind(commandBuffer, pipeline, 0, frameArena);

        MemorySegment pcData = frameArena.allocate(8);
        pcData.set(ValueLayout.JAVA_INT, 0, GRID_W);
        pcData.set(ValueLayout.JAVA_INT, 4, GRID_H);
        VkPushConstantsCmd.pushConstants(commandBuffer, pipeline.layout(),
                VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value(), 0, pcData, 8);
    }

    @Override
    protected int vertexCount() {
        return 3;
    }

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        if (fragShader != null) fragShader.close();
        if (fragDescriptorPool != null) fragDescriptorPool.close();
        if (computeDone != null) computeDone.close();
        if (computePipeline != null) computePipeline.close();
        if (computePool != null) computePool.close();
        if (computeLayout != null) computeLayout.close();
        if (cellsB != null) cellsB.close();
        if (cellsA != null) cellsA.close();
    }
}
