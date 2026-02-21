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
