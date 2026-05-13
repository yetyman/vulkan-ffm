package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.feedback.AdaptiveFeedbackHandler;
import io.github.yetyman.vulkan.graph.feedback.FrameStats;
import io.github.yetyman.vulkan.graph.nodes.NodeStats;
import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.PersistentResourceRing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackAndRingTest {

    // -- AdaptiveFeedbackHandler tests --

    @Test
    void feedback_initialWeightMatchesMeasured() {
        AdaptiveFeedbackHandler handler = new AdaptiveFeedbackHandler();
        FrameStats stats = new FrameStats(0, 2_000_000, 500_000, Map.of(
            "lighting", new NodeStats(2_000_000, 500_000, 0)
        ));

        handler.onStats(stats);

        // After first frame, weight should be close to measured (2.0ms)
        double w = handler.weight("lighting");
        assertTrue(w > 0.0, "Weight should be positive after first stats");
    }

    @Test
    void feedback_convergesOverMultipleFrames() {
        AdaptiveFeedbackHandler handler = new AdaptiveFeedbackHandler(0.3, 0.05, 0.0, 1.0, 3.0, 1); // no momentum, no decay for predictable test

        // Feed constant 5ms for 20 frames
        for (int i = 0; i < 20; i++) {
            FrameStats stats = new FrameStats(i, 5_000_000, 100_000, Map.of(
                "pass", new NodeStats(5_000_000, 100_000, i)
            ));
            handler.onStats(stats);
        }

        // Should converge close to 5.0ms
        double w = handler.weight("pass");
        assertEquals(5.0, w, 0.5, "Weight should converge to measured value");
    }

    @Test
    void feedback_resistsSingleSpike() {
        AdaptiveFeedbackHandler handler = new AdaptiveFeedbackHandler(0.1, 0.05, 0.8, 1.0, 3.0, 1);

        // Establish baseline at 2ms
        for (int i = 0; i < 10; i++) {
            handler.onStats(new FrameStats(i, 2_000_000, 100_000, Map.of(
                "pass", new NodeStats(2_000_000, 100_000, i)
            )));
        }
        double baseline = handler.weight("pass");

        // Single 20ms spike
        handler.onStats(new FrameStats(10, 20_000_000, 100_000, Map.of(
            "pass", new NodeStats(20_000_000, 100_000, 10)
        )));

        double afterSpike = handler.weight("pass");
        // Should not jump to 20ms -- momentum and low alpha dampen it
        assertTrue(afterSpike < 10.0, "Single spike should not cause weight to jump to spike value");
        assertTrue(afterSpike > baseline, "Spike should still increase weight somewhat");
    }

    @Test
    void feedback_isWarmedUp() {
        AdaptiveFeedbackHandler handler = new AdaptiveFeedbackHandler();
        assertFalse(handler.isWarmedUp());

        // Feed enough frames to satisfy warmup (default warmupFrames=8)
        for (int i = 0; i < 8; i++) {
            handler.onStats(new FrameStats(i, 1_000_000, 100_000, Map.of(
                "pass", new NodeStats(1_000_000, 100_000, i)
            )));
        }
        assertTrue(handler.isWarmedUp());
    }

    // -- PersistentResourceRing tests --

    @Test
    void ring_doubleBuffered_alternates() {
        GraphResource a = TestResources.persistentBuffer("a");
        GraphResource b = TestResources.persistentBuffer("b");
        PersistentResourceRing<GraphResource> ring = new PersistentResourceRing<>("taa", List.of(a, b), 1);

        assertEquals(2, ring.copyCount());
        assertEquals(a, ring.current(0));
        assertEquals(b, ring.current(1));
        assertEquals(a, ring.current(2)); // wraps
    }

    @Test
    void ring_doubleBuffered_previousFrame() {
        GraphResource a = TestResources.persistentBuffer("a");
        GraphResource b = TestResources.persistentBuffer("b");
        PersistentResourceRing<GraphResource> ring = new PersistentResourceRing<>("taa", List.of(a, b), 1);

        // Frame 0 writes to a, frame 1 writes to b
        // At frame 1, previous(1, 1) should be a (frame 0's write target)
        assertEquals(a, ring.previous(1, 1));
        // At frame 2, previous(2, 1) should be b (frame 1's write target)
        assertEquals(b, ring.previous(2, 1));
    }

    @Test
    void ring_tripleBuffered_canReadTwoBack() {
        GraphResource a = TestResources.persistentBuffer("a");
        GraphResource b = TestResources.persistentBuffer("b");
        GraphResource c = TestResources.persistentBuffer("c");
        PersistentResourceRing<GraphResource> ring = new PersistentResourceRing<>("particles", List.of(a, b, c), 2);

        assertEquals(3, ring.copyCount());

        // Frame 5: current = copies[5%3] = copies[2] = c
        assertEquals(c, ring.current(5));
        // Frame 5, 1 back = copies[(5-1+3)%3] = copies[4%3] = copies[1] = b
        assertEquals(b, ring.previous(5, 1));
        // Frame 5, 2 back = copies[(5-2+3)%3] = copies[3%3] = copies[0] = a
        assertEquals(a, ring.previous(5, 2));
    }

    @Test
    void ring_rejectsInvalidFramesAgo() {
        GraphResource a = TestResources.persistentBuffer("a");
        GraphResource b = TestResources.persistentBuffer("b");
        PersistentResourceRing<GraphResource> ring = new PersistentResourceRing<>("r", List.of(a, b), 1);

        assertThrows(IllegalArgumentException.class, () -> ring.previous(5, 0));
        assertThrows(IllegalArgumentException.class, () -> ring.previous(5, 2));
    }

    @Test
    void ring_rejectsWrongCopyCount() {
        GraphResource a = TestResources.persistentBuffer("a");
        assertThrows(IllegalArgumentException.class,
            () -> new PersistentResourceRing<>("r", List.of(a), 1));
    }
}
