package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.vulkan.mesh.AttributeFormat;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.DeviceRange;
import io.github.yetyman.vulkan.mesh.MeshLayout;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

/**
 * An {@link AttributeStream} that is already GPU-resident and not host-readable.
 *
 * <p>This is the stream type for geometry produced by compute shaders, device-to-device copies,
 * or any situation where the data lives exclusively in device memory and has no host backing.
 * Its {@link #transcodeInto} always throws because there is no host data to transcode from;
 * the upload layer handles device-resident streams via device-to-device copies using the
 * {@link #deviceRange()}.</p>
 *
 * <p>Use cases:</p>
 * <ul>
 *   <li>Compute-shader-generated geometry (particle positions, skinned vertices, procedural terrain)</li>
 *   <li>Geometry that was uploaded once and evicted from host memory</li>
 *   <li>Streams obtained from GPU decompression (e.g. Draco GPU decode)</li>
 * </ul>
 */
public final class DeviceAttributeStream implements AttributeStream {

    private final AttributeSemantic semantic;
    private final AttributeFormat format;
    private final long elementCount;
    private final DeviceRange range;

    /**
     * @param semantic     which attribute this carries
     * @param format       how each element is encoded in device memory
     * @param elementCount number of elements
     * @param range        the device buffer range holding this stream's data
     */
    public DeviceAttributeStream(AttributeSemantic semantic, AttributeFormat format,
                                 long elementCount, DeviceRange range) {
        if (semantic == null) throw new IllegalArgumentException("semantic required");
        if (format == null) throw new IllegalArgumentException("format required");
        if (range == null) throw new IllegalArgumentException("range required");
        if (elementCount < 0) throw new IllegalArgumentException("elementCount must be >= 0");
        this.semantic = semantic;
        this.format = format;
        this.elementCount = elementCount;
        this.range = range;
    }

    @Override
    public AttributeSemantic semantic() {
        return semantic;
    }

    @Override
    public AttributeFormat sourceFormat() {
        return format;
    }

    @Override
    public long elementCount() {
        return elementCount;
    }

    @Override
    public Residency residency() {
        return Residency.DEVICE;
    }

    @Override
    public boolean isHostReadable() {
        return false;
    }

    @Override
    public Optional<DeviceRange> deviceRange() {
        return Optional.of(range);
    }

    /**
     * Always throws: device-only streams cannot be transcoded from the CPU. The upload layer
     * handles these via device-to-device copies using {@link #deviceRange()}.
     *
     * @throws IllegalStateException always
     */
    @Override
    public void transcodeInto(MeshLayout targetLayout, MemorySegment dst, long dstOffset,
                              long dstStride, long firstElement, long elementCount) {
        throw new IllegalStateException(
                "DeviceAttributeStream is not host-readable. Use deviceRange() for "
                + "device-to-device copies. Semantic: '" + semantic.name() + "'");
    }
}
