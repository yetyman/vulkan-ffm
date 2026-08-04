package io.github.yetyman.helpers.math.spatial.bvh;

import io.github.yetyman.helpers.math.geometry.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Top-down BVH construction via median split on the longest axis.
 * O(n log n), decent quality, good default.
 */
public class MedianSplitBuilder implements BvhBuilder {

    private final int maxLeafSize;

    public MedianSplitBuilder() {
        this(1);
    }

    public MedianSplitBuilder(int maxLeafSize) {
        this.maxLeafSize = Math.max(1, maxLeafSize);
    }

    @Override
    public BuildResult build(List<AABB> bounds) {
        if (bounds.isEmpty()) return new BuildResult(new BvhNode[0], new int[0]);

        int[] indices = new int[bounds.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;

        List<BvhNode> nodes = new ArrayList<>();
        buildRecursive(bounds, indices, 0, indices.length, nodes);
        return new BuildResult(nodes.toArray(new BvhNode[0]), indices);
    }

    private int buildRecursive(List<AABB> bounds, int[] indices, int start, int end, List<BvhNode> nodes) {
        int count = end - start;

        // Compute enclosing AABB
        AABB enclosing = computeEnclosing(bounds, indices, start, end);

        // Leaf condition
        if (count <= maxLeafSize) {
            int nodeIndex = nodes.size();
            nodes.add(BvhNode.leaf(enclosing, start, count));
            return nodeIndex;
        }

        // Find longest axis
        float dx = enclosing.max.x - enclosing.min.x;
        float dy = enclosing.max.y - enclosing.min.y;
        float dz = enclosing.max.z - enclosing.min.z;
        int axis;
        if (dx >= dy && dx >= dz) axis = 0;
        else if (dy >= dz) axis = 1;
        else axis = 2;

        // Sort indices by centroid on chosen axis
        final int sortAxis = axis;
        final List<AABB> b = bounds;
        sortIndices(indices, start, end, sortAxis, b);

        // Split at median
        int mid = (start + end) / 2;

        // Reserve slot for this internal node
        int nodeIndex = nodes.size();
        nodes.add(null); // placeholder

        int leftChild = buildRecursive(bounds, indices, start, mid, nodes);
        int rightChild = buildRecursive(bounds, indices, mid, end, nodes);

        nodes.set(nodeIndex, BvhNode.internal(enclosing, leftChild, rightChild));
        return nodeIndex;
    }

    private AABB computeEnclosing(List<AABB> bounds, int[] indices, int start, int end) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            AABB a = bounds.get(indices[i]);
            minX = Math.min(minX, a.min.x); minY = Math.min(minY, a.min.y); minZ = Math.min(minZ, a.min.z);
            maxX = Math.max(maxX, a.max.x); maxY = Math.max(maxY, a.max.y); maxZ = Math.max(maxZ, a.max.z);
        }
        return new AABB(
                new io.github.yetyman.helpers.math.Vec3(minX, minY, minZ),
                new io.github.yetyman.helpers.math.Vec3(maxX, maxY, maxZ)
        );
    }

    private void sortIndices(int[] indices, int start, int end, int axis, List<AABB> bounds) {
        // Simple insertion sort for small ranges, Arrays.sort for larger
        Integer[] boxed = new Integer[end - start];
        for (int i = 0; i < boxed.length; i++) boxed[i] = indices[start + i];

        Comparator<Integer> cmp = (a, b) -> {
            AABB ab = bounds.get(a);
            AABB bb = bounds.get(b);
            float ca = centroid(ab, axis);
            float cb = centroid(bb, axis);
            return Float.compare(ca, cb);
        };
        java.util.Arrays.sort(boxed, cmp);

        for (int i = 0; i < boxed.length; i++) indices[start + i] = boxed[i];
    }

    private static float centroid(AABB a, int axis) {
        return switch (axis) {
            case 0 -> (a.min.x + a.max.x) * 0.5f;
            case 1 -> (a.min.y + a.max.y) * 0.5f;
            case 2 -> (a.min.z + a.max.z) * 0.5f;
            default -> 0f;
        };
    }
}
