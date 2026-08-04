package io.github.yetyman.helpers.math;

import java.util.function.Supplier;

/**
 * Strategy interface for controlling allocation of math type instances from builders.
 * Default implementation allocates a new instance each time. Can be swapped to pooling
 * without changing calling code.
 */
public interface BuildStrategy<T> {

    /**
     * Obtains an instance (either freshly allocated or from a pool).
     */
    T obtain();

    /**
     * Releases an instance back to the pool. Default implementation is a no-op (GC handles it).
     */
    default void release(T instance) {}

    /**
     * Creates an allocating strategy that produces a new instance via the given factory each time.
     */
    static <T> BuildStrategy<T> allocating(Supplier<T> factory) {
        return factory::get;
    }
}
