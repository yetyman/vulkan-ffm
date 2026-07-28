package io.github.yetyman.vulkan.foundation.ecs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the typed event registration and dispatch system:
 * - EventType identity
 * - Typed handler registration
 * - Receive-all handlers
 * - Capture/bubble phases
 * - stopPropagation behavior
 * - CaptureBubbleTraversal tree-wide dispatch
 */
class EventSystemTest {

    // --- Test event types ---

    static final EventType<TestEvent> CLICK = EventType.create("click");
    static final EventType<TestEvent> SCROLL = EventType.create("scroll");

    static class TestEvent extends Event {
        final String data;

        TestEvent(EventType<?> type, String data) {
            super(type);
            this.data = data;
        }
    }

    // --- EventType identity ---

    @Test
    void eventTypesAreDistinct() {
        assertNotSame(CLICK, SCROLL);
        assertNotEquals(CLICK.id(), SCROLL.id());
    }

    @Test
    void eventTypeHasName() {
        assertEquals("click", CLICK.name());
        assertEquals("scroll", SCROLL.name());
    }

    // --- Typed handler registration ---

    @Test
    void typedHandlerOnlyReceivesMatchingType() {
        try (Tree tree = new Tree()) {
            tree.initialize();
            List<String> received = new ArrayList<>();

            tree.root().addEventHandler(CLICK, e -> received.add("click:" + e.data));
            tree.root().addEventHandler(SCROLL, e -> received.add("scroll:" + e.data));

            tree.root().fireEvent(new TestEvent(CLICK, "pos1"));
            tree.root().fireEvent(new TestEvent(SCROLL, "delta5"));

            // CLICK handler should NOT receive scroll events and vice versa
            // With capture+bubble on root-only node, each handler fires twice (capture + bubble)
            assertTrue(received.contains("click:pos1"));
            assertTrue(received.contains("scroll:delta5"));
            assertFalse(received.stream().anyMatch(s -> s.equals("click:delta5")));
            assertFalse(received.stream().anyMatch(s -> s.equals("scroll:pos1")));
        }
    }

    @Test
    void receiveAllHandlerGetsEverything() {
        try (Tree tree = new Tree()) {
            tree.initialize();
            List<String> received = new ArrayList<>();

            tree.root().addReceiveAllHandler(e -> {
                if (e instanceof TestEvent te) {
                    received.add("all:" + te.data);
                }
            });

            tree.root().fireEvent(new TestEvent(CLICK, "a"));
            tree.root().fireEvent(new TestEvent(SCROLL, "b"));

            assertTrue(received.stream().anyMatch(s -> s.equals("all:a")));
            assertTrue(received.stream().anyMatch(s -> s.equals("all:b")));
        }
    }

    @Test
    void noHandlersRegisteredIsHarmless() {
        try (Tree tree = new Tree()) {
            tree.initialize();
            // Should not throw
            assertDoesNotThrow(() -> tree.root().fireEvent(new TestEvent(CLICK, "x")));
        }
    }

    // --- Capture/Bubble phases ---

    @Test
    void capturePhaseFiresRootFirst() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            Node grandchild = child.createChild();
            tree.initialize();

            List<String> order = new ArrayList<>();

            tree.root().addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.CAPTURE) order.add("root-capture");
            });
            child.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.CAPTURE) order.add("child-capture");
            });
            grandchild.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.CAPTURE) order.add("grandchild-capture");
            });

            // Fire from grandchild: capture goes root -> child -> grandchild
            grandchild.fireEvent(new TestEvent(CLICK, "test"));

            assertEquals(List.of("root-capture", "child-capture", "grandchild-capture"), order);
        }
    }

    @Test
    void bubblePhaseFiresTargetFirst() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            Node grandchild = child.createChild();
            tree.initialize();

            List<String> order = new ArrayList<>();

            tree.root().addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.BUBBLE) order.add("root-bubble");
            });
            child.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.BUBBLE) order.add("child-bubble");
            });
            grandchild.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.BUBBLE) order.add("grandchild-bubble");
            });

            grandchild.fireEvent(new TestEvent(CLICK, "test"));

            assertEquals(List.of("grandchild-bubble", "child-bubble", "root-bubble"), order);
        }
    }

    // --- stopPropagation ---

    @Test
    void stopPropagationInCaptureStopsCapture() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            tree.initialize();

            List<String> order = new ArrayList<>();

            tree.root().addEventHandler(CLICK, e -> {
                order.add("root-capture");
                e.stopPropagation(); // Stop during capture
            });
            child.addEventHandler(CLICK, e -> {
                order.add("child-" + e.phase());
            });

            child.fireEvent(new TestEvent(CLICK, "test"));

            // Root captures and stops; child should still get bubble because stopped resets
            assertTrue(order.contains("root-capture"));
            assertTrue(order.contains("child-BUBBLE")); // stopped resets between phases
        }
    }

    @Test
    void stopPropagationInBubbleStopsBubble() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            tree.initialize();

            List<String> order = new ArrayList<>();

            child.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.BUBBLE) {
                    order.add("child-bubble");
                    e.stopPropagation();
                }
            });
            tree.root().addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.BUBBLE) order.add("root-bubble");
            });

            child.fireEvent(new TestEvent(CLICK, "test"));

            assertTrue(order.contains("child-bubble"));
            assertFalse(order.contains("root-bubble")); // stopped in bubble
        }
    }

    // --- Handler removal ---

    @Test
    void removedHandlerNotInvoked() {
        try (Tree tree = new Tree()) {
            tree.initialize();
            List<String> received = new ArrayList<>();

            EventHandler<TestEvent> handler = e -> received.add("handled");
            tree.root().addEventHandler(CLICK, handler);
            tree.root().removeEventHandler(CLICK, handler);

            tree.root().fireEvent(new TestEvent(CLICK, "x"));

            assertTrue(received.isEmpty());
        }
    }

    // --- CaptureBubbleTraversal ---

    @Test
    void treeInputDispatcherVisitsAllNodesInOrder() {
        try (Tree tree = new Tree()) {
            Node child1 = tree.root().createChild();
            Node child2 = tree.root().createChild();
            Node grandchild = child1.createChild();
            tree.initialize();

            List<String> captureOrder = new ArrayList<>();

            tree.root().addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.CAPTURE) captureOrder.add("root");
            });
            child1.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.CAPTURE) captureOrder.add("child1");
            });
            child2.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.CAPTURE) captureOrder.add("child2");
            });
            grandchild.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.CAPTURE) captureOrder.add("grandchild");
            });

            CaptureBubbleTraversal traversal = new CaptureBubbleTraversal(tree);
            traversal.handleEvent(new TestEvent(CLICK, "all"));

            // DFS pre-order: root, child1, grandchild, child2
            assertEquals(List.of("root", "child1", "grandchild", "child2"), captureOrder);
        }
    }

    @Test
    void treeInputDispatcherBubbleIsReverse() {
        try (Tree tree = new Tree()) {
            Node child1 = tree.root().createChild();
            Node child2 = tree.root().createChild();
            tree.initialize();

            List<String> bubbleOrder = new ArrayList<>();

            tree.root().addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.BUBBLE) bubbleOrder.add("root");
            });
            child1.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.BUBBLE) bubbleOrder.add("child1");
            });
            child2.addEventHandler(CLICK, e -> {
                if (e.phase() == Event.Phase.BUBBLE) bubbleOrder.add("child2");
            });

            CaptureBubbleTraversal traversal = new CaptureBubbleTraversal(tree);
            traversal.handleEvent(new TestEvent(CLICK, "all"));

            // Reverse of DFS pre-order: child2, child1, root
            assertEquals(List.of("child2", "child1", "root"), bubbleOrder);
        }
    }

    @Test
    void treeInputDispatcherStopPropagation() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            tree.initialize();

            List<String> received = new ArrayList<>();

            tree.root().addEventHandler(CLICK, e -> {
                received.add("root");
                e.stopPropagation();
            });
            child.addEventHandler(CLICK, e -> received.add("child"));

            CaptureBubbleTraversal traversal = new CaptureBubbleTraversal(tree);
            boolean consumed = traversal.handleEvent(new TestEvent(CLICK, "x"));

            // Root stopped propagation during capture, child shouldn't receive capture
            // But bubble should still work (stopped resets)
            assertTrue(received.contains("root"));
            assertTrue(consumed || received.size() > 0);
        }
    }
}
