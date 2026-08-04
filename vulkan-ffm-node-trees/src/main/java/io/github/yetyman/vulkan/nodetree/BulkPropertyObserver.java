package io.github.yetyman.vulkan.nodetree;

/**
 * Optimized bulk property change observer for tree-scoped renderers.
 *
 * Instead of registering a per-instance lambda on every component's PropertyNotifier,
 * a tree-scoped renderer registers itself ONCE as the bulk observer. When any property
 * fires, the observer receives a direct call with:
 * - The source component that changed
 * - The property ordinal (enum.ordinal())
 * - The slot index assigned to that component in the renderer's backing array
 *
 * This eliminates per-instance lambda allocation and list iteration on the hot path.
 * One direct method call per property change, with all the information needed to do
 * a targeted write into the correct array slot.
 *
 * Usage by a tree-scoped renderer:
 * <pre>
 * // During afterResolve, set yourself as the bulk observer on each component's notifier
 * view.forEach((node, rectComponent) -> {
 *     int slot = allocateSlot();
 *     rectComponent.properties().setBulkObserver(this, slot);
 * });
 * </pre>
 */
@FunctionalInterface
public interface BulkPropertyObserver {

    /**
     * Called when a property changes on a component that has this observer registered.
     *
     * @param source the component instance whose property changed
     * @param propertyOrdinal the ordinal of the property that changed (enum.ordinal())
     * @param slotIndex the slot index assigned to this component by the registering renderer
     */
    void onPropertyChanged(Component source, int propertyOrdinal, int slotIndex);
}
