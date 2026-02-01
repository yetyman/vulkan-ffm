package io.github.yetyman.vulkan.sample.complex.models.decimation;

import java.util.*;

public class MeshSimplifier {
    private List<Vertex> vertices;
    private List<Triangle> triangles;
    
    public SimplifiedMesh simplify(float[] vertexData, int[] indices, float targetRatio) {
        buildMesh(vertexData, indices);
        
        // Don't decimate small meshes (< 20 triangles)
        if (triangles.size() < 20) {
            return new SimplifiedMesh(vertexData, indices);
        }
        
        calculateQuadrics();
        
        int targetTriCount = Math.max(4, (int)(triangles.size() * targetRatio));
        PriorityQueue<EdgeCollapse> queue = buildCollapseQueue();
        
        while (countActiveTriangles() > targetTriCount && !queue.isEmpty()) {
            EdgeCollapse collapse = queue.poll();
            if (vertices.get(collapse.v1).removed || vertices.get(collapse.v2).removed) continue;
            
            performCollapse(collapse);
            
            Vertex v = vertices.get(collapse.v1);
            for (int faceId : v.faces) {
                Triangle t = triangles.get(faceId);
                if (!t.removed) {
                    addEdgeCollapses(t, queue);
                }
            }
        }
        
        return extractMesh();
    }
    
    public SimplifiedMesh simplifyWithMorphTargets(float[] vertexData, int[] indices, float targetRatio) {
        buildMesh(vertexData, indices);
        
        // Don't decimate small meshes (< 20 triangles)
        if (triangles.size() < 20) {
            return new SimplifiedMesh(vertexData, indices);
        }
        
        calculateQuadrics();
        
        int targetTriCount = Math.max(4, (int)(triangles.size() * targetRatio));
        PriorityQueue<EdgeCollapse> queue = buildCollapseQueue();
        
        while (countActiveTriangles() > targetTriCount && !queue.isEmpty()) {
            EdgeCollapse collapse = queue.poll();
            if (vertices.get(collapse.v1).removed || vertices.get(collapse.v2).removed) continue;
            performCollapse(collapse);
            
            Vertex v = vertices.get(collapse.v1);
            for (int faceId : v.faces) {
                Triangle t = triangles.get(faceId);
                if (!t.removed) {
                    addEdgeCollapses(t, queue);
                }
            }
        }
        
        return extractMeshWithMorphTargets();
    }
    
    private void buildMesh(float[] vertexData, int[] indices) {
        int vertexCount = vertexData.length / 8;
        vertices = new ArrayList<>(vertexCount);
        
        for (int i = 0; i < vertexCount; i++) {
            int offset = i * 8;
            vertices.add(new Vertex(i,
                vertexData[offset], vertexData[offset+1], vertexData[offset+2],
                vertexData[offset+3], vertexData[offset+4], vertexData[offset+5],
                vertexData[offset+6], vertexData[offset+7]
            ));
        }
        
        triangles = new ArrayList<>(indices.length / 3);
        for (int i = 0; i < indices.length; i += 3) {
            Triangle t = new Triangle(triangles.size(), indices[i], indices[i+1], indices[i+2]);
            triangles.add(t);
            vertices.get(t.v0).faces.add(t.id);
            vertices.get(t.v1).faces.add(t.id);
            vertices.get(t.v2).faces.add(t.id);
        }
    }
    
    private void calculateQuadrics() {
        for (Triangle t : triangles) {
            Vertex v0 = vertices.get(t.v0), v1 = vertices.get(t.v1), v2 = vertices.get(t.v2);
            
            // Calculate plane equation
            double e1x = v1.x - v0.x, e1y = v1.y - v0.y, e1z = v1.z - v0.z;
            double e2x = v2.x - v0.x, e2y = v2.y - v0.y, e2z = v2.z - v0.z;
            double nx = e1y*e2z - e1z*e2y;
            double ny = e1z*e2x - e1x*e2z;
            double nz = e1x*e2y - e1y*e2x;
            double len = Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len > 0) { nx /= len; ny /= len; nz /= len; }
            double d = -(nx*v0.x + ny*v0.y + nz*v0.z);
            
            QuadricErrorMetrics q = QuadricErrorMetrics.fromPlane(nx, ny, nz, d);
            v0.quadric.add(q);
            v1.quadric.add(q);
            v2.quadric.add(q);
        }
    }
    
    private PriorityQueue<EdgeCollapse> buildCollapseQueue() {
        PriorityQueue<EdgeCollapse> queue = new PriorityQueue<>();
        Set<Long> processed = new HashSet<>();
        
        for (Triangle t : triangles) {
            addEdgeCollapses(t, queue, processed);
        }
        return queue;
    }
    
    private void addEdgeCollapses(Triangle t, PriorityQueue<EdgeCollapse> queue) {
        addEdgeCollapses(t, queue, new HashSet<>());
    }
    
    private void addEdgeCollapses(Triangle t, PriorityQueue<EdgeCollapse> queue, Set<Long> processed) {
        if (t.removed) return;
        addEdgeCollapse(t.v0, t.v1, queue, processed);
        addEdgeCollapse(t.v1, t.v2, queue, processed);
        addEdgeCollapse(t.v2, t.v0, queue, processed);
    }
    
    private void addEdgeCollapse(int id1, int id2, PriorityQueue<EdgeCollapse> queue, Set<Long> processed) {
        int min = Math.min(id1, id2), max = Math.max(id1, id2);
        long key = ((long)min << 32) | max;
        if (!processed.add(key)) return;
        
        Vertex v1 = vertices.get(id1), v2 = vertices.get(id2);
        if (v1.removed || v2.removed) return;
        
        // Don't collapse boundary edges
        Set<Integer> sharedFaces = new HashSet<>(v1.faces);
        sharedFaces.retainAll(v2.faces);
        if (sharedFaces.size() < 2) return; // Boundary edge
        
        QuadricErrorMetrics q = v1.quadric.copy();
        q.add(v2.quadric);
        
        // Target position: midpoint
        double tx = (v1.x + v2.x) / 2, ty = (v1.y + v2.y) / 2, tz = (v1.z + v2.z) / 2;
        double error = q.error(tx, ty, tz);
        
        // Interpolate attributes
        double tnx = (v1.nx + v2.nx) / 2, tny = (v1.ny + v2.ny) / 2, tnz = (v1.nz + v2.nz) / 2;
        double len = Math.sqrt(tnx*tnx + tny*tny + tnz*tnz);
        if (len > 0) { tnx /= len; tny /= len; tnz /= len; }
        double tu = (v1.u + v2.u) / 2, tv = (v1.v + v2.v) / 2;
        
        queue.add(new EdgeCollapse(id1, id2, error, tx, ty, tz, tnx, tny, tnz, tu, tv));
    }
    
    private void performCollapse(EdgeCollapse collapse) {
        Vertex v1 = vertices.get(collapse.v1), v2 = vertices.get(collapse.v2);
        
        // Update v1 to target position
        v1.x = collapse.targetX; v1.y = collapse.targetY; v1.z = collapse.targetZ;
        v1.nx = collapse.targetNX; v1.ny = collapse.targetNY; v1.nz = collapse.targetNZ;
        v1.u = collapse.targetU; v1.v = collapse.targetV;
        v1.quadric.add(v2.quadric);
        
        // Remove triangles containing both vertices (the edge)
        Set<Integer> sharedFaces = new HashSet<>(v1.faces);
        sharedFaces.retainAll(v2.faces);
        for (int faceId : sharedFaces) {
            triangles.get(faceId).removed = true;
            v1.faces.remove(faceId);
        }
        
        // Update remaining triangles that only contain v2
        for (int faceId : v2.faces) {
            if (sharedFaces.contains(faceId)) continue;
            
            Triangle t = triangles.get(faceId);
            t.replaceVertex(collapse.v2, collapse.v1);
            v1.faces.add(faceId);
        }
        
        v2.removed = true;
        v2.faces.clear();
    }
    
    private int countActiveTriangles() {
        return (int) triangles.stream().filter(t -> !t.removed).count();
    }
    
    private SimplifiedMesh extractMesh() {
        Map<Integer, Integer> vertexRemap = new HashMap<>();
        List<Float> outVertices = new ArrayList<>();
        List<Integer> outIndices = new ArrayList<>();
        
        for (Triangle t : triangles) {
            if (t.removed) continue;
            
            int i0 = remapVertex(t.v0, vertexRemap, outVertices);
            int i1 = remapVertex(t.v1, vertexRemap, outVertices);
            int i2 = remapVertex(t.v2, vertexRemap, outVertices);
            
            outIndices.add(i0);
            outIndices.add(i1);
            outIndices.add(i2);
        }
        
        float[] verts = new float[outVertices.size()];
        for (int i = 0; i < verts.length; i++) verts[i] = outVertices.get(i);
        
        int[] inds = outIndices.stream().mapToInt(Integer::intValue).toArray();
        
        return new SimplifiedMesh(verts, inds);
    }
    
    private SimplifiedMesh extractMeshWithMorphTargets() {
        float[] outVertices = new float[vertices.size() * 8];
        
        for (int i = 0; i < vertices.size(); i++) {
            Vertex v = vertices.get(i);
            int offset = i * 8;
            outVertices[offset] = (float)v.x;
            outVertices[offset + 1] = (float)v.y;
            outVertices[offset + 2] = (float)v.z;
            outVertices[offset + 3] = (float)v.nx;
            outVertices[offset + 4] = (float)v.ny;
            outVertices[offset + 5] = (float)v.nz;
            outVertices[offset + 6] = (float)v.u;
            outVertices[offset + 7] = (float)v.v;
        }
        
        List<Integer> outIndices = new ArrayList<>();
        for (Triangle t : triangles) {
            if (!t.removed) {
                outIndices.add(t.v0);
                outIndices.add(t.v1);
                outIndices.add(t.v2);
            }
        }
        
        int[] inds = outIndices.stream().mapToInt(Integer::intValue).toArray();
        return new SimplifiedMesh(outVertices, inds);
    }
    
    private int remapVertex(int oldId, Map<Integer, Integer> remap, List<Float> outVertices) {
        return remap.computeIfAbsent(oldId, id -> {
            Vertex v = vertices.get(id);
            int newId = outVertices.size() / 8;
            outVertices.add((float)v.x); outVertices.add((float)v.y); outVertices.add((float)v.z);
            outVertices.add((float)v.nx); outVertices.add((float)v.ny); outVertices.add((float)v.nz);
            outVertices.add((float)v.u); outVertices.add((float)v.v);
            return newId;
        });
    }
}
