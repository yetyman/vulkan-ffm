package io.github.yetyman.structures.state;

/**
 * Strategy for accumulating and flushing dirty slots during a batch.
 * Snapshot capture is handled by the slot itself via {@link StateSlot#mark()}.
 * Implementations must be thread-safe with respect to concurrent markDirty calls.
 */
public interface BatchStrategy {

    /** Definition-order strategy: fires in slot index order using a bitmask. Fast, allocation-free. */
    static BatchStrategy definitionOrder() { return new DefinitionOrderBatch(); }

    /** Change-order strategy: fires in the order set() was called within the batch. */
    static BatchStrategy changeOrder() { return new ChangeOrderBatch(); }
    /**
     * Called once at seal() time with the final slot count.
     * Implementations should pre-size all internal structures here.
     */
    void init(int slotCount);

    /** Called when a slot is dirtied during an active batch. */
    void markDirty(int slotIndex);

    /**
     * Flush all dirty slots, firing their listeners.
     * Each slot resets its own dirty flag inside {@link StateSlot#fireListeners()}.
     * Called when batch depth reaches zero.
     * @return true if at least one slot fired its listeners
     */
    boolean flush(StateSlot[] slots);

    /**
     * Reset ordering structures. Called at the start of a new outermost batch.
     * For bitmask strategies this is a no-op since the mask is cleared inline during flush.
     */
    void reset();


}
