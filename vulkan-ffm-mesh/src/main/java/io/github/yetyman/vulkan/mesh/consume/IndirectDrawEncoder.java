package io.github.yetyman.vulkan.mesh.consume;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

/**
 * Encodes draw commands into a {@link MemorySegment} in the layout Vulkan's indirect draw commands
 * expect. Both a primitive form (no allocation, hot-path) and a record form (convenience,
 * implemented on top of the primitive form) are provided.
 *
 * <p>Layouts match {@code VkDrawIndexedIndirectCommand} (20 bytes), {@code VkDrawIndirectCommand}
 * (16 bytes), and {@code VkDrawMeshTasksIndirectCommandEXT} (12 bytes) exactly.
 */
public final class IndirectDrawEncoder {

    /** Bytes per indexed indirect command. */
    public static final int INDEXED_STRIDE = 20;
    /** Bytes per non-indexed indirect command. */
    public static final int NON_INDEXED_STRIDE = 16;
    /** Bytes per mesh-task dispatch command. */
    public static final int MESH_TASK_STRIDE = 12;

    private IndirectDrawEncoder() {}

    // -------------------------------------------------------------------------
    // Primitive forms: no allocation, the hot path
    // -------------------------------------------------------------------------

    /**
     * Encodes one {@code VkDrawIndexedIndirectCommand} at position {@code commandIndex}.
     */
    public static void encodeIndexed(MemorySegment dst, long commandIndex,
                                     int indexCount, int instanceCount, int firstIndex,
                                     int vertexOffset, int firstInstance) {
        long o = commandIndex * INDEXED_STRIDE;
        dst.set(JAVA_INT_UNALIGNED, o, indexCount);
        dst.set(JAVA_INT_UNALIGNED, o + 4, instanceCount);
        dst.set(JAVA_INT_UNALIGNED, o + 8, firstIndex);
        dst.set(JAVA_INT_UNALIGNED, o + 12, vertexOffset);
        dst.set(JAVA_INT_UNALIGNED, o + 16, firstInstance);
    }

    /**
     * Encodes one {@code VkDrawIndirectCommand} at position {@code commandIndex}.
     */
    public static void encodeNonIndexed(MemorySegment dst, long commandIndex,
                                        int vertexCount, int instanceCount,
                                        int firstVertex, int firstInstance) {
        long o = commandIndex * NON_INDEXED_STRIDE;
        dst.set(JAVA_INT_UNALIGNED, o, vertexCount);
        dst.set(JAVA_INT_UNALIGNED, o + 4, instanceCount);
        dst.set(JAVA_INT_UNALIGNED, o + 8, firstVertex);
        dst.set(JAVA_INT_UNALIGNED, o + 12, firstInstance);
    }

    /**
     * Encodes one {@code VkDrawMeshTasksIndirectCommandEXT} at position {@code commandIndex}.
     */
    public static void encodeMeshTask(MemorySegment dst, long commandIndex,
                                      int groupCountX, int groupCountY, int groupCountZ) {
        long o = commandIndex * MESH_TASK_STRIDE;
        dst.set(JAVA_INT_UNALIGNED, o, groupCountX);
        dst.set(JAVA_INT_UNALIGNED, o + 4, groupCountY);
        dst.set(JAVA_INT_UNALIGNED, o + 8, groupCountZ);
    }

    // -------------------------------------------------------------------------
    // Record form: convenience, implemented on the primitive form
    // -------------------------------------------------------------------------

    /**
     * Encodes a {@link GeometryDrawRange} at position {@code commandIndex}.
     */
    public static void encode(MemorySegment dst, long commandIndex, GeometryDrawRange range) {
        if (range.indexed()) {
            encodeIndexed(dst, commandIndex, range.indexCount(), range.instanceCount(),
                    range.firstIndex(), range.vertexOffset(), range.firstInstance());
        } else {
            encodeNonIndexed(dst, commandIndex, range.indexCount(), range.instanceCount(),
                    range.firstIndex(), range.firstInstance());
        }
    }

    /**
     * @return the stride in bytes for the given draw kind
     */
    public static int stride(boolean indexed) {
        return indexed ? INDEXED_STRIDE : NON_INDEXED_STRIDE;
    }
}
