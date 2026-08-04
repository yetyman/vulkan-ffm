package io.github.yetyman.helpers.math.spatial.geodesic;

import io.github.yetyman.helpers.math.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Geodesic grid — subdivided icosahedron producing hexagonal/pentagonal cells on a sphere.
 * Subdivision level 0 = 12 vertices (pentagons). Each level quadruples the face count.
 * Vertex count at level N = 10 * (4^N) + 2.
 */
public class GeodesicGrid {

    private final int subdivisionLevel;
    private final Vec3[] vertices;
    private final int[][] faces; // triangles (3 vertex indices each)
    private final List<GeodesicCell> cells;

    public GeodesicGrid(int subdivisionLevel) {
        this.subdivisionLevel = subdivisionLevel;

        // Build icosahedron then subdivide
        float t = (1f + (float) Math.sqrt(5.0)) / 2f;
        Vec3[] icoVerts = {
                norm(-1, t, 0), norm(1, t, 0), norm(-1, -t, 0), norm(1, -t, 0),
                norm(0, -1, t), norm(0, 1, t), norm(0, -1, -t), norm(0, 1, -t),
                norm(t, 0, -1), norm(t, 0, 1), norm(-t, 0, -1), norm(-t, 0, 1)
        };
        int[][] icoFaces = {
                {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},
                {1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
                {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},
                {4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}
        };

        // Subdivide
        List<Vec3> verts = new ArrayList<>(List.of(icoVerts));
        List<int[]> faceList = new ArrayList<>(List.of(icoFaces));
        Map<Long, Integer> midpointCache = new HashMap<>();

        for (int level = 0; level < subdivisionLevel; level++) {
            List<int[]> newFaces = new ArrayList<>();
            midpointCache.clear();
            for (int[] tri : faceList) {
                int a = getMidpoint(verts, midpointCache, tri[0], tri[1]);
                int b = getMidpoint(verts, midpointCache, tri[1], tri[2]);
                int c = getMidpoint(verts, midpointCache, tri[2], tri[0]);
                newFaces.add(new int[]{tri[0], a, c});
                newFaces.add(new int[]{tri[1], b, a});
                newFaces.add(new int[]{tri[2], c, b});
                newFaces.add(new int[]{a, b, c});
            }
            faceList = newFaces;
        }

        this.vertices = verts.toArray(new Vec3[0]);
        this.faces = faceList.toArray(new int[0][]);

        // Build cells (one per vertex — dual of the triangulation)
        this.cells = buildCells();
    }

    public int subdivisionLevel() { return subdivisionLevel; }
    public int vertexCount() { return vertices.length; }
    public int faceCount() { return faces.length; }
    public int cellCount() { return cells.size(); }
    public Vec3 vertex(int index) { return vertices[index]; }
    public GeodesicCell cell(int index) { return cells.get(index); }

    /**
     * Finds the cell whose center is closest to the given direction (unit vector on sphere).
     */
    public int findCell(Vec3 direction) {
        int best = 0;
        float bestDot = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vertices.length; i++) {
            float dot = vertices[i].dot(direction);
            if (dot > bestDot) { bestDot = dot; best = i; }
        }
        return best;
    }

    /**
     * Returns all vertices as an array (positions on unit sphere).
     */
    public Vec3[] allVertices() { return vertices.clone(); }

    // --- Internal ---

    private static Vec3 norm(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new Vec3(x / len, y / len, z / len);
    }

    private int getMidpoint(List<Vec3> verts, Map<Long, Integer> cache, int a, int b) {
        long key = a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
        Integer cached = cache.get(key);
        if (cached != null) return cached;
        Vec3 mid = norm(
                (verts.get(a).x + verts.get(b).x) * 0.5f,
                (verts.get(a).y + verts.get(b).y) * 0.5f,
                (verts.get(a).z + verts.get(b).z) * 0.5f
        );
        int idx = verts.size();
        verts.add(mid);
        cache.put(key, idx);
        return idx;
    }

    private List<GeodesicCell> buildCells() {
        // For each vertex, find all adjacent vertices (neighbors in the triangulation)
        Map<Integer, List<Integer>> adjacency = new HashMap<>();
        for (int[] face : faces) {
            addEdge(adjacency, face[0], face[1]);
            addEdge(adjacency, face[1], face[2]);
            addEdge(adjacency, face[2], face[0]);
        }
        List<GeodesicCell> result = new ArrayList<>(vertices.length);
        for (int i = 0; i < vertices.length; i++) {
            List<Integer> neighbors = adjacency.getOrDefault(i, List.of());
            boolean isPentagon = neighbors.size() == 5;
            result.add(new GeodesicCell(i, vertices[i], neighbors.stream().mapToInt(Integer::intValue).toArray(), isPentagon));
        }
        return result;
    }

    private void addEdge(Map<Integer, List<Integer>> adj, int a, int b) {
        adj.computeIfAbsent(a, k -> new ArrayList<>());
        adj.computeIfAbsent(b, k -> new ArrayList<>());
        if (!adj.get(a).contains(b)) adj.get(a).add(b);
        if (!adj.get(b).contains(a)) adj.get(b).add(a);
    }
}
