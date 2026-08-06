package io.github.yetyman.vulkan.mesh.consume;

import io.github.yetyman.vulkan.mesh.PrimitiveTopology;

/**
 * A plain record describing one draw call's parameters. A consumer reads this and calls
 * {@code vkCmdDrawIndexed} or writes it into an indirect buffer. Nothing in this type records a
 * command.
 *
 * @param indexCount     number of indices (or vertex count when not indexed)
 * @param instanceCount  number of instances
 * @param firstIndex     offset into the index buffer (or first vertex)
 * @param vertexOffset   added to each index; 0 when indices were rewritten absolute
 * @param firstInstance  first instance ID
 * @param topology       the primitive topology
 * @param indexed        whether this draw uses an index buffer
 */
public record GeometryDrawRange(
        int indexCount,
        int instanceCount,
        int firstIndex,
        int vertexOffset,
        int firstInstance,
        PrimitiveTopology topology,
        boolean indexed
) {
    /**
     * Convenience factory for a single-instance indexed draw.
     */
    public static GeometryDrawRange indexed(int indexCount, int firstIndex, int vertexOffset,
                                            PrimitiveTopology topology) {
        return new GeometryDrawRange(indexCount, 1, firstIndex, vertexOffset, 0, topology, true);
    }

    /**
     * Convenience factory for a single-instance non-indexed draw.
     */
    public static GeometryDrawRange nonIndexed(int vertexCount, int firstVertex,
                                               PrimitiveTopology topology) {
        return new GeometryDrawRange(vertexCount, 1, firstVertex, 0, 0, topology, false);
    }
}
