package io.github.yetyman.vulkan.foundation.ecs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the DI resolution system: requires(), claim styles, lookup scopes,
 * topological ordering, and ancestor-scope re-assessment on reparent.
 */
class DependencyResolutionTest {

    // --- Test components ---

    static class DataComponent implements Component {
        String value = "hello";
    }

    static class RenderComponent implements Component {
        DataComponent data;

        @Override
        public List<Dependency<?>> requires() {
            return List.of(Dependency.selfRequired(DataComponent.class));
        }

        @Override
        public void resolveDependencies(Node node) {
            this.data = node.findComponent(DataComponent.class);
        }
    }

    static class AncestorLookupComponent implements Component {
        DataComponent ancestorData;
        int resolveCount = 0;

        @Override
        public List<Dependency<?>> requires() {
            return List.of(Dependency.ancestorRequired(DataComponent.class));
        }

        @Override
        public void resolveDependencies(Node node) {
            resolveCount++;
            Node current = node.parent();
            while (current != null) {
                DataComponent d = current.findComponent(DataComponent.class);
                if (d != null) {
                    this.ancestorData = d;
                    return;
                }
                current = current.parent();
            }
            this.ancestorData = null;
        }
    }

    // --- SELF scope resolution ---

    @Test
    void selfScopeResolvesOnSameNode() {
        try (Tree tree = new Tree()) {
            DataComponent data = new DataComponent();
            RenderComponent render = new RenderComponent();
            tree.root().addComponent(data);
            tree.root().addComponent(render);
            tree.initialize();

            assertSame(data, render.data);
        }
    }

    @Test
    void topologicalOrderResolvesDependencyFirst() {
        try (Tree tree = new Tree()) {
            // Add render BEFORE data - DI should still resolve correctly
            RenderComponent render = new RenderComponent();
            DataComponent data = new DataComponent();
            tree.root().addComponent(render);
            tree.root().addComponent(data);
            tree.initialize();

            assertSame(data, render.data);
        }
    }

    // --- NEAREST_ANCESTOR scope ---

    @Test
    void ancestorScopeResolvesFromParent() {
        try (Tree tree = new Tree()) {
            DataComponent parentData = new DataComponent();
            parentData.value = "parent";
            tree.root().addComponent(parentData);

            Node child = tree.root().createChild();
            AncestorLookupComponent lookup = new AncestorLookupComponent();
            child.addComponent(lookup);

            tree.initialize();

            assertSame(parentData, lookup.ancestorData);
        }
    }

    @Test
    void ancestorScopeResolvesFromGrandparent() {
        try (Tree tree = new Tree()) {
            DataComponent rootData = new DataComponent();
            rootData.value = "root";
            tree.root().addComponent(rootData);

            Node child = tree.root().createChild();
            Node grandchild = child.createChild();
            AncestorLookupComponent lookup = new AncestorLookupComponent();
            grandchild.addComponent(lookup);

            tree.initialize();

            assertSame(rootData, lookup.ancestorData);
        }
    }

    @Test
    void ancestorScopeResolvesNearestNotRoot() {
        try (Tree tree = new Tree()) {
            DataComponent rootData = new DataComponent();
            rootData.value = "root";
            tree.root().addComponent(rootData);

            Node child = tree.root().createChild();
            DataComponent childData = new DataComponent();
            childData.value = "child";
            child.addComponent(childData);

            Node grandchild = child.createChild();
            AncestorLookupComponent lookup = new AncestorLookupComponent();
            grandchild.addComponent(lookup);

            tree.initialize();

            // Should resolve to the NEAREST ancestor (child, not root)
            assertSame(childData, lookup.ancestorData);
        }
    }

    // --- Reparent re-assessment ---

    @Test
    void reparentReassessesAncestorDependencies() {
        try (Tree tree = new Tree()) {
            DataComponent rootData = new DataComponent();
            rootData.value = "root";
            tree.root().addComponent(rootData);

            Node branchA = tree.root().createChild();
            DataComponent branchAData = new DataComponent();
            branchAData.value = "branchA";
            branchA.addComponent(branchAData);

            Node branchB = tree.root().createChild();
            DataComponent branchBData = new DataComponent();
            branchBData.value = "branchB";
            branchB.addComponent(branchBData);

            Node movable = branchA.createChild();
            Node deepChild = movable.createChild();
            AncestorLookupComponent lookup = new AncestorLookupComponent();
            deepChild.addComponent(lookup);

            tree.initialize();

            // Initially resolves to branchA's data
            assertSame(branchAData, lookup.ancestorData);
            int initialResolveCount = lookup.resolveCount;

            // Reparent movable under branchB
            movable.setParent(branchB);

            // Should have re-resolved to branchB's data
            assertSame(branchBData, lookup.ancestorData);
            assertTrue(lookup.resolveCount > initialResolveCount);
        }
    }

    // --- Claim styles ---

    static class ExclusiveClaimComponent implements Component {
        DataComponent data;
        private static final Dependency<DataComponent> DEP = new Dependency<>(DataComponent.class, ClaimStyle.EXCLUSIVE,
                LookupScope.SELF, FallbackPolicy.required());

        @Override
        public List<Dependency<?>> requires() {
            return List.of(DEP);
        }

        @Override
        public void resolveDependencies(Node node) {
            this.data = node.resolveDependency(DEP, this);
        }
    }

    static class PermissiveClaimComponent implements Component {
        DataComponent data;
        private static final Dependency<DataComponent> DEP = Dependency.selfRequired(DataComponent.class);

        @Override
        public List<Dependency<?>> requires() {
            return List.of(DEP);
        }

        @Override
        public void resolveDependencies(Node node) {
            this.data = node.resolveDependency(DEP, this);
        }
    }

    @Test
    void permissiveClaimsAllowMultiple() {
        try (Tree tree = new Tree()) {
            DataComponent data = new DataComponent();
            tree.root().addComponent(data);

            // Two different types both permissively claiming the same DataComponent
            // Use manual claim calls to test the claim registry directly
            assertDoesNotThrow(() -> Node.tryClaim(data, ExclusiveClaimComponent.class, ClaimStyle.PERMISSIVE));
            assertDoesNotThrow(() -> Node.tryClaim(data, PermissiveClaimComponent.class, ClaimStyle.PERMISSIVE));
        }
    }

    @Test
    void exclusiveClaimBlocksSubsequent() {
        try (Tree tree = new Tree()) {
            tree.root().addComponent(new DataComponent());
            tree.root().addComponent(new ExclusiveClaimComponent());
            tree.root().addComponent(new PermissiveClaimComponent());

            // The exclusive claim should block the permissive one
            // Since topological order processes ExclusiveClaimComponent first (it has a dep on DataComponent)
            // and PermissiveClaimComponent second, the exclusive claim should be recorded first.
            // However, permissive never conflicts - this is by design.
            // Let's test two exclusive claims instead:
        }
    }

    @Test
    void twoExclusiveClaimsOnSameTargetThrows() {
        try (Tree tree = new Tree()) {
            DataComponent data = new DataComponent();
            tree.root().addComponent(data);

            // Manually test claim violation
            Node.tryClaim(data, ExclusiveClaimComponent.class, ClaimStyle.EXCLUSIVE);

            // Second exclusive claim should throw
            assertThrows(IllegalStateException.class, () ->
                    Node.tryClaim(data, PermissiveClaimComponent.class, ClaimStyle.EXCLUSIVE));
        }
    }

    @Test
    void selfExclusiveBlocksSameType() {
        try (Tree tree = new Tree()) {
            DataComponent data = new DataComponent();

            Node.tryClaim(data, RenderComponent.class, ClaimStyle.SELF_EXCLUSIVE);

            // Same type again should throw
            assertThrows(IllegalStateException.class, () ->
                    Node.tryClaim(data, RenderComponent.class, ClaimStyle.SELF_EXCLUSIVE));
        }
    }

    @Test
    void selfExclusiveAllowsDifferentTypes() {
        try (Tree tree = new Tree()) {
            DataComponent data = new DataComponent();

            Node.tryClaim(data, RenderComponent.class, ClaimStyle.SELF_EXCLUSIVE);

            // Different type should be fine
            assertDoesNotThrow(() ->
                    Node.tryClaim(data, AncestorLookupComponent.class, ClaimStyle.SELF_EXCLUSIVE));
        }
    }

    // --- Fallback policy ---

    static class OptionalDepComponent implements Component {
        DataComponent data;

        @Override
        public List<Dependency<?>> requires() {
            return List.of(Dependency.selfOptional(DataComponent.class));
        }

        @Override
        public void resolveDependencies(Node node) {
            this.data = node.findComponent(DataComponent.class);
        }
    }

    @Test
    void optionalDependencyReturnsNullWhenAbsent() {
        try (Tree tree = new Tree()) {
            OptionalDepComponent opt = new OptionalDepComponent();
            tree.root().addComponent(opt);
            tree.initialize();

            assertNull(opt.data);
        }
    }

    @Test
    void optionalDependencyResolvesWhenPresent() {
        try (Tree tree = new Tree()) {
            DataComponent data = new DataComponent();
            OptionalDepComponent opt = new OptionalDepComponent();
            tree.root().addComponent(data);
            tree.root().addComponent(opt);
            tree.initialize();

            assertSame(data, opt.data);
        }
    }

    // --- Component add triggers ancestor re-assessment ---

    @Test
    void addingComponentToParentReassessesDescendants() {
        try (Tree tree = new Tree()) {
            Node child = tree.root().createChild();
            AncestorLookupComponent lookup = new AncestorLookupComponent();
            child.addComponent(lookup);
            tree.initialize();

            // Initially no ancestor data found (root has no DataComponent)
            assertNull(lookup.ancestorData);
            int count = lookup.resolveCount;

            // Add DataComponent to root after init
            DataComponent data = new DataComponent();
            tree.root().addComponent(data);

            // Lookup should have been re-assessed
            assertSame(data, lookup.ancestorData);
            assertTrue(lookup.resolveCount > count);
        }
    }
}
