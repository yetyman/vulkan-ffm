package io.github.yetyman.vulkan.foundation.ui.input;

import io.github.yetyman.vulkan.foundation.ui.UILayer;
import java.util.List;

/**
 * Orchestrates capture/bubble input dispatch across layers.
 *
 * Capture phase: highest order (frontmost) to lowest (backmost).
 *   - Layers annotate context, can stop propagation.
 *   - Use case: 3D layer adds world-space hit coordinates.
 *
 * Bubble phase: lowest order (backmost) to highest (frontmost).
 *   - Layers react to event with full context from capture.
 *   - Use case: HUD layer reads world coords, shows tooltip.
 *
 * Between phases: propagation stopped flag resets, context persists.
 */
public class UIInputDispatcher {

    /**
     * Dispatches an input event through capture then bubble phases across the given layers.
     * The layers list is expected to already be sorted ascending by UILayer.order().
     */
    public void dispatch(InputEvent event, List<UILayer> layers) {
        // --- Capture phase: highest order first (index layers.size()-1 down to 0) ---
        event.setPhase(InputPhase.CAPTURE);
        event.propagation().markCaptureStart();
        for (int i = layers.size() - 1; i >= 0; i--) {
            UILayer layer = layers.get(i);
            if (!layer.acceptsInput()) continue;
            boolean consumed = layer.handleInput(event);
            if (consumed || event.propagation().isStopped()) break;
        }

        // Reset stop flags for bubble. Context + handled persist.
        event.propagation().resetForBubble();

        // --- Bubble phase: lowest order first (index 0 up to layers.size()-1) ---
        event.setPhase(InputPhase.BUBBLE);
        event.propagation().markBubbleStart();
        for (int i = 0; i < layers.size(); i++) {
            UILayer layer = layers.get(i);
            if (!layer.acceptsInput()) continue;
            boolean consumed = layer.handleInput(event);
            if (consumed || event.propagation().isStopped()) break;
        }

        event.propagation().markFinished();
    }
}
