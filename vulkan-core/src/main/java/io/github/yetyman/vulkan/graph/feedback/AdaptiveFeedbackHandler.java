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
 */
public class AdaptiveFeedbackHandler implements FeedbackHandler {

    private final double alpha;     // EMA smoothing factor (0..1), lower = more smoothing
    private final double momentum;  // momentum term to prevent oscillation
    private final Map<String, Double> weights = new HashMap<>();
    private final Map<String, Double> velocities = new HashMap<>();

    /**
     * @param alpha EMA smoothing factor. 0.1 = very smooth, 0.5 = responsive
     * @param momentum momentum coefficient. 0.9 = high momentum, 0.0 = none
     */
    public AdaptiveFeedbackHandler(double alpha, double momentum) {
        this.alpha = alpha;
        this.momentum = momentum;
    }

    /** Default: alpha=0.2, momentum=0.8 */
    public AdaptiveFeedbackHandler() {
        this(0.2, 0.8);
    }

    @Override
    public void onStats(FrameStats stats) {
        for (Map.Entry<String, NodeStats> entry : stats.nodeStats().entrySet()) {
            String name = entry.getKey();
            double measured = entry.getValue().gpuMs();

            double currentWeight = weights.getOrDefault(name, measured);
            double error = measured - currentWeight;

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
        return !weights.isEmpty();
    }
}
