package io.github.yetyman.vulkan.sample.spatial;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.Vec3;

/**
 * Simple orbit camera. Rotates around a center point.
 * Left-drag rotates, scroll zooms.
 */
public class OrbitCamera {

    private final Vec3 center = new Vec3(0, 0, 0);
    private float distance = 20f;
    private float yaw = 0f;   // radians
    private float pitch = 0.4f; // radians
    private float fov = (float) Math.toRadians(60);
    private float aspect = 1f;
    private float near = 0.1f;
    private float far = 500f;

    private final Mat4 viewMatrix = new Mat4();
    private final Mat4 projMatrix = new Mat4();
    private boolean dirty = true;

    public OrbitCamera() {}

    public void setCenter(float x, float y, float z) { center.set(x, y, z); dirty = true; }
    public void setDistance(float d) { distance = Math.max(0.1f, d); dirty = true; }
    public void setAspect(float aspect) { this.aspect = aspect; dirty = true; }

    public void rotate(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch = Math.max(-1.5f, Math.min(1.5f, pitch + deltaPitch));
        dirty = true;
    }

    public void zoom(float delta) {
        distance = Math.max(0.5f, distance - delta);
        dirty = true;
    }

    public void pan(float dx, float dy) {
        // Pan relative to camera right/up
        float cosYaw = (float) Math.cos(yaw), sinYaw = (float) Math.sin(yaw);
        center.x += (-cosYaw * dx) * distance * 0.01f;
        center.z += (-sinYaw * dx) * distance * 0.01f;
        center.y += dy * distance * 0.01f;
        dirty = true;
    }

    public Vec3 eye() {
        float cosPitch = (float) Math.cos(pitch), sinPitch = (float) Math.sin(pitch);
        float cosYaw = (float) Math.cos(yaw), sinYaw = (float) Math.sin(yaw);
        return new Vec3(
                center.x + distance * cosPitch * sinYaw,
                center.y + distance * sinPitch,
                center.z + distance * cosPitch * cosYaw
        );
    }

    public float[] viewArray() {
        recompute();
        return viewMatrix.toArray();
    }

    public float[] projArray() {
        recompute();
        return projMatrix.toArray();
    }

    public Mat4 view() { recompute(); return viewMatrix; }
    public Mat4 proj() { recompute(); return projMatrix; }
    public float distance() { return distance; }
    public Vec3 center() { return center; }

    private void recompute() {
        if (!dirty) return;
        Vec3 eye = eye();
        Mat4 v = Mat4.lookAt(eye, center, Vec3.up());
        viewMatrix.set(v);
        Mat4 p = Mat4.perspective(fov, aspect, near, far);
        projMatrix.set(p);
        dirty = false;
    }
}
