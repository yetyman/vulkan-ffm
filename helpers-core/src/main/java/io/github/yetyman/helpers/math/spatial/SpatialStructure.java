package io.github.yetyman.helpers.math.spatial;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.buffers.BufferWritable;

import java.util.Map;
import java.util.function.Function;

/**
 * Mutable spatial structure interface. Extends {@link SpatialQuery} with insert/remove/update operations.
 * Also implements {@link BufferWritable} — structures can serialize themselves in their default layout
 * directly into a buffer. Use {@code GpuLayout<StructureType>} for alternative layouts.
 *
 * @param <T> the type of items stored in the spatial structure
 */
public interface SpatialStructure<T> extends SpatialQuery<T>, BufferWritable {

    /** Inserts an item with the given bounds into the structure. */
    void insert(T item, AABB bounds);

    /**
     * Bulk inserts items. Implementations may defer rebalancing until the batch completes
     * for better tree quality and performance than repeated single inserts.
     *
     * @param items the items to insert
     * @param boundsProvider function that returns the AABB for each item
     */
    void insertAll(Iterable<T> items, Function<T, AABB> boundsProvider);

    /**
     * Bulk inserts items from a pre-computed bounds map.
     * Implementations may defer rebalancing until the batch completes.
     */
    void insertAll(Map<T, AABB> itemBounds);

    /** Removes an item from the structure. */
    void remove(T item);

    /** Updates an item's bounds. Equivalent to remove + insert but may be optimized. */
    void update(T item, AABB newBounds);

    /** Fully rebuilds the structure from its current contents (optimal tree quality). */
    void rebuild();

    /** Removes all items from the structure. */
    void clear();

    /** Returns the number of items in the structure. */
    int size();

    /** Returns the world-space bounding box enclosing all items. */
    AABB worldBounds();

    /** Returns the dirty tracker for this structure. */
    DirtyTracker dirtyTracker();
}
