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
    }
    
    public void setScale(float x, float y, float z) {
        scale[0] = x;
        scale[1] = y;
        scale[2] = z;
        dirty = true;
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
        
        // Column-major layout: Scale + Rotation
        matrix[0] = scale[0] * cos;   // col0, row0
        matrix[1] = scale[0] * sin;   // col0, row1
        matrix[4] = scale[1] * -sin;  // col1, row0
        matrix[5] = scale[1] * cos;   // col1, row1
        matrix[10] = scale[2];        // col2, row2
        matrix[15] = 1.0f;            // col3, row3
        
        // Translation (column 3)
        matrix[12] = position[0];     // col3, row0
        matrix[13] = position[1];     // col3, row1
        matrix[14] = position[2];     // col3, row2
    }
}