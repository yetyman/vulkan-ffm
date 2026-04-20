package io.github.yetyman.vulkan.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Thread-local bump allocator for short-lived native structs.
 * Intended for use inside command recording methods where allocations only need to
 * live until the Vulkan call returns. Eliminates Arena.ofConfined() overhead on hot paths.
 *
 * Usage:
 * <pre>
 *     BumpAllocator ba = BumpAllocator.get();
 *     ba.push();
 *     try {
 *         MemorySegment s = ba.alloc(VkViewport.sizeof(), 8);
 *         // fill and use s ...
 *     } finally {
 *         ba.pop();
 *     }
 * </pre>
 *
 * Nesting is supported up to STACK_DEPTH levels. If a single allocation exceeds the
 * remaining block space, it falls back to Arena.ofConfined() with a warning logged once.
 */
public final class BumpAllocator {

    private static final int BLOCK_SIZE  = 64 * 1024; // 64 KB — massively oversized, costs nothing
    private static final int STACK_DEPTH = 16;
    private static final int ALIGN       = 8;

    private static final ThreadLocal<BumpAllocator> INSTANCE =
            ThreadLocal.withInitial(BumpAllocator::new);

    private final MemorySegment block = Arena.global().allocate(BLOCK_SIZE, ALIGN);
    private final int[] offsetStack   = new int[STACK_DEPTH];
    private int offset     = 0;
    private int stackDepth = 0;
    private boolean overflowWarned = false;

    private BumpAllocator() {}

    /** @return the thread-local BumpAllocator instance */
    public static BumpAllocator get() {
        return INSTANCE.get();
    }

    /**
     * Saves the current allocation offset. Must be paired with {@link #pop()}.
     */
    public void push() {
        offsetStack[stackDepth++] = offset;
    }

    /**
     * Restores the allocation offset saved by the matching {@link #push()},
     * effectively freeing all allocations made since that push.
     */
    public void pop() {
        offset = offsetStack[--stackDepth];
    }

    /**
     * Allocates {@code size} bytes aligned to {@link #ALIGN} from the bump block.
     * Falls back to a confined Arena allocation if the block is exhausted.
     *
     * @param size byte size of the allocation
     * @return a MemorySegment valid until the next matching {@link #pop()}
     */
    public MemorySegment alloc(long size) {
        int aligned = (int) ((size + ALIGN - 1) & ~(ALIGN - 1));
        if (offset + aligned <= BLOCK_SIZE) {
            MemorySegment slice = block.asSlice(offset, size);
            offset += aligned;
            return slice;
        }
        // overflow — fall back, warn once
        if (!overflowWarned) {
            Logger.warn("BumpAllocator overflow on thread " + Thread.currentThread().getName()
                    + " — falling back to Arena.ofConfined(). Consider increasing BLOCK_SIZE.");
            overflowWarned = true;
        }
        return Arena.ofConfined().allocate(size, ALIGN);
    }
}
