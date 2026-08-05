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
    private float connectivity = 1.0f;

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
     * Sets the connectivity exponent. At 1.0 (default), contributions sum linearly.
     * Higher values make nearby points reinforce each other more strongly,
     * causing adjacent points to merge while isolated point distance stays similar.
     */
    public void setConnectivity(float connectivity) {
        this.connectivity = Math.max(0.5f, connectivity);
    }

    /**
     * Extracts a mesh with default spread multiplier.
     */
    public MeshOutput extract(List<Vec3> points) {
        return extract(points, 1.0f);
    }

    /**
     * Extracts a mesh from a point cloud using inverse-cube field with density-weighted points.
     *
     * Field at (x,y,z) = sum over all points of: weight_i / distance_i^3
     * Weight per point = 1 / localDensity (inversely proportional to number of nearby neighbors)
     * Surface is where field = surfaceRadius (used as threshold here).
     * spreadMultiplier globally scales all weights.
     *
     * @param points the point cloud positions
     * @param spreadMultiplier global multiplier on point weights
     * @return mesh output
     */
    public MeshOutput extract(List<Vec3> points, float spreadMultiplier) {
        if (points.isEmpty()) return new MeshOutput();

        // Precompute local density for each point (count of neighbors within average spacing)
        float[] localDensity = new float[points.size()];
        float avgSpacing = 0;

        // First pass: find nearest neighbor distance for each point
        float[] nearestDist = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            float nearest = Float.MAX_VALUE;
            Vec3 pi = points.get(i);
            for (int j = 0; j < points.size(); j++) {
                if (i == j) continue;
                float d = pi.distance(points.get(j));
                if (d < nearest) nearest = d;
            }
            nearestDist[i] = nearest;
            avgSpacing += nearest;
        }
        avgSpacing /= points.size();

        // Second pass: count neighbors within 3x average spacing as density measure
        float densityRadius = avgSpacing * 3f;
        for (int i = 0; i < points.size(); i++) {
            int count = 0;
            Vec3 pi = points.get(i);
            for (int j = 0; j < points.size(); j++) {
                if (i == j) continue;
                if (pi.distance(points.get(j)) < densityRadius) count++;
            }
            localDensity[i] = Math.max(1, count);
        }

        // Weight per point: inversely proportional to local density, scaled by spread
        float[] weights = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            weights[i] = spreadMultiplier / localDensity[i];
        }

        // Estimate max reach of any point to determine world bounds
        // At distance r, contribution = weight / r^3. Surface where sum = surfaceRadius.
        // Single point reaches: r = (weight / surfaceRadius)^(1/3)
        float maxReach = 0;
        for (int i = 0; i < points.size(); i++) {
            float reach = (float) Math.pow(weights[i] / surfaceRadius, 1.0 / 3.0);
            if (reach > maxReach) maxReach = reach;
        }
        maxReach = Math.max(maxReach, avgSpacing * 2);
        float finalMaxReach = maxReach;
        float[] finalWeights = weights;

        // Compute world bounds — pad generously to avoid flat cutoffs at edges
        Vec3 min = new Vec3(Float.MAX_VALUE);
        Vec3 max = new Vec3(-Float.MAX_VALUE);
        for (Vec3 p : points) { min.min(p); max.max(p); }
        float padding = Math.max(maxReach * 2f, avgSpacing * 5f);
        min.add(-padding, -padding, -padding);
        max.add(padding, padding, padding);

        // Build octree
        LinkedOctree<Integer> octree = new LinkedOctree<>(OctreeConfig.builder()
                .worldBounds(new AABB(min, max))
                .maxDepth(maxDepth)
                .splitThreshold(splitThreshold)
                .mergeThreshold(Math.max(1, splitThreshold / 2))
                .build());

        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            float s = 0.01f; // tiny — octree subdivides only when points are actually close
            octree.insert(i, new AABB(new Vec3(p.x - s, p.y - s, p.z - s), new Vec3(p.x + s, p.y + s, p.z + s)));
        }

        // Enforce 2:1 balance
        octree.balance();

        // Inverse-cube field: connectivity only scales weights (more merging power)
        // Falloff is always 1/r^3 — no exponent scaling that causes inflection
        ScalarField3D field = (x, y, z) -> {
            float sum = 0;
            for (int i = 0; i < points.size(); i++) {
                Vec3 pt = points.get(i);
                float dx = x - pt.x, dy = y - pt.y, dz = z - pt.z;
                float distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < 1e-6f) return Float.MAX_VALUE;
                float dist = (float) Math.sqrt(distSq);
                sum += (finalWeights[i] * connectivity) / (dist * dist * dist);
            }
            return sum;
        };

        // March each leaf cell — iso-level is surfaceRadius (threshold)
        MeshOutput mesh = new MeshOutput();

        octree.visitNodes((bounds, depth, isLeaf, itemCount) -> {
            if (!isLeaf) return;

            // Quick reject: check if any point could contribute enough at cell center
            Vec3 center = bounds.center();
            float centerField = 0;
            for (int i = 0; i < points.size(); i++) {
                float d = center.distance(points.get(i));
                if (d < 0.001f) { centerField = Float.MAX_VALUE; break; }
                centerField += finalWeights[i] / (d * d * d);
            }
            float cellDiag = (bounds.max.x - bounds.min.x) * 0.87f;
            // If field at center is way below threshold and cell is small, skip
            if (centerField < surfaceRadius * 0.01f && cellDiag < finalMaxReach * 0.5f) return;

            // Resolution scales with cell size — no arbitrary cap
            float cellSize = Math.max(bounds.max.x - bounds.min.x,
                    Math.max(bounds.max.y - bounds.min.y, bounds.max.z - bounds.min.z));
            int subdivs = Math.max(2, Math.min(6, (int) Math.ceil(cellSize / (finalMaxReach * 0.3f)) + 1));

            MeshOutput cellMesh = MarchingCubes.extract(field, bounds.min, bounds.max, subdivs, subdivs, subdivs, surfaceRadius);

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
