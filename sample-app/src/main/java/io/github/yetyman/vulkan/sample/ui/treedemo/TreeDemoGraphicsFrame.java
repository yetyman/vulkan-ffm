package io.github.yetyman.vulkan.sample.ui.treedemo;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.command.VkPushConstantsCmd;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkShaderStageFlagBits;
import io.github.yetyman.vulkan.sample.simple.SimpleGraphicsFrame;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.ui2d.NineSliceRenderer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;

/**
 * Vulkan rendering frame for the tree-based draggable squares demo.
 *
 * Reads vertex data from the TreeDemoFrame's NineSliceRenderer and uploads to a
 * Vulkan storage buffer. Draws all rectangles in a single draw call using the
 * same nine-slice shader approach as DraggableSquaresApp.
 */
public class TreeDemoGraphicsFrame extends SimpleGraphicsFrame {

    private static final int PC_SIZE = 8; // push constants: viewport width, height (2 floats)

    private final TreeDemoFrame demoFrame;

    private VkBuffer vertexBuf;
    private VkDescriptorSetLayout descLayout;
    private VkDescriptorPool descPool;
    private VkDescriptorSet descSet;

    public TreeDemoGraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                                 MemorySegment surface, int width, int height,
                                 TreeDemoFrame demoFrame) {
        super(arena, device, queue, surface, width, height, 3);
        this.demoFrame = demoFrame;
    }

    @Override
    protected void initializeResources(int queueFamilyIndex) {
        // Allocate storage buffer large enough for all vertex data
        // 7500 rects × 432 floats × 4 bytes ≈ 13 MB
        long bufSize = (long) Math.max(8192, demoFrame.renderer().slotCount()) *
                NineSliceRenderer.FLOATS_PER_SLOT * Float.BYTES;

        vertexBuf = VkBuffer.builder()
                .device(device)
                .size(bufSize)
                .storageBuffer()
                .hostVisible()
                .build(arena);

        // Upload initial vertex data
        uploadVertexData();

        int vertStage = VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value();
        descLayout = VkDescriptorSetLayout.builder()
                .device(device)
                .storageBuffer(0, vertStage)
                .build(arena);

        descPool = VkDescriptorPool.builder()
                .device(device)
                .maxSets(1)
                .storageBuffers(1)
                .build(arena);

        descSet = descPool.allocateDescriptorSet(descLayout);
        try (Arena tmp = Arena.ofConfined()) {
            descSet.bind(0, vertexBuf, tmp);
        }

        super.initializeResources(queueFamilyIndex);
    }

    @Override
    protected VkPipeline createPipeline() {
        VkPipeline.Builder builder = VkPipeline.builder()
                .device(device)
                .vertexShader(ShaderLoader.builder("/shaders/ui_rect.vert").compile())
                .fragmentShader(ShaderLoader.builder("/shaders/ui_rect.frag").compile())
                .triangleTopology()
                .dynamicViewport()
                .dynamicScissor()
                .alphaBlend()
                .descriptorSetLayouts(descLayout.handle())
                .pushConstantRange(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, PC_SIZE);

        if (useDynamicRendering) {
            builder.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        } else {
            builder.renderPass(renderPass.handle());
        }

        return builder.build(arena);
    }

    @Override
    protected void onDraw(VkCommandBuffer commandBuffer, SegmentAllocator frameAllocator) {
        // Upload dirty vertex data
        if (demoFrame.isDirty()) {
            uploadVertexData();
            demoFrame.clearDirty();
        }

        // Bind descriptor set
        descSet.bind(commandBuffer, pipeline, 0, frameAllocator);

        // Push constants: viewport dimensions
        VkPushConstantsCmd.push(commandBuffer, pipeline,
                VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value(), 0, PC_SIZE, pc -> {
                    pc.set(ValueLayout.JAVA_FLOAT, 0, (float) width);
                    pc.set(ValueLayout.JAVA_FLOAT, 4, (float) height);
                });
    }

    @Override
    protected int vertexCount() {
        return demoFrame.vertexCount();
    }

    private void uploadVertexData() {
        float[] data = demoFrame.vertexData();
        int floatCount = demoFrame.vertexCount() * NineSliceRenderer.FLOATS_PER_VERTEX;
        if (floatCount == 0 || data == null) return;

        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment mapped = vertexBuf.map(tmp);
            int uploadCount = Math.min(floatCount, data.length);
            for (int i = 0; i < uploadCount; i++) {
                mapped.setAtIndex(ValueLayout.JAVA_FLOAT, i, data[i]);
            }
            vertexBuf.unmap();
        }
    }

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        if (descPool != null) descPool.close();
        if (descLayout != null) descLayout.close();
        if (vertexBuf != null) vertexBuf.close();
    }
}
