package io.github.yetyman.vulkan.mesh.partition;

import io.github.yetyman.vulkan.buffers.GpuLayout;

/**
 * A typed key for per-partition metadata. Each channel carries its own {@link GpuLayout} so the
 * metadata is uploadable without the module understanding what the channel means.
 *
 * <p>Meshlet cone axes, cluster LOD error metrics, terrain neighbour masks, and splat covariance
 * matrices are all just different channels -- none of them belongs on {@link GeometryPartition}
 * itself, which is what keeps the base type from growing without bound.
 *
 * @param <T> the type of value stored per partition in this channel
 */
public final class MetadataChannel<T> {

    private final String name;
    private final GpuLayout<T> layout;

    private MetadataChannel(String name, GpuLayout<T> layout) {
        this.name = name;
        this.layout = layout;
    }

    /**
     * Creates a channel.
     *
     * @param name   diagnostic name; carries no semantics
     * @param layout how one value is serialized for GPU upload
     */
    public static <T> MetadataChannel<T> of(String name, GpuLayout<T> layout) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (layout == null) throw new IllegalArgumentException("layout required");
        return new MetadataChannel<>(name, layout);
    }

    public String name() {
        return name;
    }

    /**
     * @return the layout used for dense-array GPU upload of this channel's values
     */
    public GpuLayout<T> layout() {
        return layout;
    }

    @Override
    public String toString() {
        return "MetadataChannel[" + name + "]";
    }
}
