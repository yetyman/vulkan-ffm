package io.github.yetyman.structures.spatial;

/**
 * CPU-side frustum culler using plane equations extracted from a view-projection matrix.
 */
public class FrustumCuller {
    private final float[] planes = new float[24]; // 6 planes × 4 coefficients (a,b,c,d)

    /**
     * Updates frustum planes from a column-major view-projection matrix.
     */
    public void updateFromMatrix(float[] viewProj) {
        // Left:   row4 + row1
        planes[0]  = viewProj[3]  + viewProj[0];  planes[1]  = viewProj[7]  + viewProj[4];
        planes[2]  = viewProj[11] + viewProj[8];   planes[3]  = viewProj[15] + viewProj[12];
        // Right:  row4 - row1
        planes[4]  = viewProj[3]  - viewProj[0];  planes[5]  = viewProj[7]  - viewProj[4];
        planes[6]  = viewProj[11] - viewProj[8];   planes[7]  = viewProj[15] - viewProj[12];
        // Bottom: row4 + row2
        planes[8]  = viewProj[3]  + viewProj[1];  planes[9]  = viewProj[7]  + viewProj[5];
        planes[10] = viewProj[11] + viewProj[9];   planes[11] = viewProj[15] + viewProj[13];
        // Top:    row4 - row2
        planes[12] = viewProj[3]  - viewProj[1];  planes[13] = viewProj[7]  - viewProj[5];
        planes[14] = viewProj[11] - viewProj[9];   planes[15] = viewProj[15] - viewProj[13];
        // Near:   row4 + row3
        planes[16] = viewProj[3]  + viewProj[2];  planes[17] = viewProj[7]  + viewProj[6];
        planes[18] = viewProj[11] + viewProj[10];  planes[19] = viewProj[15] + viewProj[14];
        // Far:    row4 - row3
        planes[20] = viewProj[3]  - viewProj[2];  planes[21] = viewProj[7]  - viewProj[6];
        planes[22] = viewProj[11] - viewProj[10];  planes[23] = viewProj[15] - viewProj[14];

        for (int i = 0; i < 6; i++) {
            int o = i * 4;
            float len = (float) Math.sqrt(planes[o] * planes[o] + planes[o+1] * planes[o+1] + planes[o+2] * planes[o+2]);
            if (len > 0.0001f) { planes[o] /= len; planes[o+1] /= len; planes[o+2] /= len; planes[o+3] /= len; }
        }
    }

    /** @return true if the sphere is inside or intersecting the frustum. */
    public boolean testSphere(float cx, float cy, float cz, float radius) {
        for (int i = 0; i < 6; i++) {
            int o = i * 4;
            if (planes[o] * cx + planes[o+1] * cy + planes[o+2] * cz + planes[o+3] < -radius) return false;
        }
        return true;
    }

    /** @return true if the AABB is inside or intersecting the frustum. */
    public boolean testAABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            int o = i * 4;
            float a = planes[o], b = planes[o+1], c = planes[o+2], d = planes[o+3];
            if (a * (a > 0 ? maxX : minX) + b * (b > 0 ? maxY : minY) + c * (c > 0 ? maxZ : minZ) + d < 0) return false;
        }
        return true;
    }
}
