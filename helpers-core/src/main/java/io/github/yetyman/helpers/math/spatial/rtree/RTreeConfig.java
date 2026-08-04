package io.github.yetyman.helpers.math.spatial.rtree;

/**
 * Configuration for R-tree construction and behavior.
 */
public class RTreeConfig {

    public enum SplitStrategy {
        /** Quadratic split: pick seeds by maximum waste, assign remaining by least enlargement. */
        QUADRATIC,
        /** Linear split: pick seeds by maximum separation, assign remaining sequentially. */
        LINEAR
    }

    private final int maxChildren;
    private final int minChildren;
    private final SplitStrategy splitStrategy;

    private RTreeConfig(int maxChildren, int minChildren, SplitStrategy splitStrategy) {
        this.maxChildren = maxChildren;
        this.minChildren = minChildren;
        this.splitStrategy = splitStrategy;
    }

    public int maxChildren() { return maxChildren; }
    public int minChildren() { return minChildren; }
    public SplitStrategy splitStrategy() { return splitStrategy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int maxChildren = 16;
        private int minChildren = 2;
        private SplitStrategy splitStrategy = SplitStrategy.QUADRATIC;

        public Builder maxChildren(int maxChildren) { this.maxChildren = maxChildren; return this; }
        public Builder minChildren(int minChildren) { this.minChildren = minChildren; return this; }
        public Builder splitStrategy(SplitStrategy splitStrategy) { this.splitStrategy = splitStrategy; return this; }

        public RTreeConfig build() {
            if (maxChildren < minChildren * 2)
                throw new IllegalArgumentException("maxChildren must be >= minChildren * 2");
            return new RTreeConfig(maxChildren, minChildren, splitStrategy);
        }
    }
}
