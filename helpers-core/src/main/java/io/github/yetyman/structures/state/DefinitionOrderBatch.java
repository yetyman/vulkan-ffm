package io.github.yetyman.structures.state;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Fires listeners in slot definition order.
 * Uses a long[] bitmask — each word covers 64 slots.
 * markDirty is a single OR into one word; flush iterates only set bits via
 * Long.numberOfTrailingZeros, touching only words with dirty slots.
 * Concurrent markDirty calls use VarHandle getAndBitwiseOr for atomicity.
 */
public final class DefinitionOrderBatch implements BatchStrategy {
    private static final VarHandle WORDS;
    static {
        try {
            WORDS = MethodHandles.arrayElementVarHandle(long[].class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private long[] words;

    @Override
    public void init(int slotCount) {
        words = new long[(slotCount + 63) >>> 6];
    }

    @Override
    public void markDirty(int slotIndex) {
        WORDS.getAndBitwiseOr(words, slotIndex >>> 6, 1L << (slotIndex & 63));
    }

    @Override
    public boolean flush(StateSlot[] slots) {
        long[] w = words;
        boolean anyFired = false;
        for (int word = 0; word < w.length; word++) {
            long bits = (long) WORDS.getAcquire(w, word);
            if (bits == 0) continue;
            WORDS.setOpaque(w, word, 0L);
            while (bits != 0) {
                int bit = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int idx = (word << 6) | bit;
                if (idx < slots.length) {
                    if (!slots[idx].silenced) { slots[idx].fireListeners(); anyFired = true; }
                    else slots[idx].dirty = false;
                }
            }
        }
        return anyFired;
    }

    @Override
    public void reset() {
        // words are cleared inline during flush via setOpaque; nothing to reset here
    }
}
