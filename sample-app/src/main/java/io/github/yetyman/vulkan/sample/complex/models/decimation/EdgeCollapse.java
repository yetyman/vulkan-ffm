package io.github.yetyman.vulkan.sample.complex.models.decimation;

public class EdgeCollapse implements Comparable<EdgeCollapse> {
    public final int v1, v2;
    public final double error;
    public final double targetX, targetY, targetZ;
    public final double targetNX, targetNY, targetNZ;
    public final double targetU, targetV;
    
    public EdgeCollapse(int v1, int v2, double error, 
                       double tx, double ty, double tz,
                       double tnx, double tny, double tnz,
                       double tu, double tv) {
        this.v1 = v1;
        this.v2 = v2;
        this.error = error;
        this.targetX = tx; this.targetY = ty; this.targetZ = tz;
        this.targetNX = tnx; this.targetNY = tny; this.targetNZ = tnz;
        this.targetU = tu; this.targetV = tv;
    }
    
    @Override
    public int compareTo(EdgeCollapse other) {
        return Double.compare(this.error, other.error);
    }
}
