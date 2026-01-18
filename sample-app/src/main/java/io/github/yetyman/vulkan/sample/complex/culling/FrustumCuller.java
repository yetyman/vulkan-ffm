package io.github.yetyman.vulkan.sample.complex.culling;

/**
 * CPU-side frustum culling using plane equations
 */
public class FrustumCuller {
    private final float[] planes = new float[24]; // 6 planes * 4 coefficients (a,b,c,d)
    
    /**
     * Update frustum planes from view-projection matrix
     * @param viewProj 16-element view-projection matrix (column-major, Vulkan standard)
     */
    public void updateFromMatrix(float[] viewProj) {
        // Extract 6 frustum planes from VP matrix (column-major layout)
        // Left: row4 + row1
        planes[0] = viewProj[3] + viewProj[0];
        planes[1] = viewProj[7] + viewProj[4];
        planes[2] = viewProj[11] + viewProj[8];
        planes[3] = viewProj[15] + viewProj[12];
        
        // Right: row4 - row1
        planes[4] = viewProj[3] - viewProj[0];
        planes[5] = viewProj[7] - viewProj[4];
        planes[6] = viewProj[11] - viewProj[8];
        planes[7] = viewProj[15] - viewProj[12];
        
        // Bottom: row4 + row2
        planes[8] = viewProj[3] + viewProj[1];
        planes[9] = viewProj[7] + viewProj[5];
        planes[10] = viewProj[11] + viewProj[9];
        planes[11] = viewProj[15] + viewProj[13];
        
        // Top: row4 - row2
        planes[12] = viewProj[3] - viewProj[1];
        planes[13] = viewProj[7] - viewProj[5];
        planes[14] = viewProj[11] - viewProj[9];
        planes[15] = viewProj[15] - viewProj[13];
        
        // Near: row4 + row3
        planes[16] = viewProj[3] + viewProj[2];
        planes[17] = viewProj[7] + viewProj[6];
        planes[18] = viewProj[11] + viewProj[10];
        planes[19] = viewProj[15] + viewProj[14];
        
        // Far: row4 - row3
        planes[20] = viewProj[3] - viewProj[2];
        planes[21] = viewProj[7] - viewProj[6];
        planes[22] = viewProj[11] - viewProj[10];
        planes[23] = viewProj[15] - viewProj[14];
        
        // Normalize planes
        for (int i = 0; i < 6; i++) {
            int offset = i * 4;
            float len = (float)Math.sqrt(planes[offset] * planes[offset] + 
                                        planes[offset+1] * planes[offset+1] + 
                                        planes[offset+2] * planes[offset+2]);
            if (len > 0.0001f) {
                planes[offset] /= len;
                planes[offset+1] /= len;
                planes[offset+2] /= len;
                planes[offset+3] /= len;
            }
        }
    }
    
    /**
     * Test if sphere is inside frustum
     * @param centerX sphere center X
     * @param centerY sphere center Y
     * @param centerZ sphere center Z
     * @param radius sphere radius
     * @return true if visible (inside or intersecting frustum)
     */
    public boolean testSphere(float centerX, float centerY, float centerZ, float radius) {
        for (int i = 0; i < 6; i++) {
            int offset = i * 4;
            float distance = planes[offset] * centerX + 
                           planes[offset+1] * centerY + 
                           planes[offset+2] * centerZ + 
                           planes[offset+3];
            if (distance < -radius) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Test if AABB is inside frustum
     * @param minX AABB min X
     * @param minY AABB min Y
     * @param minZ AABB min Z
     * @param maxX AABB max X
     * @param maxY AABB max Y
     * @param maxZ AABB max Z
     * @return true if visible
     */
    public boolean testAABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            int offset = i * 4;
            float a = planes[offset];
            float b = planes[offset+1];
            float c = planes[offset+2];
            float d = planes[offset+3];
            
            // Find positive vertex (furthest along plane normal)
            float px = a > 0 ? maxX : minX;
            float py = b > 0 ? maxY : minY;
            float pz = c > 0 ? maxZ : minZ;
            
            if (a * px + b * py + c * pz + d < 0) {
                return false;
            }
        }
        return true;
    }
}
