package io.github.yetyman.vulkan.sample.graph;

import io.github.yetyman.vulkan.graph.RenderGraph;
import io.github.yetyman.vulkan.graph.RenderGraphVisualizer;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.edges.TemporalEdge;
import io.github.yetyman.vulkan.graph.nodes.ComputePassNode;
import io.github.yetyman.vulkan.graph.nodes.GraphicsPassNode;
import io.github.yetyman.vulkan.graph.nodes.PresentNode;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.InitialState;
import io.github.yetyman.vulkan.graph.resources.ResourceDescriptor;
import io.github.yetyman.vulkan.graph.resources.TemporalResource;
import io.github.yetyman.vulkan.graph.scheduling.QueueCapability;
import io.github.yetyman.vulkan.graph.scheduling.ScheduleHint;

/**
 * Demonstrates temporal unrolling: a TAA-style feedback loop where the resolve pass
 * reads the previous frame's history and writes the current frame's history.
 *
 * Without the graph, you'd manually manage:
 * - Two physical history buffers and a flip counter
 * - Barriers between frames for the history slot transitions
 * - Initial clear on frame 0
 * - Correct slot indexing every frame
 *
 * With temporal unrolling, you just declare the cycle and the graph handles everything:
 *
 * <pre>
 *   Logical graph (has cycle):
 *     Render --> TAA Resolve --> Present
 *                   ^    |
 *                   |    v
 *                 history (temporal feedback)
 *
 *   Per-submission (DAG - what actually executes):
 *     Render --> TAA Resolve --> Present
 *                   ^
 *                   |
 *              history[prev]  (auto-selected physical slot)
 *
 *     TAA Resolve also writes --> history[curr] (for next frame)
 * </pre>
 *
 * This example only builds and validates the graph structure (no GPU needed).
 * It prints the compiled DAG to show the resolved execution order.
 */
public class TemporalUnrollingExample {

    public static void main(String[] args) {
        System.out.println("=== Temporal Unrolling Example ===\n");
        System.out.println("Demonstrates a TAA feedback loop where the graph automatically");
        System.out.println("manages double-buffered history with correct slot selection.\n");

        // -- Declare the temporal resource --
        // The graph allocates 2 physical copies and flips between them each frame.
        // Frame 0: reads get a black-cleared resource (no history yet).
        TemporalResource taaHistory = TemporalResource.builder()
            .name("taa_history")
            .descriptor(ResourceDescriptor.image(
                44,   // VK_FORMAT_R16G16B16A16_SFLOAT
                1920, 1080,
                0x10 | 0x04))  // COLOR_ATTACHMENT | SAMPLED
            .bufferCount(2)
            .initialState(InitialState.Clear.BLACK)
            .build();

        // -- Simulate physical slot allocation (normally done by the graph allocator) --
        GraphResource historySlotA = fakeResource("taa_history_A");
        GraphResource historySlotB = fakeResource("taa_history_B");
        taaHistory.setPhysicalSlots(new GraphResource[]{historySlotA, historySlotB});

        // -- Show the flip logic --
        System.out.println("Physical slot rotation:");
        for (int frame = 0; frame < 5; frame++) {
            GraphResource readSlot = taaHistory.previousReadSlot();
            GraphResource writeSlot = taaHistory.currentWriteSlot();
            System.out.printf("  Frame %d: read from [%s], write to [%s]%n",
                frame, readSlot.name(), writeSlot.name());
            taaHistory.onWriteExecuted();
        }

        System.out.println("\nThe graph handles this automatically. Your pass declaration is just:\n");
        System.out.println("""
            // Declare temporal resource
            TemporalResource taaHistory = TemporalResource.builder()
                .name("taa_history")
                .descriptor(ResourceDescriptor.image(format, w, h, usage))
                .bufferCount(2)
                .initialState(InitialState.Clear.BLACK)
                .build();
            
            // TAA resolve pass - reads previous history, writes current history
            GraphicsPassNode.builder()
                .name("taa_resolve")
                .reads(ResourceEdge.read(currentColor, ...))
                .temporalEdge(TemporalEdge.readPrevious(taaHistory, ...))
                .temporalEdge(TemporalEdge.writeCurrent(taaHistory, ...))
                .writes(ResourceEdge.write(output, ...))
                .execute(ctx -> { /* TAA shader dispatch */ })
                .build();
            """);

        System.out.println("Compare to manual management (without the graph):\n");
        System.out.println("""
            // Manual: you manage all of this yourself
            VkImage[] historySlots = new VkImage[2];
            int writeCount = 0;
            
            void renderFrame() {
                int readIdx = (writeCount - 1 + 2) % 2;
                int writeIdx = writeCount % 2;
                
                // Barrier: transition readSlot from WRITE to READ layout
                insertBarrier(historySlots[readIdx], WRITE_LAYOUT, READ_LAYOUT);
                // Barrier: transition writeSlot from READ to WRITE layout  
                insertBarrier(historySlots[writeIdx], READ_LAYOUT, WRITE_LAYOUT);
                
                if (writeCount == 0) {
                    // First frame: clear the read slot (no history yet)
                    clearImage(historySlots[readIdx]);
                }
                
                // Bind correct slots to descriptor set
                updateDescriptorSet(readIdx, writeIdx);
                
                // Dispatch TAA
                dispatch();
                
                writeCount++;
            }
            """);

        System.out.println("The temporal unrolling eliminates all manual buffer management,");
        System.out.println("barrier insertion, initial state handling, and slot indexing.");
    }

    private static GraphResource fakeResource(String name) {
        return new GraphResource() {
            @Override public String name() { return name; }
            @Override public java.lang.foreign.MemorySegment handle() { return java.lang.foreign.MemorySegment.NULL; }
            @Override public int lastAccessMask() { return 0; }
            @Override public int lastStageMask() { return 0; }
            @Override public int owningQueueFamily() { return 0; }
            @Override public void updateState(int a, int s, int q) {}
            @Override public boolean isTransient() { return true; }
            @Override public boolean isImported() { return false; }
            @Override public io.github.yetyman.vulkan.graph.resources.ResourceLifetime lifetime() {
                return new io.github.yetyman.vulkan.graph.resources.ResourceLifetime();
            }
        };
    }
}
