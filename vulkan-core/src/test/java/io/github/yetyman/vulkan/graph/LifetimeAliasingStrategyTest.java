package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.memory.LifetimeAliasingStrategy;
import io.github.yetyman.vulkan.graph.memory.NullAliasingStrategy;
import io.github.yetyman.vulkan.graph.memory.ResourceAlias;
import io.github.yetyman.vulkan.graph.resources.GraphResource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LifetimeAliasingStrategyTest {

    private final LifetimeAliasingStrategy strategy = new LifetimeAliasingStrategy();

    @Test
    void nonOverlapping_aliasedTogether() {
        // A: passes 0-2, B: passes 3-5 -> can share memory
        GraphResource a = TestResources.transientBuffer("a");
        a.lifetime().recordWrite(0);
        a.lifetime().recordRead(2);

        GraphResource b = TestResources.transientBuffer("b");
        b.lifetime().recordWrite(3);
        b.lifetime().recordRead(5);

        List<ResourceAlias> groups = strategy.alias(List.of(a, b));

        assertEquals(1, groups.size(), "Non-overlapping resources should share one group");
        assertEquals(2, groups.get(0).members().size());
    }

    @Test
    void overlapping_separateGroups() {
        // A: passes 0-3, B: passes 2-5 -> overlap at pass 2-3
        GraphResource a = TestResources.transientBuffer("a");
        a.lifetime().recordWrite(0);
        a.lifetime().recordRead(3);

        GraphResource b = TestResources.transientBuffer("b");
        b.lifetime().recordWrite(2);
        b.lifetime().recordRead(5);

        List<ResourceAlias> groups = strategy.alias(List.of(a, b));

        assertEquals(2, groups.size(), "Overlapping resources need separate groups");
    }

    @Test
    void threeResources_twoCanAlias() {
        // A: 0-1, B: 2-3, C: 1-3
        // A and B don't overlap -> same group
        // C overlaps with both -> separate group
        GraphResource a = TestResources.transientBuffer("a");
        a.lifetime().recordWrite(0);
        a.lifetime().recordRead(1);

        GraphResource b = TestResources.transientBuffer("b");
        b.lifetime().recordWrite(2);
        b.lifetime().recordRead(3);

        GraphResource c = TestResources.transientBuffer("c");
        c.lifetime().recordWrite(1);
        c.lifetime().recordRead(3);

        List<ResourceAlias> groups = strategy.alias(List.of(a, b, c));

        assertEquals(2, groups.size());
        // A and B should be in the same group
        ResourceAlias abGroup = groups.stream()
            .filter(g -> g.members().contains(a))
            .findFirst().orElseThrow();
        assertTrue(abGroup.members().contains(b));
        assertFalse(abGroup.members().contains(c));
    }

    @Test
    void emptyInput_emptyOutput() {
        List<ResourceAlias> groups = strategy.alias(List.of());
        assertTrue(groups.isEmpty());
    }

    @Test
    void invalidLifetime_skipped() {
        // Resource with no recorded usage
        GraphResource unused = TestResources.transientBuffer("unused");

        GraphResource valid = TestResources.transientBuffer("valid");
        valid.lifetime().recordWrite(0);
        valid.lifetime().recordRead(2);

        List<ResourceAlias> groups = strategy.alias(List.of(unused, valid));

        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).members().size());
        assertTrue(groups.get(0).members().contains(valid));
    }

    @Test
    void nullStrategy_noAliasing() {
        NullAliasingStrategy nullStrategy = new NullAliasingStrategy();

        GraphResource a = TestResources.transientBuffer("a");
        a.lifetime().recordWrite(0);
        a.lifetime().recordRead(1);

        GraphResource b = TestResources.transientBuffer("b");
        b.lifetime().recordWrite(2);
        b.lifetime().recordRead(3);

        List<ResourceAlias> groups = nullStrategy.alias(List.of(a, b));

        assertEquals(2, groups.size(), "Null strategy should never alias");
        assertEquals(1, groups.get(0).members().size());
        assertEquals(1, groups.get(1).members().size());
    }

    @Test
    void manyNonOverlapping_allInOneGroup() {
        // 5 resources with sequential non-overlapping lifetimes
        List<GraphResource> resources = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            GraphResource r = TestResources.transientBuffer("r" + i);
            r.lifetime().recordWrite(i * 2);
            r.lifetime().recordRead(i * 2 + 1);
            resources.add(r);
        }

        List<ResourceAlias> groups = strategy.alias(resources);

        assertEquals(1, groups.size(), "All sequential non-overlapping should fit in one group");
        assertEquals(5, groups.get(0).members().size());
    }
}
