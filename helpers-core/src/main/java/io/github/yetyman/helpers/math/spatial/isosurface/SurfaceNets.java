package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec3;

/**
 * Surface Nets — dual contouring variant that produces smoother meshes with fewer triangles.
 * Places one vertex per cell that contains the isosurface, positioned at the average of edge crossings.
 * Connects adjacent cells with quads (split into triangles).
 */
public class SurfaceNets {

    private SurfaceNets() {}

    /**
     * Extracts an isosurface using the surface nets algorithm.
     */
    public static MeshOutput extract(ScalarField3D field, Vec3 min, Vec3 max,
                                     int resX, int resY, int resZ, float isoLevel) {
        MeshOutput mesh = new MeshOutput();
        float stepX = (max.x - min.x) / resX;
        float stepY = (max.y - min.y) / resY;
        float stepZ = (max.z - min.z) / resZ;

        // Sample field
        float[][][] grid = new float[resX + 1][resY + 1][resZ + 1];
        for (int x = 0; x <= resX; x++)
            for (int y = 0; y <= resY; y++)
                for (int z = 0; z <= resZ; z++)
                    grid[x][y][z] = field.sample(min.x + x * stepX, min.y + y * stepY, min.z + z * stepZ);

        // Vertex index per cell (-1 = no vertex)
        int[][][] vertexIndices = new int[resX][resY][resZ];
        for (int[][] plane : vertexIndices)
            for (int[] row : plane)
                java.util.Arrays.fill(row, -1);

        // Pass 1: create vertices at cells containing the surface
        for (int x = 0; x < resX; x++)
            for (int y = 0; y < resY; y++)
                for (int z = 0; z < resZ; z++) {
                    int mask = cellMask(grid, x, y, z, isoLevel);
                    if (mask == 0 || mask == 255) continue;

                    // Average edge crossing positions
                    float avgX = 0, avgY = 0, avgZ = 0;
                    int crossingCount = 0;
                    float px = min.x + x * stepX;
                    float py = min.y + y * stepY;
                    float pz = min.z + z * stepZ;

                    // Check all 12 edges of the cube for crossings
                    float[] corners = getCubeCorners(grid, x, y, z);
                    // X-aligned edges
                    if (crosses(corners[0], corners[1], isoLevel)) { avgX += px + interp(corners[0], corners[1], isoLevel) * stepX; avgY += py; avgZ += pz; crossingCount++; }
                    if (crosses(corners[2], corners[3], isoLevel)) { avgX += px + interp(corners[2], corners[3], isoLevel) * stepX; avgY += py + stepY; avgZ += pz; crossingCount++; }
                    if (crosses(corners[4], corners[5], isoLevel)) { avgX += px + interp(corners[4], corners[5], isoLevel) * stepX; avgY += py; avgZ += pz + stepZ; crossingCount++; }
                    if (crosses(corners[6], corners[7], isoLevel)) { avgX += px + interp(corners[6], corners[7], isoLevel) * stepX; avgY += py + stepY; avgZ += pz + stepZ; crossingCount++; }
                    // Y-aligned edges
                    if (crosses(corners[0], corners[2], isoLevel)) { avgX += px; avgY += py + interp(corners[0], corners[2], isoLevel) * stepY; avgZ += pz; crossingCount++; }
                    if (crosses(corners[1], corners[3], isoLevel)) { avgX += px + stepX; avgY += py + interp(corners[1], corners[3], isoLevel) * stepY; avgZ += pz; crossingCount++; }
                    if (crosses(corners[4], corners[6], isoLevel)) { avgX += px; avgY += py + interp(corners[4], corners[6], isoLevel) * stepY; avgZ += pz + stepZ; crossingCount++; }
                    if (crosses(corners[5], corners[7], isoLevel)) { avgX += px + stepX; avgY += py + interp(corners[5], corners[7], isoLevel) * stepY; avgZ += pz + stepZ; crossingCount++; }
                    // Z-aligned edges
                    if (crosses(corners[0], corners[4], isoLevel)) { avgX += px; avgY += py; avgZ += pz + interp(corners[0], corners[4], isoLevel) * stepZ; crossingCount++; }
                    if (crosses(corners[1], corners[5], isoLevel)) { avgX += px + stepX; avgY += py; avgZ += pz + interp(corners[1], corners[5], isoLevel) * stepZ; crossingCount++; }
                    if (crosses(corners[2], corners[6], isoLevel)) { avgX += px; avgY += py + stepY; avgZ += pz + interp(corners[2], corners[6], isoLevel) * stepZ; crossingCount++; }
                    if (crosses(corners[3], corners[7], isoLevel)) { avgX += px + stepX; avgY += py + stepY; avgZ += pz + interp(corners[3], corners[7], isoLevel) * stepZ; crossingCount++; }

                    if (crossingCount > 0) {
                        float inv = 1f / crossingCount;
                        vertexIndices[x][y][z] = mesh.addVertex(avgX * inv, avgY * inv, avgZ * inv);
                    }
                }

        // Pass 2: connect adjacent cells with quads (as two triangles)
        for (int x = 0; x < resX; x++)
            for (int y = 0; y < resY; y++)
                for (int z = 0; z < resZ; z++) {
                    int v0 = vertexIndices[x][y][z];
                    if (v0 < 0) continue;

                    // Connect along X edge (shared face between (x,y,z) and (x+1,y,z))
                    if (x + 1 < resX && y + 1 < resY) {
                        int v1 = vertexIndices[x + 1][y][z];
                        int v2 = vertexIndices[x + 1][y + 1][z];
                        int v3 = vertexIndices[x][y + 1][z];
                        if (v1 >= 0 && v2 >= 0 && v3 >= 0 && edgeCrosses(grid, x+1, y, z, x+1, y+1, z, isoLevel)) {
                            mesh.addTriangle(v0, v1, v2);
                            mesh.addTriangle(v0, v2, v3);
                        }
                    }
                    // Connect along Y edge
                    if (y + 1 < resY && z + 1 < resZ) {
                        int v1 = vertexIndices[x][y + 1][z];
                        int v2 = vertexIndices[x][y + 1][z + 1];
                        int v3 = vertexIndices[x][y][z + 1];
                        if (v1 >= 0 && v2 >= 0 && v3 >= 0 && edgeCrosses(grid, x, y+1, z, x, y+1, z+1, isoLevel)) {
                            mesh.addTriangle(v0, v1, v2);
                            mesh.addTriangle(v0, v2, v3);
                        }
                    }
                    // Connect along Z edge
                    if (x + 1 < resX && z + 1 < resZ) {
                        int v1 = vertexIndices[x][y][z + 1];
                        int v2 = vertexIndices[x + 1][y][z + 1];
                        int v3 = vertexIndices[x + 1][y][z];
                        if (v1 >= 0 && v2 >= 0 && v3 >= 0 && edgeCrosses(grid, x, y, z+1, x+1, y, z+1, isoLevel)) {
                            mesh.addTriangle(v0, v1, v2);
                            mesh.addTriangle(v0, v2, v3);
                        }
                    }
                }

        return mesh;
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

    private static boolean crosses(float v1, float v2, float iso) {
        return (v1 < iso) != (v2 < iso);
    }

    private static boolean edgeCrosses(float[][][] grid, int x1, int y1, int z1, int x2, int y2, int z2, float iso) {
        return (grid[x1][y1][z1] < iso) != (grid[x2][y2][z2] < iso);
    }

    private static float interp(float v1, float v2, float iso) {
        if (Math.abs(v2 - v1) < 1e-6f) return 0.5f;
        return (iso - v1) / (v2 - v1);
    }
}
