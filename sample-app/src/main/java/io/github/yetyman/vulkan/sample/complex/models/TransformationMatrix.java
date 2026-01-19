package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.util.VkDataCopy;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Wrapper for 4x4 transformation matrix (column-major, Vulkan standard)
 */
public class TransformationMatrix {
    private final float[] position = new float[3];
    private final float[] scale = {1.0f, 1.0f, 1.0f};
    private float rotation = 0.0f;
    
    private final float[] matrix = new float[16];
    private boolean dirty = true;
    
    public TransformationMatrix() {
        updateMatrix();
    }
    
    public TransformationMatrix(float[] pos, float[] scl, float rot) {
        System.arraycopy(pos, 0, position, 0, 3);
        System.arraycopy(scl, 0, scale, 0, 3);
        rotation = rot;
        updateMatrix();
    }
    
    public void setPosition(float x, float y, float z) {
        position[0] = x;
        position[1] = y;
        position[2] = z;
        dirty = true;
        updateMatrix();
    }
    
    public void setScale(float x, float y, float z) {
        scale[0] = x;
        scale[1] = y;
        scale[2] = z;
        dirty = true;
        updateMatrix(); // Force immediate update
    }
    
    public void setRotation(float rot) {
        rotation = rot;
        dirty = true;
    }
    
    public float[] getPosition() { return position.clone(); }
    public float[] getScale() { return scale.clone(); }
    public float getRotation() { return rotation; }
    
    public float[] getMatrix() {
        if (dirty) {
            updateMatrix();
            dirty = false;
        }
        return matrix;
    }
    
    public void copyMatrixTo(MemorySegment target, int offset) {
        if (dirty) {
            updateMatrix();
            dirty = false;
        }
        VkDataCopy.copyFloatArrayTo(matrix, target.asSlice(offset * Float.BYTES));
    }
    
    private void updateMatrix() {
        // Clear matrix
        for (int i = 0; i < 16; i++) matrix[i] = 0.0f;
        
        // Rotation (simplified - Z-axis only)
        float cos = (float)Math.cos(rotation);
        float sin = (float)Math.sin(rotation);
        
        // Column-major layout for GLSL: each vec4 attribute is one COLUMN
        // Column 0 (scale.x * rotation)
        matrix[0] = scale[0] * cos;   // row 0
        matrix[1] = scale[0] * sin;   // row 1
        matrix[2] = 0.0f;             // row 2
        matrix[3] = 0.0f;             // row 3
        
        // Column 1 (scale.y * rotation)
        matrix[4] = scale[1] * -sin;  // row 0
        matrix[5] = scale[1] * cos;   // row 1
        matrix[6] = 0.0f;             // row 2
        matrix[7] = 0.0f;             // row 3
        
        // Column 2 (scale.z)
        matrix[8] = 0.0f;             // row 0
        matrix[9] = 0.0f;             // row 1
        matrix[10] = scale[2];        // row 2
        matrix[11] = 0.0f;            // row 3
        
        // Column 3 (translation)
        matrix[12] = position[0];     // row 0
        matrix[13] = position[1];     // row 1
        matrix[14] = position[2];     // row 2
        matrix[15] = 1.0f;            // row 3
    }
}