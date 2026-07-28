package io.github.yetyman.vulkan.foundation.ui.input;

import io.github.yetyman.vulkan.foundation.ui.UILayer;
import java.util.List;

/**
 * Provides the current set of input-accepting layers to a dispatch strategy.
 * Implementations return layers sorted ascending by order().
 * The strategy calls this each dispatch cycle — the provider is the single
 * source of truth for what layers exist.
 */
@FunctionalInterface
public interface LayerProvider {
    List<UILayer> inputLayers();
}
