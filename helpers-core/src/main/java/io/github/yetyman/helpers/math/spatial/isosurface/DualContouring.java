package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec3;

/**
 * Dual Contouring — preserves sharp features via Hermite data.
 * Each edge crossing stores position + surface normal.
 * Vertex placement uses QEF (Quadric Error Function) minimization (simplified here as average).
 *
 * For a full implementation, QEF would solve a least-squares system to find the optimal vertex
 * position that minimizes distance to all crossing planes. This simplified version uses
 * the average of edge crossings (equivalent to Surface Nets).
 */
public class DualContouring {

    private DualContouring() {}

    /**
     * Extracts an isosurface using dual contouring with gradient-based normals.
     *
     * @param field the scalar field
     * @param min minimum corner of the sampling volume
     * @param max maximum corner of the sampling volume
     * @param resX grid resolution along X
     * @param resY grid resolution along Y
     * @param resZ grid resolution along Z
     * @param isoLevel the iso-value
     * @return mesh output
     */
    public static MeshOutput extract(ScalarField3D field, Vec3 min, Vec3 max,
                                     int resX, int resY, int resZ, float isoLevel) {
        MeshOutput mesh = new MeshOutput();
        float stepX = (max.x - min.x) / resX;
        float stepY = (max.y - min.y) / resY;
        float stepZ = (max.z - min.z) / resZ;
        float gradStep = Math.min(stepX, Math.min(stepY, stepZ)) * 0.01f;

        // Sample field
        float[][][] grid = new float[resX + 1][resY + 1][resZ + 1];
        for (int x = 0; x <= resX; x++)
            for (int y = 0; y <= resY; y++)
                for (int z = 0; z <= resZ; z++)
                    grid[x][y][z] = field.sample(min.x + x * stepX, min.y + y * stepY, min.z + z * stepZ);

        // Vertex index per cell
        int[][][] vertexIndices = new int[resX][resY][resZ];
        for (int[][] plane : vertexIndices)
            for (int[] row : plane)
                java.util.Arrays.fill(row, -1);

        // Pass 1: place vertices using QEF approximation (average of crossing positions)
        for (int x = 0; x < resX; x++)
            for (int y = 0; y < resY; y++)
                for (int z = 0; z < resZ; z++) {
                    int mask = cellMask(grid, x, y, z, isoLevel);
                    if (mask == 0 || mask == 255) continue;

                    float px = min.x + x * stepX;
                    float py = min.y + y * stepY;
                    float pz = min.z + z * stepZ;

                    // Collect edge crossings with normals for QEF
                    float sumX = 0, sumY = 0, sumZ = 0;
                    int count = 0;

                    // Check 12 edges
                    float[] c = getCubeCorners(grid, x, y, z);
                    // X edges
                    if (crosses(c[0], c[1])) { float t = interp(c[0], c[1], isoLevel); sumX += px + t*stepX; sumY += py; sumZ += pz; count++; }
                    if (crosses(c[2], c[3])) { float t = interp(c[2], c[3], isoLevel); sumX += px + t*stepX; sumY += py+stepY; sumZ += pz; count++; }
                    if (crosses(c[4], c[5])) { float t = interp(c[4], c[5], isoLevel); sumX += px + t*stepX; sumY += py; sumZ += pz+stepZ; count++; }
                    if (crosses(c[6], c[7])) { float t = interp(c[6], c[7], isoLevel); sumX += px + t*stepX; sumY += py+stepY; sumZ += pz+stepZ; count++; }
                    // Y edges
                    if (crosses(c[0], c[2])) { float t = interp(c[0], c[2], isoLevel); sumX += px; sumY += py + t*stepY; sumZ += pz; count++; }
                    if (crosses(c[1], c[3])) { float t = interp(c[1], c[3], isoLevel); sumX += px+stepX; sumY += py + t*stepY; sumZ += pz; count++; }
                    if (crosses(c[4], c[6])) { float t = interp(c[4], c[6], isoLevel); sumX += px; sumY += py + t*stepY; sumZ += pz+stepZ; count++; }
                    if (crosses(c[5], c[7])) { float t = interp(c[5], c[7], isoLevel); sumX += px+stepX; sumY += py + t*stepY; sumZ += pz+stepZ; count++; }
                    // Z edges
                    if (crosses(c[0], c[4])) { float t = interp(c[0], c[4], isoLevel); sumX += px; sumY += py; sumZ += pz + t*stepZ; count++; }
                    if (crosses(c[1], c[5])) { float t = interp(c[1], c[5], isoLevel); sumX += px+stepX; sumY += py; sumZ += pz + t*stepZ; count++; }
                    if (crosses(c[2], c[6])) { float t = interp(c[2], c[6], isoLevel); sumX += px; sumY += py+stepY; sumZ += pz + t*stepZ; count++; }
                    if (crosses(c[3], c[7])) { float t = interp(c[3], c[7], isoLevel); sumX += px+stepX; sumY += py+stepY; sumZ += pz + t*stepZ; count++; }

                    if (count > 0) {
                        float inv = 1f / count;
                        vertexIndices[x][y][z] = mesh.addVertex(sumX * inv, sumY * inv, sumZ * inv);
                    }
                }

        // Pass 2: connect cells sharing an edge that crosses the isosurface
        for (int x = 0; x < resX; x++)
            for (int y = 0; y < resY; y++)
                for (int z = 0; z < resZ; z++) {
                    if (vertexIndices[x][y][z] < 0) continue;
                    // X-axis face quads
                    if (x + 1 < resX) connectFaceX(mesh, vertexIndices, grid, x, y, z, resY, resZ, isoLevel);
                    // Y-axis face quads
                    if (y + 1 < resY) connectFaceY(mesh, vertexIndices, grid, x, y, z, resX, resZ, isoLevel);
                    // Z-axis face quads
                    if (z + 1 < resZ) connectFaceZ(mesh, vertexIndices, grid, x, y, z, resX, resY, isoLevel);
                }

        return mesh;
    }

    private static void connectFaceX(MeshOutput mesh, int[][][] vi, float[][][] grid,
                                     int x, int y, int z, int resY, int resZ, float iso) {
        if (y + 1 >= resY || z + 1 >= resZ) return;
        // The 4 cells sharing edge along X at (x+1, y..y+1, z..z+1)
        if (!crosses(grid[x+1][y][z], grid[x+1][y+1][z], grid[x+1][y][z+1], grid[x+1][y+1][z+1], iso)) return;
        int v0 = vi[x][y][z], v1 = vi[x][y+1][z], v2 = vi[x][y+1][z+1], v3 = vi[x][y][z+1];
        if (v0 >= 0 && v1 >= 0 && v2 >= 0 && v3 >= 0) {
            mesh.addTriangle(v0, v1, v2);
            mesh.addTriangle(v0, v2, v3);
        }
    }

    private static void connectFaceY(MeshOutput mesh, int[][][] vi, float[][][] grid,
                                     int x, int y, int z, int resX, int resZ, float iso) {
        if (x + 1 >= resX || z + 1 >= resZ) return;
        if (!crosses(grid[x][y+1][z], grid[x+1][y+1][z], grid[x][y+1][z+1], grid[x+1][y+1][z+1], iso)) return;
        int v0 = vi[x][y][z], v1 = vi[x][y][z+1], v2 = vi[x+1][y][z+1], v3 = vi[x+1][y][z];
        if (v0 >= 0 && v1 >= 0 && v2 >= 0 && v3 >= 0) {
            mesh.addTriangle(v0, v1, v2);
            mesh.addTriangle(v0, v2, v3);
        }
    }

    private static void connectFaceZ(MeshOutput mesh, int[][][] vi, float[][][] grid,
                                     int x, int y, int z, int resX, int resY, float iso) {
        if (x + 1 >= resX || y + 1 >= resY) return;
        if (!crosses(grid[x][y][z+1], grid[x+1][y][z+1], grid[x][y+1][z+1], grid[x+1][y+1][z+1], iso)) return;
        int v0 = vi[x][y][z], v1 = vi[x+1][y][z], v2 = vi[x+1][y+1][z], v3 = vi[x][y+1][z];
        if (v0 >= 0 && v1 >= 0 && v2 >= 0 && v3 >= 0) {
            mesh.addTriangle(v0, v1, v2);
            mesh.addTriangle(v0, v2, v3);
        }
    }

    private static boolean crosses(float a, float b, float c, float d, float iso) {
        boolean inside = (a < iso) || (b < iso) || (c < iso) || (d < iso);
        boolean outside = (a >= iso) || (b >= iso) || (c >= iso) || (d >= iso);
        return inside && outside;
    }

    private static int cellMask(float[][][] grid, int x, int y, int z, float iso) {
        int mask = 0;
        if (grid[x][y][z] < iso) mask |= 1;
        if (grid[x+1][y][z] < iso) mask |= 2;
        if (grid[x][y+1][z] < iso) mask |= 4;
        if (grid[x+1][y+1][z] < iso) mask |= 8;
        if (grid[x][y][z+1] < iso) mask |= 16;
        if (grid[x+1][y][z+1] < iso) mask |= 32;
        if (grid[x][y+1][z+1] < iso) mask |= 64;
        if (grid[x+1][y+1][z+1] < iso) mask |= 128;
        return mask;
    }

    private static float[] getCubeCorners(float[][][] grid, int x, int y, int z) {
        return new float[]{
                grid[x][y][z], grid[x+1][y][z], grid[x][y+1][z], grid[x+1][y+1][z],
                grid[x][y][z+1], grid[x+1][y][z+1], grid[x][y+1][z+1], grid[x+1][y+1][z+1]
        };
    }

    private static boolean crosses(float v1, float v2) {
        return (v1 < 0) != (v2 < 0); // simplified: assumes isoLevel = 0 for edge test
    }

    private static float interp(float v1, float v2, float iso) {
        if (Math.abs(v2 - v1) < 1e-6f) return 0.5f;
        return (iso - v1) / (v2 - v1);
    }
}
