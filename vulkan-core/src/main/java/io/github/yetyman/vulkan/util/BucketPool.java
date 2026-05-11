package io.github.yetyman.vulkan.util;

import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Power-of-2 bucketed object pool for sized objects. Each bucket holds objects of a specific
 * power-of-2 size. Acquire rounds up to the next power of 2 and pulls from that bucket.
 *
 * Buckets range from 2^minExp to 2^maxExp. Objects outside this range are not pooled.
 *
 * Use for byte arrays, temporary buffers, or any object whose "size" varies and can be reused
 * if the capacity is >= the requested size.
 *
 * Example:
 * <pre>
 * BucketPool<byte[]> byteArrayPool = new BucketPool<>(4, 20, // 16 bytes to 1MB
 *     size -> new byte[size],
 *     null);
 * byte[] buf = byteArrayPool.acquire(5000); // gets an 8192-byte array
 * byteArrayPool.release(buf, buf.length);
 * </pre>
 */
public class BucketPool<T> {

    private final int minExp;
    private final int maxExp;
    private final ObjectPool<T>[] buckets;
    private final IntFunction<T> factory;

    /**
     * @param minExp minimum exponent (bucket 0 = 2^minExp)
     * @param maxExp maximum exponent (last bucket = 2^maxExp)
     * @param factory creates a new object of the given size (receives the bucket size, not requested size)
     * @param reset called on release to clear the object (may be null)
     */
    @SuppressWarnings("unchecked")
    public BucketPool(int minExp, int maxExp, IntFunction<T> factory, Consumer<T> reset) {
        this.minExp = minExp;
        this.maxExp = maxExp;
        this.factory = factory;
        int bucketCount = maxExp - minExp + 1;
        this.buckets = new ObjectPool[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            int bucketSize = 1 << (minExp + i);
            int finalI = i;
            this.buckets[i] = new ObjectPool<>(8, () -> factory.apply(1 << (minExp + finalI)), reset);
        }
    }

    /**
     * Acquires an object with capacity >= requestedSize.
     * Rounds up to the next power of 2 and pulls from that bucket.
     * Returns a freshly allocated object if the size exceeds maxExp.
     */
    public T acquire(int requestedSize) {
        int exp = exponentFor(requestedSize);
        if (exp < minExp || exp > maxExp) {
            // Outside pooled range -- allocate directly
            return factory.apply(requestedSize);
        }
        return buckets[exp - minExp].acquire();
    }

    /**
     * Returns an object to the appropriate bucket.
     *
     * @param obj the object to return
     * @param objectSize the actual capacity of the object (used to find the right bucket)
     */
    public void release(T obj, int objectSize) {
        if (obj == null) return;
        int exp = exponentFor(objectSize);
        if (exp < minExp || exp > maxExp) return; // not poolable
        // Only return to pool if it's exactly a power-of-2 size (i.e. came from us)
        if ((1 << exp) == objectSize) {
            buckets[exp - minExp].release(obj);
        }
    }

    /** @return the exponent of the smallest power of 2 >= size */
    private static int exponentFor(int size) {
        if (size <= 1) return 0;
        return 32 - Integer.numberOfLeadingZeros(size - 1);
    }

    /** @return the bucket size (power of 2) for a given requested size */
    public static int bucketSize(int requestedSize) {
        if (requestedSize <= 1) return 1;
        return Integer.highestOneBit(requestedSize - 1) << 1;
    }
}
