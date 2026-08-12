package io.github.yetyman.vulkan.mesh.lod;

/**
 * The structural shape of a representation set: how the individual {@link RepresentationNode}s
 * relate to each other. This is pure topology and error-bound data, completely independent of
 * selection policy, execution location, or transition strategy.
 *
 * <p>LOD schemes differ less in what they select than in what structural relationships exist
 * between the available representations. There are exactly four structural shapes that cover
 * every known LOD technique:
 *
 * <ul>
 *   <li><b>Flat</b> - independent variants with no relationship other than being alternatives
 *       for the same geometry (discrete LOD chain, impostor set)</li>
 *   <li><b>Chain</b> - ordered refinement where each level is derived from (and improves upon)
 *       the previous (progressive mesh, vertex-split stream, cascaded simplification)</li>
 *   <li><b>Graph</b> - DAG where nodes have children that refine them; multiple parents allowed
 *       (Nanite-style cluster LOD, virtual geometry, HLOD)</li>
 *   <li><b>Parametric</b> - a single representation controlled by continuous parameters
 *       (tessellation factor, displacement amplitude, SDF iso-level)</li>
 * </ul>
 *
 * <p>Every commonly named LOD technique composes from one or two of these shapes. If a technique
 * requires a fifth shape, that is a signal this categorization is wrong and should be revised
 * rather than extended with special cases.
 *
 * <p>This interface is sealed. Implementations are inner records/classes of this file for
 * discoverability and because the closed set is a design invariant, not an implementation convenience.
 *
 * @see RepresentationNode
 * @see RepresentationGraph
 * @see RefinementStream
 */
public sealed interface RepresentationStructure
        permits RepresentationStructure.Flat,
                RepresentationStructure.Chain,
                RepresentationStructure.Graph,
                RepresentationStructure.Parametric {

    /**
     * @return the total number of representation nodes in this structure
     */
    int nodeCount();

    /**
     * @return the node at the given index
     */
    RepresentationNode node(int index);

    // -------------------------------------------------------------------------
    // Structural shapes
    // -------------------------------------------------------------------------

    /**
     * Independent variants with no structural relationship. Any one can be selected without
     * affecting or depending on any other.
     *
     * <p>Examples: Unity-style discrete LOD, impostor swaps, billboard fallbacks, quality presets.
     *
     * <p>Nodes are ordered by convention from finest (index 0) to coarsest (last index), but
     * selectors should use {@link RepresentationNode#errorBound()} rather than index for decisions.
     */
    record Flat(RepresentationNode[] nodes) implements RepresentationStructure {
        public Flat {
            if (nodes == null || nodes.length == 0)
                throw new IllegalArgumentException("at least one node required");
        }

        @Override public int nodeCount() { return nodes.length; }
        @Override public RepresentationNode node(int index) { return nodes[index]; }
    }

    /**
     * Ordered refinement chain where each successive node is derived from (and improves upon) the
     * previous. The chain may optionally carry a {@link RefinementStream} that allows continuous
     * interpolation between levels rather than discrete switching.
     *
     * <p>Examples: progressive mesh (vertex-split records), cascaded simplification, wavelet
     * terrain, streaming point cloud density.
     *
     * <p>Node 0 is the base (coarsest). Each subsequent node refines it further. A selector may
     * pick any single node, or (if the refinement stream supports it) any point between two
     * adjacent nodes.
     */
    record Chain(RepresentationNode[] nodes, RefinementStream refinementStream) implements RepresentationStructure {
        public Chain {
            if (nodes == null || nodes.length == 0)
                throw new IllegalArgumentException("at least one node required");
            // refinementStream may be null (discrete chain with no continuous refinement)
        }

        /**
         * Creates a discrete chain with no continuous refinement capability.
         */
        public Chain(RepresentationNode[] nodes) {
            this(nodes, null);
        }

        @Override public int nodeCount() { return nodes.length; }
        @Override public RepresentationNode node(int index) { return nodes[index]; }

        /**
         * @return true if this chain supports continuous refinement between levels
         */
        public boolean supportsContinuousRefinement() {
            return refinementStream != null;
        }
    }

    /**
     * DAG (directed acyclic graph) where nodes have children that represent finer detail.
     * Multiple parents are allowed: a child cluster may refine two adjacent parent clusters
     * at their boundary.
     *
     * <p>Examples: Nanite-style cluster LOD, virtual geometry, HLOD trees, hierarchical
     * impostor systems.
     *
     * <p>The graph is held in a separate {@link RepresentationGraph} type that provides
     * parent/child traversal, root enumeration, and DAG validation.
     */
    record Graph(RepresentationGraph graph) implements RepresentationStructure {
        public Graph {
            if (graph == null) throw new IllegalArgumentException("graph required");
        }

        @Override public int nodeCount() { return graph.nodeCount(); }
        @Override public RepresentationNode node(int index) { return graph.node(index); }
    }

    /**
     * A single base representation controlled by continuous parameters. The hardware or a
     * shader decides the detail level based on these parameters; no discrete variants exist.
     *
     * <p>Examples: hardware tessellation (tessellation factor), displacement mapping (amplitude),
     * SDF rendering (iso-level, step count), mesh-shader amplification (amplification factor).
     *
     * <p>The parameter names are opaque strings whose meaning is defined by the consumer's
     * pipeline and the selector that produces values for them. This module provides the
     * structure; it does not interpret the parameters.
     */
    record Parametric(RepresentationNode base, ParameterDescriptor[] parameters) implements RepresentationStructure {
        public Parametric {
            if (base == null) throw new IllegalArgumentException("base required");
            if (parameters == null || parameters.length == 0)
                throw new IllegalArgumentException("at least one parameter required");
        }

        @Override public int nodeCount() { return 1; }
        @Override public RepresentationNode node(int index) {
            if (index != 0) throw new IndexOutOfBoundsException(index);
            return base;
        }
    }
}
