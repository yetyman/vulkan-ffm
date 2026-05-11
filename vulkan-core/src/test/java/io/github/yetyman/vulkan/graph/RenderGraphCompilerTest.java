package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.PresentNode;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;
import io.github.yetyman.vulkan.graph.scheduling.ListSchedulingStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderGraphCompilerTest {

    private RenderGraphCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new RenderGraphCompiler(new ListSchedulingStrategy(), null, null);
    }

    // -- Validation tests --

    @Test
    void validate_passesWhenAllReadsHaveProducers() {
        GraphResource buf = TestResources.transientBuffer("buf");
        RenderNode writer = ComputePassNode.builder()
            .name("writer")
            .writes(ResourceEdge.write(buf, 0x00000020, 0x00000800)) // SHADER_WRITE, COMPUTE
            .execute(ctx -> {})
            .build();
        RenderNode reader = ComputePassNode.builder()
            .name("reader")
            .reads(ResourceEdge.read(buf, 0x00000020, 0x00000800))
            .writes(ResourceEdge.write(buf, 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();

        assertDoesNotThrow(() -> compiler.validate(List.of(writer, reader)));
    }

    @Test
    void validate_passesForImportedResourceWithNoProducer() {
        GraphResource imported = TestResources.importedBuffer("external");
        RenderNode reader = ComputePassNode.builder()
            .name("reader")
            .reads(ResourceEdge.read(imported, 0x00000020, 0x00000800))
            .writes(ResourceEdge.write(TestResources.transientBuffer("out"), 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();

        assertDoesNotThrow(() -> compiler.validate(List.of(reader)));
    }

    @Test
    void validate_throwsForOrphanRead() {
        GraphResource buf = TestResources.transientBuffer("orphan");
        RenderNode reader = ComputePassNode.builder()
            .name("reader")
            .reads(ResourceEdge.read(buf, 0x00000020, 0x00000800))
            .writes(ResourceEdge.write(TestResources.transientBuffer("out"), 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();

        RenderGraphException ex = assertThrows(RenderGraphException.class,
            () -> compiler.validate(List.of(reader)));
        assertTrue(ex.getMessage().contains("orphan"));
        assertTrue(ex.getMessage().contains("reader"));
    }

    // -- Lifetime tests --

    @Test
    void computeLifetimes_singleWriterSingleReader() {
        GraphResource buf = TestResources.transientBuffer("buf");
        RenderNode writer = ComputePassNode.builder()
            .name("writer")
            .writes(ResourceEdge.write(buf, 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();
        RenderNode reader = ComputePassNode.builder()
            .name("reader")
            .reads(ResourceEdge.read(buf, 0x00000020, 0x00000800))
            .writes(ResourceEdge.write(TestResources.transientBuffer("out"), 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();

        Map<GraphResource, ResourceLifetime> lifetimes = compiler.computeLifetimes(List.of(writer, reader));

        ResourceLifetime lt = lifetimes.get(buf);
        assertNotNull(lt);
        assertEquals(0, lt.firstWritePass());
        assertEquals(1, lt.lastReadPass());
    }

    @Test
    void computeLifetimes_multipleReaders() {
        GraphResource buf = TestResources.transientBuffer("shared");
        RenderNode writer = ComputePassNode.builder()
            .name("writer")
            .writes(ResourceEdge.write(buf, 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();
        RenderNode reader1 = ComputePassNode.builder()
            .name("reader1")
            .reads(ResourceEdge.read(buf, 0x00000020, 0x00000800))
            .writes(ResourceEdge.write(TestResources.transientBuffer("o1"), 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();
        RenderNode reader2 = ComputePassNode.builder()
            .name("reader2")
            .reads(ResourceEdge.read(buf, 0x00000020, 0x00000800))
            .writes(ResourceEdge.write(TestResources.transientBuffer("o2"), 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();

        Map<GraphResource, ResourceLifetime> lifetimes = compiler.computeLifetimes(List.of(writer, reader1, reader2));

        ResourceLifetime lt = lifetimes.get(buf);
        assertEquals(0, lt.firstWritePass());
        assertEquals(2, lt.lastReadPass());
    }

    // -- Cull tests --

    @Test
    void cull_keepsNodesReachableFromPresent() {
        GraphResource color = TestResources.transientBuffer("color");
        GraphResource swapchain = TestResources.importedBuffer("swapchain");

        RenderNode lighting = GraphicsPassNode.builder()
            .name("lighting")
            .writes(ResourceEdge.write(color, 0x00000100, 0x00000400))
            .execute(ctx -> {})
            .build();
        RenderNode tonemap = GraphicsPassNode.builder()
            .name("tonemap")
            .reads(ResourceEdge.read(color, 0x00000020, 0x00000080))
            .writes(ResourceEdge.write(swapchain, 0x00000100, 0x00000400))
            .execute(ctx -> {})
            .build();
        RenderNode present = PresentNode.of(swapchain, java.lang.foreign.MemorySegment.NULL);

        List<RenderNode> culled = compiler.cull(List.of(lighting, tonemap, present));

        assertEquals(3, culled.size());
        assertTrue(culled.contains(lighting));
        assertTrue(culled.contains(tonemap));
        assertTrue(culled.contains(present));
    }

    @Test
    void cull_removesOrphanedNodes() {
        GraphResource color = TestResources.transientBuffer("color");
        GraphResource debug = TestResources.transientBuffer("debug");
        GraphResource swapchain = TestResources.importedBuffer("swapchain");

        RenderNode lighting = GraphicsPassNode.builder()
            .name("lighting")
            .writes(ResourceEdge.write(color, 0x00000100, 0x00000400))
            .execute(ctx -> {})
            .build();
        // This node writes only to a transient resource that nobody reads -> orphan
        RenderNode debugPass = GraphicsPassNode.builder()
            .name("debug")
            .writes(ResourceEdge.write(debug, 0x00000100, 0x00000400))
            .execute(ctx -> {})
            .build();
        RenderNode tonemap = GraphicsPassNode.builder()
            .name("tonemap")
            .reads(ResourceEdge.read(color, 0x00000020, 0x00000080))
            .writes(ResourceEdge.write(swapchain, 0x00000100, 0x00000400))
            .execute(ctx -> {})
            .build();
        RenderNode present = PresentNode.of(swapchain, java.lang.foreign.MemorySegment.NULL);

        List<RenderNode> culled = compiler.cull(List.of(lighting, debugPass, tonemap, present));

        assertEquals(3, culled.size());
        assertFalse(culled.contains(debugPass));
    }

    @Test
    void cull_respectsIsActive() {
        GraphResource color = TestResources.transientBuffer("color");
        GraphResource swapchain = TestResources.importedBuffer("swapchain");

        GraphicsPassNode lighting = GraphicsPassNode.builder()
            .name("lighting")
            .writes(ResourceEdge.write(color, 0x00000100, 0x00000400))
            .execute(ctx -> {})
            .build();
        lighting.setActive(false); // disabled

        RenderNode tonemap = GraphicsPassNode.builder()
            .name("tonemap")
            .reads(ResourceEdge.read(color, 0x00000020, 0x00000080))
            .writes(ResourceEdge.write(swapchain, 0x00000100, 0x00000400))
            .execute(ctx -> {})
            .build();
        RenderNode present = PresentNode.of(swapchain, java.lang.foreign.MemorySegment.NULL);

        List<RenderNode> culled = compiler.cull(List.of(lighting, tonemap, present));

        // lighting is inactive so it can't be reached even though tonemap reads its output
        assertFalse(culled.contains(lighting));
    }

    @Test
    void cull_keepsPersistentWriters() {
        GraphResource persistent = TestResources.persistentBuffer("particles");

        RenderNode sim = ComputePassNode.builder()
            .name("particle-sim")
            .writes(ResourceEdge.write(persistent, 0x00000020, 0x00000800))
            .execute(ctx -> {})
            .build();

        // No present node, but sim writes to a persistent resource -> it's a sink
        List<RenderNode> culled = compiler.cull(List.of(sim));

        assertEquals(1, culled.size());
        assertTrue(culled.contains(sim));
    }
}
