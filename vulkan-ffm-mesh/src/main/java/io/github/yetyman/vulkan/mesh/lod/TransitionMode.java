package io.github.yetyman.vulkan.mesh.lod;

/**
 * Describes how the visual transition between two LOD representations should appear.
 *
 * <p>The mesh module tracks transition state ({@link TransitionState}) and reports it through
 * {@link LodSelection}. The module does NOT implement the visual effect; that is the renderer's
 * responsibility. Different renderers interpret the same transition mode differently:
 *
 * <ul>
 *   <li>A forward renderer might implement {@link Dither} as screen-space alpha noise</li>
 *   <li>A deferred renderer might implement it as stencil-based stipple</li>
 *   <li>A mesh-shader renderer might implement {@link Geomorph} as vertex interpolation</li>
 * </ul>
 *
 * <p>The separation ensures the mesh module provides the data (what transition, how far along)
 * without dictating how it looks. This is the same separation as GeometryBinding (provides data)
 * vs. command recording (uses data).
 *
 * <p>Sealed so switch expressions are exhaustive.
 */
public sealed interface TransitionMode
        permits TransitionMode.HardCut,
                TransitionMode.Dither,
                TransitionMode.Geomorph,
                TransitionMode.CrossFade,
                TransitionMode.Continuous {

    /**
     * Instant swap. No blending, no temporal interpolation. The new representation replaces
     * the old in a single frame. Simplest, cheapest, but can produce visible pops.
     */
    record HardCut() implements TransitionMode {}

    /**
     * Dither-based transition over a number of frames. Both representations are drawn
     * simultaneously with complementary dither patterns that blend over time.
     *
     * <p>Cheap on bandwidth (no alpha blend), works in deferred renderers, but introduces
     * temporal noise during the transition.
     *
     * @param durationSeconds how long the dither blending lasts
     */
    record Dither(float durationSeconds) implements TransitionMode {
        public Dither {
            if (durationSeconds <= 0) throw new IllegalArgumentException("duration must be positive");
        }
    }

    /**
     * Geomorphing: vertex positions and attributes are interpolated between the outgoing and
     * incoming representations. Requires that both representations share the same vertex
     * topology (or a mapping between them).
     *
     * <p>Smooth, no popping, but requires shader support and compatible mesh topologies.
     * Works well for terrain LOD and cascaded simplification chains.
     *
     * @param durationSeconds how long the interpolation takes
     */
    record Geomorph(float durationSeconds) implements TransitionMode {
        public Geomorph {
            if (durationSeconds <= 0) throw new IllegalArgumentException("duration must be positive");
        }
    }

    /**
     * Alpha cross-fade: both representations are drawn with complementary alpha values.
     * Straightforward but doubles draw cost during transition and requires sorting/OIT.
     *
     * @param durationSeconds how long the cross-fade lasts
     */
    record CrossFade(float durationSeconds) implements TransitionMode {
        public CrossFade {
            if (durationSeconds <= 0) throw new IllegalArgumentException("duration must be positive");
        }
    }

    /**
     * Continuous refinement: no discrete transition occurs because the representation changes
     * continuously (progressive mesh, tessellation). The "transition" is the refinement itself.
     * No blending factor is needed; the selector directly controls the detail level.
     */
    record Continuous() implements TransitionMode {}

    /** Convenience singleton for the most common case. */
    HardCut HARD_CUT = new HardCut();

    /** Convenience singleton for continuous (progressive/parametric). */
    Continuous CONTINUOUS = new Continuous();
}
