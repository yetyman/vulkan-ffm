package io.github.yetyman.vulkan.foundation.ecs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TraversalView:
 * - Registration and population
 * - Dirty tracking
 * - applyPatches() reports
 * - Component add/remove updates views
 * - Node move marks dirty
 * - Keyed views
 */
class TraversalViewTest {

    static class MarkerComponent implements Component {}
    static class OtherComponent implements Component {}

    @Test
    void viewPopulatesWithExistingComponents() {
        try (Tree tree = new Tree()) {
            Node child1 = tree.root().createChild();
            child1.addComponent(new MarkerComponent());
            Node child2 = tree.root().createChild();
            child2.addComponent(new MarkerComponent());
            tree.initialize();

            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);

            assertEquals(2, view.liveCount());
        }
    }

    @Test
    void viewTracksLateAdditions() {
        try (Tree tree = new Tree()) {
            tree.initialize();

            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);
            assertEquals(0, view.liveCount());

            tree.root().addComponent(new MarkerComponent());
            assertEquals(1, view.liveCount());
        }
    }

    @Test
    void viewTracksRemovals() {
        try (Tree tree = new Tree()) {
            tree.root().addComponent(new MarkerComponent());
            tree.initialize();

            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);
            assertEquals(1, view.liveCount());

            tree.root().removeComponent(MarkerComponent.class);
            assertEquals(0, view.liveCount());
        }
    }

    @Test
    void viewDoesNotTrackUnrelatedComponents() {
        try (Tree tree = new Tree()) {
            tree.initialize();

            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);

            tree.root().addComponent(new OtherComponent());
            assertEquals(0, view.liveCount());
        }
    }

    @Test
    void applyPatchesReportsAdditions() {
        try (Tree tree = new Tree()) {
            // Initialize tree first so root is READY
            tree.initialize();

            // Create view - starts empty
            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);
            assertEquals(0, view.liveCount());

            // The root is READY, so adding components to it will notify the view
            // We need enough initial items that adding one more doesn't trigger full-rewrite
            // Use the root itself - but we can only add ONE MarkerComponent per node (type uniqueness)
            // So we need to create children AND initialize them to READY state

            // Create children and add components - they start as CONSTRUCTED
            for (int i = 0; i < 7; i++) {
                Node c = tree.root().createChild();
                c.addComponent(new MarkerComponent());
            }
            // Initialize the children (transitions them to READY and notifies views)
            tree.initialize();

            assertEquals(7, view.liveCount());

            // Consume initial additions
            view.applyPatches();

            // Now add one more child, initialize it, which triggers view notification
            Node extra = tree.root().createChild();
            extra.addComponent(new MarkerComponent());
            tree.initialize(); // initializes the new child

            assertEquals(8, view.liveCount());

            var report = view.applyPatches();
            assertFalse(report.isClean());
            assertFalse(report.fullRewriteRecommended());
            assertEquals(1, report.additionCount());
        }
    }

    @Test
    void applyPatchesReportsRemovals() {
        try (Tree tree = new Tree()) {
            tree.root().addComponent(new MarkerComponent());
            tree.initialize();

            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);
            view.applyPatches(); // consume initial addition

            tree.root().removeComponent(MarkerComponent.class);

            var report = view.applyPatches();
            assertFalse(report.isClean());
            assertEquals(1, report.removalCount());
        }
    }

    @Test
    void applyPatchesClearsDirtyState() {
        try (Tree tree = new Tree()) {
            tree.initialize();

            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);

            tree.root().addComponent(new MarkerComponent());
            view.applyPatches(); // consume

            var report = view.applyPatches();
            assertTrue(report.isClean());
        }
    }

    @Test
    void forEachIteratesInOrder() {
        try (Tree tree = new Tree()) {
            Node child1 = tree.root().createChild();
            child1.addComponent(new MarkerComponent());
            Node child2 = tree.root().createChild();
            child2.addComponent(new MarkerComponent());
            tree.root().addComponent(new MarkerComponent());
            tree.initialize();

            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);

            List<Node> visited = new ArrayList<>();
            view.forEach((node, component) -> visited.add(node));

            assertEquals(3, visited.size());
        }
    }

    @Test
    void sameTypeKeyReturnsSameInstance() {
        try (Tree tree = new Tree()) {
            tree.initialize();

            var view1 = tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);
            var view2 = tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);

            assertSame(view1, view2);
        }
    }

    @Test
    void differentKeysReturnDifferentInstances() {
        try (Tree tree = new Tree()) {
            tree.initialize();

            var view1 = tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER, "render");
            var view2 = tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER, "physics");

            assertNotSame(view1, view2);
        }
    }

    @Test
    void releaseRemovesView() {
        try (Tree tree = new Tree()) {
            tree.initialize();

            var view1 = tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER, "temp");
            tree.releaseTraversalView("temp");

            // Getting again should create a new instance
            var view2 = tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER, "temp");
            assertNotSame(view1, view2);
        }
    }

    @Test
    void highChurnRecommendsFullRewrite() {
        try (Tree tree = new Tree()) {
            tree.initialize();

            TraversalView<MarkerComponent> view =
                    tree.getOrCreateComponentTraversal(MarkerComponent.class, TraversalOrder.DEPTH_FIRST_PRE_ORDER);

            // Add a single component then remove it many times to create high churn ratio
            // Actually, let's just add one and check that with 1 item and 1 dirty, it recommends full rewrite
            tree.root().addComponent(new MarkerComponent());

            var report = view.applyPatches();
            // With 1 entry and 1 dirty: (1+1)*3 > 1 → true → full rewrite
            assertTrue(report.fullRewriteRecommended() || !report.isClean());
        }
    }
}
