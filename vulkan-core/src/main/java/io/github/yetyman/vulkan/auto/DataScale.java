package io.github.yetyman.vulkan.auto;

/**
 * Scale of data size for buffer allocation.
 */
public enum DataScale {
    /** Trivial size (less than 1KB) - typically uniform buffers */
    TRIVIAL(1024),
    
    /** Small size (less than 1MB) - dynamic geometry, instance data */
    SMALL(1024 * 1024),
    
    /** Medium size (less than 1GB) - large meshes, textures */
    MEDIUM(1024L * 1024 * 1024),
    
    /** Large size (greater than 1GB) - massive datasets */
    LARGE(Long.MAX_VALUE);
    
    private final long maxBytes;
    
    DataScale(long maxBytes) {
        this.maxBytes = maxBytes;
    }
    
    public long maxBytes() {
        return maxBytes;
    }
    
    /** Returns a representative size in bytes for this scale */
    public long getBytes() {
        return switch (this) {
            case TRIVIAL -> 512; // 512 bytes
            case SMALL -> 64 * 1024; // 64KB
            case MEDIUM -> 16 * 1024 * 1024; // 16MB
            case LARGE -> 256 * 1024 * 1024; // 256MB
        };
    }
    
    /** Determines the scale category for a given size in bytes */
    public static DataScale fromSize(long bytes) {
        if (bytes < TRIVIAL.maxBytes) return TRIVIAL;
        if (bytes < SMALL.maxBytes) return SMALL;
        if (bytes < MEDIUM.maxBytes) return MEDIUM;
        return LARGE;
    }
}
