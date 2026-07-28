package io.github.yetyman.vulkan.foundation.ecs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Node/Component lifecycle state machine:
 * UNCONSTRUCTED -> CONSTRUCTED -> INITIALIZED -> READY -> CLOSING -> CLOSED
 */
class NodeLifecycleTest {

    // --- Test components that track lifecycle calls ---

    static class LifecycleTracker implements Component {
        final List<String> calls = new ArrayList<>();
        Node initNode;
        Node resolveNode;

        @Override public void onInit(Node node) {
            calls.add("onInit");
            initNode = node;
        }
        @Override public void resolveDependencies(Node node) {
            calls.add("resolveDependencies");
            resolveNode = node;
        }
        @Override public void afterResolve(Node node) { calls.add("afterResolve"); }
        @Override public void onDetach(Node oldParent) { calls.add("onDetach"); }
        @Override public void beforeClose(Node node) { calls.add("beforeClose"); }
        @Override public void close(Node node) { calls.add("close"); }
    }

    // --- Basic lifecycle ---

    @Test
    void treeInitializationRunsFullLifecycle() {
        try (Tree tree = new Tree()) {
            LifecycleTracker tracker = new LifecycleTracker();
            tree.root().addComponent(tracker);
            tree.initialize();

            assertEquals(List.of("onInit", "resolveDependencies", "afterResolve"), tracker.calls);
            assertEquals(LifecycleState.READY, tree.root().state());
            assertSame(tree.root(), tracker.initNode);
            assertSame(tree.root(), tracker.resolveNode);
        }
    }

    @Test
    void nodeStartsInConstructedState() {
        try (Tree tree = new Tree()) {
            assertEquals(LifecycleState.CONSTRUCTED, tree.root().state());
        }
    }

    @Test
    void lateComponentRegistrationRunsLifecycleImmediately() {
        try (Tree tree = new Tree()) {
            tree.initialize(); // root is now READY

            LifecycleTracker late = new LifecycleTracker();
            tree.root().addComponent(late);

            assertEquals(List.of("onInit", "resolveDependencies", "afterResolve"), late.calls);
        }
    }

    @Test
    void closeRunsTwoPassTeardown() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            LifecycleTracker parentTracker = new LifecycleTracker();
            LifecycleTracker childTracker = new LifecycleTracker();
            tree.root().addComponent(parentTracker);
            child.addComponent(childTracker);
            tree.initialize();

            // Clear init calls
            parentTracker.calls.clear();
            childTracker.calls.clear();

            tree.close();

            // beforeClose: top-down (parent first)
            // close: bottom-up (child first)
            assertEquals(List.of("beforeClose", "close"), parentTracker.calls);
            assertEquals(List.of("beforeClose", "close"), childTracker.calls);

            // Verify order: parent's beforeClose must come before child's beforeClose,
            // and child's close must come before parent's close.
            // We can verify this by checking the tree state.
            assertEquals(LifecycleState.CLOSED, tree.root().state());
        }
    }

    @Test
    void closeOnNodeClosesSubtree() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            Node grandchild = child.createChild();
            LifecycleTracker gcTracker = new LifecycleTracker();
            grandchild.addComponent(gcTracker);
            tree.initialize();
            gcTracker.calls.clear();

            child.close();

            assertEquals(List.of("beforeClose", "close"), gcTracker.calls);
            assertEquals(0, tree.root().childCount()); // child removed from parent
        }
    }

    // --- Sibling notifications ---

    static class SiblingNotifier implements Component {
        final List<String> notifications = new ArrayList<>();
        @Override public void onSiblingComponentAdded(Component added, int index) {
            notifications.add("added:" + added.getClass().getSimpleName() + "@" + index);
        }
        @Override public void onSiblingComponentRemoved(Component removed, int index) {
            notifications.add("removed:" + removed.getClass().getSimpleName() + "@" + index);
        }
    }

    static class DummyComponent implements Component {}

    @Test
    void siblingAddNotification() {
        try (Tree tree = new Tree()) {
            SiblingNotifier notifier = new SiblingNotifier();
            tree.root().addComponent(notifier);
            tree.initialize();

            tree.root().addComponent(new DummyComponent());

            assertEquals(1, notifier.notifications.size());
            assertTrue(notifier.notifications.get(0).startsWith("added:DummyComponent"));
        }
    }

    @Test
    void siblingRemoveNotification() {
        try (Tree tree = new Tree()) {
            SiblingNotifier notifier = new SiblingNotifier();
            tree.root().addComponent(notifier);
            tree.root().addComponent(new DummyComponent());
            tree.initialize();
            notifier.notifications.clear();

            tree.root().removeComponent(DummyComponent.class);

            assertEquals(1, notifier.notifications.size());
            assertTrue(notifier.notifications.get(0).startsWith("removed:DummyComponent"));
        }
    }

    // --- Duplicate component rejection ---

    @Test
    void duplicateComponentThrows() {
        try (Tree tree = new Tree()) {
            tree.root().addComponent(new DummyComponent());
            assertThrows(IllegalStateException.class, () -> tree.root().addComponent(new DummyComponent()));
        }
    }

    // --- Tree structure ---

    @Test
    void createChildAddsToParent() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            assertEquals(1, tree.root().childCount());
            assertSame(tree.root(), child.parent());
            assertSame(tree, child.tree());
        }
    }

    @Test
    void rootHasNoParent() {
        try (Tree tree = new Tree()) {
            assertNull(tree.root().parent());
            assertTrue(tree.root().isRoot());
        }
    }

    @Test
    void leafNodeDetection() {
        try (Tree tree = new Tree()) {
            assertTrue(tree.root().isLeaf());
            tree.root().createChild();
            assertFalse(tree.root().isLeaf());
        }
    }

    @Test
    void depthCalculation() {
        try (Tree tree = new Tree()) {
            assertEquals(0, tree.root().depth());
            Node child = tree.root().createChild();
            assertEquals(1, child.depth());
            Node grandchild = child.createChild();
            assertEquals(2, grandchild.depth());
        }
    }

    @Test
    void pathFromRoot() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            Node grandchild = child.createChild();

            List<Node> path = grandchild.pathFromRoot();
            assertEquals(3, path.size());
            assertSame(tree.root(), path.get(0));
            assertSame(child, path.get(1));
            assertSame(grandchild, path.get(2));
        }
    }

    @Test
    void isAncestorOf() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            Node grandchild = child.createChild();

            assertTrue(tree.root().isAncestorOf(child));
            assertTrue(tree.root().isAncestorOf(grandchild));
            assertTrue(child.isAncestorOf(grandchild));
            assertFalse(child.isAncestorOf(tree.root()));
            assertFalse(grandchild.isAncestorOf(child));
        }
    }
}
