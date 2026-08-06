package io.github.yetyman.vulkan.mesh.source;

import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.AttributeSemantic;
import io.github.yetyman.vulkan.mesh.MeshLayout;
import io.github.yetyman.vulkan.mesh.PrimitiveTopology;

import java.util.Optional;
import java.util.Set;

/**
 * The producer abstraction for geometry data. This is the file-format seam and the procedural seam
 * simultaneously. glTF readers, OBJ parsers, PLY loaders, marching cubes output, compute-shader
 * generated geometry, and hand-coded procedural primitives all implement this one interface.
 *
 * <p>Implementations are never required to hold all data in host memory at once. A memory-mapped
 * file view is a valid backing for a stream, as is a procedural generator that writes directly into
 * the destination on demand. The element-windowed transcodeInto operations on the returned streams
 * make partial and progressive upload possible.
 *
 * <p>{@link #nativeLayout()} is present so the upload path can detect the identity case (source
 * layout equals target layout) and collapse all per-attribute transcoding into one flat memory
 * copy.
 */
public interface GeometrySource {

    /**
     * @return the set of attribute semantics this source can provide
     */
    Set<AttributeSemantic> available();

    /**
     * @return the stream for the given semantic
     * @throws IllegalArgumentException if this source does not have the requested semantic
     */
    AttributeStream stream(AttributeSemantic semantic);

    /**
     * @return the index stream, or empty if this source is not indexed
     */
    Optional<IndexStream> indices();

    /**
     * @return number of vertices (elements) in this source
     */
    long elementCount();

    /**
     * @return primitive topology
     */
    PrimitiveTopology topology();

    /**
     * @return axis-aligned bounds enclosing all positions. Required rather than optional because
     * every allocator, culling scheme, and future LOD selector needs it, and computing it lazily
     * from a possibly-non-host-readable stream is worse than requiring the source to know.
     */
    AABB bounds();

    /**
     * @return the layout the source's data is natively stored in, when it has one. Used to detect
     * the identity case so transcoding can be collapsed to one flat copy. Procedural sources that
     * generate directly into the destination typically return empty.
     */
    Optional<MeshLayout> nativeLayout();
}
