package io.github.yetyman.vulkan.foundation.ecs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for node reparenting:
 * - Structural move (parent/child lists updated)
 * - onDetach fired on components
 * - Ancestor DI re-assessment on descendants
 * - Cycle detection
 * - Detach (null parent)
 */
class ReparentingTest {

    static class DetachTracker implements Component {
        final List<String> calls = new ArrayList<>();
        @Override public void onDetach(Node oldParent) {
            calls.add("detach:" + (oldParent != null ? "had-parent" : "null"));
        }
    }

    @Test
    void reparentMovesChild() {
        try (Tree tree = new Tree()) {
            Node branchA = tree.root().createChild();
            Node branchB = tree.root().createChild();
            Node movable = branchA.createChild();
            tree.initialize();

            assertEquals(1, branchA.childCount());
            assertEquals(0, branchB.childCount());

            movable.setParent(branchB);

            assertEquals(0, branchA.childCount());
            assertEquals(1, branchB.childCount());
            assertSame(branchB, movable.parent());
        }
    }

    @Test
    void reparentFiresOnDetach() {
        try (Tree tree = new Tree()) {
            Node branchA = tree.root().createChild();
            Node branchB = tree.root().createChild();
            Node movable = branchA.createChild();
            DetachTracker tracker = new DetachTracker();
            movable.addComponent(tracker);
            tree.initialize();

            movable.setParent(branchB);

            assertEquals(1, tracker.calls.size());
            assertEquals("detach:had-parent", tracker.calls.get(0));
        }
    }

    @Test
    void detachSetsParentToNull() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            tree.initialize();

            child.detach();

            assertNull(child.parent());
            assertEquals(0, tree.root().childCount());
        }
    }

    @Test
    void reparentToSelfThrows() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            tree.initialize();

            assertThrows(IllegalArgumentException.class, () -> child.setParent(child));
        }
    }

    @Test
    void reparentUnderOwnDescendantThrows() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            Node grandchild = child.createChild();
            tree.initialize();

            assertThrows(IllegalArgumentException.class, () -> child.setParent(grandchild));
        }
    }

    @Test
    void reparentToSameParentIsNoop() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            DetachTracker tracker = new DetachTracker();
            child.addComponent(tracker);
            tree.initialize();

            child.setParent(tree.root()); // Already the parent

            assertTrue(tracker.calls.isEmpty()); // No detach fired
        }
    }

    @Test
    void reparentPreservesSubtree() {
        try (Tree tree = new Tree()) {
            Node branchA = tree.root().createChild();
            Node branchB = tree.root().createChild();
            Node movable = branchA.createChild();
            Node grandchild = movable.createChild();
            tree.initialize();

            movable.setParent(branchB);

            // Grandchild should still be under movable
            assertEquals(1, movable.childCount());
            assertSame(grandchild, movable.children().get(0));
            assertSame(movable, grandchild.parent());
        }
    }

    @Test
    void depthUpdatesAfterReparent() {
        try (Tree tree = new Tree()) {
            Node branchA = tree.root().createChild();
            Node deep = branchA.createChild();
            Node movable = deep.createChild();
            tree.initialize();

            assertEquals(3, movable.depth()); // root -> branchA -> deep -> movable

            movable.setParent(tree.root());

            assertEquals(1, movable.depth()); // root -> movable
        }
    }

    @Test
    void traversalAfterReparentReflectsNewStructure() {
        try (Tree tree = new Tree()) {
            Node branchA = tree.root().createChild();
            Node branchB = tree.root().createChild();
            Node movable = branchA.createChild();
            tree.initialize();

            List<Node> beforeMove = new ArrayList<>();
            tree.root().traverseDepthFirst(beforeMove::add);
            assertTrue(beforeMove.indexOf(movable) > beforeMove.indexOf(branchA));
            assertTrue(beforeMove.indexOf(movable) < beforeMove.indexOf(branchB));

            movable.setParent(branchB);

            List<Node> afterMove = new ArrayList<>();
            tree.root().traverseDepthFirst(afterMove::add);
            assertTrue(afterMove.indexOf(movable) > afterMove.indexOf(branchB));
        }
    }
}
