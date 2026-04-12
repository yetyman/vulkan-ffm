package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.shaders.PushConstant;
import io.github.yetyman.vulkan.shaders.ShaderInstance;
import io.github.yetyman.vulkan.shaders.ShaderLoader;

import java.lang.foreign.*;

public class TriangleGraphicsFrame extends SimpleGraphicsFrame {

    private ShaderInstance vertShader;
    private ShaderInstance fragShader;
    private PushConstant<Float> time;

    private final long startTime = System.nanoTime();

    public TriangleGraphicsFrame(Arena arena, VkDevice device, VkQueue queue,
                                  MemorySegment surface, int width, int height) {
        super(arena, device, queue, surface, width, height, 3);
    }

    @Override
    protected VkPipeline createPipeline() {
        vertShader = ShaderLoader.load("/shaders/triangle.vert", device);
        fragShader = ShaderLoader.load("/shaders/triangle.frag", device);
        time = vertShader.getPushConstant("time", Float.class);

        VkPipeline.Builder builder = VkPipeline.builder()
            .device(device)
            .vertexShader(vertShader)
            .fragmentShader(fragShader)
            .triangleTopology()
            .dynamicViewport()
            .dynamicScissor();

        if (useDynamicRendering) {
            builder.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        } else {
            builder.renderPass(renderPass.handle());
        }

        VkPipeline p = builder.build(arena);
        vertShader.pipelineLayout(p.layout());
        return p;
    }

    @Override
    protected void onDraw(VkCommandBuffer commandBuffer, Arena frameArena) {
        time.set((System.nanoTime() - startTime) / 1_000_000_000.0f);
        vertShader.flush(commandBuffer);
    }

    @Override protected int vertexCount() { return 3; }
    @Override protected int instanceCount() { return 1000; }

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        if (vertShader != null) vertShader.close();
        if (fragShader != null) fragShader.close();
    }
}
