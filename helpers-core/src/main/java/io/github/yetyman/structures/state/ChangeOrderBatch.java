package io.github.yetyman.structures.state;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Fires listeners in the order set() was called within the batch.
 * <p>
 * WARNING: Change ordering adds overhead for concurrent systems.
 * <p>
 * Pre-sized at seal() time. markDirty uses a CAS on enqueued[] to ensure each slot
 * is recorded at most once, then claims a unique index in orderBuf via getAndIncrement().
 * The write to orderBuf[idx] is a VarHandle release write; flush() reads each entry
 * with an acquire read. This closes a store-reorder correctness hole on non-x86 architectures
 * (ARM/POWER) where a plain write could be reordered past the CAS and arrive after flush reads it.
 * On x86 this is free; on ARM it compiles to stlr/ldar.
 * <p>
 * Note: beginBatch/endBatch must not overlap with concurrent set() calls — the batch boundary
 * is not concurrency-safe. If beginBatch and a concurrent set() race, the set() notification
 * may be silently dropped with no exception. Concurrent set() calls are safe once a batch
 * is in progress.
 */
public final class ChangeOrderBatch implements BatchStrategy {
    private static final VarHandle ORDER_BUF;
    static {
        try {
            ORDER_BUF = MethodHandles.arrayElementVarHandle(int[].class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private AtomicIntegerArray enqueued;
    private int[] orderBuf;
    private final AtomicInteger orderSize = new AtomicInteger(0);

    @Override
    public void init(int slotCount) {
        enqueued = new AtomicIntegerArray(slotCount);
        orderBuf = new int[slotCount];
    }

    @Override
    public void markDirty(int slotIndex) {
        if (enqueued.compareAndSet(slotIndex, 0, 1)) {
            int pos = orderSize.getAndIncrement();
            ORDER_BUF.setRelease(orderBuf, pos, slotIndex);
        }
    }

    @Override
    public boolean flush(StateSlot[] slots) {
        int size = orderSize.get();
        boolean anyFired = false;
        for (int i = 0; i < size; i++) {
            int idx = (int) ORDER_BUF.getAcquire(orderBuf, i);
            enqueued.set(idx, 0);
            if (!slots[idx].silenced) { slots[idx].fireListeners(); anyFired = true; }
            else slots[idx].dirty = false;
        }
        orderSize.set(0);
        return anyFired;
    }

    @Override
    public void reset() {
        int size = orderSize.get();
        for (int i = 0; i < size; i++) {
            int idx = (int) ORDER_BUF.getAcquire(orderBuf, i);
            enqueued.set(idx, 0);
        }
        orderSize.set(0);
    }
}
