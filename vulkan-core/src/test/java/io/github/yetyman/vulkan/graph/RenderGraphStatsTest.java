package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.nodes.NodeStats;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderGraphStatsTest {

    @Test
    void collectsCpuAndGpuTimes() {
        RenderGraphStats stats = new RenderGraphStats();
        stats.beginFrame(42);
        stats.recordCpuTime("lighting", 500_000);
        stats.recordCpuTime("bloom", 200_000);
        stats.recordGpuTime("lighting", 2_000_000);
        stats.recordGpuTime("bloom", 800_000);

        FrameStats result = stats.build();

        assertEquals(42, result.frameGeneration());
        assertEquals(2_800_000, result.totalGpuNanos());
        assertEquals(700_000, result.totalCpuNanos());

        NodeStats lighting = result.forNode("lighting");
        assertNotNull(lighting);
        assertEquals(2_000_000, lighting.gpuTimeNanos());
        assertEquals(500_000, lighting.cpuRecordTimeNanos());

        NodeStats bloom = result.forNode("bloom");
        assertNotNull(bloom);
        assertEquals(800_000, bloom.gpuTimeNanos());
    }

    @Test
    void recordCpuTimes_fromMap() {
        RenderGraphStats stats = new RenderGraphStats();
        stats.beginFrame(0);
        stats.recordCpuTimes(Map.of("a", 100L, "b", 200L));

        FrameStats result = stats.build();
        assertEquals(300, result.totalCpuNanos());
        assertNotNull(result.forNode("a"));
        assertNotNull(result.forNode("b"));
    }

    @Test
    void beginFrame_resetsState() {
        RenderGraphStats stats = new RenderGraphStats();
        stats.beginFrame(0);
        stats.recordCpuTime("old", 999);
        stats.recordGpuTime("old", 999);

        stats.beginFrame(1);
        FrameStats result = stats.build();

        assertEquals(1, result.frameGeneration());
        assertEquals(0, result.totalGpuNanos());
        assertEquals(0, result.totalCpuNanos());
        assertNull(result.forNode("old"));
    }

    @Test
    void gpuOnlyNode_included() {
        RenderGraphStats stats = new RenderGraphStats();
        stats.beginFrame(0);
        stats.recordGpuTime("gpuOnly", 5_000_000);

        FrameStats result = stats.build();
        NodeStats ns = result.forNode("gpuOnly");
        assertNotNull(ns);
        assertEquals(5_000_000, ns.gpuTimeNanos());
        assertEquals(0, ns.cpuRecordTimeNanos());
    }
}
