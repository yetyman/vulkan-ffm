package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.VkAccessFlagBits;
import io.github.yetyman.vulkan.enums.VkAttachmentLoadOp;
import io.github.yetyman.vulkan.enums.VkAttachmentStoreOp;
import io.github.yetyman.vulkan.enums.VkDescriptorType;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.highlevel.GraphicsRenderer;
import io.github.yetyman.vulkan.highlevel.VulkanApplication;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.loop.ComputeLoop;
import io.github.yetyman.vulkan.loop.LoopDriver;
import io.github.yetyman.vulkan.sample.windowing.GLFWInputSystem;
import io.github.yetyman.vulkan.sample.windowing.GLFWWindowSystem;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.shaders.StorageBufferSlot;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Random;

public class GameOfLifeApp extends VulkanApplication implements ILifecycleListener {

    private static final int GRID_W = 512;
    private static final int GRID_H = 512;

    private GameOfLifeRenderer renderer;
    private ComputeLoop computeLoop;

    public GameOfLifeApp() {
        super("Game of Life", 800, 800, new GLFWWindowSystem(), new GLFWInputSystem());
    }

    @Override
    protected void initialize() {
        VulkanCapabilities.initialize(vulkanContext().physicalDevice());
        renderer = new GameOfLifeRenderer(
            vulkanContext().arena(), vulkanContext().device(),
            vulkanContext().graphicsQueue(), surface(), 800, 800);
        renderer.init(vulkanContext().graphicsQueueFamily());

        computeLoop = renderer.buildComputeLoop(
            vulkanContext().computeQueue(),
            vulkanContext().computeQueueFamily());

        if (vulkanContext().graphicsQueue().equals(vulkanContext().computeQueue())) {
            renderer.setQueueLock(renderer.computeQueueLock());
            Logger.info("Compute: shared queue (lock+yield mode)");
        } else {
            Logger.info("Compute: dedicated queue (true async)");
        }

        registerLifecycleDependency(computeLoop);
        computeLoop.start();
        addLifecycleListener(this);
    }

    @Override public void onBeforeShutdown() { if (renderer != null) renderer.close(); }

    @Override protected void render()               { renderer.drawFrame(); }
    @Override protected void onResize(int w, int h) { renderer.resize(w, h); }
    @Override protected void shutdown()             {}
    @Override protected void onFPSUpdate(int fps)   { Logger.info("FPS: " + fps); }

    public static void main(String[] args) {
        try (GameOfLifeApp app = new GameOfLifeApp()) {
            app.run();
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------

    static class GameOfLifeRenderer extends GraphicsRenderer {

        private VkBuffer cellsA, cellsB;

        // Compute descriptors — manual since VkBuffer isn't ManagedBuffer
        private VkDescriptorSetLayout computeLayout;
        private VkDescriptorPool computePool;
        private VkDescriptorSet computeSetAtoB, computeSetBtoA;
        private VkComputePipeline computePipeline;

        // Graphics: two ShaderInstances from the same CompiledShader, each bound to one buffer
        private CompiledShader fragCompiled;
        private ShaderInstance fragInstanceA, fragInstanceB;
        private VkPipeline graphicsPipeline;

        private VkTimelineSemaphore computeDone;
        private ComputeLoop computeLoop; // set after buildComputeLoop()
        // Exposed so GameOfLifeApp can install the shared-queue lock
        private final java.util.concurrent.locks.ReentrantLock sharedQueueLock =
            new java.util.concurrent.locks.ReentrantLock();

        java.util.concurrent.locks.ReentrantLock computeQueueLock() { return sharedQueueLock; }

        GameOfLifeRenderer(Arena arena, VkDevice device, MemorySegment queue,
                           MemorySegment surface, int width, int height) {
            super(arena, device, queue, surface, width, height, 3);
        }

        @Override
        protected VkRenderPass createRenderPassImpl() {
            return VkRenderPass.builder()
                .device(device)
                .colorAttachment(VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value(),
                    VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                    VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value())
                .subpassDependency(~0, 0,
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value(),
                    0, VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
                .build(arena);
        }

        @Override
        protected VkFramebuffer createFramebufferImpl(int imageIndex) {
            return VkFramebuffer.builder()
                .device(device)
                .renderPass(renderPass.handle())
                .attachment(new VkFramebufferAttachment(
                    swapchainImageViews[imageIndex],
                    VkFramebufferAttachment.AttachmentType.COLOR, 0, 0))
                .dimensions(width, height)
                .build(arena);
        }

        @Override
        protected void initializeResources(int queueFamilyIndex) {
            long cellCount = (long) GRID_W * GRID_H;
            long bufSize   = cellCount * Integer.BYTES;

            cellsA = VkBuffer.builder().device(device).size(bufSize)
                .storageBuffer().hostVisible().build(arena);
            cellsB = VkBuffer.builder().device(device).size(bufSize)
                .storageBuffer().hostVisible().build(arena);

            try (Arena tmp = Arena.ofConfined()) {
                MemorySegment mapped = cellsA.map(tmp);
                Random rng = new Random();
                for (int i = 0; i < cellCount; i++)
                    mapped.setAtIndex(ValueLayout.JAVA_INT, i, rng.nextFloat() < 0.3f ? 1 : 0);
                cellsA.unmap();
            }

            // Compute descriptors
            int computeStage = VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value();
            computeLayout = VkDescriptorSetLayout.builder().device(device)
                .storageBuffer(0, computeStage)
                .storageBuffer(1, computeStage)
                .build(arena);

            computePool = VkDescriptorPool.builder().device(device)
                .maxSets(2).storageBuffers(4).build(arena);

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

            // Graphics: two ShaderInstances from the same compiled frag shader,
            // each bound to one of the ping-pong buffers via its descriptor set.
            fragCompiled = ShaderLoader.compileShader("/shaders/gol.frag");
            fragInstanceA = ShaderInstance.from(fragCompiled, device);
            fragInstanceB = ShaderInstance.from(fragCompiled, device);

            StorageBufferSlot slotA = fragInstanceA.getStorageBufferSlot("cellBuf");
            StorageBufferSlot slotB = fragInstanceB.getStorageBufferSlot("cellBuf");
            slotA.set(cellsA);
            slotB.set(cellsB);

            VkPipeline.Builder pb = VkPipeline.builder()
                .device(device)
                .vertexShader(ShaderLoader.builder("/shaders/fullscreen.vert").compile())
                .fragmentShader(fragCompiled.getSpirV())
                .triangleTopology()
                .dynamicViewport()
                .dynamicScissor()
                .descriptorSetLayouts(fragInstanceA.layoutHandle(0))
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value(), 0, 8);

            if (useDynamicRendering) {
                pb.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
            } else {
                pb.renderPass(renderPass.handle());
            }
            graphicsPipeline = pb.build(arena);

            fragInstanceA.pipelineLayout(graphicsPipeline.layout());
            fragInstanceB.pipelineLayout(graphicsPipeline.layout());

            computeDone = VkTimelineSemaphore.create(device, 0, arena);
        }

        /** Builds the ComputeLoop that drives the GOL simulation. Called after initializeResources. */
        ComputeLoop buildComputeLoop(MemorySegment computeQueue, int computeQueueFamily) {
            computeLoop = ComputeLoop.builder()
                .device(device)
                .queue(computeQueue)
                .queueFamilyIndex(computeQueueFamily)
                .semaphore(computeDone)
                .driver(LoopDriver.uncapped())
                .name("gol-compute")
                .work((cmd, generation, frameArena) -> {
                    int slot = (int)(generation % 2);
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
        protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, Arena frameArena) {
            long gen = computeLoop.completedGeneration();
            boolean showB = (gen % 2 == 0) && gen > 0;
            ShaderInstance fragInstance = showB ? fragInstanceB : fragInstanceA;

            VkCommandBuffer.begin(commandBuffer).execute(frameArena);

            if (useDynamicRendering) {
                VkImageBarrier.builder()
                    .image(swapchainImageViews[imageIndex].image())
                    .srcAccess(0)
                    .dstAccess(VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
                    .transition(VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(),
                                VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value())
                    .build(frameArena)
                    .execute(commandBuffer.handle(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value());

                VkRendering.builder()
                    .renderArea(0, 0, width, height)
                    .colorAttachment(
                        swapchainImageViews[imageIndex].handle(),
                        VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                        VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                        VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                        0.0f, 0.0f, 0.0f, 1.0f)
                    .begin(commandBuffer.handle(), frameArena);
            } else {
                VkCommandBuffer.beginRenderPass(commandBuffer, renderPass.handle(), framebuffers[imageIndex].handle())
                    .renderArea(0, 0, width, height)
                    .clearColor(0.0f, 0.0f, 0.0f, 1.0f)
                    .execute(frameArena);
            }

            Vulkan.cmdBindPipeline(commandBuffer.handle(),
                VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), graphicsPipeline.handle());

            Vulkan.cmdSetViewport(commandBuffer.handle(), 0, 1,
                VkViewport.builder().position(0, 0).size(width, height).depthRange(0.0f, 1.0f).build(frameArena));
            Vulkan.cmdSetScissor(commandBuffer.handle(), 0, 1,
                VkRect2D.builder().offset(0, 0).extent(width, height).build(frameArena));

            // ShaderInstance.flush() binds the descriptor set and writes any dirty push constants
            fragInstance.flush(commandBuffer);

            MemorySegment pcData = frameArena.allocate(8);
            pcData.set(ValueLayout.JAVA_INT, 0, GRID_W);
            pcData.set(ValueLayout.JAVA_INT, 4, GRID_H);
            Vulkan.cmdPushConstants(commandBuffer.handle(), graphicsPipeline.layout(),
                VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value(), 0, 8, pcData);

            Vulkan.cmdDraw(commandBuffer.handle(), 3, 1, 0, 0);

            if (useDynamicRendering) {
                VkRendering.end(commandBuffer.handle());

                VkImageBarrier.builder()
                    .image(swapchainImageViews[imageIndex].image())
                    .srcAccess(VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
                    .dstAccess(0)
                    .transition(VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                                VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR.value())
                    .build(frameArena)
                    .execute(commandBuffer.handle(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());
            } else {
                Vulkan.cmdEndRenderPass(commandBuffer.handle());
            }

            Vulkan.endCommandBuffer(commandBuffer.handle()).check();
        }

        @Override
        protected void cleanupResources() {
            if (graphicsPipeline != null) graphicsPipeline.close();
            if (fragInstanceB    != null) fragInstanceB.close();
            if (fragInstanceA    != null) fragInstanceA.close();
            if (computeDone      != null) computeDone.close();
            if (computePipeline  != null) computePipeline.close();
            if (computePool      != null) computePool.close();
            if (computeLayout    != null) computeLayout.close();
            if (cellsB           != null) cellsB.close();
            if (cellsA           != null) cellsA.close();
        }
    }
}
