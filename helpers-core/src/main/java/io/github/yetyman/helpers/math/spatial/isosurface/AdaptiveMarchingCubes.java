package io.github.yetyman.helpers.math.spatial.isosurface;

import io.github.yetyman.helpers.math.Vec3;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.helpers.math.spatial.octree.LinkedOctree;
import io.github.yetyman.helpers.math.spatial.octree.OctreeConfig;

import java.util.List;

/**
 * Adaptive Marching Cubes — uses an octree to concentrate mesh resolution
 * where point density is high. Points are inserted into an octree which
 * subdivides naturally at dense regions. Then each leaf cell is marched
 * using a density field derived from the point cloud.
 *
 * The density at any position is computed as the number of points within
 * a kernel radius, weighted by a smooth falloff.
 */
public class AdaptiveMarchingCubes {

    private final float surfaceRadius;
    private final int splitThreshold;
    private final int maxDepth;

    /**
     * @param surfaceRadius distance from any point where the surface is placed (controls shape)
     * @param splitThreshold octree split threshold (controls where resolution increases)
     * @param maxDepth maximum octree depth
     */
    public AdaptiveMarchingCubes(float surfaceRadius, int splitThreshold, int maxDepth) {
        this.surfaceRadius = surfaceRadius;
        this.splitThreshold = splitThreshold;
        this.maxDepth = maxDepth;
    }

    public AdaptiveMarchingCubes() {
        this(1.0f, 4, 6);
    }

    /**
     * Extracts a mesh with uniform weight for all points.
     */
    public MeshOutput extract(List<Vec3> points) {
        return extract(points, 1.0f);
    }

    /**
     * Extracts a mesh from a point cloud. Weight scales how much each point
     * contributes to the surface — the effective radius for each point is
     * surfaceRadius * weight. Higher weight = larger sphere of influence.
     *
     * @param points the point cloud positions
     * @param weight global weight multiplier for all points
     * @return mesh output
     */
    public MeshOutput extract(List<Vec3> points, float weight) {
        if (points.isEmpty()) return new MeshOutput();

        float effectiveRadius = surfaceRadius * weight;

        // Compute world bounds from points (with padding for effective radius)
        Vec3 min = new Vec3(Float.MAX_VALUE);
        Vec3 max = new Vec3(-Float.MAX_VALUE);
        for (Vec3 p : points) { min.min(p); max.max(p); }
        min.add(-effectiveRadius * 2, -effectiveRadius * 2, -effectiveRadius * 2);
        max.add(effectiveRadius * 2, effectiveRadius * 2, effectiveRadius * 2);

        // Build octree
        LinkedOctree<Integer> octree = new LinkedOctree<>(OctreeConfig.builder()
                .worldBounds(new AABB(min, max))
                .maxDepth(maxDepth)
                .splitThreshold(splitThreshold)
                .mergeThreshold(Math.max(1, splitThreshold / 2))
                .build());

        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            float s = 0.01f;
            octree.insert(i, new AABB(new Vec3(p.x - s, p.y - s, p.z - s), new Vec3(p.x + s, p.y + s, p.z + s)));
        }

        // Ensure every point has adequate resolution by inserting ghost points
        int ghostId = points.size();
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            float r = effectiveRadius * 0.5f;
            // 6 axis-aligned ghost points to force subdivision in the neighborhood
            octree.insert(ghostId++, new AABB(new Vec3(p.x+r-0.01f,p.y-0.01f,p.z-0.01f), new Vec3(p.x+r+0.01f,p.y+0.01f,p.z+0.01f)));
            octree.insert(ghostId++, new AABB(new Vec3(p.x-r-0.01f,p.y-0.01f,p.z-0.01f), new Vec3(p.x-r+0.01f,p.y+0.01f,p.z+0.01f)));
            octree.insert(ghostId++, new AABB(new Vec3(p.x-0.01f,p.y+r-0.01f,p.z-0.01f), new Vec3(p.x+0.01f,p.y+r+0.01f,p.z+0.01f)));
            octree.insert(ghostId++, new AABB(new Vec3(p.x-0.01f,p.y-r-0.01f,p.z-0.01f), new Vec3(p.x+0.01f,p.y-r+0.01f,p.z+0.01f)));
            octree.insert(ghostId++, new AABB(new Vec3(p.x-0.01f,p.y-0.01f,p.z+r-0.01f), new Vec3(p.x+0.01f,p.y+0.01f,p.z+r+0.01f)));
            octree.insert(ghostId++, new AABB(new Vec3(p.x-0.01f,p.y-0.01f,p.z-r-0.01f), new Vec3(p.x+0.01f,p.y+0.01f,p.z-r+0.01f)));
        }

        // Distance-to-nearest-point field
        ScalarField3D distField = (x, y, z) -> {
            float minDist = Float.MAX_VALUE;
            for (Vec3 p : points) {
                float dx = x - p.x, dy = y - p.y, dz = z - p.z;
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < minDist) minDist = dist;
            }
            return minDist;
        };

        // March each leaf cell
        // Resolution is driven by cell size (octree depth) — NOT by surfaceRadius.
        // Dense areas have small cells (high depth) → fine mesh.
        // Sparse areas have large cells (low depth) → coarse mesh.
        // Surface shape is always at distance surfaceRadius from nearest point.
        MeshOutput mesh = new MeshOutput();

        octree.visitNodes((bounds, depth, isLeaf, itemCount) -> {
            if (!isLeaf) return;

            // Quick reject: if cell center is further than surfaceRadius + cell diagonal from any point
            Vec3 center = bounds.center();
            float centerDist = Float.MAX_VALUE;
            for (Vec3 p : points) {
                float d = center.distance(p);
                if (d < centerDist) centerDist = d;
            }
            float cellDiag = (bounds.max.x - bounds.min.x) * 0.87f;
            if (centerDist - cellDiag > effectiveRadius) return;

            // Resolution: fixed subdivisions per cell — deeper cells are already smaller,
            // so same subdivision count = finer absolute resolution in dense areas
            int subdivs = 3;

            MeshOutput cellMesh = MarchingCubes.extract(distField, bounds.min, bounds.max, subdivs, subdivs, subdivs, effectiveRadius);

            // Merge into main mesh
            int vertOffset = mesh.vertexCount();
            for (Vec3 v : cellMesh.vertices()) {
                mesh.addVertex(v);
            }
            List<Integer> indices = cellMesh.indices();
            for (int i = 0; i < indices.size(); i += 3) {
                mesh.addTriangle(indices.get(i) + vertOffset, indices.get(i + 1) + vertOffset, indices.get(i + 2) + vertOffset);
            }
        });

        return mesh;
    }
}
