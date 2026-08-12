package io.github.yetyman.vulkan.mesh.lod;

import io.github.yetyman.vulkan.buffers.IBuffer;

/**
 * Describes GPU work that will produce LOD selection results (indirect draw arguments and a
 * draw count). The consumer records whatever this description requires into a command buffer.
 *
 * <p>This is an interface rather than a concrete record because different GPU-driven LOD
 * paradigms have fundamentally different dispatch shapes:
 * <ul>
 *   <li>A simple frustum cull: one compute dispatch, one output buffer</li>
 *   <li>Nanite-style hierarchical traversal: multiple passes (persistent threads, prefix sum,
 *       compaction), multiple intermediate buffers</li>
 *   <li>Indirect dispatch: group counts read from a GPU buffer rather than known at record time</li>
 *   <li>Task/mesh shader amplification: no compute at all, just pipeline configuration</li>
 * </ul>
 *
 * <p>What all GPU LOD paths share is the output contract: after the dispatch completes, there
 * is an argument buffer containing draw commands and a count buffer containing the draw count.
 * The consumer issues {@code vkCmdDrawIndexedIndirectCount} (or equivalent) against these.
 *
 * <p>Implementations must be recordable by any command buffer recorder that understands the
 * {@link Recorder} functional interface. This keeps the dispatch description decoupled from
 * any specific command buffer abstraction.
 *
 * @see LodSelection.Indirect
 */
public interface DispatchDescription {

    /**
     * @return the buffer where draw arguments are written by the GPU work. The consumer binds
     * this as the indirect argument source for the draw call.
     */
    IBuffer argBuffer();

    /**
     * @return the buffer where the draw count is written (single uint32). The consumer binds
     * this as the count source for {@code vkCmdDrawIndexedIndirectCount}.
     */
    IBuffer countBuffer();

    /**
     * @return byte offset into {@link #countBuffer()} where the count uint32 lives
     */
    long countBufferOffset();

    /**
     * @return maximum number of draws the arg buffer can hold. Caps the indirect count to
     * prevent buffer overrun regardless of what the GPU writes.
     */
    int maxDrawCount();

    /**
     * @return the stride in bytes between consecutive draw commands in the arg buffer.
     * Typically 20 (VkDrawIndexedIndirectCommand) or 16 (VkDrawIndirectCommand).
     */
    int argStride();

    /**
     * @return true if the draw commands in the arg buffer are indexed (VkDrawIndexedIndirectCommand).
     * False means VkDrawIndirectCommand layout.
     */
    boolean indexed();

    /**
     * Records the GPU work that produces the selection into a command buffer via the provided
     * recorder. The implementation calls recorder methods to bind pipelines, descriptor sets,
     * push constants, dispatch compute, insert barriers, etc.
     *
     * <p>This is the extension point where paradigm-specific dispatch logic lives. A simple
     * cull records one dispatch. A hierarchical traversal records multiple passes with
     * barriers between them.
     *
     * @param recorder the abstraction through which commands are recorded
     */
    void record(Recorder recorder);

    // -------------------------------------------------------------------------
    // Recorder interface
    // -------------------------------------------------------------------------

    /**
     * Functional interface for recording GPU commands. Implementations are provided by the
     * consumer (renderer, frame graph executor, etc.) and translate these calls into actual
     * Vulkan commands.
     *
     * <p>This keeps DispatchDescription decoupled from VkCommandBuffer and from the frame
     * graph. The same description can be recorded by a raw command buffer, by a graph node,
     * or by a test harness.
     */
    interface Recorder {

        /**
         * Binds a compute pipeline.
         *
         * @param pipelineHandle the VkPipeline handle (MemorySegment)
         */
        void bindPipeline(java.lang.foreign.MemorySegment pipelineHandle);

        /**
         * Binds a descriptor set for compute.
         *
         * @param pipelineLayoutHandle the VkPipelineLayout handle
         * @param set                  set index
         * @param descriptorSetHandle  the VkDescriptorSet handle
         */
        void bindDescriptorSet(java.lang.foreign.MemorySegment pipelineLayoutHandle,
                               int set,
                               java.lang.foreign.MemorySegment descriptorSetHandle);

        /**
         * Pushes constants.
         *
         * @param pipelineLayoutHandle the VkPipelineLayout handle
         * @param stageFlags           VkShaderStageFlags
         * @param offset               byte offset
         * @param data                 the constant data segment
         */
        void pushConstants(java.lang.foreign.MemorySegment pipelineLayoutHandle,
                           int stageFlags, int offset, java.lang.foreign.MemorySegment data);

        /**
         * Dispatches a compute workgroup.
         */
        void dispatch(int groupCountX, int groupCountY, int groupCountZ);

        /**
         * Dispatches compute with group counts read from a buffer (indirect dispatch).
         *
         * @param buffer the buffer containing VkDispatchIndirectCommand
         * @param offset byte offset into the buffer
         */
        void dispatchIndirect(IBuffer buffer, long offset);

        /**
         * Inserts a compute-to-compute memory barrier (between passes of a multi-pass dispatch).
         *
         * @param srcAccess source access flags
         * @param dstAccess destination access flags
         */
        void barrier(int srcAccess, int dstAccess);
    }
}
