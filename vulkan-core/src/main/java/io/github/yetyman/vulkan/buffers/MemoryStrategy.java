package io.github.yetyman.vulkan.buffers;

/**
 * Memory allocation and access strategy for buffers.
 * Orthogonal to BufferUsage (UBO/VBO/SSBO).
 */
public enum MemoryStrategy {
    /** Persistent CPU-mapped memory (HOST_VISIBLE | HOST_COHERENT) */
    MAPPED,
    
    /** Persistent CPU-mapped with cached reads (HOST_VISIBLE | HOST_CACHED) */
    MAPPED_CACHED,
    
    /** GPU-only memory with staging buffer for uploads (DEVICE_LOCAL) */
    DEVICE_LOCAL,

    /**
     * GPU-only memory with a CPU-side mirror ByteBuffer for zero-cost reads.
     * Writes go to both the device-local buffer (via staging) and the CPU mirror.
     * Only safe when the CPU is the sole writer — GPU writes will not update the mirror.
     * Best for: discrete GPU, rarely written, frequently read on both CPU and GPU.
     */
    DEVICE_LOCAL_MIRRORED,
    
    /** Staging pattern: CPU-mapped staging + device-local backing */
    STAGING,

    /** Resizable BAR: direct CPU map into DEVICE_LOCAL | HOST_VISIBLE VRAM (requires ReBAR/SAM hardware support) */
    REBAR,
    
    /** Ring buffer (N-buffered) wrapping another strategy */
    RING_BUFFER,
    
    /** Sparse virtual memory with on-demand page binding */
    SPARSE,
    
    /** Suballocator: single large buffer with multiple small allocations */
    SUBALLOCATOR
}
