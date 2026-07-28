package io.github.yetyman.vulkan.foundation.ui.input;

/**
 * Strategy for dispatching input events across UI layers.
 *
 * The default implementation is {@link UIInputDispatcher} which performs
 * capture/bubble dual-pass dispatch. Alternative implementations might use
 * focus-chain dispatch, hit-test dispatch, priority queues, or single-pass models.
 *
 * The strategy receives a {@link LayerProvider} at bind time and queries it
 * each dispatch cycle for the current layer set. This avoids duplicated layer
 * state and handles dynamic layer changes transparently.
 */
public interface InputDispatchStrategy {

    /**
     * Binds this strategy to a layer source. Called once when the owning
     * UIComposite is built. The provider returns layers sorted ascending by order().
     */
    void bind(LayerProvider layerProvider);

    /**
     * Dispatches a single input event through this strategy's dispatch model.
     * Must be called on the dispatch thread only.
     */
    void dispatch(InputEvent event);
}
