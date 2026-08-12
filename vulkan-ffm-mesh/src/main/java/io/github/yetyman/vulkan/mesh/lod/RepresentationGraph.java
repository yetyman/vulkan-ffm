package io.github.yetyman.vulkan.mesh.lod;

import java.util.Arrays;

/**
 * A directed acyclic graph over {@link RepresentationNode}s. Nodes near the root are coarse
 * (high error, few triangles). Children refine their parents (lower error, more triangles).
 * Multiple parents are allowed: a child cluster may refine the boundary shared by two
 * adjacent parent clusters.
 *
 * <p>This structure exists independently of spatial acceleration (BVH, quadtree, etc.) because
 * it answers a different question: "given an error budget, which nodes satisfy it?" versus
 * "given a frustum, which items intersect it?" They happen to both be hierarchies but serve
 * different purposes and have different topologies (a BVH over clusters vs. an error-bound DAG
 * of refinement relationships).
 *
 * <p>The graph is stored in compressed adjacency form: two flat int arrays (childOffsets,
 * childData) and two flat int arrays (parentOffsets, parentData), so traversal involves no
 * pointer chasing and the structure is uploadable to a GPU SSBO for GPU-driven LOD selection.
 *
 * <p>Immutable after construction. Use {@link Builder} for construction.
 */
public final class RepresentationGraph {

    private final RepresentationNode[] nodes;
    private final int[] roots;

    // Compressed adjacency: children
    private final int[] childOffsets;  // length = nodeCount + 1; children of node i are childData[childOffsets[i]..childOffsets[i+1])
    private final int[] childData;

    // Compressed adjacency: parents (inverse of children)
    private final int[] parentOffsets; // length = nodeCount + 1
    private final int[] parentData;

    private RepresentationGraph(RepresentationNode[] nodes, int[] roots,
                                int[] childOffsets, int[] childData,
                                int[] parentOffsets, int[] parentData) {
        this.nodes = nodes;
        this.roots = roots;
        this.childOffsets = childOffsets;
        this.childData = childData;
        this.parentOffsets = parentOffsets;
        this.parentData = parentData;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * @return number of nodes in the graph
     */
    public int nodeCount() {
        return nodes.length;
    }

    /**
     * @return the node at the given index
     */
    public RepresentationNode node(int index) {
        return nodes[index];
    }

    /**
     * @return indices of root nodes (coarsest representations with no parent)
     */
    public int[] roots() {
        return roots;
    }

    /**
     * @return number of children of node {@code nodeIndex}
     */
    public int childCount(int nodeIndex) {
        return childOffsets[nodeIndex + 1] - childOffsets[nodeIndex];
    }

    /**
     * @return the i-th child index of node {@code nodeIndex}
     */
    public int child(int nodeIndex, int i) {
        return childData[childOffsets[nodeIndex] + i];
    }

    /**
     * Copies children of node {@code nodeIndex} into the destination array.
     *
     * @return number of children written
     */
    public int children(int nodeIndex, int[] dst, int dstOffset) {
        int start = childOffsets[nodeIndex];
        int count = childOffsets[nodeIndex + 1] - start;
        System.arraycopy(childData, start, dst, dstOffset, count);
        return count;
    }

    /**
     * @return number of parents of node {@code nodeIndex}
     */
    public int parentCount(int nodeIndex) {
        return parentOffsets[nodeIndex + 1] - parentOffsets[nodeIndex];
    }

    /**
     * @return the i-th parent index of node {@code nodeIndex}
     */
    public int parent(int nodeIndex, int i) {
        return parentData[parentOffsets[nodeIndex] + i];
    }

    /**
     * Copies parents of node {@code nodeIndex} into the destination array.
     *
     * @return number of parents written
     */
    public int parents(int nodeIndex, int[] dst, int dstOffset) {
        int start = parentOffsets[nodeIndex];
        int count = parentOffsets[nodeIndex + 1] - start;
        System.arraycopy(parentData, start, dst, dstOffset, count);
        return count;
    }

    /**
     * @return true if every node has at most one parent (the graph is a tree)
     */
    public boolean isTree() {
        for (int i = 0; i < nodes.length; i++) {
            if (parentCount(i) > 1) return false;
        }
        return true;
    }

    /**
     * @return true if node {@code nodeIndex} is a leaf (has no children)
     */
    public boolean isLeaf(int nodeIndex) {
        return childCount(nodeIndex) == 0;
    }

    /**
     * @return true if node {@code nodeIndex} is a root (has no parents)
     */
    public boolean isRoot(int nodeIndex) {
        return parentCount(nodeIndex) == 0;
    }

    /**
     * @return the raw child offsets array (CSR format) for bulk GPU upload.
     * Length is nodeCount + 1.
     */
    public int[] childOffsetsRaw() {
        return childOffsets;
    }

    /**
     * @return the raw child data array (CSR format) for bulk GPU upload
     */
    public int[] childDataRaw() {
        return childData;
    }

    /**
     * @return the raw parent offsets array (CSR format) for bulk GPU upload.
     * Length is nodeCount + 1.
     */
    public int[] parentOffsetsRaw() {
        return parentOffsets;
    }

    /**
     * @return the raw parent data array (CSR format) for bulk GPU upload
     */
    public int[] parentDataRaw() {
        return parentData;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructs a {@link RepresentationGraph} from nodes and edge declarations.
     *
     * <p>Edges are declared as parent-to-child relationships. The builder computes the inverse
     * (child-to-parent) and identifies roots automatically.
     */
    public static final class Builder {
        private RepresentationNode[] nodes;
        private int[][] childrenPerNode; // temporary: childrenPerNode[parent] = [child0, child1, ...]
        private int edgeCount;

        private Builder() {}

        /**
         * Sets the nodes. Must be called before any edge declarations.
         */
        public Builder nodes(RepresentationNode[] nodes) {
            if (nodes == null || nodes.length == 0)
                throw new IllegalArgumentException("at least one node required");
            this.nodes = nodes;
            this.childrenPerNode = new int[nodes.length][];
            this.edgeCount = 0;
            return this;
        }

        /**
         * Declares that {@code parentIndex} refines into {@code childIndex}.
         * Children have lower error than their parents.
         */
        public Builder edge(int parentIndex, int childIndex) {
            if (nodes == null) throw new IllegalStateException("nodes(...) must be called first");
            if (parentIndex < 0 || parentIndex >= nodes.length)
                throw new IndexOutOfBoundsException("parent " + parentIndex);
            if (childIndex < 0 || childIndex >= nodes.length)
                throw new IndexOutOfBoundsException("child " + childIndex);
            if (parentIndex == childIndex)
                throw new IllegalArgumentException("self-edge not allowed");

            int[] existing = childrenPerNode[parentIndex];
            if (existing == null) {
                childrenPerNode[parentIndex] = new int[]{childIndex};
            } else {
                int[] grown = Arrays.copyOf(existing, existing.length + 1);
                grown[existing.length] = childIndex;
                childrenPerNode[parentIndex] = grown;
            }
            edgeCount++;
            return this;
        }

        /**
         * Declares multiple children for one parent at once.
         */
        public Builder edges(int parentIndex, int... childIndices) {
            for (int c : childIndices) edge(parentIndex, c);
            return this;
        }

        public RepresentationGraph build() {
            if (nodes == null) throw new IllegalStateException("nodes not set");

            int n = nodes.length;

            // Build child CSR
            int[] cOff = new int[n + 1];
            for (int i = 0; i < n; i++) {
                cOff[i + 1] = cOff[i] + (childrenPerNode[i] != null ? childrenPerNode[i].length : 0);
            }
            int[] cData = new int[cOff[n]];
            for (int i = 0; i < n; i++) {
                if (childrenPerNode[i] != null) {
                    System.arraycopy(childrenPerNode[i], 0, cData, cOff[i], childrenPerNode[i].length);
                }
            }

            // Build parent CSR (inverse edges)
            // Count parents per node
            int[] parentCounts = new int[n];
            for (int parent = 0; parent < n; parent++) {
                if (childrenPerNode[parent] != null) {
                    for (int child : childrenPerNode[parent]) {
                        parentCounts[child]++;
                    }
                }
            }
            int[] pOff = new int[n + 1];
            for (int i = 0; i < n; i++) {
                pOff[i + 1] = pOff[i] + parentCounts[i];
            }
            int[] pData = new int[pOff[n]];
            int[] pFill = new int[n]; // write cursor per node
            for (int parent = 0; parent < n; parent++) {
                if (childrenPerNode[parent] != null) {
                    for (int child : childrenPerNode[parent]) {
                        pData[pOff[child] + pFill[child]] = parent;
                        pFill[child]++;
                    }
                }
            }

            // Identify roots (nodes with no parents)
            int rootCount = 0;
            for (int i = 0; i < n; i++) {
                if (parentCounts[i] == 0) rootCount++;
            }
            int[] roots = new int[rootCount];
            int ri = 0;
            for (int i = 0; i < n; i++) {
                if (parentCounts[i] == 0) roots[ri++] = i;
            }

            return new RepresentationGraph(nodes, roots, cOff, cData, pOff, pData);
        }
    }
}
