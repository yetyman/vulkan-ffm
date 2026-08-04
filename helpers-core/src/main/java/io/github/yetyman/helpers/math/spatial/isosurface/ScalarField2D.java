package io.github.yetyman.helpers.math.spatial.isosurface;

/**
 * A 2D scalar field function. Returns a density value at any point.
 * Positive = inside the contour, negative = outside (convention).
 */
@FunctionalInterface
public interface ScalarField2D {
    float sample(float x, float y);
}
