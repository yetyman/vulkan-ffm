package io.github.yetyman.vulkan.sample.graph;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.graph.RenderGraph;
import io.github.yetyman.vulkan.graph.RenderGraphVisualizer;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.resources.InitialState;
import io.github.yetyman.vulkan.graph.resources.ResourceDescriptor;
import io.github.yetyman.vulkan.graph.resources.TemporalResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;
import io.github.yetyman.vulkan.highlevel.DrawCommand;
import io.github.yetyman.vulkan.highlevel.GraphicsFrame;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.*;

/**
 * Temporal trail effect driven by the render graph.
 *
 * Demonstrates:
 * - TemporalResource with auto-allocated physical slots
 * - ctx.temporalReadHandle() / ctx.temporalWriteHandle() for slot resolution
 * - Automatic flip advancement by the executor
 * - ImportedResource for swapchain with auto final-layout transition
 * - Graph-determined execution order from resource dependencies
 * - Zero manual barrier management in node lambdas (except swapchain begin-rendering)
 */
public class TemporalTrailGraphFrame extends GraphicsFrame {

    private static final int GRID_W = 512;
    private static final int GRID_H = 512;

    private VkBuffer pixelsA, pixelsB;
    private VkDescriptorSetLayout computeLayout;
    private VkDescriptorPool computePool;
    private VkDescriptorSet computeSetAtoB, computeSetBtoA;
    private VkComputePipeline computePipeline;
    private CompiledShader fragCompiled;
    private ShaderInstance fragShader;
    private VkDescriptorPool fragDescriptorPool;
    private VkDescriptorSet fragSetA, fragSetB;
    private VkPipeline displayPipeline;

    private RenderGraph graph;
    private final long startTime = System.nanoTime();

    public TemporalTrailGraphFrame(Arena arena, VkDevice device, VkQueue queue,
                                   MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    public void printRenderGraph() {
        if (graph != null) RenderGraphVisualizer.print(graph.compiledGraph());
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        long bufSize = (long) GRID_W * GRID_H * 4 * Float.BYTES;
        int cs = VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value();
        int fs = VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value();

        // Buffers
        pixelsA = VkBuffer.builder().device(device).size(bufSize).storageBuffer().hostVisible().build(arena);
        pixelsB = VkBuffer.builder().device(device).size(bufSize).storageBuffer().hostVisible().build(arena);
        try (Arena tmp = Arena.ofConfined()) {
            pixelsA.map(tmp).fill((byte) 0); pixelsA.unmap();
            pixelsB.map(tmp).fill((byte) 0); pixelsB.unmap();
        }

        // Compute descriptors
        computeLayout = VkDescriptorSetLayout.builder().device(device)
            .storageBuffer(0, cs).storageBuffer(1, cs).build(arena);
        computePool = VkDescriptorPool.builder().device(device).maxSets(2).storageBuffers(4).build(arena);
        computeSetAtoB = computePool.allocateDescriptorSet(computeLayout);
        computeSetBtoA = computePool.allocateDescriptorSet(computeLayout);
        try (Arena tmp = Arena.ofConfined()) {
            computeSetAtoB.bind(0, pixelsA, tmp); computeSetAtoB.bind(1, pixelsB, tmp);
            computeSetBtoA.bind(0, pixelsB, tmp); computeSetBtoA.bind(1, pixelsA, tmp);
        }
        computePipeline = VkComputePipeline.builder().device(device)
            .computeShader(ShaderLoader.builder("/shaders/temporal_trail.comp").compile())
            .descriptorSetLayouts(computeLayout.handle())
            .pushConstantRange(cs, 0, 16).build(arena);

        // Display pipeline
        fragCompiled = ShaderLoader.compileShader("/shaders/temporal_trail.frag");
        fragShader = ShaderInstance.from(fragCompiled, device);
        fragDescriptorPool = VkDescriptorPool.builder().device(device).maxSets(2).storageBuffers(2).build(arena);
        var fl = fragShader.layouts().get(0).getLayout();
        fragSetA = fragDescriptorPool.allocateDescriptorSet(fl);
        fragSetB = fragDescriptorPool.allocateDescriptorSet(fl);
        try (Arena tmp = Arena.ofConfined()) {
            fragSetA.bind(0, pixelsA, tmp); fragSetB.bind(0, pixelsB, tmp);
        }
        var pb = VkPipeline.builder().device(device)
            .vertexShader(ShaderLoader.builder("/shaders/fullscreen.vert").compile())
            .fragmentShader(fragCompiled.getSpirV())
            .triangleTopology().dynamicViewport().dynamicScissor()
            .descriptorSetLayouts(fragShader.layoutHandle(0))
            .pushConstantRange(fs, 0, 8);
        if (useDynamicRendering) pb.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        else pb.renderPass(renderPass.handle());
        displayPipeline = pb.build(arena);
        fragShader.pipelineLayout(displayPipeline.layout());

        // -- Temporal resource --
        TemporalResource trailHistory = TemporalResource.builder()
            .name("trail_history")
            .descriptor(ResourceDescriptor.buffer(bufSize, 0x80 | 0x20))
            .bufferCount(2)
            .initialState(InitialState.Clear.BLACK)
            .build();
        trailHistory.setPhysicalSlots(new io.github.yetyman.vulkan.graph.resources.GraphResource[]{
            wrapBuf("trail_A", pixelsA), wrapBuf("trail_B", pixelsB)
        });

        // Dummy resource for ordering (compute -> display dependency)
        var orderingRes = wrapBuf("compute_output", pixelsA);

        // -- Build graph --
        graph = RenderGraph.builder()
            .device(device)
            .queue(QueueCapability.GRAPHICS, queue.handle(), queueFamilyIndex)
            .queue(QueueCapability.COMPUTE, queue.handle(), queueFamilyIndex)
            .temporal(trailHistory)
            .node(ComputePassNode.builder()
                .name("trail-compute")
                .temporalEdge(TemporalEdge.readPrevious(trailHistory, 0x20, 0x800))
                .temporalEdge(TemporalEdge.writeCurrent(trailHistory, 0x40, 0x800))
                .writes(ResourceEdge.write(orderingRes, 0x40, 0x800))
                .scheduleHint(ScheduleHint.EARLY)
                .execute(ctx -> {
                    var cmd = ctx.commandBuffer();
                    var fa = ctx.frameArena();
                    MemorySegment rh = ctx.temporalReadHandle("trail_history");
                    var set = rh.equals(pixelsA.handle()) ? computeSetAtoB : computeSetBtoA;
                    computePipeline.bind(cmd.handle());
                    set.bind(cmd, computePipeline, 0, fa);
                    float t = (float)((System.nanoTime() - startTime) / 1e9);
                    MemorySegment pc = fa.allocate(16);
                    pc.set(ValueLayout.JAVA_INT, 0, GRID_W);
                    pc.set(ValueLayout.JAVA_INT, 4, GRID_H);
                    pc.set(ValueLayout.JAVA_FLOAT, 8, t);
                    pc.set(ValueLayout.JAVA_FLOAT, 12, 0.92f);
                    VkPushConstantsCmd.pushConstants(cmd, computePipeline.layout(), cs, 0, pc, 16);
                    VkComputePipeline.dispatch(cmd.handle(), (GRID_W+15)/16, (GRID_H+15)/16, 1);
                })
                .build())
            .node(GraphicsPassNode.builder()
                .name("trail-display")
                .reads(ResourceEdge.read(orderingRes, 0x20, 0x80))
                .writes(ResourceEdge.write(orderingRes, 0x100, 0x400))
                .execute(ctx -> {
                    var cmd = ctx.commandBuffer();
                    var fa = ctx.frameArena();
                    int idx = ctx.frameIndex();

                    // Begin rendering to swapchain
                    VkImageBarrier.builder()
                        .image(swapchainImageViews[idx].image())
                        .srcAccess(0).dstAccess(0x100)
                        .transition(VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(),
                            VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value())
                        .build(fa).execute(cmd.handle(), 0x1, 0x400);
                    VkRendering.builder().device(device).renderArea(0, 0, width, height)
                        .colorAttachment(swapchainImageViews[idx].handle(),
                            VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                            VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                            VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                            0, 0, 0, 1).begin(cmd.handle(), fa);

                    MemorySegment wh = ctx.temporalWriteHandle("trail_history");
                    var fragSet = wh.equals(pixelsA.handle()) ? fragSetA : fragSetB;
                    VkBind.bindPipeline(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), displayPipeline.handle());
                    VkSetState.setViewport(cmd, 0, 0, 0, width, height, 0, 1);
                    VkSetState.setScissor(cmd, 0, 0, 0, width, height);
                    fragSet.bind(cmd, displayPipeline, 0, fa);
                    MemorySegment pc = fa.allocate(8);
                    pc.set(ValueLayout.JAVA_INT, 0, GRID_W);
                    pc.set(ValueLayout.JAVA_INT, 4, GRID_H);
                    VkPushConstantsCmd.pushConstants(cmd, displayPipeline.layout(), fs, 0, pc, 8);
                    DrawCommand.direct(3, 1).execute(cmd.handle());

                    VkRendering.end(device, cmd.handle());

                    // Present transition
                    VkImageBarrier.builder()
                        .image(swapchainImageViews[idx].image())
                        .srcAccess(0x100).dstAccess(0)
                        .transition(VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                            VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR.value())
                        .build(fa).execute(cmd.handle(), 0x400, 0x2000);
                })
                .build())
            .build();
    }

    @Override
    protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);
        graph.executeInto(frameArena, imageIndex, commandBuffer);
        Vulkan.endCommandBuffer(commandBuffer.handle()).check();
    }

    @Override
    protected void cleanupResources() {
        if (graph != null) graph.close();
        if (displayPipeline != null) displayPipeline.close();
        if (fragShader != null) fragShader.close();
        if (fragDescriptorPool != null) fragDescriptorPool.close();
        if (computePipeline != null) computePipeline.close();
        if (computePool != null) computePool.close();
        if (computeLayout != null) computeLayout.close();
        if (pixelsB != null) pixelsB.close();
        if (pixelsA != null) pixelsA.close();
    }

    private static io.github.yetyman.vulkan.graph.resources.GraphResource wrapBuf(String name, VkBuffer buf) {
        return new io.github.yetyman.vulkan.graph.resources.GraphResource() {
            @Override public String name() { return name; }
            @Override public MemorySegment handle() { return buf.handle(); }
            @Override public int lastAccessMask() { return 0; }
            @Override public int lastStageMask() { return 0; }
            @Override public int owningQueueFamily() { return 0; }
            @Override public void updateState(int a, int s, int q) {}
            @Override public boolean isTransient() { return false; }
            @Override public boolean isImported() { return false; }
            @Override public io.github.yetyman.vulkan.graph.resources.ResourceLifetime lifetime() {
                return new io.github.yetyman.vulkan.graph.resources.ResourceLifetime();
            }
        };
    }
}
