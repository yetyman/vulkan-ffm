package io.github.yetyman.structures.input;

import io.github.yetyman.structures.state.StateRegistry;
import io.github.yetyman.structures.state.StateSlot;

/**
 * Observable mouse state backed by a {@link StateRegistry}.
 * <p>
 * Position derivatives (velocitySmooth, accelerationSmooth, jerkSmooth) use a
 * one-euro filter — adapts cutoff based on speed so fast motion is tracked faithfully
 * while jitter at rest is suppressed without amplitude attenuation.
 * <p>
 * Scroll derivatives use a simple EMA with a fixed time constant.
 * <p>
 * Scroll uses per-axis timestamps to handle touchpads that fire alternating X/Y events.
 */
public class MouseState {

    public enum ButtonState { UP, DOWN }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private final double minCutoff;   // one-euro: min cutoff Hz at rest
    private final double beta;        // one-euro: speed coefficient
    private final double dCutoff;     // one-euro: derivative cutoff Hz
    private final long   scrollSmoothingNanos;
    private final int    buttonCount;

    // -------------------------------------------------------------------------
    // Registry
    // -------------------------------------------------------------------------

    public final StateRegistry registry;

    // -------------------------------------------------------------------------
    // Primary slots
    // -------------------------------------------------------------------------

    public final StateSlot.DoublesSlot position;
    public final StateSlot.EnumSlot[]  button;
    public final StateSlot.DoublesSlot scrollDelta;

    // -------------------------------------------------------------------------
    // Derived from position
    // -------------------------------------------------------------------------

    public final StateSlot.DoublesSlot delta;
    public final StateSlot.DoublesSlot velocity;
    public final StateSlot.DoublesSlot acceleration;
    public final StateSlot.DoublesSlot jerk;
    public final StateSlot.DoublesSlot velocitySmooth;
    public final StateSlot.DoublesSlot accelerationSmooth;
    public final StateSlot.DoublesSlot jerkSmooth;

    public final StateSlot.BoolSlot moving;   // PENDING: shared timeout system
    public final StateSlot.BoolSlot dragging; // PENDING: shared timeout system

    // -------------------------------------------------------------------------
    // Derived from scroll
    // -------------------------------------------------------------------------

    public final StateSlot.DoublesSlot scrollVelocity;
    public final StateSlot.DoublesSlot scrollAcceleration;
    public final StateSlot.DoublesSlot scrollJerk;
    public final StateSlot.DoublesSlot scrollVelocitySmooth;
    public final StateSlot.DoublesSlot scrollAccelerationSmooth;
    public final StateSlot.DoublesSlot scrollJerkSmooth;

    public final StateSlot.BoolSlot scrolling; // PENDING: shared timeout system

    // -------------------------------------------------------------------------
    // One-euro filter state — position smooth chain
    // -------------------------------------------------------------------------

    private double oeVelDx = 0, oeVelDy = 0;
    private double oeAccDx = 0, oeAccDy = 0;
    private double oeJrkDx = 0, oeJrkDy = 0;

    // -------------------------------------------------------------------------
    // Position regression buffers — jitter-robust derivative estimation
    // -------------------------------------------------------------------------

    private static final int POS_REG_N = 8;
    private final double[] posRegT  = new double[POS_REG_N];
    private final double[] posRegX  = new double[POS_REG_N];
    private final double[] posRegY  = new double[POS_REG_N];
    private final double[] velRegT  = new double[POS_REG_N];
    private final double[] velRegX  = new double[POS_REG_N];
    private final double[] velRegY  = new double[POS_REG_N];
    private final double[] accRegT  = new double[POS_REG_N];
    private final double[] accRegX  = new double[POS_REG_N];
    private final double[] accRegY  = new double[POS_REG_N];
    private int  posRegHead = 0, posRegCount = 0;
    private int  velRegHead = 0, velRegCount = 0;
    private int  accRegHead = 0, accRegCount = 0;
    private double posRegT0 = 0;

    // -------------------------------------------------------------------------
    // Scroll regression buffer — for jitter-robust acceleration estimation
    // -------------------------------------------------------------------------

    private static final int SCROLL_REG_N = 8;
    private final double[] regT  = new double[SCROLL_REG_N];
    private final double[] regVx = new double[SCROLL_REG_N];
    private final double[] regVy = new double[SCROLL_REG_N];
    private int  regHead  = 0;
    private int  regCount = 0;
    private double regT0  = 0; // time origin to keep values small

    private long lastPositionNanos = Long.MIN_VALUE;
    private long lastScrollNanos   = Long.MIN_VALUE;
    private long lastScrollXNanos  = Long.MIN_VALUE;
    private long lastScrollYNanos  = Long.MIN_VALUE;
    private double lastScrollVx    = 0.0;
    private double lastScrollVy    = 0.0;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * @param buttonCount          number of mouse buttons to track
     * @param minCutoff            one-euro min cutoff Hz for position derivatives (e.g. 1.0)
     * @param beta                 one-euro speed coefficient (e.g. 0.007)
     * @param dCutoff              one-euro derivative cutoff Hz (e.g. 1.0)
     * @param scrollSmoothingNanos EMA time constant for scroll derivatives in nanoseconds
     */
    @SuppressWarnings("unchecked")
    public MouseState(int buttonCount, double minCutoff, double beta, double dCutoff,
                      long scrollSmoothingNanos) {
        this.buttonCount          = buttonCount;
        this.minCutoff            = minCutoff;
        this.beta                 = beta;
        this.dCutoff              = dCutoff;
        this.scrollSmoothingNanos = scrollSmoothingNanos;

        registry = new StateRegistry();

        position    = registry.addStateArr("mouse.position",    0.0, 0.0);
        button      = new StateSlot.EnumSlot[buttonCount];
        for (int i = 0; i < buttonCount; i++)
            button[i] = registry.addState("mouse.button." + i, ButtonState.class, ButtonState.UP);
        scrollDelta = registry.addStateArr("mouse.scroll.delta", 0.0, 0.0);

        delta              = registry.addStateArr("mouse.delta",              0.0, 0.0);
        velocity           = registry.addStateArr("mouse.velocity",           0.0, 0.0);
        acceleration       = registry.addStateArr("mouse.acceleration",       0.0, 0.0);
        jerk               = registry.addStateArr("mouse.jerk",               0.0, 0.0);
        velocitySmooth     = registry.addStateArr("mouse.velocity.smooth",     0.0, 0.0);
        accelerationSmooth = registry.addStateArr("mouse.acceleration.smooth", 0.0, 0.0);
        jerkSmooth         = registry.addStateArr("mouse.jerk.smooth",         0.0, 0.0);
        moving             = registry.addState("mouse.moving",   false);
        dragging           = registry.addState("mouse.dragging", false);

        scrollVelocity           = registry.addStateArr("mouse.scroll.velocity",             0.0, 0.0);
        scrollAcceleration       = registry.addStateArr("mouse.scroll.acceleration",         0.0, 0.0);
        scrollJerk               = registry.addStateArr("mouse.scroll.jerk",                 0.0, 0.0);
        scrollVelocitySmooth     = registry.addStateArr("mouse.scroll.velocity.smooth",      0.0, 0.0);
        scrollAccelerationSmooth = registry.addStateArr("mouse.scroll.acceleration.smooth",  0.0, 0.0);
        scrollJerkSmooth         = registry.addStateArr("mouse.scroll.jerk.smooth",          0.0, 0.0);
        scrolling                = registry.addState("mouse.scrolling", false);

        registry.seal();
    }

    // -------------------------------------------------------------------------
    // Feed-in API
    // -------------------------------------------------------------------------

    public void updatePosition(double x, double y, long now) {
        registry.beginBatch();
        try {
            double[] prev = position.getValue();
            registry.setArrRef(position, new double[]{x, y});

            if (lastPositionNanos != Long.MIN_VALUE) {
                double dt = (now - lastPositionNanos) * 1e-9;
                if (dt > 0) {
                    double dx = x - prev[0];
                    double dy = y - prev[1];
                    registry.setArrRef(delta, new double[]{dx, dy});

                    // Raw instantaneous derivatives — noisy but unbiased
                    double rawVx = dx / dt, rawVy = dy / dt;
                    double[] prevVel = velocity.getValue();
                    double rawAx = (rawVx - prevVel[0]) / dt, rawAy = (rawVy - prevVel[1]) / dt;
                    double[] prevAcc = acceleration.getValue();
                    double rawJx = (rawAx - prevAcc[0]) / dt, rawJy = (rawAy - prevAcc[1]) / dt;

                    // Push position sample
                    if (posRegCount == 0) posRegT0 = now * 1e-9;
                    double t = now * 1e-9 - posRegT0;
                    posRegT[posRegHead] = t; posRegX[posRegHead] = x;    posRegY[posRegHead] = y;
                    posRegHead = (posRegHead + 1) % POS_REG_N;
                    if (posRegCount < POS_REG_N) posRegCount++;

                    // Push raw velocity sample
                    velRegT[velRegHead] = t; velRegX[velRegHead] = rawVx; velRegY[velRegHead] = rawVy;
                    velRegHead = (velRegHead + 1) % POS_REG_N;
                    if (velRegCount < POS_REG_N) velRegCount++;

                    // Push raw acceleration sample
                    accRegT[accRegHead] = t; accRegX[accRegHead] = rawAx; accRegY[accRegHead] = rawAy;
                    accRegHead = (accRegHead + 1) % POS_REG_N;
                    if (accRegCount < POS_REG_N) accRegCount++;

                    // Each regression independently estimates slope of its own raw data window
                    double vx = rawVx, vy = rawVy;
                    if (posRegCount >= 2) { double[] s = regSlope(posRegT, posRegX, posRegY, posRegHead, posRegCount, POS_REG_N); vx = s[0]; vy = s[1]; }
                    registry.setArrRef(velocity, new double[]{vx, vy});

                    double ax = rawAx, ay = rawAy;
                    if (velRegCount >= 2) { double[] s = regSlope(velRegT, velRegX, velRegY, velRegHead, velRegCount, POS_REG_N); ax = s[0]; ay = s[1]; }
                    registry.setArrRef(acceleration, new double[]{ax, ay});

                    double jx = rawJx, jy = rawJy;
                    if (accRegCount >= 2) { double[] s = regSlope(accRegT, accRegX, accRegY, accRegHead, accRegCount, POS_REG_N); jx = s[0]; jy = s[1]; }
                    registry.setArrRef(jerk, new double[]{jx, jy});

                    double[] vs = velocitySmooth.getValue();
                    double[] velS = oneEuro2(vx, vy, vs[0], vs[1], oeVelDx, oeVelDy, dt);
                    double svx = velS[0], svy = velS[1];
                    oeVelDx = velS[2]; oeVelDy = velS[3];
                    registry.setArrRef(velocitySmooth, new double[]{svx, svy});

                    double sax = (svx - vs[0]) / dt, say = (svy - vs[1]) / dt;
                    double[] as = accelerationSmooth.getValue();
                    double[] accS = oneEuro2(sax, say, as[0], as[1], oeAccDx, oeAccDy, dt);
                    oeAccDx = accS[2]; oeAccDy = accS[3];
                    registry.setArrRef(accelerationSmooth, new double[]{accS[0], accS[1]});

                    // jerkSmooth — one-euro on regression jerk (no longer raw finite-difference)
                    double[] js = jerkSmooth.getValue();
                    double[] jrkS = oneEuro2(jx, jy, js[0], js[1], oeJrkDx, oeJrkDy, dt);
                    oeJrkDx = jrkS[2]; oeJrkDy = jrkS[3];
                    registry.setArrRef(jerkSmooth, new double[]{jrkS[0], jrkS[1]});
                }
            }
            // PENDING: shared timeout system — See NEXT_STEPS.md
        } finally {
            lastPositionNanos = now;
            registry.endBatch();
        }
    }

    public void updateButton(int index, boolean down, long now) {
        if (index < 0 || index >= buttonCount) return;
        registry.set(button[index], down ? ButtonState.DOWN : ButtonState.UP);
        // PENDING: shared timeout system — See NEXT_STEPS.md
    }

    public void updateScroll(double dx, double dy, long now) {
        registry.beginBatch();
        try {
            registry.setArrRef(scrollDelta, new double[]{dx, dy});

            if (lastScrollNanos != Long.MIN_VALUE) {
                double dt = (now - lastScrollNanos) * 1e-9;
                if (dt > 0) {
                    // Reset regression buffer if this is a new gesture (gap > 200ms)
                    if (dt > 0.2) { regCount = 0; regHead = 0; lastScrollVx = 0; lastScrollVy = 0; }
                    // Per-axis velocity — carry last non-zero value to avoid touchpad alternation zeros
                    double vx = lastScrollVx, vy = lastScrollVy;
                    if (dx != 0 && lastScrollXNanos != Long.MIN_VALUE) {
                        double dtx = (now - lastScrollXNanos) * 1e-9;
                        if (dtx > 0) vx = dx / dtx;
                    }
                    if (dy != 0 && lastScrollYNanos != Long.MIN_VALUE) {
                        double dty = (now - lastScrollYNanos) * 1e-9;
                        if (dty > 0) vy = dy / dty;
                    }
                    lastScrollVx = vx;
                    lastScrollVy = vy;

                    double[] prevSV = scrollVelocity.getValue();
                    registry.setArrRef(scrollVelocity, new double[]{vx, vy});

                    // Push sample into regression buffer
                    if (regCount == 0) regT0 = now * 1e-9;
                    double t = now * 1e-9 - regT0;
                    regT [regHead] = t; regVx[regHead] = vx; regVy[regHead] = vy;
                    regHead = (regHead + 1) % SCROLL_REG_N;
                    if (regCount < SCROLL_REG_N) regCount++;

                    double ax = 0, ay = 0;
                    if (regCount >= 2) {
                        double[] s = regSlope(regT, regVx, regVy, regHead, regCount, SCROLL_REG_N);
                        ax = s[0]; ay = s[1];
                    }
                    registry.setArrRef(scrollAcceleration, new double[]{ax, ay});

                    // Scroll smooth — EMA on velocity and acceleration
                    double alpha = 1.0 - Math.exp(-dt * 1e9 / scrollSmoothingNanos);
                    double[] svs = scrollVelocitySmooth.getValue();
                    double svx = svs[0] + alpha * (vx - svs[0]);
                    double svy = svs[1] + alpha * (vy - svs[1]);
                    registry.setArrRef(scrollVelocitySmooth, new double[]{svx, svy});

                    double[] sas = scrollAccelerationSmooth.getValue();
                    registry.setArrRef(scrollAccelerationSmooth, new double[]{
                            sas[0] + alpha * (ax - sas[0]),
                            sas[1] + alpha * (ay - sas[1])});

                    // scrollJerk / scrollJerkSmooth left at zero — not meaningful for discrete scroll events.
                }
            }
            // PENDING: shared timeout system — See NEXT_STEPS.md
        } finally {
            lastScrollNanos = now;
            if (dx != 0) lastScrollXNanos = now;
            if (dy != 0) lastScrollYNanos = now;
            registry.endBatch();
        }
    }

    // -------------------------------------------------------------------------
    // One-euro filter
    // -------------------------------------------------------------------------

    /** Returns [filteredX, filteredY, newDxHat, newDyHat] */
    /** Least-squares linear regression slope of (x,y) over t. Returns [slopeX, slopeY]. */
    private double[] regSlope(double[] t, double[] fx, double[] fy, int head, int count, int n) {
        double sumT = 0, sumFx = 0, sumFy = 0, sumT2 = 0, sumTFx = 0, sumTFy = 0;
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + n) % n;
            double ti = t[idx];
            sumT   += ti;   sumFx  += fx[idx];  sumFy  += fy[idx];
            sumT2  += ti * ti;
            sumTFx += ti * fx[idx];
            sumTFy += ti * fy[idx];
        }
        double denom = count * sumT2 - sumT * sumT;
        if (Math.abs(denom) < 1e-12) return new double[]{0, 0};
        return new double[]{
            (count * sumTFx - sumT * sumFx) / denom,
            (count * sumTFy - sumT * sumFy) / denom
        };
    }

    private double[] oneEuro2(double x, double y,
                               double xHat, double yHat,
                               double dxHat, double dyHat,
                               double dt) {
        double dAlpha   = alpha(dCutoff, dt);
        double newDxHat = dxHat + dAlpha * ((x - xHat) / dt - dxHat);
        double newDyHat = dyHat + dAlpha * ((y - yHat) / dt - dyHat);
        double speed    = Math.sqrt(newDxHat * newDxHat + newDyHat * newDyHat);
        double a        = alpha(minCutoff + beta * speed, dt);
        return new double[]{xHat + a * (x - xHat), yHat + a * (y - yHat), newDxHat, newDyHat};
    }

    private double alpha(double cutoff, double dt) {
        double tau = 1.0 / (2.0 * Math.PI * cutoff);
        return 1.0 / (1.0 + tau / dt);
    }
}
