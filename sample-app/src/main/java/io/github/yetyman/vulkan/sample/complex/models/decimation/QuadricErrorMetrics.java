package io.github.yetyman.vulkan.sample.complex.models.decimation;

/**
 * Quadric Error Metrics for mesh simplification (Garland & Heckbert 1997)
 */
public class QuadricErrorMetrics {
    private final double[] q = new double[10];
    
    public QuadricErrorMetrics() {}
    
    public static QuadricErrorMetrics fromPlane(double a, double b, double c, double d) {
        QuadricErrorMetrics qem = new QuadricErrorMetrics();
        qem.q[0] = a*a; qem.q[1] = a*b; qem.q[2] = a*c; qem.q[3] = a*d;
        qem.q[4] = b*b; qem.q[5] = b*c; qem.q[6] = b*d;
        qem.q[7] = c*c; qem.q[8] = c*d;
        qem.q[9] = d*d;
        return qem;
    }
    
    public void add(QuadricErrorMetrics other) {
        for (int i = 0; i < 10; i++) q[i] += other.q[i];
    }
    
    public double error(double x, double y, double z) {
        return q[0]*x*x + 2*q[1]*x*y + 2*q[2]*x*z + 2*q[3]*x
             + q[4]*y*y + 2*q[5]*y*z + 2*q[6]*y
             + q[7]*z*z + 2*q[8]*z + q[9];
    }
    
    public QuadricErrorMetrics copy() {
        QuadricErrorMetrics copy = new QuadricErrorMetrics();
        System.arraycopy(q, 0, copy.q, 0, 10);
        return copy;
    }
}
