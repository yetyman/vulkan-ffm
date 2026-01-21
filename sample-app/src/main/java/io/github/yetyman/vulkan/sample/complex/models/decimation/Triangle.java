package io.github.yetyman.vulkan.sample.complex.models.decimation;

public class Triangle {
    public final int id;
    public int v0, v1, v2;
    public boolean removed = false;
    
    public Triangle(int id, int v0, int v1, int v2) {
        this.id = id;
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
    }
    
    public boolean hasVertex(int vertexId) {
        return v0 == vertexId || v1 == vertexId || v2 == vertexId;
    }
    
    public void replaceVertex(int oldId, int newId) {
        if (v0 == oldId) v0 = newId;
        else if (v1 == oldId) v1 = newId;
        else if (v2 == oldId) v2 = newId;
    }
    
    public boolean isDegenerate() {
        return v0 == v1 || v1 == v2 || v2 == v0;
    }
}
