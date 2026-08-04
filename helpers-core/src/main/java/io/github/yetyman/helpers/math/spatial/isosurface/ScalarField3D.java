package io.github.yetyman.helpers.math.spatial.isosurface;

/**
 * A 3D scalar field function. Returns a density value at any point.
 * Positive = inside the surface, negative = outside (convention).
 */
@FunctionalInterface
public interface ScalarField3D {
    float sample(float x, float y, float z);
}
