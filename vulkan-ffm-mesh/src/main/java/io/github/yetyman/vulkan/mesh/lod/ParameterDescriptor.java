package io.github.yetyman.vulkan.mesh.lod;

/**
 * Describes one continuous parameter that controls a parametric representation's detail level.
 *
 * <p>This is metadata only: it tells a selector what parameters exist, their valid range, and
 * what they mean at a high level. The actual value is produced by the selector and consumed by
 * the renderer pipeline. This module does not interpret parameter values.
 *
 * @param name         unique name within the parametric representation (e.g. "tessellationFactor",
 *                     "displacementAmplitude", "sdfStepCount")
 * @param min          minimum valid value (inclusive)
 * @param max          maximum valid value (inclusive)
 * @param defaultValue the value that produces "normal" detail (used when no selection has run yet)
 * @param higherMeansMore true if increasing this parameter increases detail/quality (tessellation
 *                        factor). False if increasing decreases detail (e.g. simplification ratio).
 */
public record ParameterDescriptor(
        String name,
        float min,
        float max,
        float defaultValue,
        boolean higherMeansMore
) {
    public ParameterDescriptor {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (min > max) throw new IllegalArgumentException("min must be <= max");
        if (defaultValue < min || defaultValue > max)
            throw new IllegalArgumentException("defaultValue must be within [min, max]");
    }

    /**
     * Convenience factory for the common case where higher = more detail.
     */
    public static ParameterDescriptor increasing(String name, float min, float max, float defaultValue) {
        return new ParameterDescriptor(name, min, max, defaultValue, true);
    }

    /**
     * Convenience factory for the case where higher = less detail.
     */
    public static ParameterDescriptor decreasing(String name, float min, float max, float defaultValue) {
        return new ParameterDescriptor(name, min, max, defaultValue, false);
    }
}
