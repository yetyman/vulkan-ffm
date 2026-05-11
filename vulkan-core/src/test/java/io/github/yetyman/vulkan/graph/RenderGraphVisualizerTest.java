package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.PresentNode;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.scheduling.ListSchedulingStrategy;
import io.github.yetyman.vulkan.graph.scheduling.QueueAssignment;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderGraphVisualizerTest {

    @Test
    void visualize_deferredPipeline() {
        GraphResource gbuffer = TestResources.transientBuffer("gbuffer-color");
        GraphResource gbufferNormal = TestResources.transientBuffer("gbuffer-normal");
        GraphResource shadowMap = TestResources.transientBuffer("shadow-map");
        GraphResource hdrColor = TestResources.transientBuffer("hdr-color");
        GraphResource bloom = TestResources.transientBuffer("bloom");
        GraphResource swapchain = TestResources.importedBuffer("swapchain");

        RenderNode shadowPass = GraphicsPassNode.builder()
            .name("shadow-pass")
            .writes(ResourceEdge.write(shadowMap, 0x400, 0x100))
            .scheduleHint(ScheduleHint.EARLY)
            .execute(ctx -> {})
            .build();

        RenderNode gbufferPass = GraphicsPassNode.builder()
            .name("gbuffer-pass")
            .writes(ResourceEdge.write(gbuffer, 0x100, 0x400))
            .writes(ResourceEdge.write(gbufferNormal, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        RenderNode lightingPass = GraphicsPassNode.builder()
            .name("lighting")
            .reads(ResourceEdge.read(gbuffer, 0x20, 0x80))
            .reads(ResourceEdge.read(gbufferNormal, 0x20, 0x80))
            .reads(ResourceEdge.read(shadowMap, 0x20, 0x80))
            .writes(ResourceEdge.write(hdrColor, 0x100, 0x400))
            .scheduleHint(ScheduleHint.CRITICAL_PATH)
            .execute(ctx -> {})
            .build();

        RenderNode bloomPass = ComputePassNode.builder()
            .name("bloom")
            .reads(ResourceEdge.read(hdrColor, 0x20, 0x800))
            .writes(ResourceEdge.write(bloom, 0x40, 0x800))
            .execute(ctx -> {})
            .build();

        RenderNode tonemapPass = GraphicsPassNode.builder()
            .name("tonemap")
            .reads(ResourceEdge.read(hdrColor, 0x20, 0x80))
            .reads(ResourceEdge.read(bloom, 0x20, 0x80))
            .writes(ResourceEdge.write(swapchain, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        RenderNode present = PresentNode.of(swapchain, MemorySegment.NULL);

        RenderGraphCompiler compiler = new RenderGraphCompiler(
            new ListSchedulingStrategy(), null, null);
        CompiledGraph compiled = compiler.compile(
            List.of(shadowPass, gbufferPass, lightingPass, bloomPass, tonemapPass, present),
            Map.of(
                QueueCapability.GRAPHICS, new QueueAssignment(MemorySegment.NULL, 0, QueueCapability.GRAPHICS),
                QueueCapability.COMPUTE, new QueueAssignment(MemorySegment.NULL, 1, QueueCapability.COMPUTE)
            ));

        String output = RenderGraphVisualizer.visualize(compiled);
        System.out.println(output);

        // Verify structure
        assertFalse(output.isEmpty());
        assertTrue(output.contains("shadow-pass"));
        assertTrue(output.contains("gbuffer-pass"));
        assertTrue(output.contains("lighting"));
        assertTrue(output.contains("bloom"));
        assertTrue(output.contains("tonemap"));
        assertTrue(output.contains("present"));
        assertTrue(output.contains("Bucket"));
        assertTrue(output.contains("-->"));
    }
}
