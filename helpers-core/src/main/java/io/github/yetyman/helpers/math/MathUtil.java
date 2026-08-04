package io.github.yetyman.helpers.math;

/**
 * Constants and scalar utility methods for the math library.
 */
public final class MathUtil {

    private MathUtil() {}

    public static final float EPSILON = 1e-6f;
    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = PI * 2f;
    public static final float HALF_PI = PI * 0.5f;
    public static final float DEG_TO_RAD = PI / 180f;
    public static final float RAD_TO_DEG = 180f / PI;

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static boolean epsilonEquals(float a, float b) {
        return Math.abs(a - b) <= EPSILON;
    }

    public static boolean epsilonEquals(float a, float b, float tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    public static float toRadians(float degrees) {
        return degrees * DEG_TO_RAD;
    }

    public static float toDegrees(float radians) {
        return radians * RAD_TO_DEG;
    }

    public static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float inverseSqrt(float x) {
        return 1f / (float) Math.sqrt(x);
    }

    public static float sign(float x) {
        if (x > 0f) return 1f;
        if (x < 0f) return -1f;
        return 0f;
    }

    public static float step(float edge, float x) {
        return x < edge ? 0f : 1f;
    }

    public static float min3(float a, float b, float c) {
        return Math.min(a, Math.min(b, c));
    }

    public static float max3(float a, float b, float c) {
        return Math.max(a, Math.max(b, c));
    }
}
