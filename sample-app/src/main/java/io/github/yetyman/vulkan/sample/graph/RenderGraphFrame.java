package io.github.yetyman.vulkan.sample.graph;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDescriptorPool;
import io.github.yetyman.vulkan.VkDescriptorSet;
import io.github.yetyman.vulkan.VkDescriptorSetLayout;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkImage;
import io.github.yetyman.vulkan.VkImageView;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkRendering;
import io.github.yetyman.vulkan.VkSampler;
import io.github.yetyman.vulkan.Vulkan;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.enums.VkAccessFlagBits;
import io.github.yetyman.vulkan.enums.VkAttachmentLoadOp;
import io.github.yetyman.vulkan.enums.VkAttachmentStoreOp;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkImageAspectFlagBits;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkImageUsageFlagBits;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.graph.CompiledGraph;
import io.github.yetyman.vulkan.graph.RenderGraph;
import io.github.yetyman.vulkan.graph.RenderGraphCompiler;
import io.github.yetyman.vulkan.graph.RenderGraphExecutor;
import io.github.yetyman.vulkan.graph.RenderGraphVisualizer;
import io.github.yetyman.vulkan.graph.barriers.SplitBarrierStrategy;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.PresentNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.GraphImageResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;
import io.github.yetyman.vulkan.graph.scheduling.ListSchedulingStrategy;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;
import io.github.yetyman.vulkan.highlevel.DrawCommand;
import io.github.yetyman.vulkan.highlevel.GraphicsFrame;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.PushConstant;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;

/**
 * Multi-pass post-processing renderer driven by the render graph system.
 *
 * The render graph declares the full pass structure up front. Each node's execute() lambda
 * contains only draw commands. The graph executor handles:
 * - Topological ordering of passes based on resource dependencies
 * - Automatic barrier emission between passes (layout transitions, access synchronization)
 * - Resource state tracking across the frame
 *
 * Pipeline:
 * 1. triangle-pass: Renders an animated triangle to an offscreen color image
 * 2. (barrier: offscreen COLOR_ATTACHMENT -> SHADER_READ_ONLY, emitted by graph executor)
 * 3. edge-detect: Fullscreen pass sampling the offscreen image, writes original + green edge
 *    overlay to the swapchain
 * 4. (barrier: swapchain COLOR_ATTACHMENT -> PRESENT_SRC, emitted by graph executor)
 * 5. present: Swapchain image is presented
 *
 * On startup, the compiled DAG is printed to the console showing pass ordering,
 * resource lifetimes, and inter-pass dependencies.
 */
public class RenderGraphFrame extends GraphicsFrame {

    // Shaders
    private ShaderInstance triangleVert;
    private ShaderInstance triangleFrag;
    private CompiledShader edgeFragCompiled;
    private ShaderInstance edgeFragShader;
    private PushConstant<Float> time;

    // Pipelines
    private VkPipeline trianglePipeline;
    private VkPipeline edgePipeline;

    // Offscreen resources
    private VkImage offscreenImage;
    private VkImageView offscreenView;
    private VkSampler sampler;
    private VkDescriptorSetLayout edgeLayout;
    private VkDescriptorPool edgeDescPool;
    private VkDescriptorSet edgeDescSet;

    // Render graph
    private CompiledGraph compiledGraph;
    private RenderGraphExecutor executor;

    // Graph resources (tracked state for barrier synthesis)
    private ImageGraphResource offscreenRes;
    private ImageGraphResource swapchainRes;

    private final long startTime = System.nanoTime();

    public RenderGraphFrame(Arena arena, VkDevice device, VkQueue queue,
                            MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    /** Prints the compiled render graph DAG to stdout */
    public void printRenderGraph() {
        if (compiledGraph != null) {
            RenderGraphVisualizer.print(compiledGraph);
        }
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        // Load shaders
        triangleVert = ShaderLoader.load("/shaders/triangle.vert", device);
        triangleFrag = ShaderLoader.load("/shaders/triangle.frag", device);
        time = triangleVert.getPushConstant("time", Float.class);

        edgeFragCompiled = ShaderLoader.compileShader("/shaders/edge_simple.frag");
        edgeFragShader = ShaderInstance.from(edgeFragCompiled, device);

        // Create offscreen image
        int colorFormat = VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value();
        offscreenImage = VkImage.builder()
            .device(device)
            .dimensions(width, height, 1)
            .format(colorFormat)
            .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT.value()
                 | VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value())
            .build(arena);

        offscreenView = VkImageView.builder()
            .device(device)
            .image(offscreenImage.handle())
            .format(colorFormat)
            .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
            .build(arena);

        sampler = VkSampler.builder().device(device).build(arena);

        // Descriptor set for edge shader
        int fragStage = VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value();
        edgeLayout = VkDescriptorSetLayout.builder().device(device)
            .combinedImageSampler(0, fragStage)
            .build(arena);

        edgeDescPool = VkDescriptorPool.builder().device(device)
            .maxSets(1)
            .combinedImageSamplers(1)
            .build(arena);
        edgeDescSet = edgeDescPool.allocateDescriptorSet(edgeLayout);

        try (Arena tmp = Arena.ofConfined()) {
            edgeDescSet.updateImageSampler(0, sampler.handle(), offscreenView.handle(),
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(), tmp);
        }

        // Pipelines
        trianglePipeline = VkPipeline.builder()
            .device(device)
            .vertexShader(triangleVert)
            .fragmentShader(triangleFrag)
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .dynamicRendering(0, colorFormat)
            .build(arena);
        triangleVert.pipelineLayout(trianglePipeline.layout());

        edgePipeline = VkPipeline.builder()
            .device(device)
            .vertexShader(ShaderLoader.builder("/shaders/fullscreen.vert").compile())
            .fragmentShader(edgeFragCompiled.getSpirV())
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor()
            .descriptorSetLayouts(edgeLayout.handle())
            .dynamicRendering(0, colorFormat)
            .build(arena);

        // Build render graph
        buildRenderGraph(queueFamilyIndex);
    }

    private void buildRenderGraph(int queueFamilyIndex) {
        int COLOR_WRITE = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value();
        int SHADER_READ = VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value();
        int COLOR_STAGE = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value();
        int FRAG_STAGE = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value();
        int COLOR_ATTACH = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value();
        int SHADER_READ_LAYOUT = VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value();
        int PRESENT_LAYOUT = VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR.value();

        offscreenRes = new ImageGraphResource("offscreen-color", true, false, width, height);
        swapchainRes = new ImageGraphResource("swapchain", false, true, width, height);

        // Node execute lambdas contain ONLY draw commands -- barriers are handled by the executor
        GraphicsPassNode trianglePass = GraphicsPassNode.builder()
            .name("triangle-pass")
            .writes(ResourceEdge.writeImage(offscreenRes, COLOR_WRITE, COLOR_STAGE, COLOR_ATTACH))
            .scheduleHint(ScheduleHint.EARLY)
            .execute(ctx -> {
                VkCommandBuffer cmd = ctx.commandBuffer();
                Arena fa = ctx.frameArena();

                VkRendering.builder()
                    .device(device)
                    .renderArea(0, 0, width, height)
                    .colorAttachment(offscreenView.handle(),
                        COLOR_ATTACH,
                        VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_CLEAR.value(),
                        VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                        0.05f, 0.05f, 0.1f, 1.0f)
                    .begin(cmd.handle(), fa);

                VkBind.bindPipeline(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), trianglePipeline.handle());
                VkSetState.setViewport(cmd, 0, 0, 0, width, height, 0.0f, 1.0f);
                VkSetState.setScissor(cmd, 0, 0, 0, width, height);

                time.set((System.nanoTime() - startTime) / 1_000_000_000.0f);
                triangleVert.flush(cmd);

                DrawCommand.direct(3, 1).execute(cmd.handle());
                VkRendering.end(device, cmd.handle());
            })
            .build();

        GraphicsPassNode edgePass = GraphicsPassNode.builder()
            .name("edge-detect")
            .reads(ResourceEdge.readImage(offscreenRes, SHADER_READ, FRAG_STAGE, SHADER_READ_LAYOUT))
            .writes(ResourceEdge.writeImage(swapchainRes, COLOR_WRITE, COLOR_STAGE, COLOR_ATTACH))
            .execute(ctx -> {
                VkCommandBuffer cmd = ctx.commandBuffer();
                Arena fa = ctx.frameArena();
                int imageIndex = ctx.frameIndex();

                VkRendering.builder()
                    .device(device)
                    .renderArea(0, 0, width, height)
                    .colorAttachment(swapchainImageViews[imageIndex].handle(),
                        COLOR_ATTACH,
                        VkAttachmentLoadOp.VK_ATTACHMENT_LOAD_OP_DONT_CARE.value(),
                        VkAttachmentStoreOp.VK_ATTACHMENT_STORE_OP_STORE.value(),
                        0, 0, 0, 1)
                    .begin(cmd.handle(), fa);

                VkBind.bindPipeline(cmd, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), edgePipeline.handle());
                VkSetState.setViewport(cmd, 0, 0, 0, width, height, 0.0f, 1.0f);
                VkSetState.setScissor(cmd, 0, 0, 0, width, height);

                edgeDescSet.bind(cmd, edgePipeline, 0, fa);

                DrawCommand.direct(3, 1).execute(cmd.handle());
                VkRendering.end(device, cmd.handle());
            })
            .build();

        PresentNode present = PresentNode.of(swapchainRes, MemorySegment.NULL);

        // Compile
        RenderGraphCompiler compiler = new RenderGraphCompiler(
            new ListSchedulingStrategy(), new SplitBarrierStrategy(), null);
        compiledGraph = compiler.compile(
            List.of(trianglePass, edgePass, present),
            Map.of(QueueCapability.GRAPHICS, new QueueAssignment(MemorySegment.NULL, queueFamilyIndex, QueueCapability.GRAPHICS)));

        executor = new RenderGraphExecutor(device, new SplitBarrierStrategy());
    }

    @Override
    protected void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex, Arena frameArena) {
        VkCommandBuffer.begin(commandBuffer).execute(frameArena);

        // Reset resource state each frame (offscreen starts UNDEFINED, swapchain starts UNDEFINED)
        offscreenRes.reset();
        swapchainRes.reset();

        // The graph executor handles everything: pass ordering, barrier emission, draw calls
        executor.execute(compiledGraph, commandBuffer, frameArena, imageIndex, 0, null);

        // Final transition to present (the graph's PresentNode is a no-op, so we do this explicitly)
        io.github.yetyman.vulkan.VkImageBarrier.builder()
            .image(swapchainImageViews[imageIndex].image())
            .srcAccess(VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value())
            .dstAccess(0)
            .transition(
                VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL.value(),
                VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR.value())
            .build(frameArena)
            .execute(commandBuffer.handle(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value());

        Vulkan.endCommandBuffer(commandBuffer.handle()).check();
    }

    @Override
    protected void cleanupResources() {
        if (edgePipeline != null) edgePipeline.close();
        if (trianglePipeline != null) trianglePipeline.close();
        if (edgeDescPool != null) edgeDescPool.close();
        if (edgeLayout != null) edgeLayout.close();
        if (sampler != null) sampler.close();
        if (offscreenView != null) offscreenView.close();
        if (offscreenImage != null) offscreenImage.close();
        if (triangleVert != null) triangleVert.close();
        if (triangleFrag != null) triangleFrag.close();
        if (edgeFragShader != null) edgeFragShader.close();
    }

    /**
     * GraphImageResource implementation with state tracking for the barrier strategy.
     */
    private static class ImageGraphResource implements GraphImageResource {
        private final String name;
        private final boolean transientRes;
        private final boolean imported;
        private final int w, h;
        private final ResourceLifetime lifetime = new ResourceLifetime();
        private int currentLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value();
        private int lastAccessMask = 0;
        private int lastStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value();
        private int owningQueueFamily = ~0;

        ImageGraphResource(String name, boolean transientRes, boolean imported, int w, int h) {
            this.name = name;
            this.transientRes = transientRes;
            this.imported = imported;
            this.w = w;
            this.h = h;
        }

        void reset() {
            currentLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value();
            lastAccessMask = 0;
            lastStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value();
        }

        @Override public String name() { return name; }
        @Override public MemorySegment handle() { return MemorySegment.NULL; }
        @Override public int lastAccessMask() { return lastAccessMask; }
        @Override public int lastStageMask() { return lastStageMask; }
        @Override public int owningQueueFamily() { return owningQueueFamily; }
        @Override public void updateState(int accessMask, int stageMask, int queueFamily) {
            this.lastAccessMask = accessMask;
            this.lastStageMask = stageMask;
            this.owningQueueFamily = queueFamily;
        }
        @Override public boolean isTransient() { return transientRes; }
        @Override public boolean isImported() { return imported; }
        @Override public ResourceLifetime lifetime() { return lifetime; }
        @Override public int format() { return VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value(); }
        @Override public int currentLayout() { return currentLayout; }
        @Override public int width() { return w; }
        @Override public int height() { return h; }
        @Override public int layers() { return 1; }
        @Override public int mipLevels() { return 1; }
        @Override public int sampleCount() { return 1; }
        @Override public void updateLayout(int layout) { this.currentLayout = layout; }
    }
}
