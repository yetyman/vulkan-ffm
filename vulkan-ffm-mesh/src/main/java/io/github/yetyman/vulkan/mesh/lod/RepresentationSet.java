package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.vulkan.mesh.Mesh;
import io.github.yetyman.vulkan.mesh.partition.PartitionSet;

import java.util.List;

/**
 * Associates a {@link RepresentationStructure} with the concrete partitions and meshes it
 * references. This is the top-level "LOD-enabled mesh" type: it knows the structural shape,
 * the partition sets for each representation, and can supply the data a selector needs.
 *
 * <p>{@link Mesh} itself does NOT hold LOD data. A Mesh is one representation — one allocation,
 * one partition set, one binding. RepresentationSet is the thing that groups multiple Meshes
 * (or multiple partition sets within a single pool allocation) into an LOD-aware entity.
 *
 * <p>This separation keeps Mesh thin and unbiased: applications that do not use LOD never
 * encounter this type. Applications that do use LOD compose it alongside (not inside) their
 * mesh instances.
 *
 * <h2>Two usage modes</h2>
 * <ul>
 *   <li><b>Multi-mesh mode</b>: each representation node is backed by a different Mesh (different
 *       allocations, different partition sets). Used for discrete LOD chains where each level is
 *       a separate asset.</li>
 *   <li><b>Single-mesh mode</b>: all representation nodes reference partitions within a single
 *       Mesh's PartitionSet (single pool allocation). Used for cluster DAGs, GPU-driven LOD,
 *       and anything that shares a pool.</li>
 * </ul>
 *
 * <p>The representation set does not own the meshes or partition sets it references. Lifecycle
 * of the backing data is managed externally (by the app, the residency system, etc.).
 */
public final class RepresentationSet {

    private final RepresentationStructure structure;
    private final List<PartitionSet> partitionSets;
    private final TransitionMode defaultTransitionMode;

    private RepresentationSet(RepresentationStructure structure,
                              List<PartitionSet> partitionSets,
                              TransitionMode defaultTransitionMode) {
        this.structure = structure;
        this.partitionSets = partitionSets;
        this.defaultTransitionMode = defaultTransitionMode;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The structural shape of this LOD set. */
    public RepresentationStructure structure() { return structure; }

    /**
     * The partition sets backing the representation nodes. In single-mesh mode this list
     * has one element shared by all nodes. In multi-mesh mode, element i backs node i.
     */
    public List<PartitionSet> partitionSets() { return partitionSets; }

    /**
     * The partition set that backs the given node index. In single-mesh mode always returns
     * the shared set. In multi-mesh mode returns the per-node set.
     */
    public PartitionSet partitionSetFor(int nodeIndex) {
        if (partitionSets.size() == 1) return partitionSets.getFirst();
        return partitionSets.get(nodeIndex);
    }

    /** Default transition mode for this LOD set. Selectors may override per-transition. */
    public TransitionMode defaultTransitionMode() { return defaultTransitionMode; }

    /** Convenience delegation to the structure. */
    public int nodeCount() { return structure.nodeCount(); }

    /** Convenience delegation to the structure. */
    public RepresentationNode node(int index) { return structure.node(index); }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private RepresentationStructure structure;
        private List<PartitionSet> partitionSets;
        private TransitionMode transitionMode = TransitionMode.HARD_CUT;

        private Builder() {}

        /** Sets the structural shape. */
        public Builder structure(RepresentationStructure structure) {
            this.structure = structure;
            return this;
        }

        /**
         * Single-mesh mode: all nodes share one partition set (pool allocation).
         */
        public Builder sharedPartitions(PartitionSet partitions) {
            this.partitionSets = List.of(partitions);
            return this;
        }

        /**
         * Multi-mesh mode: each node has its own partition set. List size must match node count.
         */
        public Builder perNodePartitions(List<PartitionSet> sets) {
            this.partitionSets = List.copyOf(sets);
            return this;
        }

        /** Sets the default transition mode. Defaults to hard cut. */
        public Builder transitionMode(TransitionMode mode) {
            this.transitionMode = mode;
            return this;
        }

        public RepresentationSet build() {
            if (structure == null) throw new IllegalStateException("structure required");
            if (partitionSets == null || partitionSets.isEmpty())
                throw new IllegalStateException("partitionSets required");
            if (partitionSets.size() > 1 && partitionSets.size() != structure.nodeCount())
                throw new IllegalStateException("perNodePartitions size (" + partitionSets.size()
                        + ") must match node count (" + structure.nodeCount() + ")");
            return new RepresentationSet(structure, partitionSets, transitionMode);
        }
    }
}
