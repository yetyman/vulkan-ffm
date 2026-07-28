package io.github.yetyman.vulkan.foundation.ecs;

import java.util.ArrayList;
import java.util.List;

/**
 * Enum-keyed property change notification for components.
 *
 * Each component type defines its own small nested enum of properties that can change.
 * Observers register per-property on a specific component instance via this notifier.
 * When a property changes, only observers of that specific property are invoked.
 *
 * Not tree-propagated. Not string-based. Not broadcast. Direct registration on a
 * specific component instance, keyed by enum value.
 *
 * Usage in a component:
 * <pre>
 * public class TextComponent implements Component {
 *     public enum Prop { TEXT, FONT_SIZE, COLOR }
 *
 *     private final PropertyNotifier&lt;Prop&gt; notifier = new PropertyNotifier&lt;&gt;(Prop.class);
 *     private String text = "";
 *
 *     public String text() { return text; }
 *     public void setText(String value) {
 *         this.text = value;
 *         notifier.fire(Prop.TEXT);
 *     }
 *
 *     public PropertyNotifier&lt;Prop&gt; properties() { return notifier; }
 * }
 * </pre>
 *
 * Usage by an observer (typically a render component):
 * <pre>
 * textComponent.properties().observe(TextComponent.Prop.TEXT, () -&gt; {
 *     // text changed, update my cached vertex data at my slot
 * });
 * </pre>
 *
 * Performance:
 * - fire(): O(listeners for that property) — typically 1-2.
 * - observe/unobserve: O(1) amortized (ArrayList add/remove).
 * - Zero allocation on fire path. No event objects created.
 * - Storage: one Runnable[] per enum value (lazily allocated per-property on first observe).
 *
 * @param <E> the property enum type defined by the owning component
 */
public final class PropertyNotifier<E extends Enum<E>> {

    // One list of listeners per enum value. Indexed by ordinal for O(1) access.
    // Lazily allocated: null until the first observer registers for that property.
    private final List<Runnable>[] listeners;

    // Bulk observer path: direct calls instead of per-instance lambdas.
    // Supports multiple bulk observers (e.g., renderer + spatial grid).
    private BulkPropertyObserver[] bulkObservers;
    private Component[] bulkSources;
    private int[] bulkSlotIndices;
    private int bulkObserverCount = 0;

    @SuppressWarnings("unchecked")
    public PropertyNotifier(Class<E> enumType) {
        E[] constants = enumType.getEnumConstants();
        this.listeners = new List[constants.length];
    }

    /**
     * Registers a bulk observer with a slot index. Multiple bulk observers can be
     * registered on the same notifier (e.g., renderer and spatial grid).
     *
     * @param observer the bulk observer
     * @param source the component instance that owns this notifier
     * @param slotIndex the array slot assigned to this component by the registering system
     */
    public void setBulkObserver(BulkPropertyObserver observer, Component source, int slotIndex) {
        if (bulkObservers == null) {
            bulkObservers = new BulkPropertyObserver[2];
            bulkSources = new Component[2];
            bulkSlotIndices = new int[2];
        } else if (bulkObserverCount >= bulkObservers.length) {
            int newCap = bulkObservers.length * 2;
            BulkPropertyObserver[] newObs = new BulkPropertyObserver[newCap];
            Component[] newSrc = new Component[newCap];
            int[] newSlots = new int[newCap];
            System.arraycopy(bulkObservers, 0, newObs, 0, bulkObserverCount);
            System.arraycopy(bulkSources, 0, newSrc, 0, bulkObserverCount);
            System.arraycopy(bulkSlotIndices, 0, newSlots, 0, bulkObserverCount);
            bulkObservers = newObs;
            bulkSources = newSrc;
            bulkSlotIndices = newSlots;
        }
        bulkObservers[bulkObserverCount] = observer;
        bulkSources[bulkObserverCount] = source;
        bulkSlotIndices[bulkObserverCount] = slotIndex;
        bulkObserverCount++;
    }

    /**
     * Updates the slot index for a specific bulk observer.
     */
    public void updateBulkSlotIndex(BulkPropertyObserver observer, int newSlotIndex) {
        for (int i = 0; i < bulkObserverCount; i++) {
            if (bulkObservers[i] == observer) {
                bulkSlotIndices[i] = newSlotIndex;
                return;
            }
        }
    }

    /**
     * Updates the slot index for the first registered bulk observer (backward compat).
     */
    public void updateBulkSlotIndex(int newSlotIndex) {
        if (bulkObserverCount > 0) {
            bulkSlotIndices[0] = newSlotIndex;
        }
    }

    /**
     * Removes a specific bulk observer.
     */
    public void removeBulkObserver(BulkPropertyObserver observer) {
        for (int i = 0; i < bulkObserverCount; i++) {
            if (bulkObservers[i] == observer) {
                // Shift down
                int remaining = bulkObserverCount - i - 1;
                if (remaining > 0) {
                    System.arraycopy(bulkObservers, i + 1, bulkObservers, i, remaining);
                    System.arraycopy(bulkSources, i + 1, bulkSources, i, remaining);
                    System.arraycopy(bulkSlotIndices, i + 1, bulkSlotIndices, i, remaining);
                }
                bulkObserverCount--;
                bulkObservers[bulkObserverCount] = null;
                bulkSources[bulkObserverCount] = null;
                return;
            }
        }
    }

    /**
     * Clears all bulk observer registrations.
     */
    public void clearBulkObserver() {
        if (bulkObservers != null) {
            for (int i = 0; i < bulkObserverCount; i++) {
                bulkObservers[i] = null;
                bulkSources[i] = null;
            }
        }
        bulkObserverCount = 0;
    }

    /** @return true if any bulk observer is registered. */
    public boolean hasBulkObserver() {
        return bulkObserverCount > 0;
    }

    /**
     * Registers a listener for a specific property.
     *
     * @param property the property to observe
     * @param listener the callback invoked when this property changes
     */
    public void observe(E property, Runnable listener) {
        int idx = property.ordinal();
        if (listeners[idx] == null) {
            listeners[idx] = new ArrayList<>(2); // most properties have 1-2 observers
        }
        listeners[idx].add(listener);
    }

    /**
     * Removes a listener for a specific property.
     *
     * @param property the property to stop observing
     * @param listener the callback to remove
     */
    public void unobserve(E property, Runnable listener) {
        int idx = property.ordinal();
        List<Runnable> list = listeners[idx];
        if (list != null) {
            list.remove(listener);
        }
    }

    /**
     * Fires notification for a property change.
     * If a bulk observer is registered, calls it directly with (source, ordinal, slotIndex).
     * Then invokes any per-property listeners.
     * Zero allocation — no event objects, just direct calls.
     *
     * @param property the property that changed
     */
    public void fire(E property) {
        int ordinal = property.ordinal();

        // Bulk observer path: call all registered bulk observers
        for (int i = 0; i < bulkObserverCount; i++) {
            bulkObservers[i].onPropertyChanged(bulkSources[i], ordinal, bulkSlotIndices[i]);
        }

        // Per-property listener path
        List<Runnable> list = listeners[ordinal];
        if (list != null) {
            for (int i = 0, size = list.size(); i < size; i++) {
                list.get(i).run();
            }
        }
    }

    /**
     * Fires notification for multiple properties at once (e.g., after a bulk update).
     *
     * @param properties the properties that changed
     */
    @SafeVarargs
    public final void fire(E... properties) {
        for (E property : properties) {
            fire(property);
        }
    }

    /**
     * Removes all listeners for all properties.
     * Typically called during component close/detach.
     */
    public void clear() {
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] != null) {
                listeners[i].clear();
            }
        }
    }

    /**
     * Removes all listeners for a specific property.
     */
    public void clear(E property) {
        List<Runnable> list = listeners[property.ordinal()];
        if (list != null) {
            list.clear();
        }
    }

    /**
     * @return the number of observers registered for a property.
     */
    public int observerCount(E property) {
        List<Runnable> list = listeners[property.ordinal()];
        return list != null ? list.size() : 0;
    }

    /**
     * @return true if any observer is registered for the given property.
     */
    public boolean hasObservers(E property) {
        List<Runnable> list = listeners[property.ordinal()];
        return list != null && !list.isEmpty();
    }
}
