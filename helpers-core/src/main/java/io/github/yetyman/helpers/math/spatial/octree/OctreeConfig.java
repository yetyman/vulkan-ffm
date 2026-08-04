package io.github.yetyman.helpers.math.spatial.octree;

import io.github.yetyman.helpers.math.geometry.AABB;

/**
 * Configuration for octree construction and behavior.
 */
public class OctreeConfig {

    public enum BucketStrategy {
        /** Object placed in the cell containing its center. Simple, but boundary objects may be missed without query expansion. */
        POINT_REGION,
        /** Cells expanded by 2x. Every object fits in exactly one cell. Queries must check expanded bounds. */
        LOOSE,
        /** Object placed in every leaf it overlaps. Results need deduplication. */
        TIGHT_DUPLICATES,
        /** Object stored at the smallest node that fully contains it. Large objects live high. */
        DEEPEST_CONTAINING
    }

    private final int maxDepth;
    private final int splitThreshold;
    private final int mergeThreshold;
    private final BucketStrategy strategy;
    private final AABB worldBounds;

    private OctreeConfig(int maxDepth, int splitThreshold, int mergeThreshold, BucketStrategy strategy, AABB worldBounds) {
        this.maxDepth = maxDepth;
        this.splitThreshold = splitThreshold;
        this.mergeThreshold = mergeThreshold;
        this.strategy = strategy;
        this.worldBounds = worldBounds;
    }

    public int maxDepth() { return maxDepth; }
    public int splitThreshold() { return splitThreshold; }
    public int mergeThreshold() { return mergeThreshold; }
    public BucketStrategy strategy() { return strategy; }
    public AABB worldBounds() { return worldBounds; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int maxDepth = 8;
        private int splitThreshold = 16;
        private int mergeThreshold = 4;
        private BucketStrategy strategy = BucketStrategy.LOOSE;
        private AABB worldBounds = new AABB(
                new io.github.yetyman.helpers.math.Vec3(-1000, -1000, -1000),
                new io.github.yetyman.helpers.math.Vec3(1000, 1000, 1000)
        );

        public Builder maxDepth(int maxDepth) { this.maxDepth = maxDepth; return this; }
        public Builder splitThreshold(int splitThreshold) { this.splitThreshold = splitThreshold; return this; }
        public Builder mergeThreshold(int mergeThreshold) { this.mergeThreshold = mergeThreshold; return this; }
        public Builder strategy(BucketStrategy strategy) { this.strategy = strategy; return this; }
        public Builder worldBounds(AABB worldBounds) { this.worldBounds = worldBounds; return this; }

        public OctreeConfig build() {
            return new OctreeConfig(maxDepth, splitThreshold, mergeThreshold, strategy, worldBounds);
        }
    }
}
