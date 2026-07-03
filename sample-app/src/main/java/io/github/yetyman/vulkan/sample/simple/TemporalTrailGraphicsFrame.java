package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.resources.InitialState;
import io.github.yetyman.vulkan.graph.resources.ResourceDescriptor;
import io.github.yetyman.vulkan.graph.resources.TemporalResource;
import io.github.yetyman.vulkan.shaders.CompiledShader;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.*;

/**
 * Visual demonstration of temporal feedback (motion trails).
 *
 * A compute shader blends animated orbiting circles with the previous frame's output,
 * creating persistent colorful trails. The temporal resource (double-buffered pixel buffer)
 * is managed using the TemporalResource API which handles:
 * - Physical slot allocation (2 buffers)
 * - Automatic flip each frame
 * - Initial clear on frame 0
 * - Correct read/write slot selection
 *
 * Without temporal unrolling, you'd manually manage two buffers, a flip counter,
 * two descriptor sets, and swap them each frame. With it, the graph knows which
 * slot to read and which to write based on the temporal edge declarations.
 */
public class TemporalTrailGraphicsFrame extends SimpleGraphicsFrame {

    private static final int GRID_W = 512;
    private static final int GRID_H = 512;

    // The temporal resource declaration - the graph manages the double-buffer
    private TemporalResource trailHistory;

    // Physical buffers (allocated by us, managed by TemporalResource)
    private VkBuffer pixelsA, pixelsB;

    // Compute pipeline for the trail effect
    private VkDescriptorSetLayout computeLayout;
    private VkDescriptorPool computePool;
    private VkDescriptorSet computeSetAtoB, computeSetBtoA;
    private VkComputePipeline computePipeline;

    // Display pipeline
    private CompiledShader fragCompiled;
    private ShaderInstance fragShader;
    private VkDescriptorPool fragDescriptorPool;
    private VkDescriptorSet fragSetA, fragSetB;

    private final long startTime = System.nanoTime();

    public TemporalTrailGraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                                      MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        long pixelCount = (long) GRID_W * GRID_H;
        long bufSize = pixelCount * 4 * Float.BYTES; // vec4 per pixel

        // Allocate the two physical buffers
        pixelsA = VkBuffer.builder().device(device).size(bufSize).storageBuffer().hostVisible().build(arena);
        pixelsB = VkBuffer.builder().device(device).size(bufSize).storageBuffer().hostVisible().build(arena);

        // Clear both buffers to zero (black)
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment mapped = pixelsA.map(tmp);
            mapped.fill((byte) 0);
            pixelsA.unmap();
            mapped = pixelsB.map(tmp);
            mapped.fill((byte) 0);
            pixelsB.unmap();
        }

        // -- Temporal resource declaration --
        // This is the key part: declare the temporal resource and assign physical slots.
        // The TemporalResource tracks which slot to read/write each frame.
        trailHistory = TemporalResource.builder()
            .name("trail_history")
            .descriptor(ResourceDescriptor.buffer(bufSize, 0x80 | 0x20)) // STORAGE | TRANSFER_DST
            .bufferCount(2)
            .initialState(InitialState.Clear.BLACK)
            .build();

        // In a full graph integration, the graph allocator would do this.
        // Here we do it manually to show the concept works standalone.
        trailHistory.setPhysicalSlots(new io.github.yetyman.vulkan.graph.resources.GraphResource[]{
            wrapBuffer("trail_A", pixelsA),
            wrapBuffer("trail_B", pixelsB)
        });

        // Compute shader setup
        int computeStage = VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value();
        computeLayout = VkDescriptorSetLayout.builder().device(device)
            .storageBuffer(0, computeStage)  // history read
            .storageBuffer(1, computeStage)  // history write
            .build(arena);

        computePool = VkDescriptorPool.builder().device(device).maxSets(2).storageBuffers(4).build(arena);
        computeSetAtoB = computePool.allocateDescriptorSet(computeLayout);
        computeSetBtoA = computePool.allocateDescriptorSet(computeLayout);

        try (Arena tmp = Arena.ofConfined()) {
            // A->B: read A, write B
            computeSetAtoB.bind(0, pixelsA, tmp);
            computeSetAtoB.bind(1, pixelsB, tmp);
            // B->A: read B, write A
            computeSetBtoA.bind(0, pixelsB, tmp);
            computeSetBtoA.bind(1, pixelsA, tmp);
        }

        computePipeline = VkComputePipeline.builder()
            .device(device)
            .computeShader(ShaderLoader.builder("/shaders/temporal_trail.comp").compile())
            .descriptorSetLayouts(computeLayout.handle())
            .pushConstantRange(computeStage, 0, 16) // width, height, time, decay
            .build(arena);

        // Fragment shader for display
        fragCompiled = ShaderLoader.compileShader("/shaders/temporal_trail.frag");
        fragShader = ShaderInstance.from(fragCompiled, device);

        fragDescriptorPool = VkDescriptorPool.builder().device(device)
            .maxSets(2).storageBuffers(2).build(arena);
        VkDescriptorSetLayout fragLayout = fragShader.layouts().get(0).getLayout();
        fragSetA = fragDescriptorPool.allocateDescriptorSet(fragLayout);
        fragSetB = fragDescriptorPool.allocateDescriptorSet(fragLayout);

        try (Arena tmp = Arena.ofConfined()) {
            fragSetA.bind(0, pixelsA, tmp);
            fragSetB.bind(0, pixelsB, tmp);
        }

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

    @Override
    protected void beforeRenderPass(VkCommandBuffer commandBuffer, SegmentAllocator frameAllocator) {
        // -- Temporal feedback: use TemporalResource to select correct slots --
        // The temporal resource knows which slot is "previous" (read) and "current" (write)
        // based on how many times onWriteExecuted() has been called.

        // Determine which descriptor set to use based on temporal slot selection
        // previousReadSlot is what we read from, currentWriteSlot is what we write to
        int writeCount = trailHistory.writeCount();
        VkDescriptorSet computeSet = (writeCount % 2 == 0) ? computeSetAtoB : computeSetBtoA;

        // Dispatch compute: blend history + new frame -> output
        computePipeline.bind(commandBuffer.handle());
        computeSet.bind(commandBuffer, computePipeline, 0, frameAllocator);

        float time = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
        VkPushConstantsCmd.push(commandBuffer, computePipeline, VkShaderStageFlagBits.VK_SHADER_STAGE_COMPUTE_BIT.value(), 0, 16, pc -> {
            pc.set(ValueLayout.JAVA_INT, 0, GRID_W);
            pc.set(ValueLayout.JAVA_INT, 4, GRID_H);
            pc.set(ValueLayout.JAVA_FLOAT, 8, time);
            pc.set(ValueLayout.JAVA_FLOAT, 12, 0.92f); // trail decay
        });

        VkComputePipeline.dispatch(commandBuffer.handle(), (GRID_W + 15) / 16, (GRID_H + 15) / 16, 1);

        // Barrier: compute write -> fragment read
        VkMemoryBarrier.builder()
            .srcAccess(VkAccessFlagBits.VK_ACCESS_SHADER_WRITE_BIT.value())
            .dstAccess(VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value())
            .build(frameAllocator)
            .execute(commandBuffer.handle(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT.value(),
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value());

        // Advance the temporal resource - the write is done
        trailHistory.onWriteExecuted();
    }

    @Override
    protected void onDraw(VkCommandBuffer commandBuffer, SegmentAllocator frameAllocator) {
        // Display the buffer that was just written (current write slot, which just advanced)
        // After onWriteExecuted(), previousReadSlot() points to what we just wrote
        int writeCount = trailHistory.writeCount();
        VkDescriptorSet fragSet = (writeCount % 2 == 0) ? fragSetA : fragSetB;

        fragSet.bind(commandBuffer, pipeline, 0, frameAllocator);

        VkPushConstantsCmd.push(commandBuffer, pipeline, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value(), 0, 8, pc -> {
            pc.set(ValueLayout.JAVA_INT, 0, GRID_W);
            pc.set(ValueLayout.JAVA_INT, 4, GRID_H);
        });
    }

    @Override
    protected int vertexCount() { return 3; }

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        if (fragShader != null) fragShader.close();
        if (fragDescriptorPool != null) fragDescriptorPool.close();
        if (computePipeline != null) computePipeline.close();
        if (computePool != null) computePool.close();
        if (computeLayout != null) computeLayout.close();
        if (pixelsB != null) pixelsB.close();
        if (pixelsA != null) pixelsA.close();
    }

    private static io.github.yetyman.vulkan.graph.resources.GraphResource wrapBuffer(String name, VkBuffer buf) {
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
