package io.github.yetyman.vulkan.sample.graph;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkComputePipeline;
import io.github.yetyman.vulkan.VkDescriptorSetLayout;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkRendering;
import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkAttachmentLoadOp;
import io.github.yetyman.vulkan.enums.VkAttachmentStoreOp;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.graph.RenderGraph;
import io.github.yetyman.vulkan.graph.RenderGraphVisualizer;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.resources.ImportedResource;
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;

/**
 * Temporal trail effect driven by the render graph.
 *
 * Demonstrates:
 * - TemporalResource with graph-allocated physical slots (no manual buffer creation)
 * - Paired temporal descriptor binding (compute reads+writes in one set, auto-selected)
 * - Single temporal descriptor binding (fragment reads one buffer, auto-selected)
 * - ImportedResource for swapchain with auto-rendering and auto barriers
 * - Zero manual barrier management, zero manual descriptor set selection
 */
public class TemporalTrailGraphFrame extends GraphicsFrame {

    private static final int GRID_W = 512;
    private static final int GRID_H = 512;

    // Compute pipeline
    private VkDescriptorSetLayout computeLayout;
    private VkComputePipeline computePipeline;

    // Display pipeline
    private CompiledShader fragCompiled;
    private ShaderInstance fragShader;
    private VkPipeline displayPipeline;

    // Imported swapchain resource
    private ImportedResource swapchainImport;

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

        // Compute layout: binding 0 = read, binding 1 = write
        computeLayout = VkDescriptorSetLayout.builder().device(device)
            .storageBuffer(0, cs).storageBuffer(1, cs).build(arena);
        computePipeline = VkComputePipeline.builder().device(device)
            .computeShader(ShaderLoader.builder("/shaders/temporal_trail.comp").compile())
            .descriptorSetLayouts(computeLayout.handle())
            .pushConstantRange(cs, 0, 16).build(arena);

        // Display pipeline
        fragCompiled = ShaderLoader.compileShader("/shaders/temporal_trail.frag");
        fragShader = ShaderInstance.from(fragCompiled, device);
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

        // Imported swapchain
        swapchainImport = ImportedResource.builder()
            .name("swapchain")
            .format(VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
            .dimensions(width, height)
            .initialLayout(VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value())
            .finalLayout(VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR.value())
            .build();

        // Temporal resource: graph allocates buffers, creates paired + single descriptor sets
        var fragLayout = fragShader.layouts().get(0).getLayout();
        TemporalResource trailHistory = TemporalResource.builder()
            .name("trail_history")
            .descriptor(ResourceDescriptor.buffer(bufSize, 0x80 | 0x20)) // STORAGE | TRANSFER_DST
            .bufferCount(2)
            .initialState(InitialState.Clear.BLACK)
            .pairedDescriptor(0, 1, computeLayout) // compute: read@0, write@1
            .descriptorBinding(0)                   // fragment: read@0
            .descriptorLayout(fragLayout)
            .build();

        // Build graph
        graph = RenderGraph.builder()
            .device(device)
            .queue(QueueCapability.GRAPHICS, queue.handle(), queueFamilyIndex)
            .queue(QueueCapability.COMPUTE, queue.handle(), queueFamilyIndex)
            .temporal(trailHistory)
            .importedImage(swapchainImport)
            .node(ComputePassNode.builder()
                .name("trail-compute")
                .temporalEdge(TemporalEdge.readPrevious(trailHistory, 0x20, 0x800))
                .temporalEdge(TemporalEdge.writeCurrent(trailHistory, 0x40, 0x800))
                .scheduleHint(ScheduleHint.EARLY)
                .execute(ctx -> {
                    var cmd = ctx.commandBuffer();
                    var fa = ctx.frameArena();
                    // Paired descriptor set: read@0 + write@1, auto-selected by flip state
                    var set = ctx.temporalPairedDescriptorSet("trail_history");
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
                // Read the temporal resource (what compute just wrote) - establishes ordering
                .temporalEdge(TemporalEdge.readPrevious(trailHistory, 0x20, 0x80))
                // Auto-rendering: graph handles barriers + VkRendering begin/end for swapchain
                .autoRendering(VkRendering.builder().device(device)
                    .renderArea(0, 0, width, height)
                    .colorAttachment(MemorySegment.NULL, // patched per-frame by colorAttachment(import)
                        VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                        VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                        VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                        0, 0, 0, 1))
                .colorAttachment(swapchainImport)
                .execute(ctx -> {
                    var cmd = ctx.commandBuffer();
                    var fa = ctx.frameArena();
                    // Single descriptor set: read@0, auto-selected by flip state
                    var fragSet = ctx.temporalReadDescriptorSet("trail_history");
                    VkBind.bindPipeline(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), displayPipeline.handle());
                    VkSetState.setViewport(cmd, 0, 0, 0, width, height, 0, 1);
                    VkSetState.setScissor(cmd, 0, 0, 0, width, height);
                    fragSet.bind(cmd, displayPipeline, 0, fa);
                    MemorySegment pc = fa.allocate(8);
                    pc.set(ValueLayout.JAVA_INT, 0, GRID_W);
                    pc.set(ValueLayout.JAVA_INT, 4, GRID_H);
                    VkPushConstantsCmd.pushConstants(cmd, displayPipeline.layout(), fs, 0, pc, 8);
                    DrawCommand.direct(3, 1).execute(cmd.handle());
                })
                .build())
            .build();
    }

    @Override
    protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, SegmentAllocator frameAllocator) {
        // Rebind swapchain image for this frame (graph uses it for barriers + auto-rendering)
        swapchainImport.rebindWithView(
            swapchainImageViews[imageIndex].image(),
            swapchainImageViews[imageIndex].handle());

        VkCommandBuffer.begin(commandBuffer).execute(frameArena());
        graph.executeInto(frameArena(), imageIndex, commandBuffer);
        Vulkan.endCommandBuffer(commandBuffer.handle()).check();
    }

    @Override
    protected void cleanupResources() {
        if (graph != null) graph.close();
        if (displayPipeline != null) displayPipeline.close();
        if (fragShader != null) fragShader.close();
        if (computePipeline != null) computePipeline.close();
        if (computeLayout != null) computeLayout.close();
    }
}
