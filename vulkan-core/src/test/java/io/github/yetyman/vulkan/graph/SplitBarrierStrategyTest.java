package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.barriers.BarrierBatch;
import io.github.yetyman.vulkan.graph.barriers.SplitBarrierStrategy;
import io.github.yetyman.vulkan.graph.edges.ResourceEdge;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.*;

class SplitBarrierStrategyTest {

    private SplitBarrierStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SplitBarrierStrategy();
    }

    @Test
    void noBarrier_readToRead_sameQueue() {
        // SHADER_READ (0x20) -> SHADER_READ (0x20) with no layout change = no barrier needed
        GraphResource buf = TestResources.transientBuffer("buf");
        buf.updateState(0x00000020, 0x00000080, 0); // SHADER_READ, FRAGMENT_SHADER

        ResourceEdge consumer = ResourceEdge.read(buf, 0x00000020, 0x00000080);

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(buf, consumer, batch, arena);
            assertTrue(batch.isEmpty(), "Read-to-read should not emit a barrier");
        }
    }

    @Test
    void emitsBarrier_writeToRead() {
        // SHADER_WRITE (0x40) -> SHADER_READ (0x20) = barrier needed
        GraphResource buf = TestResources.transientBuffer("buf");
        buf.updateState(0x00000040, 0x00000800, 0); // SHADER_WRITE, COMPUTE_SHADER

        ResourceEdge consumer = ResourceEdge.read(buf, 0x00000020, 0x00000080); // SHADER_READ, FRAGMENT

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(buf, consumer, batch, arena);
            assertFalse(batch.isEmpty(), "Write-to-read should emit a barrier");
            assertEquals(1, batch.barriers().size());
            assertEquals(0x00000800, batch.srcStageMask()); // COMPUTE
            assertEquals(0x00000080, batch.dstStageMask()); // FRAGMENT
        }
    }

    @Test
    void emitsBarrier_writeToWrite() {
        // TRANSFER_WRITE (0x800) -> SHADER_WRITE (0x40) = barrier needed
        GraphResource buf = TestResources.transientBuffer("buf");
        buf.updateState(0x00000800, 0x00001000, 0); // TRANSFER_WRITE, TRANSFER

        ResourceEdge consumer = ResourceEdge.write(buf, 0x00000040, 0x00000800); // SHADER_WRITE, COMPUTE

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(buf, consumer, batch, arena);
            assertFalse(batch.isEmpty(), "Write-to-write should emit a barrier");
        }
    }

    @Test
    void noBarrier_firstUseWithNoHistory() {
        // Resource with no prior access (srcAccess=0, srcStage=0)
        GraphResource buf = TestResources.transientBuffer("fresh");
        buf.updateState(0, 0, ~0);

        ResourceEdge consumer = ResourceEdge.write(buf, 0x00000040, 0x00000800);

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(buf, consumer, batch, arena);
            assertTrue(batch.isEmpty(), "First use with no prior access should skip barrier");
        }
    }

    @Test
    void emitsImageBarrier_layoutTransition() {
        // Image needs layout transition: UNDEFINED -> COLOR_ATTACHMENT_OPTIMAL
        TestImageResource img = new TestImageResource("color", 0, 0, 0); // layout=UNDEFINED(0)
        img.updateState(0, 0x00000001, ~0); // TOP_OF_PIPE

        // Consumer wants COLOR_ATTACHMENT_OPTIMAL (2) with WRITE
        ResourceEdge consumer = ResourceEdge.writeImage(img, 0x00000100, 0x00000400, 2);

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(img, consumer, batch, arena);
            assertFalse(batch.isEmpty(), "Layout transition should emit a barrier");
            assertEquals(2, img.currentLayout(), "Layout should be updated after barrier");
        }
    }

    @Test
    void noBarrier_imageReadToRead_sameLayout() {
        // Image read-to-read with same layout = no barrier
        TestImageResource img = new TestImageResource("tex", 5, 0x00000020, 0x00000080);
        // layout=5 (SHADER_READ_ONLY_OPTIMAL), SHADER_READ, FRAGMENT

        ResourceEdge consumer = ResourceEdge.readImage(img, 0x00000020, 0x00000080, 5);

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(img, consumer, batch, arena);
            assertTrue(batch.isEmpty(), "Image read-to-read same layout should skip barrier");
        }
    }

    @Test
    void emitsBarrier_imageReadToRead_differentLayout() {
        // Image read-to-read but layout changes = barrier needed
        TestImageResource img = new TestImageResource("tex", 5, 0x00000020, 0x00000080);
        // layout=5 (SHADER_READ_ONLY), SHADER_READ, FRAGMENT

        // Consumer wants GENERAL layout (1)
        ResourceEdge consumer = ResourceEdge.readImage(img, 0x00000020, 0x00000800, 1);

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(img, consumer, batch, arena);
            assertFalse(batch.isEmpty(), "Layout change requires barrier even for read-to-read");
            assertEquals(1, img.currentLayout());
        }
    }

    @Test
    void batchAccumulatesMultipleBarriers() {
        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();

            GraphResource buf1 = TestResources.transientBuffer("buf1");
            buf1.updateState(0x00000040, 0x00000800, 0); // SHADER_WRITE, COMPUTE
            strategy.emit(buf1, ResourceEdge.read(buf1, 0x00000020, 0x00000080), batch, arena);

            GraphResource buf2 = TestResources.transientBuffer("buf2");
            buf2.updateState(0x00000800, 0x00001000, 0); // TRANSFER_WRITE, TRANSFER
            strategy.emit(buf2, ResourceEdge.read(buf2, 0x00000020, 0x00000080), batch, arena);

            assertEquals(2, batch.barriers().size());
            // Stage masks should be OR'd together
            assertEquals(0x00000800 | 0x00001000, batch.srcStageMask());
            assertEquals(0x00000080, batch.dstStageMask());
        }
    }

    @Test
    void emitsOwnershipTransfer_bufferCrossQueue() {
        // Buffer written on queue family 0 (compute), read on queue family 1 (graphics)
        GraphResource buf = TestResources.transientBuffer("crossQueue");
        buf.updateState(0x00000040, 0x00000800, 0); // SHADER_WRITE, COMPUTE, queue family 0

        ResourceEdge consumer = ResourceEdge.read(buf, 0x00000020, 0x00000080); // SHADER_READ, FRAGMENT

        strategy.setConsumerQueueFamily(1); // consumer is on queue family 1

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(buf, consumer, batch, arena);

            // Should NOT emit a same-queue barrier
            assertTrue(batch.hasNoSameQueueBarriers(), "Cross-queue should not emit same-queue barrier");
            // Should emit an ownership transfer pair
            assertTrue(batch.hasOwnershipTransfers(), "Cross-queue should emit ownership transfer");
            assertEquals(1, batch.transferCount());

            var transfer = batch.getTransfer(0);
            assertEquals(0, transfer.srcQueueFamily());
            assertEquals(1, transfer.dstQueueFamily());
        }
    }

    @Test
    void emitsOwnershipTransfer_imageCrossQueue_withLayoutTransition() {
        // Image written on queue family 0 (compute), read on queue family 1 (graphics)
        // with a layout transition from GENERAL(1) to SHADER_READ_ONLY(5)
        TestImageResource img = new TestImageResource("crossQueueImg", 1, 0x00000040, 0x00000800);
        img.updateState(0x00000040, 0x00000800, 0); // SHADER_WRITE, COMPUTE, queue family 0

        ResourceEdge consumer = ResourceEdge.readImage(img, 0x00000020, 0x00000080, 5); // SHADER_READ, FRAGMENT, layout=5

        strategy.setConsumerQueueFamily(1);

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(img, consumer, batch, arena);

            assertTrue(batch.hasNoSameQueueBarriers());
            assertTrue(batch.hasOwnershipTransfers());
            assertEquals(1, batch.transferCount());

            var transfer = batch.getTransfer(0);
            assertEquals(0, transfer.srcQueueFamily());
            assertEquals(1, transfer.dstQueueFamily());

            // Layout should be updated
            assertEquals(5, img.currentLayout());
        }
    }

    @Test
    void noOwnershipTransfer_sameQueueFamily() {
        // Both on queue family 0 -- no ownership transfer needed
        GraphResource buf = TestResources.transientBuffer("sameQueue");
        buf.updateState(0x00000040, 0x00000800, 0); // SHADER_WRITE, COMPUTE, queue family 0

        ResourceEdge consumer = ResourceEdge.read(buf, 0x00000020, 0x00000080);

        strategy.setConsumerQueueFamily(0); // same queue family

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(buf, consumer, batch, arena);

            // Should emit a regular same-queue barrier, not an ownership transfer
            assertFalse(batch.hasNoSameQueueBarriers(), "Same-queue write-to-read should emit barrier");
            assertFalse(batch.hasOwnershipTransfers(), "Same-queue should not emit ownership transfer");
        }
    }

    @Test
    void noOwnershipTransfer_ignoredQueueFamily() {
        // Source queue is IGNORED -- no ownership transfer
        GraphResource buf = TestResources.transientBuffer("ignored");
        buf.updateState(0x00000040, 0x00000800, ~0); // SHADER_WRITE, COMPUTE, QUEUE_FAMILY_IGNORED

        ResourceEdge consumer = ResourceEdge.read(buf, 0x00000020, 0x00000080);

        strategy.setConsumerQueueFamily(1);

        try (Arena arena = Arena.ofConfined()) {
            BarrierBatch batch = new BarrierBatch();
            strategy.emit(buf, consumer, batch, arena);

            // Should emit a regular barrier since source is IGNORED
            assertFalse(batch.hasNoSameQueueBarriers());
            assertFalse(batch.hasOwnershipTransfers());
        }
    }
}
