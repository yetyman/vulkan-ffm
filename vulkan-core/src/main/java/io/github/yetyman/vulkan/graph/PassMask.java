package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.nodes.RenderNode;

import java.util.BitSet;
import java.util.List;

/**
 * A bitset representing which passes are active in a given submission.
 * Used as a cache key for compiled graph variants. Two submissions with the same
 * PassMask produce identical compiled graphs and can share the cached result.
 */
public class PassMask {

    private final BitSet bits;
    private final int hashCode;

    private PassMask(BitSet bits) {
        this.bits = bits;
        this.hashCode = bits.hashCode();
    }

    /**
     * Evaluates the current activation state of all nodes and produces a PassMask.
     *
     * @param nodes all nodes in the graph (order must be stable)
     * @return the current pass mask
     */
    public static PassMask evaluate(List<RenderNode> nodes) {
        BitSet bits = new BitSet(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).isActive()) {
                bits.set(i);
            }
        }
        return new PassMask(bits);
    }

    /**
     * Creates a PassMask from an explicit bitset.
     */
    public static PassMask of(BitSet bits) {
        return new PassMask((BitSet) bits.clone());
    }

    /**
     * Creates a PassMask where all passes are active.
     */
    public static PassMask allActive(int nodeCount) {
        BitSet bits = new BitSet(nodeCount);
        bits.set(0, nodeCount);
        return new PassMask(bits);
    }

    /** @return true if the pass at the given index is active in this mask */
    public boolean isActive(int index) {
        return bits.get(index);
    }

    /** @return the number of active passes */
    public int activeCount() {
        return bits.cardinality();
    }

    /** @return the underlying bitset (defensive copy) */
    public BitSet bits() {
        return (BitSet) bits.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PassMask other)) return false;
        return bits.equals(other.bits);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "PassMask{active=" + bits.cardinality() + "/" + bits.length() + "}";
    }
}
