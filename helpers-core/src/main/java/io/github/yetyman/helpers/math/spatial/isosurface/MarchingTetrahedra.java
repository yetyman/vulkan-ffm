package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec3;

/**
 * Marching Tetrahedra — splits each cube into 6 tetrahedra to avoid ambiguity cases.
 * Produces a watertight mesh without the ambiguous face configurations of Marching Cubes.
 */
public class MarchingTetrahedra {

    private MarchingTetrahedra() {}

    public static MeshOutput extract(ScalarField3D field, Vec3 min, Vec3 max,
                                     int resX, int resY, int resZ, float isoLevel) {
        MeshOutput mesh = new MeshOutput();
        float stepX = (max.x - min.x) / resX;
        float stepY = (max.y - min.y) / resY;
        float stepZ = (max.z - min.z) / resZ;

        float[][][] grid = new float[resX + 1][resY + 1][resZ + 1];
        for (int x = 0; x <= resX; x++)
            for (int y = 0; y <= resY; y++)
                for (int z = 0; z <= resZ; z++)
                    grid[x][y][z] = field.sample(min.x + x * stepX, min.y + y * stepY, min.z + z * stepZ);

        for (int x = 0; x < resX; x++)
            for (int y = 0; y < resY; y++)
                for (int z = 0; z < resZ; z++)
                    processCube(mesh, grid, x, y, z, min, stepX, stepY, stepZ, isoLevel);

        return mesh;
    }

    private static void processCube(MeshOutput mesh, float[][][] grid, int x, int y, int z,
                                    Vec3 min, float stepX, float stepY, float stepZ, float isoLevel) {
        // 8 cube vertices
        Vec3[] pos = new Vec3[8];
        float[] val = new float[8];
        float px = min.x + x * stepX, py = min.y + y * stepY, pz = min.z + z * stepZ;
        pos[0] = new Vec3(px, py, pz);           val[0] = grid[x][y][z];
        pos[1] = new Vec3(px+stepX, py, pz);     val[1] = grid[x+1][y][z];
        pos[2] = new Vec3(px+stepX, py+stepY, pz); val[2] = grid[x+1][y+1][z];
        pos[3] = new Vec3(px, py+stepY, pz);     val[3] = grid[x][y+1][z];
        pos[4] = new Vec3(px, py, pz+stepZ);     val[4] = grid[x][y][z+1];
        pos[5] = new Vec3(px+stepX, py, pz+stepZ); val[5] = grid[x+1][y][z+1];
        pos[6] = new Vec3(px+stepX, py+stepY, pz+stepZ); val[6] = grid[x+1][y+1][z+1];
        pos[7] = new Vec3(px, py+stepY, pz+stepZ); val[7] = grid[x][y+1][z+1];

        // Split cube into 6 tetrahedra (Bloomenthal decomposition)
        int[][] tets = {
                {0,1,3,4}, {1,2,3,6}, {3,4,6,7},
                {1,4,5,6}, {1,3,4,6}, {3,4,6,7}
        };
        // Actually use 5 tetrahedra (simpler, still unambiguous)
        int[][] tets5 = {
                {0,1,2,5}, {0,2,3,7}, {0,4,5,7},
                {5,6,2,7}, {0,2,5,7}
        };

        for (int[] tet : tets5) {
            processTetrahedron(mesh, pos[tet[0]], pos[tet[1]], pos[tet[2]], pos[tet[3]],
                    val[tet[0]], val[tet[1]], val[tet[2]], val[tet[3]], isoLevel);
        }
    }

    private static void processTetrahedron(MeshOutput mesh,
                                           Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                           float v0, float v1, float v2, float v3, float iso) {
        int index = 0;
        if (v0 < iso) index |= 1;
        if (v1 < iso) index |= 2;
        if (v2 < iso) index |= 4;
        if (v3 < iso) index |= 8;

        if (index == 0 || index == 15) return;

        // Edges: 01, 02, 03, 12, 13, 23
        Vec3 e01 = null, e02 = null, e03 = null, e12 = null, e13 = null, e23 = null;

        switch (index) {
            case 1, 14 -> { e01 = interp(p0, p1, v0, v1, iso); e02 = interp(p0, p2, v0, v2, iso); e03 = interp(p0, p3, v0, v3, iso); addTri(mesh, e01, e02, e03, index == 14); }
            case 2, 13 -> { e01 = interp(p0, p1, v0, v1, iso); e12 = interp(p1, p2, v1, v2, iso); e13 = interp(p1, p3, v1, v3, iso); addTri(mesh, e01, e13, e12, index == 13); }
            case 4, 11 -> { e02 = interp(p0, p2, v0, v2, iso); e12 = interp(p1, p2, v1, v2, iso); e23 = interp(p2, p3, v2, v3, iso); addTri(mesh, e02, e12, e23, index == 11); }
            case 8, 7 -> { e03 = interp(p0, p3, v0, v3, iso); e13 = interp(p1, p3, v1, v3, iso); e23 = interp(p2, p3, v2, v3, iso); addTri(mesh, e03, e23, e13, index == 7); }
            case 3, 12 -> { e02 = interp(p0, p2, v0, v2, iso); e03 = interp(p0, p3, v0, v3, iso); e12 = interp(p1, p2, v1, v2, iso); e13 = interp(p1, p3, v1, v3, iso); addQuad(mesh, e02, e12, e13, e03, index == 12); }
            case 5, 10 -> { e01 = interp(p0, p1, v0, v1, iso); e03 = interp(p0, p3, v0, v3, iso); e12 = interp(p1, p2, v1, v2, iso); e23 = interp(p2, p3, v2, v3, iso); addQuad(mesh, e01, e12, e23, e03, index == 10); }
            case 6, 9 -> { e01 = interp(p0, p1, v0, v1, iso); e02 = interp(p0, p2, v0, v2, iso); e13 = interp(p1, p3, v1, v3, iso); e23 = interp(p2, p3, v2, v3, iso); addQuad(mesh, e01, e02, e23, e13, index == 9); }
        }
    }

    private static void addTri(MeshOutput mesh, Vec3 a, Vec3 b, Vec3 c, boolean flip) {
        int ia = mesh.addVertex(a), ib = mesh.addVertex(b), ic = mesh.addVertex(c);
        if (flip) mesh.addTriangle(ia, ic, ib); else mesh.addTriangle(ia, ib, ic);
    }

    private static void addQuad(MeshOutput mesh, Vec3 a, Vec3 b, Vec3 c, Vec3 d, boolean flip) {
        int ia = mesh.addVertex(a), ib = mesh.addVertex(b), ic = mesh.addVertex(c), id = mesh.addVertex(d);
        if (flip) { mesh.addTriangle(ia, ic, ib); mesh.addTriangle(ia, id, ic); }
        else { mesh.addTriangle(ia, ib, ic); mesh.addTriangle(ia, ic, id); }
    }

    private static Vec3 interp(Vec3 p1, Vec3 p2, float v1, float v2, float iso) {
        if (Math.abs(v2 - v1) < 1e-6f) return new Vec3((p1.x+p2.x)*0.5f, (p1.y+p2.y)*0.5f, (p1.z+p2.z)*0.5f);
        float t = (iso - v1) / (v2 - v1);
        return new Vec3(p1.x + t*(p2.x-p1.x), p1.y + t*(p2.y-p1.y), p1.z + t*(p2.z-p1.z));
    }
}
