package io.github.yetyman.vulkan.state;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A typed registry of named state slots with change listeners and batch support.
 * <p>
 * Slots are defined once at construction time via {@code addState} and locked in by {@link #seal()}.
 * All {@code set}, {@code get}, and {@code on} calls are validated against declared slots —
 * unknown names throw {@link IllegalArgumentException}.
 * <p>
 * For hot paths, obtain a typed slot handle via {@code getSlot*(name)} and use the
 * handle-based {@code set} overloads to bypass map lookup entirely.
 * <p>
 * Listeners fire immediately on {@code set} outside a batch, or all together when
 * the batch is closed. Batches are reentrant — nested {@link Batch#begin()} calls are safe.
 * Outside a batch, {@code set} calls made inside a listener fire immediately (recursive dispatch).
 * Inside a batch, {@code set} calls made inside a listener during flush are implicitly batched —
 * deferred and flushed in subsequent passes until the registry reaches quiescence.
 * <p>
 * Thread safety: concurrent {@code set} calls from multiple threads are safe.
 * Listener registration ({@code on}) is safe at any time via copy-on-write.
 * <p>
 * Preferred usage:
 * <pre>{@code
 * registry.beginBatch();
 * try {
 *     registry.set(floatSlot, 1.0f);
 *     registry.set(intSlot, 42);
 * } finally {
 *     registry.endBatch();
 * }
 * }</pre>
 */
public final class StateRegistry {

    private final Map<String, StateSlot> slotMap = new HashMap<>();
    private StateSlot[] slots = new StateSlot[0];
    private boolean sealed = false;

    private final BatchStrategy batchStrategy;
    private final boolean concurrent;
    private int excessivePassThreshold = 20;
    private int hardExitThreshold = 100;

    private final java.util.concurrent.atomic.AtomicInteger batchDepth = new java.util.concurrent.atomic.AtomicInteger(0);
    /**
     * Fast-path flag: true when batchDepth > 0.
     * Read via getOpaque — atomic but no barrier. The batchDepth atomic already provides
     * the happens-before needed at batch open/close; this flag is only a cheap same-thread hint.
     */
    private static final java.lang.invoke.VarHandle IN_BATCH;
    static {
        try { IN_BATCH = java.lang.invoke.MethodHandles.lookup().findVarHandle(StateRegistry.class, "inBatch", boolean.class); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private boolean inBatch = false;

    private volatile Runnable[] anyListeners = new Runnable[0];

    public StateRegistry(BatchStrategy strategy, boolean concurrent) {
        this.batchStrategy = strategy;
        this.concurrent = concurrent;
        this.batchStrategy.init(0);
    }

    public StateRegistry(BatchStrategy strategy) {
        this(strategy, false);
    }

    public StateRegistry(boolean concurrent) {
        this(BatchStrategy.definitionOrder(), concurrent);
    }

    public StateRegistry() {
        this(BatchStrategy.definitionOrder(), false);
    }

    // ------------------------------------------------------------------
    // Batch
    // ------------------------------------------------------------------

    /**
     * Opens (or re-enters) a batch. Reentrant — nested beginBatch/endBatch pairs compose
     * correctly: the outermost endBatch triggers the flush.
     * <p>
     * Any {@code set()} calls made inside a listener during flush are implicitly batched and
     * flushed in subsequent passes until the registry reaches quiescence (no slots dirty).
     * <p>
     * Always pair with {@link #endBatch()} in a try/finally:
     * <pre>{@code
     * registry.beginBatch();
     * try {
     *     registry.set(floatSlot, 1.0f);
     *     registry.set(intSlot, 42);
     * } finally {
     *     registry.endBatch();
     * }
     * }</pre>
     */
    public void beginBatch() {
        if (batchDepth.getAndIncrement() == 0) {
            batchStrategy.reset();
            IN_BATCH.setOpaque(this, true);
        }
    }

    /**
     * Closes one level of batch nesting. Flushes to quiescence when the outermost endBatch is reached.
     */
    public void endBatch() {
        if (batchDepth.decrementAndGet() == 0) {
            flushToQuiescence();
        }
    }

    private void flushToQuiescence() {
        boolean anyEverFired = false;
        int passes = 0;
        boolean anyFired;
        do {
            anyFired = batchStrategy.flush(slots);
            if (anyFired) anyEverFired = true;
            passes++;
            if (passes == excessivePassThreshold) {
                    System.err.println("[StateRegistry] WARNING: flush has not reached quiescence after " + passes + " passes. " +
                            "A listener may be unconditionally re-dirtying a slot on every flush.");
            }
            if (passes >= hardExitThreshold) {
                    System.err.println("[StateRegistry] ERROR: flush forcibly terminated after " + passes + " passes — quiescence not reached.");
                    break;
            }
        } while (anyFired);
        IN_BATCH.setOpaque(this, false);
        flushAny(anyEverFired);
    }

    // ------------------------------------------------------------------
    // Slot definition
    // ------------------------------------------------------------------

    /** Defines a boolean slot. Returns the slot handle for caching. */
    public StateSlot.BoolSlot addState(String name, boolean initial) {
        checkNotSealed();
        return (StateSlot.BoolSlot) addSlot(new StateSlot.BoolSlot(name, slots.length, initial, concurrent));
    }

    /** Defines an int slot. Returns the slot handle for caching. */
    public StateSlot.IntSlot addState(String name, int initial) {
        checkNotSealed();
        return (StateSlot.IntSlot) addSlot(new StateSlot.IntSlot(name, slots.length, initial, concurrent));
    }

    /** Defines a float slot. Returns the slot handle for caching. */
    public StateSlot.FloatSlot addState(String name, float initial) {
        checkNotSealed();
        return (StateSlot.FloatSlot) addSlot(new StateSlot.FloatSlot(name, slots.length, initial, concurrent));
    }

    /** Defines a long slot. Returns the slot handle for caching. */
    public StateSlot.LongSlot addState(String name, long initial) {
        checkNotSealed();
        return (StateSlot.LongSlot) addSlot(new StateSlot.LongSlot(name, slots.length, initial, concurrent));
    }

    /** Defines a double slot. Returns the slot handle for caching. */
    public StateSlot.DoubleSlot addState(String name, double initial) {
        checkNotSealed();
        return (StateSlot.DoubleSlot) addSlot(new StateSlot.DoubleSlot(name, slots.length, initial, concurrent));
    }

    /**
     * Defines a doubles (double[]) slot. All provided values form the initial array.
     * {@code sm.addStateArr("location", 0.0, 0.0)} creates a two-element slot.
     */
    public StateSlot.DoublesSlot addStateArr(String name, double... initial) {
        checkNotSealed();
        return (StateSlot.DoublesSlot) addSlot(new StateSlot.DoublesSlot(name, slots.length, Arrays.copyOf(initial, initial.length), concurrent));
    }

    /**
     * Defines an ints (int[]) slot.
     */
    public StateSlot.IntsSlot addStateArr(String name, int... initial) {
        checkNotSealed();
        return (StateSlot.IntsSlot) addSlot(new StateSlot.IntsSlot(name, slots.length, Arrays.copyOf(initial, initial.length), concurrent));
    }

    /**
     * Defines a constrained string slot. Only values in {@code allowed} are accepted.
     * The first value is the initial value.
     */
    public StateSlot.ConstrainedStringSlot addState(String name, String... allowed) {
        checkNotSealed();
        if (allowed == null || allowed.length == 0)
            throw new IllegalArgumentException("String slot '" + name + "' requires at least one allowed value");
        return (StateSlot.ConstrainedStringSlot) addSlot(new StateSlot.ConstrainedStringSlot(name, slots.length, allowed, concurrent));
    }

    /** Defines an unconstrained string slot. Any string value is accepted. */
    public StateSlot.UnconstrainedStringSlot addStateString(String name, String initial) {
        checkNotSealed();
        return (StateSlot.UnconstrainedStringSlot) addSlot(new StateSlot.UnconstrainedStringSlot(name, slots.length, initial, concurrent));
    }

    /** Defines an object slot. Returns the slot handle for caching. */
    public <T> StateSlot.ObjectSlot addStateObject(String name, T initial) {
        checkNotSealed();
        return (StateSlot.ObjectSlot) addSlot(new StateSlot.ObjectSlot(name, slots.length, initial, concurrent));
    }

    /** Defines an enum slot constrained to the given enum type. Returns the slot handle for caching. */
    public <E extends Enum<E>> StateSlot.EnumSlot addState(String name, Class<E> type, E initial) {
        checkNotSealed();
        return (StateSlot.EnumSlot) addSlot(new StateSlot.EnumSlot(name, slots.length, type, initial, concurrent));
    }

    /** @return the number of flush passes after which a quiescence warning is printed. */
    public int getExcessivePassThreshold() { return excessivePassThreshold; }

    /**
     * Sets the number of flush passes after which a quiescence warning is printed.
     * Set to {@link Integer#MAX_VALUE} to disable the warning entirely.
     */
    public void setExcessivePassThreshold(int threshold) { this.excessivePassThreshold = threshold; }

    /** @return the number of flush passes after which flush is forcibly terminated. */
    public int getHardExitThreshold() { return hardExitThreshold; }

    /**
     * Sets the number of flush passes after which flush is forcibly terminated.
     * Set to {@link Integer#MAX_VALUE} to disable the hard exit entirely.
     */
    public void setHardExitThreshold(int threshold) { this.hardExitThreshold = threshold; }

    /**
     * Seals the registry. No further {@code addState} calls are permitted after this.
     * Must be called before any {@code set}, {@code get}, or {@code on} calls.
     */
    public void seal() {
        sealed = true;
        batchStrategy.init(slots.length);
    }

    private StateSlot addSlot(StateSlot slot) {
        if (slotMap.containsKey(slot.name))
            throw new IllegalArgumentException("Slot already defined: " + slot.name);
        slotMap.put(slot.name, slot);
        slots = Arrays.copyOf(slots, slots.length + 1);
        slots[slot.index] = slot;
        return slot;
    }

    // ------------------------------------------------------------------
    // Slot handle accessors — cache these for hot-path use
    // ------------------------------------------------------------------

    public StateSlot              getSlot(String name)        { return requireSlot(name); }
    public StateSlot.BoolSlot     getSlotBoolean(String name) { return boolSlot(name); }
    public StateSlot.IntSlot      getSlotInt(String name)     { return intSlot(name); }
    public StateSlot.FloatSlot    getSlotFloat(String name)   { return floatSlot(name); }
    public StateSlot.LongSlot     getSlotLong(String name)    { return longSlot(name); }
    public StateSlot.DoubleSlot   getSlotDouble(String name)  { return doubleSlot(name); }
    public StateSlot.DoublesSlot  getSlotDoubles(String name) { return doublesSlot(name); }
    public StateSlot.IntsSlot     getSlotInts(String name)    { return intsSlot(name); }
    public StateSlot.ConstrainedStringSlot   getSlotConstrainedString(String name)   { return constrainedStringSlot(name); }
    public StateSlot.UnconstrainedStringSlot getSlotUnconstrainedString(String name) { return unconstrainedStringSlot(name); }
    public StateSlot.ObjectSlot   getSlotObject(String name)  { return objectSlot(name); }
    public StateSlot.EnumSlot     getSlotEnum(String name)    { return enumSlot(name); }

    // ------------------------------------------------------------------
    // Listener registration
    // ------------------------------------------------------------------

    public void on(String name, StateSlot.BooleanListener listener)  { boolSlot(name).addListener(listener); }
    public void on(String name, StateSlot.IntListener listener)      { intSlot(name).addListener(listener); }
    public void on(String name, StateSlot.FloatListener listener)    { floatSlot(name).addListener(listener); }
    public void on(String name, StateSlot.LongListener listener)     { longSlot(name).addListener(listener); }
    public void on(String name, StateSlot.DoubleListener listener)   { doubleSlot(name).addListener(listener); }
    public void on(String name, StateSlot.DoublesListener listener)  { doublesSlot(name).addListener(listener); }
    public void on(String name, StateSlot.IntsListener listener)     { intsSlot(name).addListener(listener); }
    public void on(String name, StateSlot.StringListener listener) {
        StateSlot s = anyStringSlot(name);
        if (s instanceof StateSlot.ConstrainedStringSlot c) c.addListener(listener);
        else ((StateSlot.UnconstrainedStringSlot) s).addListener(listener);
    }
    public <T> void onObject(String name, StateSlot.ObjectListener<T> listener) { objectSlot(name).addListener(listener); }
    public <E extends Enum<E>> void onEnum(String name, StateSlot.EnumListener<E> listener) { enumSlot(name).addListener(listener); }

    public void once(String name, StateSlot.BooleanListener listener)  { boolSlot(name).addOnceListener(listener); }
    public void once(String name, StateSlot.IntListener listener)      { intSlot(name).addOnceListener(listener); }
    public void once(String name, StateSlot.FloatListener listener)    { floatSlot(name).addOnceListener(listener); }
    public void once(String name, StateSlot.LongListener listener)     { longSlot(name).addOnceListener(listener); }
    public void once(String name, StateSlot.DoubleListener listener)   { doubleSlot(name).addOnceListener(listener); }
    public void once(String name, StateSlot.DoublesListener listener)  { doublesSlot(name).addOnceListener(listener); }
    public void once(String name, StateSlot.IntsListener listener)     { intsSlot(name).addOnceListener(listener); }
    public void once(String name, StateSlot.StringListener listener) {
        StateSlot s = anyStringSlot(name);
        if (s instanceof StateSlot.ConstrainedStringSlot c) c.addOnceListener(listener);
        else ((StateSlot.UnconstrainedStringSlot) s).addOnceListener(listener);
    }
    public <T> void onceObject(String name, StateSlot.ObjectListener<T> listener) { objectSlot(name).addOnceListener(listener); }
    public <E extends Enum<E>> void onceEnum(String name, StateSlot.EnumListener<E> listener) { enumSlot(name).addOnceListener(listener); }

    /**
     * Registers a listener that fires once per flush cycle when any slot changed.
     * Fires after all per-slot listeners.
     */
    public void onAny(Runnable listener) {
        synchronized (this) {
            Runnable[] old = anyListeners;
            Runnable[] next = Arrays.copyOf(old, old.length + 1);
            next[old.length] = listener;
            anyListeners = next;
        }
    }

    /** Removes a previously registered onAny listener. */
    public void removeOnAny(Runnable listener) {
        synchronized (this) {
            Runnable[] old = anyListeners;
            int idx = -1;
            for (int i = 0; i < old.length; i++) if (old[i] == listener) { idx = i; break; }
            if (idx < 0) return;
            Runnable[] next = new Runnable[old.length - 1];
            System.arraycopy(old, 0, next, 0, idx);
            System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
            anyListeners = next;
        }
    }

    // ------------------------------------------------------------------
    // Set — string-keyed (ergonomic)
    // ------------------------------------------------------------------

    public void set(String name, boolean value) {
        StateSlot.BoolSlot slot = boolSlot(name);
        boolean prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    public void set(String name, int value) {
        StateSlot.IntSlot slot = intSlot(name);
        int prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    public void set(String name, float value) {
        StateSlot.FloatSlot slot = floatSlot(name);
        float prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    public void set(String name, long value) {
        StateSlot.LongSlot slot = longSlot(name);
        long prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    public void set(String name, double value) {
        StateSlot.DoubleSlot slot = doubleSlot(name);
        double prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    /** Sets the doubles slot by reference. No copy — caller retains ownership. Marks dirty unconditionally. */
    public void setArrRef(String name, double[] value) {
        StateSlot.DoublesSlot slot = doublesSlot(name);
        double[] prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, prev);
    }

    /** Sets the ints slot by reference. No copy — caller retains ownership. Marks dirty unconditionally. */
    public void setArrRef(String name, int[] value) {
        StateSlot.IntsSlot slot = intsSlot(name);
        int[] prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, prev);
    }

    /** Sets the doubles slot with a defensive copy. Marks dirty unconditionally. */
    public void setArrSafe(String name, double[] value) {
        StateSlot.DoublesSlot slot = doublesSlot(name);
        double[] prev = slot.getValue(); slot.setValue(Arrays.copyOf(value, value.length));
        markAndMaybeFlushRef(slot, prev);
    }

    /** Sets the ints slot with a defensive copy. Marks dirty unconditionally. */
    public void setArrSafe(String name, int[] value) {
        StateSlot.IntsSlot slot = intsSlot(name);
        int[] prev = slot.getValue(); slot.setValue(Arrays.copyOf(value, value.length));
        markAndMaybeFlushRef(slot, prev);
    }

    public void set(String name, String value) {
        StateSlot s = anyStringSlot(name);
        if (s instanceof StateSlot.ConstrainedStringSlot slot) {
            if (!slot.isAllowed(value))
                throw new IllegalArgumentException("Value '" + value + "' not allowed for slot '" + name + "'");
            String prev = slot.getValue(); slot.setValue(value);
            markAndMaybeFlushRef(slot, !prev.equals(value), prev);
        } else {
            StateSlot.UnconstrainedStringSlot slot = (StateSlot.UnconstrainedStringSlot) s;
            String prev = slot.getValue(); slot.setValue(value);
            markAndMaybeFlushRef(slot, !prev.equals(value), prev);
        }
    }

    public <T> void setObject(String name, T value) {
        StateSlot.ObjectSlot slot = objectSlot(name);
        Object prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, prev);
    }

    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> void set(String name, E value) {
        StateSlot.EnumSlot slot = enumSlot(name);
        Enum prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, prev != value, prev);
    }

    // ------------------------------------------------------------------
    // Set — handle-based (hot path, bypasses map lookup)
    // ------------------------------------------------------------------

    public void set(StateSlot.BoolSlot slot, boolean value) {
        boolean prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    public void set(StateSlot.IntSlot slot, int value) {
        int prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    public void set(StateSlot.FloatSlot slot, float value) {
        float prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    public void set(StateSlot.LongSlot slot, long value) {
        long prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    public void set(StateSlot.DoubleSlot slot, double value) {
        double prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlush(slot, prev != value, prev);
    }

    /** Sets the doubles slot by reference. No copy — caller retains ownership. Marks dirty unconditionally. */
    public void setArrRef(StateSlot.DoublesSlot slot, double[] value) {
        double[] prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, prev);
    }

    /** Sets the ints slot by reference. No copy — caller retains ownership. Marks dirty unconditionally. */
    public void setArrRef(StateSlot.IntsSlot slot, int[] value) {
        int[] prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, prev);
    }

    /** Sets the doubles slot with a defensive copy. Marks dirty unconditionally. */
    public void setArrSafe(StateSlot.DoublesSlot slot, double[] value) {
        double[] prev = slot.getValue(); slot.setValue(Arrays.copyOf(value, value.length));
        markAndMaybeFlushRef(slot, prev);
    }

    /** Sets the ints slot with a defensive copy. Marks dirty unconditionally. */
    public void setArrSafe(StateSlot.IntsSlot slot, int[] value) {
        int[] prev = slot.getValue(); slot.setValue(Arrays.copyOf(value, value.length));
        markAndMaybeFlushRef(slot, prev);
    }

    public void set(StateSlot.ConstrainedStringSlot slot, String value) {
        if (!slot.isAllowed(value))
            throw new IllegalArgumentException("Value '" + value + "' not allowed for slot '" + slot.name + "'");
        String prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, !prev.equals(value), prev);
    }

    public void set(StateSlot.UnconstrainedStringSlot slot, String value) {
        String prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, !prev.equals(value), prev);
    }

    public <T> void setObject(StateSlot.ObjectSlot slot, T value) {
        Object prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, prev);
    }

    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> void set(StateSlot.EnumSlot slot, E value) {
        Enum prev = slot.getValue(); slot.setValue(value);
        markAndMaybeFlushRef(slot, prev != value, prev);
    }

    // ------------------------------------------------------------------
    // setIndex / push
    // ------------------------------------------------------------------

    /**
     * Sets an element of a doubles slot by index and fires listeners.
     * Note: the reference captured on usage of this is the same reference that is available after mutation —
     * in-place mutations before mark() will be visible in the from value.
     */
    public void setIndex(String name, int index, double value) {
        StateSlot.DoublesSlot slot = doublesSlot(name);
        double[] arr = slot.getValue(); arr[index] = value;
        markAndMaybeFlushRef(slot, arr);
    }

    public void setIndex(String name, int index, int value) {
        StateSlot.IntsSlot slot = intsSlot(name);
        int[] arr = slot.getValue(); arr[index] = value;
        markAndMaybeFlushRef(slot, arr);
    }

    public void setIndex(StateSlot.DoublesSlot slot, int index, double value) {
        double[] arr = slot.getValue(); arr[index] = value;
        markAndMaybeFlushRef(slot, arr);
    }

    public void setIndex(StateSlot.IntsSlot slot, int index, int value) {
        int[] arr = slot.getValue(); arr[index] = value;
        markAndMaybeFlushRef(slot, arr);
    }

    /**
     * Forces listeners to fire for the named slot with its current value as both from and to.
     * Use after mutating a doubles or ints array in-place without calling setIndex.
     */
    public void push(String name) {
        StateSlot slot = requireSlot(name);
        if (slot instanceof StateSlot.DoublesSlot d) markAndMaybeFlushRef(d, d.getValue());
        else if (slot instanceof StateSlot.IntsSlot i) markAndMaybeFlushRef(i, i.getValue());
        else markAndMaybeFlushRef(slot, null);
    }
    public void push(StateSlot slot) {
        if (slot instanceof StateSlot.DoublesSlot d) markAndMaybeFlushRef(d, d.getValue());
        else if (slot instanceof StateSlot.IntsSlot i) markAndMaybeFlushRef(i, i.getValue());
        else markAndMaybeFlushRef(slot, null);
    }

    // ------------------------------------------------------------------
    // Get
    // ------------------------------------------------------------------

    public boolean  getBoolean(String name) { return boolSlot(name).getValue(); }
    public int      getInt(String name)     { return intSlot(name).getValue(); }
    public float    getFloat(String name)   { return floatSlot(name).getValue(); }
    public long     getLong(String name)    { return longSlot(name).getValue(); }
    public double   getDouble(String name)  { return doubleSlot(name).getValue(); }
    public double[] getDoubles(String name) { return doublesSlot(name).getValue(); }
    public int[]    getInts(String name)    { return intsSlot(name).getValue(); }
    public String getString(String name) {
        StateSlot s = anyStringSlot(name);
        if (s instanceof StateSlot.ConstrainedStringSlot c) return c.getValue();
        return ((StateSlot.UnconstrainedStringSlot) s).getValue();
    }
    @SuppressWarnings("unchecked")
    public <T> T    getObject(String name)  { return (T) objectSlot(name).getValue(); }
    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> E getEnum(String name) { return (E) enumSlot(name).getValue(); }

    // ------------------------------------------------------------------
    // isDirty
    // ------------------------------------------------------------------

    /**
     * Returns whether the named slot has been marked dirty in the current batch.
     * <p>
     * Note: {@code dirty} is not volatile — this read may be stale when called from a thread
     * other than the one that called {@code set}. Intended for same-thread diagnostic use only.
     */
    public boolean isDirty(String name)    { return requireSlot(name).dirty; }
    public boolean isDirty(StateSlot slot) { return slot.dirty; }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void markAndMaybeFlush(StateSlot.BoolSlot slot, boolean changed, boolean prev) {
        if (slot.silenced) return;
        if (slot.fireMode == StateSlot.FireMode.ON_CHANGE && !changed) return;
        if ((boolean) IN_BATCH.getOpaque(this)) { slot.mark(prev); batchStrategy.markDirty(slot.index); }
        else { slot.fireImmediate(prev); flushAny(true); }
    }

    private void markAndMaybeFlush(StateSlot.IntSlot slot, boolean changed, int prev) {
        if (slot.silenced) return;
        if (slot.fireMode == StateSlot.FireMode.ON_CHANGE && !changed) return;
        if ((boolean) IN_BATCH.getOpaque(this)) { slot.mark(prev); batchStrategy.markDirty(slot.index); }
        else { slot.fireImmediate(prev); flushAny(true); }
    }

    private void markAndMaybeFlush(StateSlot.FloatSlot slot, boolean changed, float prev) {
        if (slot.silenced) return;
        if (slot.fireMode == StateSlot.FireMode.ON_CHANGE && !changed) return;
        if ((boolean) IN_BATCH.getOpaque(this)) { slot.mark(prev); batchStrategy.markDirty(slot.index); }
        else { slot.fireImmediate(prev); flushAny(true); }
    }

    private void markAndMaybeFlush(StateSlot.LongSlot slot, boolean changed, long prev) {
        if (slot.silenced) return;
        if (slot.fireMode == StateSlot.FireMode.ON_CHANGE && !changed) return;
        if ((boolean) IN_BATCH.getOpaque(this)) { slot.mark(prev); batchStrategy.markDirty(slot.index); }
        else { slot.fireImmediate(prev); flushAny(true); }
    }

    private void markAndMaybeFlush(StateSlot.DoubleSlot slot, boolean changed, double prev) {
        if (slot.silenced) return;
        if (slot.fireMode == StateSlot.FireMode.ON_CHANGE && !changed) return;
        if ((boolean) IN_BATCH.getOpaque(this)) { slot.mark(prev); batchStrategy.markDirty(slot.index); }
        else { slot.fireImmediate(prev); flushAny(true); }
    }

    /** Reference-type overload — used for String, Enum, Object, and array slots. Always fires (changed=true). */
    private void markAndMaybeFlushRef(StateSlot slot, Object prev) {
        if (slot.silenced) return;
        if ((boolean) IN_BATCH.getOpaque(this)) { slot.mark(prev); batchStrategy.markDirty(slot.index); }
        else { slot.fireImmediate(prev); flushAny(true); }
    }

    /** Reference-type overload with explicit changed flag — used for String and Enum slots with ON_CHANGE semantics. */
    private void markAndMaybeFlushRef(StateSlot slot, boolean changed, Object prev) {
        if (slot.silenced) return;
        if (slot.fireMode == StateSlot.FireMode.ON_CHANGE && !changed) return;
        if ((boolean) IN_BATCH.getOpaque(this)) { slot.mark(prev); batchStrategy.markDirty(slot.index); }
        else { slot.fireImmediate(prev); flushAny(true); }
    }

    private void flushAny(boolean anyFired) {
        if (anyFired) {
            Runnable[] ls = anyListeners;
            for (int i = 0; i < ls.length; i++) ls[i].run();
        }
    }

    private StateSlot requireSlot(String name) {
        StateSlot slot = slotMap.get(name);
        if (slot == null) throw new IllegalArgumentException("Unknown state slot: '" + name + "'");
        return slot;
    }

    private StateSlot.BoolSlot boolSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.BoolSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not a boolean slot");
        return b;
    }

    private StateSlot.IntSlot intSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.IntSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not an int slot");
        return b;
    }

    private StateSlot.FloatSlot floatSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.FloatSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not a float slot");
        return b;
    }

    private StateSlot.LongSlot longSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.LongSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not a long slot");
        return b;
    }

    private StateSlot.DoubleSlot doubleSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.DoubleSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not a double slot");
        return b;
    }

    private StateSlot.DoublesSlot doublesSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.DoublesSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not a doubles slot");
        return b;
    }

    private StateSlot.IntsSlot intsSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.IntsSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not an ints slot");
        return b;
    }

    private StateSlot anyStringSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.ConstrainedStringSlot) && !(s instanceof StateSlot.UnconstrainedStringSlot))
            throw new IllegalArgumentException("Slot '" + name + "' is not a string slot");
        return s;
    }

    private StateSlot.ConstrainedStringSlot constrainedStringSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.ConstrainedStringSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not a constrained string slot");
        return b;
    }

    private StateSlot.UnconstrainedStringSlot unconstrainedStringSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.UnconstrainedStringSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not an unconstrained string slot");
        return b;
    }

    private StateSlot.ObjectSlot objectSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.ObjectSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not an object slot");
        return b;
    }

    private StateSlot.EnumSlot enumSlot(String name) {
        StateSlot s = requireSlot(name);
        if (!(s instanceof StateSlot.EnumSlot b)) throw new IllegalArgumentException("Slot '" + name + "' is not an enum slot");
        return b;
    }

    private void checkNotSealed() {
        if (sealed) throw new IllegalStateException("StateRegistry is sealed — no further addState calls permitted");
    }
}
