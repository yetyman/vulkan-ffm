package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.PresentNode;
import io.github.yetyman.vulkan.graph.nodes.RenderNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.InitialState;
import io.github.yetyman.vulkan.graph.resources.ResourceDescriptor;
import io.github.yetyman.vulkan.graph.resources.TemporalResource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemporalUnrollerTest {

    private TemporalUnroller unroller;

    @BeforeEach
    void setUp() {
        unroller = new TemporalUnroller();
    }

    private TemporalResource makeTemporalResource(String name, InitialState initialState) {
        TemporalResource.Builder b = TemporalResource.builder()
            .name(name)
            .descriptor(ResourceDescriptor.image(44, 1920, 1080, 0x10 | 0x04))
            .bufferCount(2);
        if (initialState != null) b.initialState(initialState);
        return b.build();
    }

    // -- Temporal completeness validation --

    @Test
    void validate_passesWhenTemporalResourceIsReadAndWritten() {
        TemporalResource history = makeTemporalResource("history", InitialState.Clear.BLACK);

        GraphResource output = TestResources.transientBuffer("output");
        RenderNode taa = GraphicsPassNode.builder()
            .name("taa")
            .temporalEdge(TemporalEdge.readPrevious(history, 0x20, 0x80))
            .temporalEdge(TemporalEdge.writeCurrent(history, 0x100, 0x400))
            .writes(ResourceEdge.write(output, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        assertDoesNotThrow(() -> unroller.validate(List.of(taa), List.of(history)));
    }

    @Test
    void validate_throwsWhenTemporalResourceIsReadButNeverWritten() {
        TemporalResource history = makeTemporalResource("history", InitialState.Clear.BLACK);

        GraphResource output = TestResources.transientBuffer("output");
        RenderNode taa = GraphicsPassNode.builder()
            .name("taa")
            .temporalEdge(TemporalEdge.readPrevious(history, 0x20, 0x80))
            .writes(ResourceEdge.write(output, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        RenderGraphException ex = assertThrows(RenderGraphException.class,
            () -> unroller.validate(List.of(taa), List.of(history)));
        assertTrue(ex.getMessage().contains("history"));
        assertTrue(ex.getMessage().contains("never written"));
    }

    // -- Non-temporal cycle detection --

    @Test
    void validate_throwsForNonTemporalCycle() {
        GraphResource bufA = TestResources.transientBuffer("a");
        GraphResource bufB = TestResources.transientBuffer("b");

        // A writes bufA, reads bufB; B writes bufB, reads bufA -> cycle
        RenderNode nodeA = ComputePassNode.builder()
            .name("nodeA")
            .reads(ResourceEdge.read(bufB, 0x20, 0x800))
            .writes(ResourceEdge.write(bufA, 0x20, 0x800))
            .execute(ctx -> {})
            .build();
        RenderNode nodeB = ComputePassNode.builder()
            .name("nodeB")
            .reads(ResourceEdge.read(bufA, 0x20, 0x800))
            .writes(ResourceEdge.write(bufB, 0x20, 0x800))
            .execute(ctx -> {})
            .build();

        RenderGraphException ex = assertThrows(RenderGraphException.class,
            () -> unroller.validate(List.of(nodeA, nodeB), List.of()));
        assertTrue(ex.getMessage().contains("Non-temporal cycle"));
    }

    @Test
    void validate_allowsTemporalCycleButNotNonTemporalCycle() {
        TemporalResource history = makeTemporalResource("history", InitialState.Clear.BLACK);
        GraphResource color = TestResources.transientBuffer("color");

        // TAA reads history[prev] and writes history[curr] - this is a temporal cycle, allowed
        RenderNode taa = GraphicsPassNode.builder()
            .name("taa")
            .reads(ResourceEdge.read(color, 0x20, 0x80))
            .temporalEdge(TemporalEdge.readPrevious(history, 0x20, 0x80))
            .temporalEdge(TemporalEdge.writeCurrent(history, 0x100, 0x400))
            .writes(ResourceEdge.write(TestResources.transientBuffer("out"), 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        RenderNode render = GraphicsPassNode.builder()
            .name("render")
            .writes(ResourceEdge.write(color, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        assertDoesNotThrow(() -> unroller.validate(List.of(render, taa), List.of(history)));
    }

    // -- Starting point resolution --

    @Test
    void validateStartingPoints_passesWhenInitialStateDefined() {
        TemporalResource history = makeTemporalResource("history", InitialState.Clear.BLACK);
        GraphResource swapchain = TestResources.importedBuffer("swapchain");

        RenderNode taa = GraphicsPassNode.builder()
            .name("taa")
            .temporalEdge(TemporalEdge.readPrevious(history, 0x20, 0x80))
            .temporalEdge(TemporalEdge.writeCurrent(history, 0x100, 0x400))
            .writes(ResourceEdge.write(swapchain, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        RenderNode present = PresentNode.of(swapchain, MemorySegment.NULL);

        assertDoesNotThrow(() -> unroller.validateStartingPoints(
            List.of(taa, present), List.of(history)));
    }

    @Test
    void validateStartingPoints_throwsWhenInitialStateMissing() {
        TemporalResource history = makeTemporalResource("history", null); // no initial state
        GraphResource swapchain = TestResources.importedBuffer("swapchain");

        RenderNode taa = GraphicsPassNode.builder()
            .name("taa")
            .temporalEdge(TemporalEdge.readPrevious(history, 0x20, 0x80))
            .temporalEdge(TemporalEdge.writeCurrent(history, 0x100, 0x400))
            .writes(ResourceEdge.write(swapchain, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        RenderNode present = PresentNode.of(swapchain, MemorySegment.NULL);

        RenderGraphException ex = assertThrows(RenderGraphException.class,
            () -> unroller.validateStartingPoints(List.of(taa, present), List.of(history)));
        assertTrue(ex.getMessage().contains("history"));
        assertTrue(ex.getMessage().contains("missing initial state"));
    }

    @Test
    void validateStartingPoints_unreachableTemporalDoesNotRequireInitialState() {
        TemporalResource reachable = makeTemporalResource("reachable", null); // no initial state
        TemporalResource unreachable = makeTemporalResource("unreachable", null); // no initial state

        GraphResource swapchain = TestResources.importedBuffer("swapchain");
        GraphResource unused = TestResources.transientBuffer("unused");

        // reachable is read by a pass connected to present
        RenderNode taa = GraphicsPassNode.builder()
            .name("taa")
            .temporalEdge(TemporalEdge.readPrevious(reachable, 0x20, 0x80))
            .temporalEdge(TemporalEdge.writeCurrent(reachable, 0x100, 0x400))
            .writes(ResourceEdge.write(swapchain, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        // unreachable is read by a pass that only writes to a transient resource
        // that nobody else reads - so it's an orphan (not a sink, not reachable from present)
        RenderNode orphan = ComputePassNode.builder()
            .name("orphan")
            .temporalEdge(TemporalEdge.readPrevious(unreachable, 0x20, 0x800))
            .writes(ResourceEdge.write(unused, 0x20, 0x800))
            .execute(ctx -> {})
            .build();

        // Separate node writes unreachable temporal (this IS a sink, but the reader "orphan"
        // is only reachable from this writer, not from present)
        RenderNode unreachableWriter = ComputePassNode.builder()
            .name("unreachableWriter")
            .temporalEdge(TemporalEdge.writeCurrent(unreachable, 0x20, 0x800))
            .writes(ResourceEdge.write(TestResources.transientBuffer("unused2"), 0x20, 0x800))
            .execute(ctx -> {})
            .build();

        RenderNode present = PresentNode.of(swapchain, MemorySegment.NULL);

        // Should only complain about "reachable", not "unreachable"
        // The unreachable temporal IS written (by unreachableWriter which is a sink),
        // but its READER (orphan) is not reachable from present.
        // However, unreachableWriter itself is a sink (temporal write), so "unreachable"
        // IS required because unreachableWriter is reachable from itself as a terminal.
        // Actually, the starting point resolution walks backwards from ALL sinks,
        // so unreachable IS required. The test needs to be about a temporal resource
        // whose reader is inactive.

        // Let's test with an inactive reader instead
        GraphicsPassNode inactiveReader = GraphicsPassNode.builder()
            .name("inactiveReader")
            .temporalEdge(TemporalEdge.readPrevious(unreachable, 0x20, 0x80))
            .temporalEdge(TemporalEdge.writeCurrent(unreachable, 0x100, 0x400))
            .writes(ResourceEdge.write(TestResources.transientBuffer("unused3"), 0x100, 0x400))
            .execute(ctx -> {})
            .build();
        inactiveReader.setActive(false);

        // With inactive reader, unreachable should not be required
        RenderGraphException ex = assertThrows(RenderGraphException.class,
            () -> unroller.validateStartingPoints(
                List.of(taa, inactiveReader, present), List.of(reachable, unreachable)));
        assertTrue(ex.getMessage().contains("reachable"));
        // unreachable's reader is inactive, so it shouldn't be in the error
        // But the error message contains "reachable" which is a substring of "unreachable"
        // so we need to check more carefully
        String msg = ex.getMessage();
        // Count occurrences - should only mention "reachable" resource, not "unreachable"
        int reachableIdx = msg.indexOf("\"reachable\"");
        int unreachableIdx = msg.indexOf("\"unreachable\"");
        assertTrue(reachableIdx >= 0, "Should mention reachable");
        assertEquals(-1, unreachableIdx, "Should NOT mention unreachable");
    }

    // -- resolveRequiredInitials --

    @Test
    void resolveRequiredInitials_findsTransitiveTemporalDependencies() {
        TemporalResource history = makeTemporalResource("history", InitialState.Clear.BLACK);
        TemporalResource gi = makeTemporalResource("gi", null);

        GraphResource swapchain = TestResources.importedBuffer("swapchain");
        GraphResource litColor = TestResources.transientBuffer("litColor");

        // Lighting reads GI[prev], writes litColor
        RenderNode lighting = GraphicsPassNode.builder()
            .name("lighting")
            .temporalEdge(TemporalEdge.readPrevious(gi, 0x20, 0x80))
            .temporalEdge(TemporalEdge.writeCurrent(gi, 0x100, 0x400))
            .writes(ResourceEdge.write(litColor, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        // TAA reads litColor + history[prev], writes swapchain + history[curr]
        RenderNode taa = GraphicsPassNode.builder()
            .name("taa")
            .reads(ResourceEdge.read(litColor, 0x20, 0x80))
            .temporalEdge(TemporalEdge.readPrevious(history, 0x20, 0x80))
            .temporalEdge(TemporalEdge.writeCurrent(history, 0x100, 0x400))
            .writes(ResourceEdge.write(swapchain, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        RenderNode present = PresentNode.of(swapchain, MemorySegment.NULL);

        Set<TemporalResource> required = unroller.resolveRequiredInitials(
            List.of(lighting, taa, present), 0);

        // Both history and gi should be required on frame 0
        assertTrue(required.contains(history));
        assertTrue(required.contains(gi));
    }

    // -- TemporalResource slot logic --

    @Test
    void temporalResource_doubleBufferFlipLogic() {
        TemporalResource tr = makeTemporalResource("test", InitialState.Clear.BLACK);
        GraphResource slotA = TestResources.transientBuffer("slot_a");
        GraphResource slotB = TestResources.transientBuffer("slot_b");
        tr.setPhysicalSlots(new GraphResource[]{slotA, slotB});

        // Before any write: writeCount=0, currentWrite=slot[0]=A, previousRead=slot[-1+2 % 2]=slot[1]=B
        assertEquals(slotA, tr.currentWriteSlot());
        assertEquals(slotB, tr.previousReadSlot());

        // After first write: writeCount=1, currentWrite=slot[1]=B, previousRead=slot[0]=A
        tr.onWriteExecuted();
        assertEquals(slotB, tr.currentWriteSlot());
        assertEquals(slotA, tr.previousReadSlot());

        // After second write: writeCount=2, currentWrite=slot[0]=A, previousRead=slot[1]=B
        tr.onWriteExecuted();
        assertEquals(slotA, tr.currentWriteSlot());
        assertEquals(slotB, tr.previousReadSlot());
    }

    @Test
    void temporalResource_tripleBufferFlipLogic() {
        TemporalResource tr = TemporalResource.builder()
            .name("triple")
            .descriptor(ResourceDescriptor.image(44, 1920, 1080, 0x14))
            .bufferCount(3)
            .initialState(InitialState.Clear.BLACK)
            .build();

        GraphResource slotA = TestResources.transientBuffer("a");
        GraphResource slotB = TestResources.transientBuffer("b");
        GraphResource slotC = TestResources.transientBuffer("c");
        tr.setPhysicalSlots(new GraphResource[]{slotA, slotB, slotC});

        // writeCount=0: write=A, prev=C
        assertEquals(slotA, tr.currentWriteSlot());
        assertEquals(slotC, tr.previousReadSlot());

        tr.onWriteExecuted(); // writeCount=1: write=B, prev=A
        assertEquals(slotB, tr.currentWriteSlot());
        assertEquals(slotA, tr.previousReadSlot());

        tr.onWriteExecuted(); // writeCount=2: write=C, prev=B
        assertEquals(slotC, tr.currentWriteSlot());
        assertEquals(slotB, tr.previousReadSlot());

        tr.onWriteExecuted(); // writeCount=3: write=A, prev=C
        assertEquals(slotA, tr.currentWriteSlot());
        assertEquals(slotC, tr.previousReadSlot());
    }

    // -- PassMask --

    @Test
    void passMask_evaluatesCorrectly() {
        GraphResource out = TestResources.transientBuffer("out");
        GraphicsPassNode active = GraphicsPassNode.builder()
            .name("active")
            .writes(ResourceEdge.write(out, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        GraphicsPassNode inactive = GraphicsPassNode.builder()
            .name("inactive")
            .writes(ResourceEdge.write(TestResources.transientBuffer("x"), 0x100, 0x400))
            .execute(ctx -> {})
            .build();
        inactive.setActive(false);

        PassMask mask = PassMask.evaluate(List.of(active, inactive));
        assertTrue(mask.isActive(0));
        assertFalse(mask.isActive(1));
        assertEquals(1, mask.activeCount());
    }

    @Test
    void passMask_equalityAndHashing() {
        GraphResource out = TestResources.transientBuffer("out");
        GraphicsPassNode node = GraphicsPassNode.builder()
            .name("n")
            .writes(ResourceEdge.write(out, 0x100, 0x400))
            .execute(ctx -> {})
            .build();

        List<RenderNode> nodes = List.of(node);
        PassMask mask1 = PassMask.evaluate(nodes);
        PassMask mask2 = PassMask.evaluate(nodes);

        assertEquals(mask1, mask2);
        assertEquals(mask1.hashCode(), mask2.hashCode());
    }
}
