package io.github.yetyman.vulkan.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * Thread-local bump allocator for short-lived native structs.
 * Intended for use inside command recording methods where allocations only need to
 * live until the Vulkan call returns. Eliminates Arena.ofConfined() overhead on hot paths.
 *
 * <p>Usage:
 * <pre>
 *     BumpAllocator ba = BumpAllocator.get();
 *     ba.push();
 *     try {
 *         MemorySegment s = ba.alloc(VkViewport.sizeof());
 *         // fill and use s ...
 *     } finally {
 *         ba.pop();
 *     }
 * </pre>
 *
 * <p>Nesting is supported up to STACK_DEPTH levels. If a single allocation exceeds the
 * remaining block space, it falls back to a thread-local {@link Arena.ofShared()} with a warning logged once.
 *
 * @apiNote This class is thread-local by design. {@link #get()} always returns the instance
 * for the calling thread. Do not store the returned instance and share it across threads —
 * the underlying memory block is not thread-safe.
 */
public final class BumpAllocator implements SegmentAllocator {

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

    // thread-local shared arena for overflow allocations — never closed, lives with the thread
    private static final ThreadLocal<Arena> overflowArena =
            ThreadLocal.withInitial(Arena::ofShared);

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
     * Implements {@link SegmentAllocator} so this can be passed to generated struct allocators
     * (e.g. {@code VkSubmitInfo.allocate(alloc)}) without an intermediate Arena.
     */
    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        // align the current offset up to the requested alignment before slicing
        int alignedOffset = (int) ((offset + byteAlignment - 1) & ~(byteAlignment - 1));
        int end = (int) (alignedOffset + byteSize);
        if (end <= BLOCK_SIZE) {
            MemorySegment slice = block.asSlice(alignedOffset, byteSize);
            slice.fill((byte) 0);
            offset = end;
            return slice;
        }
        if (!overflowWarned) {
            Logger.warn("BumpAllocator overflow on thread " + Thread.currentThread().getName()
                    + " — falling back to thread-local Arena. Consider increasing BLOCK_SIZE.");
            overflowWarned = true;
        }
        return overflowArena.get().allocate(byteSize, byteAlignment);
    }

    /**
     * Allocates {@code size} bytes aligned to {@link #ALIGN} from the bump block.
     *
     * @param size byte size of the allocation
     * @return a MemorySegment valid until the next matching {@link #pop()}
     */
    public MemorySegment alloc(long size) {
        return allocate(size, ALIGN);
    }
}
