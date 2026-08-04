package io.github.yetyman.helpers.math.spatial.bvh;

import io.github.yetyman.helpers.math.geometry.AABB;

/**
 * A node in the flat BVH array. Either an internal node (left/right children)
 * or a leaf node (primitive range).
 */
public class BvhNode {

    public final AABB bounds;

    /** Index of left child in the node array, or -1 if leaf. */
    public final int leftChild;

    /** Index of right child in the node array, or -1 if leaf. */
    public final int rightChild;

    /** First primitive index (inclusive). Only valid for leaf nodes. */
    public final int primStart;

    /** Number of primitives. 0 for internal nodes. */
    public final int primCount;

    private BvhNode(AABB bounds, int leftChild, int rightChild, int primStart, int primCount) {
        this.bounds = bounds;
        this.leftChild = leftChild;
        this.rightChild = rightChild;
        this.primStart = primStart;
        this.primCount = primCount;
    }

    public boolean isLeaf() { return primCount > 0; }

    public static BvhNode internal(AABB bounds, int leftChild, int rightChild) {
        return new BvhNode(bounds, leftChild, rightChild, 0, 0);
    }

    public static BvhNode leaf(AABB bounds, int primStart, int primCount) {
        return new BvhNode(bounds, -1, -1, primStart, primCount);
    }
}
