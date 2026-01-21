package io.github.yetyman.vulkan.sample.complex.culling;

/**
 * Simple camera with view and projection matrices (column-major, Vulkan standard)
 */
public class Camera {
    private float[] position = {0.0f, 0.0f, 5.0f};
    private float[] target = {0.0f, 0.0f, 0.0f};
    private float[] up = {0.0f, 1.0f, 0.0f};
    
    private float fov = 45.0f;
    private float aspectRatio = 800.0f / 600.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 100.0f;
    
    private final float[] viewMatrix = new float[16];
    private final float[] projMatrix = new float[16];
    private final float[] viewProjMatrix = new float[16];
    private boolean dirty = true;
    
    public void setPosition(float x, float y, float z) {
        position[0] = x;
        position[1] = y;
        position[2] = z;
        dirty = true;
    }
    
    public void move(float dx, float dy, float dz) {
        position[0] += dx;
        position[1] += dy;
        position[2] += dz;
        dirty = true;
    }
    
    public void moveForward(float amount) {
        float fx = target[0] - position[0];
        float fy = target[1] - position[1];
        float fz = target[2] - position[2];
        float len = (float)Math.sqrt(fx*fx + fy*fy + fz*fz);
        fx /= len; fy /= len; fz /= len;
        position[0] += fx * amount;
        position[1] += fy * amount;
        position[2] += fz * amount;
        dirty = true;
    }
    
    public void moveRight(float amount) {
        float fx = target[0] - position[0];
        float fy = target[1] - position[1];
        float fz = target[2] - position[2];
        float flen = (float)Math.sqrt(fx*fx + fy*fy + fz*fz);
        fx /= flen; fy /= flen; fz /= flen;
        
        float rx = up[1]*fz - up[2]*fy;
        float ry = up[2]*fx - up[0]*fz;
        float rz = up[0]*fy - up[1]*fx;
        float rlen = (float)Math.sqrt(rx*rx + ry*ry + rz*rz);
        rx /= rlen; ry /= rlen; rz /= rlen;
        
        position[0] += rx * amount;
        position[1] += ry * amount;
        position[2] += rz * amount;
        dirty = true;
    }
    
    public void setTarget(float x, float y, float z) {
        target[0] = x;
        target[1] = y;
        target[2] = z;
        dirty = true;
    }
    
    public void setAspectRatio(float aspect) {
        aspectRatio = aspect;
        dirty = true;
    }
    
    public float[] getPosition() {
        return position.clone();
    }
    
    public float[] getViewProjectionMatrix() {
        if (dirty) {
            updateMatrices();
            dirty = false;
        }
        return viewProjMatrix;
    }
    
    private void updateMatrices() {
        updateViewMatrix();
        updateProjectionMatrix();
        multiplyMatrices(projMatrix, viewMatrix, viewProjMatrix);
    }
    
    private void updateViewMatrix() {
        // Calculate forward, right, up vectors
        float fx = target[0] - position[0];
        float fy = target[1] - position[1];
        float fz = target[2] - position[2];
        float flen = (float)Math.sqrt(fx*fx + fy*fy + fz*fz);
        fx /= flen; fy /= flen; fz /= flen;
        
        float rx = up[1]*fz - up[2]*fy;
        float ry = up[2]*fx - up[0]*fz;
        float rz = up[0]*fy - up[1]*fx;
        float rlen = (float)Math.sqrt(rx*rx + ry*ry + rz*rz);
        rx /= rlen; ry /= rlen; rz /= rlen;
        
        float ux = fy*rz - fz*ry;
        float uy = fz*rx - fx*rz;
        float uz = fx*ry - fy*rx;
        
        // Column-major layout
        viewMatrix[0] = rx;
        viewMatrix[1] = ry;
        viewMatrix[2] = rz;
        viewMatrix[3] = 0;
        
        viewMatrix[4] = ux;
        viewMatrix[5] = uy;
        viewMatrix[6] = uz;
        viewMatrix[7] = 0;
        
        viewMatrix[8] = -fx;
        viewMatrix[9] = -fy;
        viewMatrix[10] = -fz;
        viewMatrix[11] = 0;
        
        viewMatrix[12] = -(rx*position[0] + ry*position[1] + rz*position[2]);
        viewMatrix[13] = -(ux*position[0] + uy*position[1] + uz*position[2]);
        viewMatrix[14] = fx*position[0] + fy*position[1] + fz*position[2];
        viewMatrix[15] = 1;
    }
    
    private void updateProjectionMatrix() {
        float tanHalfFov = (float)Math.tan(Math.toRadians(fov) / 2.0f);
        
        for (int i = 0; i < 16; i++) projMatrix[i] = 0;
        
        projMatrix[0] = 1.0f / (aspectRatio * tanHalfFov);
        projMatrix[5] = 1.0f / tanHalfFov;
        projMatrix[10] = farPlane / (nearPlane - farPlane);
        projMatrix[11] = -1.0f;
        projMatrix[14] = -(farPlane * nearPlane) / (farPlane - nearPlane);
    }
    
    private void multiplyMatrices(float[] a, float[] b, float[] result) {
        // Column-major matrix multiplication
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                result[col * 4 + row] = 
                    a[0 * 4 + row] * b[col * 4 + 0] +
                    a[1 * 4 + row] * b[col * 4 + 1] +
                    a[2 * 4 + row] * b[col * 4 + 2] +
                    a[3 * 4 + row] * b[col * 4 + 3];
            }
        }
    }
}
