package io.github.yetyman.helpers.math.spatial;

import java.util.stream.IntStream;

/**
 * Tracks which nodes have changed since last sync.
 * External systems (frame graph, buffer managers) use this to determine what needs re-upload.
 */
public interface DirtyTracker {

    /** Returns true if any node has been modified since last {@link #clearDirty()}. */
    boolean isDirty();

    /** Returns true if the entire structure was invalidated (rebalance, bulk insert, rebuild). */
    boolean isFullRebuild();

    /** Returns the number of individually dirty nodes (0 if full rebuild). */
    int dirtyNodeCount();

    /** Returns the indices of individually changed nodes. Empty if {@link #isFullRebuild()} is true. */
    IntStream dirtyNodeIndices();

    /** Called by external sync after upload completes. Resets dirty state. */
    void clearDirty();
}
