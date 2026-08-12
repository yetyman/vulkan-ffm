package io.github.yetyman.vulkan.mesh.consume;

/**
 * The kind of indirect draw command to encode. Each kind corresponds to a different Vulkan
 * indirect command struct layout and a different dispatch call.
 *
 * @see IndirectDrawEncoder
 */
public enum IndirectKind {

    /**
     * {@code VkDrawIndexedIndirectCommand}: 20 bytes.
     * Dispatched via {@code vkCmdDrawIndexedIndirect} or {@code vkCmdDrawIndexedIndirectCount}.
     */
    INDEXED(IndirectDrawEncoder.INDEXED_STRIDE),

    /**
     * {@code VkDrawIndirectCommand}: 16 bytes.
     * Dispatched via {@code vkCmdDrawIndirect} or {@code vkCmdDrawIndirectCount}.
     */
    NON_INDEXED(IndirectDrawEncoder.NON_INDEXED_STRIDE),

    /**
     * {@code VkDrawMeshTasksIndirectCommandEXT}: 12 bytes.
     * Dispatched via {@code vkCmdDrawMeshTasksIndirectEXT} or
     * {@code vkCmdDrawMeshTasksIndirectCountEXT}.
     */
    MESH_TASKS(IndirectDrawEncoder.MESH_TASK_STRIDE);

    private final int stride;

    IndirectKind(int stride) {
        this.stride = stride;
    }

    /**
     * @return bytes per command for this kind
     */
    public int stride() {
        return stride;
    }

    /**
     * @return the buffer size in bytes needed for {@code commandCount} commands of this kind
     */
    public long bufferSize(int commandCount) {
        return (long) commandCount * stride;
    }
}
