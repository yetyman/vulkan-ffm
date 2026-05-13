package io.github.yetyman.vulkan.graph.feedback;

import io.github.yetyman.vulkan.graph.nodes.NodeStats;

import java.util.HashMap;
import java.util.Map;

/**
 * Feedback handler that tracks per-node GPU cost over a sliding window and exposes
 * weight adjustments for the adaptive scheduler. Uses exponential moving average
 * with momentum to prevent oscillation on noisy frames.
 *
 * The "gradient" is the delta between predicted cost (current weight) and measured cost
 * (actual GPU time). A single slow frame does not cause rescheduling -- the EMA smooths it.
 *
 * Learning rate decay: the effective alpha decays as the schedule stabilizes (low variance
 * in measurements) but never reaches zero (minAlpha floor), since scene content changes
 * make this a non-stationary target. When variance spikes (scene change), the learning rate
 * resets to the initial alpha to allow fast re-convergence.
 */
public class AdaptiveFeedbackHandler implements FeedbackHandler {

    private final double initialAlpha;
    private final double minAlpha;
    private final double momentum;
    private final double decayRate;
    private final double varianceResetThreshold;
    private final int warmupFrames;

    private final Map<String, Double> weights = new HashMap<>();
    private final Map<String, Double> velocities = new HashMap<>();
    private final Map<String, Double> effectiveAlphas = new HashMap<>();
    private final Map<String, Double> varianceEstimates = new HashMap<>();

    private int frameCount = 0;

    /**
     * @param initialAlpha initial EMA smoothing factor (0..1), lower = more smoothing
     * @param minAlpha minimum alpha floor (never decays below this)
     * @param momentum momentum coefficient. 0.9 = high momentum, 0.0 = none
     * @param decayRate per-frame decay multiplier for alpha (e.g. 0.995 = slow decay)
     * @param varianceResetThreshold if measured variance exceeds this multiple of the EMA,
     *                               reset alpha to initialAlpha (scene change detection)
     * @param warmupFrames number of frames before the handler is considered warmed up
     */
    public AdaptiveFeedbackHandler(double initialAlpha, double minAlpha, double momentum,
                                   double decayRate, double varianceResetThreshold,
                                   int warmupFrames) {
        this.initialAlpha = initialAlpha;
        this.minAlpha = minAlpha;
        this.momentum = momentum;
        this.decayRate = decayRate;
        this.varianceResetThreshold = varianceResetThreshold;
        this.warmupFrames = warmupFrames;
    }

    /** Default: alpha=0.3, minAlpha=0.05, momentum=0.8, decay=0.995, variance threshold=3.0, warmup=8 */
    public AdaptiveFeedbackHandler() {
        this(0.3, 0.05, 0.8, 0.995, 3.0, 8);
    }

    @Override
    public void onStats(FrameStats stats) {
        frameCount++;

        for (Map.Entry<String, NodeStats> entry : stats.nodeStats().entrySet()) {
            String name = entry.getKey();
            double measured = entry.getValue().gpuMs();

            double currentWeight = weights.getOrDefault(name, measured);
            double error = measured - currentWeight;

            // Update variance estimate (EMA of squared error)
            double prevVariance = varianceEstimates.getOrDefault(name, 0.0);
            double newVariance = 0.9 * prevVariance + 0.1 * (error * error);
            varianceEstimates.put(name, newVariance);

            // Determine effective alpha with decay and variance-based reset
            double alpha = effectiveAlphas.getOrDefault(name, initialAlpha);

            // Detect scene change: if error is much larger than expected variance, reset alpha
            if (prevVariance > 0 && (error * error) > varianceResetThreshold * varianceResetThreshold * prevVariance) {
                alpha = initialAlpha;
            } else {
                // Decay alpha toward minAlpha
                alpha = Math.max(minAlpha, alpha * decayRate);
            }
            effectiveAlphas.put(name, alpha);

            // EMA with momentum
            double velocity = velocities.getOrDefault(name, 0.0);
            velocity = momentum * velocity + alpha * error;
            velocities.put(name, velocity);

            double newWeight = currentWeight + velocity;
            weights.put(name, Math.max(0.0, newWeight));
        }
    }

    /**
     * @return the smoothed GPU cost estimate for a node in milliseconds.
     * Used by AdaptiveSchedulingStrategy to make queue assignment decisions.
     */
    public double weight(String nodeName) {
        return weights.getOrDefault(nodeName, 0.0);
    }

    /** @return all current weights (node name -> estimated GPU ms) */
    public Map<String, Double> weights() {
        return Map.copyOf(weights);
    }

    /** @return true if enough data has been collected for meaningful scheduling */
    public boolean isWarmedUp() {
        return frameCount >= warmupFrames && !weights.isEmpty();
    }

    /** @return the current effective alpha for a node (for diagnostics) */
    public double effectiveAlpha(String nodeName) {
        return effectiveAlphas.getOrDefault(nodeName, initialAlpha);
    }

    /** @return total frames processed */
    public int frameCount() { return frameCount; }
}
