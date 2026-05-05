package io.github.yetyman.structures.spatial;

/**
 * Simple camera with view and projection matrices (column-major, Vulkan NDC).
 */
public class Camera {
    private float[] position = {0.0f, 0.0f, 5.0f};
    private float[] target   = {0.0f, 0.0f, 0.0f};
    private float[] up       = {0.0f, 1.0f, 0.0f};

    private float fov         = 45.0f;
    private float aspectRatio = 800.0f / 600.0f;
    private float nearPlane   = 0.1f;
    private float farPlane    = 1000.0f;

    private final float[] viewMatrix     = new float[16];
    private final float[] projMatrix     = new float[16];
    private final float[] viewProjMatrix = new float[16];
    private boolean dirty = true;

    private float[] velocity      = {0.0f, 0.0f, 0.0f};
    private float[] inputVelocity = {0.0f, 0.0f, 0.0f};
    private static final float SMOOTHING = 0.2f;

    public void setPosition(float x, float y, float z) {
        position[0] = x; position[1] = y; position[2] = z;
        dirty = true;
    }

    public void move(float dx, float dy, float dz) {
        inputVelocity[0] = dx; inputVelocity[1] = dy; inputVelocity[2] = dz;
    }

    public void stopX() { inputVelocity[0] = 0; }
    public void stopY() { inputVelocity[1] = 0; }
    public void stopZ() { inputVelocity[2] = 0; }

    public void update() {
        velocity[0] += (inputVelocity[0] - velocity[0]) * SMOOTHING;
        velocity[1] += (inputVelocity[1] - velocity[1]) * SMOOTHING;
        velocity[2] += (inputVelocity[2] - velocity[2]) * SMOOTHING;
        position[0] += velocity[0];
        position[1] += velocity[1];
        position[2] += velocity[2];
        dirty = true;
    }

    public void moveForward(float amount) {
        float fx = target[0] - position[0], fy = target[1] - position[1], fz = target[2] - position[2];
        float len = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        move(fx / len * amount, fy / len * amount, fz / len * amount);
    }

    public void moveRight(float amount) {
        float fx = target[0] - position[0], fy = target[1] - position[1], fz = target[2] - position[2];
        float flen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= flen; fy /= flen; fz /= flen;
        float rx = up[1] * fz - up[2] * fy, ry = up[2] * fx - up[0] * fz, rz = up[0] * fy - up[1] * fx;
        float rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        move(rx / rlen * amount, ry / rlen * amount, rz / rlen * amount);
    }

    public void setTarget(float x, float y, float z) {
        target[0] = x; target[1] = y; target[2] = z;
        dirty = true;
    }

    public void setAspectRatio(float aspect) { aspectRatio = aspect; dirty = true; }
    public void setFov(float fov)            { this.fov = fov;       dirty = true; }
    public void setNearPlane(float near)     { nearPlane = near;     dirty = true; }
    public void setFarPlane(float far)       { farPlane = far;       dirty = true; }

    public float[] getPosition() { return position.clone(); }

    public float[] getViewProjectionMatrix() {
        if (dirty) { updateMatrices(); dirty = false; }
        return viewProjMatrix;
    }

    public float[] getViewMatrix() {
        if (dirty) { updateMatrices(); dirty = false; }
        return viewMatrix;
    }

    public float[] getProjectionMatrix() {
        if (dirty) { updateMatrices(); dirty = false; }
        return projMatrix;
    }

    private void updateMatrices() {
        updateViewMatrix();
        updateProjectionMatrix();
        multiplyMatrices(projMatrix, viewMatrix, viewProjMatrix);
    }

    private void updateViewMatrix() {
        float fx = target[0] - position[0], fy = target[1] - position[1], fz = target[2] - position[2];
        float flen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= flen; fy /= flen; fz /= flen;

        float rx = up[1] * fz - up[2] * fy, ry = up[2] * fx - up[0] * fz, rz = up[0] * fy - up[1] * fx;
        float rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rlen; ry /= rlen; rz /= rlen;

        float ux = fy * rz - fz * ry, uy = fz * rx - fx * rz, uz = fx * ry - fy * rx;

        viewMatrix[0] = rx;  viewMatrix[1] = ry;  viewMatrix[2] = rz;  viewMatrix[3] = 0;
        viewMatrix[4] = ux;  viewMatrix[5] = uy;  viewMatrix[6] = uz;  viewMatrix[7] = 0;
        viewMatrix[8] = -fx; viewMatrix[9] = -fy; viewMatrix[10] = -fz; viewMatrix[11] = 0;
        viewMatrix[12] = -(rx * position[0] + ry * position[1] + rz * position[2]);
        viewMatrix[13] = -(ux * position[0] + uy * position[1] + uz * position[2]);
        viewMatrix[14] =   fx * position[0] + fy * position[1] + fz * position[2];
        viewMatrix[15] = 1;
    }

    private void updateProjectionMatrix() {
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov) / 2.0);
        for (int i = 0; i < 16; i++) projMatrix[i] = 0;
        projMatrix[0]  = 1.0f / (aspectRatio * tanHalfFov);
        projMatrix[5]  = 1.0f / tanHalfFov;
        projMatrix[10] = farPlane / (nearPlane - farPlane);
        projMatrix[11] = -1.0f;
        projMatrix[14] = -(farPlane * nearPlane) / (farPlane - nearPlane);
    }

    private void multiplyMatrices(float[] a, float[] b, float[] result) {
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++)
                result[col * 4 + row] =
                        a[0 * 4 + row] * b[col * 4 + 0] +
                        a[1 * 4 + row] * b[col * 4 + 1] +
                        a[2 * 4 + row] * b[col * 4 + 2] +
                        a[3 * 4 + row] * b[col * 4 + 3];
    }
}
