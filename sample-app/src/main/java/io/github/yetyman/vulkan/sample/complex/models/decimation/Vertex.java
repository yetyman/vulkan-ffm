package io.github.yetyman.vulkan.sample.complex.models.decimation;

import java.util.*;

public class Vertex {
    public final int id;
    public double x, y, z;
    public double nx, ny, nz;
    public double u, v;
    public QuadricErrorMetrics quadric = new QuadricErrorMetrics();
    public final Set<Integer> faces = new HashSet<>();
    public boolean removed = false;
    
    public Vertex(int id, double x, double y, double z, double nx, double ny, double nz, double u, double v) {
        this.id = id;
        this.x = x; this.y = y; this.z = z;
        this.nx = nx; this.ny = ny; this.nz = nz;
        this.u = u; this.v = v;
    }
    
    public double distanceTo(Vertex other) {
        double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
}
