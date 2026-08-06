package io.github.yetyman.vulkan.buffers;

/**
 * Implemented by types that have a canonical GPU serialization, exposed as a {@link GpuLayout}.
 *
 * <p>This replaces the earlier {@code BufferWritable} interface, which had the type serialize
 * itself. That duplicated {@link GpuLayout} with different ownership and left a permanent
 * "which one do I implement" question whose answer differed per type. Serialization is always a
 * layout; a type may declare which layout is its default, and nothing more.
 *
 * <p>Implementations conventionally expose the same layout as a {@code public static final
 * GpuLayout<T> DEFAULT_LAYOUT} field, so callers can reach it without an instance.
 *
 * @param <T> the implementing type
 */
public interface HasGpuLayout<T> {

    /**
     * @return the canonical layout for this type. Callers needing a different packing pass an
     * alternative {@link GpuLayout} explicitly instead of using this one.
     */
    GpuLayout<T> defaultLayout();
}
