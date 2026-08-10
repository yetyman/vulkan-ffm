package io.github.yetyman.vulkan.layers.text;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPipeline;
import io.github.yetyman.vulkan.VkSampler;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.command.VkBind;
import io.github.yetyman.vulkan.command.VkDraw;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkPipelineBindPoint;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.assets.FontRegistry;
import io.github.yetyman.vulkan.highlevel.VulkanCapabilities;
import io.github.yetyman.vulkan.shaders.DescriptorGroup;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Vulkan rendering backend for GPUDrivenTextLayer.
 *
 * Renders glyph quads via a single instanced draw call: 4 vertices per instance (triangle
 * strip), one instance per glyph. Glyph geometry and UVs are computed entirely in the vertex
 * shader from a per-instance storage buffer (GlyphInstance) - no per-glyph vertex/index buffer
 * is needed.
 *
 * Fixed pipeline layout (manual, non-reflection-driven, since the shader interface is fixed
 * and simple):
 *   set 0, binding 0: storage buffer of GlyphInstance (vertex stage)
 *   set 0, binding 1: combined image sampler, font atlas (fragment stage)
 *   push constant: vec2 screenSize (vertex stage)
 *
 * The instance buffer is a MAPPED (host-visible, persistently mapped) buffer re-written each
 * frame - appropriate given typical UI text volumes (hundreds to low thousands of glyphs).
 *
 * Requires dynamic rendering (VulkanCapabilities.dynamicRendering) - render-pass-based path
 * is not implemented, consistent with the minimal scope of this first text layer.
 */
public class TextRenderer implements AutoCloseable {

    private static final int INITIAL_INSTANCE_CAPACITY = 4096;

    private final UIContext ctx;
    private final VkDevice device;

    private VkPipeline pipeline;
    private VkSampler sampler;
    private io.github.yetyman.vulkan.VkDescriptorSetLayout pipelineDescriptorLayout;

    private ShaderInstance vertShader;
    private ShaderInstance fragShader;

    private IBuffer instanceBuffer;
    private int instanceCapacity;

    // Per-font-atlas descriptor group, rebuilt whenever the bound atlas or instance buffer
    // handle changes (atlas image is created lazily by FontAtlas.flush(), and the instance
    // buffer may be reallocated on growth).
    private DescriptorGroup descriptorGroup;
    private FontRegistry.FontAtlas boundAtlas;

    public TextRenderer(UIContext ctx) {
        this.ctx = ctx;
        this.device = ctx.vulkan().device();
    }

    public void initialize() {
        if (!VulkanCapabilities.dynamicRendering) {
            throw new IllegalStateException(
                "TextRenderer requires dynamic rendering support - renderPass-based path not implemented (minimal font/text module scope)");
        }

        sampler = VkSampler.builder()
            .device(device)
            .linear()
            .clampToEdge()
            .build(ctx.applicationArena());

        vertShader = ShaderLoader.load("/shaders/text.vert", device);
        fragShader = ShaderLoader.load("/shaders/text.frag", device);

        allocateInstanceBuffer(INITIAL_INSTANCE_CAPACITY);

        VkPipeline.Builder builder = VkPipeline.builder()
            .device(device)
            .vertexShader(vertShader)
            .fragmentShader(fragShader)
            .triangleStripTopology()
            .dynamicViewport()
            .dynamicScissor()
            .alphaBlend()
            .dynamicRendering(VkFormat.VK_FORMAT_D32_SFLOAT.value(), VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value())
            .descriptorSetLayouts(buildTemporaryLayoutForPipeline());

        pipeline = builder.build(ctx.applicationArena());
        vertShader.pipelineLayout(pipeline.layout());
    }

    /**
     * Renders the accumulated glyph instances from the batch against the given font's atlas.
     * Must be called once per font per frame that has glyphs to draw (the descriptor set binds
     * a single atlas at a time; mixing multiple fonts in one batch would need per-font sub-batches
     * or a texture array, both out of scope for this minimal layer).
     */
    public void render(VkCommandBuffer cmd, Arena frameArena, TextBatch batch, FontRegistry.FontAtlas atlas) {
        List<GlyphInstance> instances = batch.instances();
        if (instances.isEmpty()) return;

        atlas.flush();
        ensureInstanceCapacity(instances.size());
        uploadInstances(instances);
        ensureDescriptorGroup(atlas);

        VkBind.bindPipeline(cmd.handle(), VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(), pipeline.handle());

        descriptorGroup.set().bind(cmd, pipeline, 0, frameArena);

        MemorySegment screenSize = frameArena.allocate(8);
        screenSize.set(ValueLayout.JAVA_FLOAT, 0, (float) ctx.width());
        screenSize.set(ValueLayout.JAVA_FLOAT, 4, (float) ctx.height());
        VkPushConstantsCmd.pushConstants(cmd.handle(), pipeline.layout(),
            VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, screenSize, 8);

        VkDraw.draw(cmd.handle(), 4, instances.size());
    }

    @Override
    public void close() {
        if (descriptorGroup != null) descriptorGroup.close();
        if (pipeline != null) pipeline.close();
        if (pipelineDescriptorLayout != null) pipelineDescriptorLayout.close();
        if (vertShader != null) vertShader.close();
        if (fragShader != null) fragShader.close();
        if (sampler != null) sampler.close();
        if (instanceBuffer != null) instanceBuffer.close();
    }

    /**
     * Builds a throwaway descriptor set layout purely to describe the pipeline layout at
     * pipeline creation time. The actual descriptor set used at draw time is built later by
     * DescriptorGroup once the instance buffer and font atlas are known - both use the same
     * binding numbers/types/stages so the layouts are pipeline-compatible. Retained and closed
     * in close() since VkPipeline does not take ownership of descriptor set layouts.
     */
    private MemorySegment buildTemporaryLayoutForPipeline() {
        pipelineDescriptorLayout = io.github.yetyman.vulkan.VkDescriptorSetLayout.builder()
            .device(device)
            .storageBuffer(0, VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value())
            .combinedImageSampler(1, VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value())
            .build(ctx.applicationArena());
        return pipelineDescriptorLayout.handle();
    }

    private void ensureDescriptorGroup(FontRegistry.FontAtlas atlas) {
        if (descriptorGroup != null && boundAtlas == atlas) return;

        if (descriptorGroup != null) {
            descriptorGroup.close();
        }

        descriptorGroup = DescriptorGroup.builder()
            .device(device)
            .storageBuffer(0, instanceBuffer, VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value())
            .combinedImageSampler(1, new DescriptorGroup.ImageBinding(
                sampler.handle(), atlas.imageView().handle(),
                VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value()))
            .stageFlags(VkShaderStageFlagBits.VK_SHADER_STAGE_FRAGMENT_BIT.value())
            .build(ctx.applicationArena());

        boundAtlas = atlas;
    }

    private void allocateInstanceBuffer(int capacity) {
        if (instanceBuffer != null) instanceBuffer.close();
        this.instanceCapacity = capacity;
        this.instanceBuffer = BufferFactory.create(
            MemoryStrategy.MAPPED, null,
            (long) capacity * GlyphInstance.SIZE_BYTES,
            BufferUsage.STORAGE, device, ctx.vulkan().graphicsVkQueue());
        // Invalidate the descriptor group so it rebinds the new buffer handle on next render().
        if (descriptorGroup != null) {
            descriptorGroup.close();
            descriptorGroup = null;
            boundAtlas = null;
        }
    }

    private void ensureInstanceCapacity(int required) {
        if (required <= instanceCapacity) return;
        int newCapacity = instanceCapacity;
        while (newCapacity < required) newCapacity *= 2;
        allocateInstanceBuffer(newCapacity);
    }

    private void uploadInstances(List<GlyphInstance> instances) {
        ByteBuffer buf = ByteBuffer.allocate(instances.size() * GlyphInstance.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (GlyphInstance g : instances) {
            buf.putFloat(g.posMinX()).putFloat(g.posMinY());
            buf.putFloat(g.posMaxX()).putFloat(g.posMaxY());
            buf.putFloat(g.uvMinX()).putFloat(g.uvMinY());
            buf.putFloat(g.uvMaxX()).putFloat(g.uvMaxY());
            buf.putFloat(g.r()).putFloat(g.g()).putFloat(g.b()).putFloat(g.a());
        }
        buf.flip();
        instanceBuffer.write(buf, 0, ctx.vulkan().graphicsVkQueue());
    }
}
