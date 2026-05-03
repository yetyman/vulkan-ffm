package io.github.yetyman.vulkan.state;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * Internal sealed hierarchy of typed state slots.
 * Each slot holds its current value, a snapshot captured lazily on first dirty per batch,
 * and a copy-on-write listener array.
 * <p>
 * Value fields use VarHandle setOpaque/getOpaque — atomic (no word tearing), no ordering
 * guarantees. Safe for concurrent writers where ordering is not required.
 * Listener arrays use VarHandle getAcquire for copy-on-write visibility and are null when
 * no listeners are registered, avoiding cache-line touches on slots with no listeners.
 * <p>
 * By default slots use a plain dirty flag (single-writer fast path). Call {@link #concurrent()}
 * to opt into a CAS-based dirty flag for slots with multiple concurrent writers.
 * <p>
 * {@code silenced} is a plain boolean read on the hot path. Writes use SILENCED.setRelease
 * so the change is visible to the flush thread without paying a load-acquire on every set().
 * <p>
 * {@code hasListeners} is a plain boolean maintained by addListener/removeListener.
 * Checked by BatchStrategy.flush() to skip the acquire barrier and virtual dispatch
 * on slots with no listeners.
 */
sealed abstract class StateSlot permits
        StateSlot.BoolSlot, StateSlot.IntSlot, StateSlot.FloatSlot, StateSlot.LongSlot,
        StateSlot.DoubleSlot, StateSlot.DoublesSlot, StateSlot.IntsSlot,
        StateSlot.ConstrainedStringSlot, StateSlot.UnconstrainedStringSlot, StateSlot.ObjectSlot, StateSlot.EnumSlot {

    @FunctionalInterface interface BooleanListener  { void onChange(boolean from, boolean to); }
    @FunctionalInterface interface IntListener      { void onChange(int from, int to); }
    @FunctionalInterface interface FloatListener    { void onChange(float from, float to); }
    @FunctionalInterface interface LongListener     { void onChange(long from, long to); }
    @FunctionalInterface interface DoubleListener   { void onChange(double from, double to); }
    @FunctionalInterface interface DoublesListener  { void onChange(double[] from, double[] to); }
    @FunctionalInterface interface IntsListener     { void onChange(int[] from, int[] to); }
    @FunctionalInterface interface StringListener   { void onChange(String from, String to); }
    @FunctionalInterface interface ObjectListener<T>{ void onChange(T from, T to); }
    @FunctionalInterface interface EnumListener<E extends Enum<E>> { void onChange(E from, E to); }

    enum FireMode { ON_CHANGE, ALWAYS }

    private static final VarHandle DIRTY;
    static final VarHandle SILENCED;
    static {
        try {
            DIRTY    = MethodHandles.lookup().findVarHandle(StateSlot.class, "dirty",    boolean.class);
            SILENCED = MethodHandles.lookup().findVarHandle(StateSlot.class, "silenced", boolean.class);
        }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    final String name;
    final int index;
    boolean dirty = false;
    boolean silenced = false;   // plain read on hot path; writes use SILENCED.setRelease
    final FireMode fireMode;
    final boolean concurrent;

    StateSlot(String name, int index, FireMode fireMode, boolean concurrent) {
        this.name = name;
        this.index = index;
        this.fireMode = fireMode;
        this.concurrent = concurrent;
    }

    StateSlot(String name, int index, boolean concurrent) {
        this(name, index, FireMode.ON_CHANGE, concurrent);
    }

    /**
     * Captures snapshot on first mark per batch using the caller-provided previous value.
     * Single-writer path: plain read of dirty, plain write.
     * Concurrent path: CAS from false→true; only the winner captures the snapshot.
     * <p>
     * Each subclass provides a typed overload; this base version exists only for
     * reference-typed slots (String, Object, Enum) where boxing is unavoidable.
     */
    void mark(Object prev) { throw new UnsupportedOperationException(); }

    /** Fires listeners with an externally captured from value and the current value as to, then clears dirty. */
    abstract void fireImmediate(Object from);

    /** Fires listeners using the captured snapshot as from, current value as to, then clears dirty. */
    abstract void fireListeners();

    /**
     * Marks dirty and captures snapshot.
     * Uses CAS if concurrent=true, plain write otherwise.
     * Returns true if this call won the mark (should capture snapshot).
     */
    final boolean tryMark() {
        if (concurrent) return DIRTY.compareAndSet(this, false, true);
        if (dirty) return false;
        dirty = true;
        return true;
    }

    /** Clears dirty via plain write (called from fireListeners, always single-threaded at flush time). */
    final void clearDirty() {
        DIRTY.setOpaque(this, false);
    }

    // ------------------------------------------------------------------

    static final class BoolSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(BoolSlot.class, "value", boolean.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(BoolSlot.class, "listeners", BooleanListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        boolean value;
        boolean snapshot;
        BooleanListener[] listeners = null;

        BoolSlot(String name, int index, boolean initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        boolean getValue()            { return (boolean) VALUE.getOpaque(this); }
        void    setValue(boolean v)   { VALUE.setOpaque(this, v); }

        BooleanListener addListener(BooleanListener l) {
            synchronized (this) {
                BooleanListener[] old = (BooleanListener[]) LISTENERS.getAcquire(this);
                BooleanListener[] next = old == null ? new BooleanListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(BooleanListener l) {
            synchronized (this) {
                BooleanListener[] old = (BooleanListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                BooleanListener[] next = new BooleanListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        BooleanListener addOnceListener(BooleanListener l) {
            BooleanListener[] wrapper = new BooleanListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        void mark(boolean prev) { if (tryMark()) snapshot = prev; }

        @Override void fireImmediate(Object from) { fireImmediate((boolean) from); }

        void fireImmediate(boolean from) {
            boolean to = getValue();
            clearDirty();
            BooleanListener[] ls = (BooleanListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }

        @Override void fireListeners() {
            boolean from = snapshot, to = getValue();
            clearDirty();
            BooleanListener[] ls = (BooleanListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    static final class IntSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(IntSlot.class, "value", int.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(IntSlot.class, "listeners", IntListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        int value;
        int snapshot;
        IntListener[] listeners = null;

        IntSlot(String name, int index, int initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        int  getValue()         { return (int) VALUE.getOpaque(this); }
        void setValue(int v)    { VALUE.setOpaque(this, v); }

        IntListener addListener(IntListener l) {
            synchronized (this) {
                IntListener[] old = (IntListener[]) LISTENERS.getAcquire(this);
                IntListener[] next = old == null ? new IntListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(IntListener l) {
            synchronized (this) {
                IntListener[] old = (IntListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                IntListener[] next = new IntListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        IntListener addOnceListener(IntListener l) {
            IntListener[] wrapper = new IntListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        void mark(int prev) { if (tryMark()) snapshot = prev; }
        @Override void fireImmediate(Object from) { fireImmediate((int) from); }

        void fireImmediate(int from) {
            int to = getValue();
            clearDirty();
            IntListener[] ls = (IntListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }

        @Override void fireListeners() {
            int from = snapshot, to = getValue();
            clearDirty();
            IntListener[] ls = (IntListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    static final class FloatSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(FloatSlot.class, "value", float.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(FloatSlot.class, "listeners", FloatListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        float value;
        float snapshot;
        FloatListener[] listeners = null;

        FloatSlot(String name, int index, float initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        float getValue()          { return (float) VALUE.getOpaque(this); }
        void  setValue(float v)   { VALUE.setOpaque(this, v); }

        FloatListener addListener(FloatListener l) {
            synchronized (this) {
                FloatListener[] old = (FloatListener[]) LISTENERS.getAcquire(this);
                FloatListener[] next = old == null ? new FloatListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(FloatListener l) {
            synchronized (this) {
                FloatListener[] old = (FloatListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                FloatListener[] next = new FloatListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        FloatListener addOnceListener(FloatListener l) {
            FloatListener[] wrapper = new FloatListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        void mark(float prev) { if (tryMark()) snapshot = prev; }
        @Override void fireImmediate(Object from) { fireImmediate((float) from); }

        void fireImmediate(float from) {
            float to = getValue();
            clearDirty();
            FloatListener[] ls = (FloatListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }

        @Override void fireListeners() {
            float from = snapshot, to = getValue();
            clearDirty();
            FloatListener[] ls = (FloatListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    static final class LongSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(LongSlot.class, "value", long.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(LongSlot.class, "listeners", LongListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        long value;
        long snapshot;
        LongListener[] listeners = null;

        LongSlot(String name, int index, long initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        long getValue()          { return (long) VALUE.getOpaque(this); }
        void setValue(long v)    { VALUE.setOpaque(this, v); }

        LongListener addListener(LongListener l) {
            synchronized (this) {
                LongListener[] old = (LongListener[]) LISTENERS.getAcquire(this);
                LongListener[] next = old == null ? new LongListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(LongListener l) {
            synchronized (this) {
                LongListener[] old = (LongListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                LongListener[] next = new LongListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        LongListener addOnceListener(LongListener l) {
            LongListener[] wrapper = new LongListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        void mark(long prev) { if (tryMark()) snapshot = prev; }
        @Override void fireImmediate(Object from) { fireImmediate((long) from); }

        void fireImmediate(long from) {
            long to = getValue();
            clearDirty();
            LongListener[] ls = (LongListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }

        @Override void fireListeners() {
            long from = snapshot, to = getValue();
            clearDirty();
            LongListener[] ls = (LongListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    static final class DoubleSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(DoubleSlot.class, "value", double.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(DoubleSlot.class, "listeners", DoubleListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        double value;
        double snapshot;
        DoubleListener[] listeners = null;

        DoubleSlot(String name, int index, double initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        double getValue()           { return (double) VALUE.getOpaque(this); }
        void   setValue(double v)   { VALUE.setOpaque(this, v); }

        DoubleListener addListener(DoubleListener l) {
            synchronized (this) {
                DoubleListener[] old = (DoubleListener[]) LISTENERS.getAcquire(this);
                DoubleListener[] next = old == null ? new DoubleListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(DoubleListener l) {
            synchronized (this) {
                DoubleListener[] old = (DoubleListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                DoubleListener[] next = new DoubleListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        DoubleListener addOnceListener(DoubleListener l) {
            DoubleListener[] wrapper = new DoubleListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        void mark(double prev) { if (tryMark()) snapshot = prev; }
        @Override void fireImmediate(Object from) { fireImmediate((double) from); }

        void fireImmediate(double from) {
            double to = getValue();
            clearDirty();
            DoubleListener[] ls = (DoubleListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }

        @Override void fireListeners() {
            double from = snapshot, to = getValue();
            clearDirty();
            DoubleListener[] ls = (DoubleListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    static final class DoublesSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(DoublesSlot.class, "value", double[].class);
                LISTENERS = MethodHandles.lookup().findVarHandle(DoublesSlot.class, "listeners", DoublesListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        double[] value;
        double[] snapshot;
        DoublesListener[] listeners = null;

        DoublesSlot(String name, int index, double[] initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        double[] getValue()             { return (double[]) VALUE.getOpaque(this); }
        void     setValue(double[] v)   { VALUE.setOpaque(this, v); }

        DoublesListener addListener(DoublesListener l) {
            synchronized (this) {
                DoublesListener[] old = (DoublesListener[]) LISTENERS.getAcquire(this);
                DoublesListener[] next = old == null ? new DoublesListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(DoublesListener l) {
            synchronized (this) {
                DoublesListener[] old = (DoublesListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                DoublesListener[] next = new DoublesListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        DoublesListener addOnceListener(DoublesListener l) {
            DoublesListener[] wrapper = new DoublesListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        @Override void mark(Object prev) { if (tryMark()) snapshot = (double[]) prev; }
        @Override void fireImmediate(Object from) {
            double[] f = (double[]) from, to = getValue();
            clearDirty();
            DoublesListener[] ls = (DoublesListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(f, to);
        }

        @Override void fireListeners() {
            double[] from = snapshot, to = getValue();
            clearDirty();
            DoublesListener[] ls = (DoublesListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    static final class IntsSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(IntsSlot.class, "value", int[].class);
                LISTENERS = MethodHandles.lookup().findVarHandle(IntsSlot.class, "listeners", IntsListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        int[] value;
        int[] snapshot;
        IntsListener[] listeners = null;

        IntsSlot(String name, int index, int[] initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        int[] getValue()           { return (int[]) VALUE.getOpaque(this); }
        void  setValue(int[] v)    { VALUE.setOpaque(this, v); }

        IntsListener addListener(IntsListener l) {
            synchronized (this) {
                IntsListener[] old = (IntsListener[]) LISTENERS.getAcquire(this);
                IntsListener[] next = old == null ? new IntsListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(IntsListener l) {
            synchronized (this) {
                IntsListener[] old = (IntsListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                IntsListener[] next = new IntsListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        IntsListener addOnceListener(IntsListener l) {
            IntsListener[] wrapper = new IntsListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        @Override void mark(Object prev) { if (tryMark()) snapshot = (int[]) prev; }
        @Override void fireImmediate(Object from) {
            int[] f = (int[]) from, to = getValue();
            clearDirty();
            IntsListener[] ls = (IntsListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(f, to);
        }

        @Override void fireListeners() {
            int[] from = snapshot, to = getValue();
            clearDirty();
            IntsListener[] ls = (IntsListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    static final class ConstrainedStringSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(ConstrainedStringSlot.class, "value", String.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(ConstrainedStringSlot.class, "listeners", StringListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        String value;
        String snapshot;
        final String[] allowed;
        StringListener[] listeners = null;

        ConstrainedStringSlot(String name, int index, String[] allowed, boolean concurrent) {
            super(name, index, concurrent);
            this.allowed = allowed;
            this.value = allowed[0];
        }

        String getValue()           { return (String) VALUE.getOpaque(this); }
        void   setValue(String v)   { VALUE.setOpaque(this, v); }

        boolean isAllowed(String v) {
            for (int i = 0; i < allowed.length; i++) if (allowed[i].equals(v)) return true;
            return false;
        }

        StringListener addListener(StringListener l) {
            synchronized (this) {
                StringListener[] old = (StringListener[]) LISTENERS.getAcquire(this);
                StringListener[] next = old == null ? new StringListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(StringListener l) {
            synchronized (this) {
                StringListener[] old = (StringListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                StringListener[] next = new StringListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        StringListener addOnceListener(StringListener l) {
            StringListener[] wrapper = new StringListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        @Override void mark(Object prev) { if (tryMark()) snapshot = (String) prev; }
        @Override void fireImmediate(Object from) {
            String f = (String) from, to = getValue();
            clearDirty();
            StringListener[] ls = (StringListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(f, to);
        }

        @Override void fireListeners() {
            String from = snapshot, to = getValue();
            clearDirty();
            StringListener[] ls = (StringListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    static final class UnconstrainedStringSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(UnconstrainedStringSlot.class, "value", String.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(UnconstrainedStringSlot.class, "listeners", StringListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        String value;
        String snapshot;
        StringListener[] listeners = null;

        UnconstrainedStringSlot(String name, int index, String initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        String getValue()           { return (String) VALUE.getOpaque(this); }
        void   setValue(String v)   { VALUE.setOpaque(this, v); }

        StringListener addListener(StringListener l) {
            synchronized (this) {
                StringListener[] old = (StringListener[]) LISTENERS.getAcquire(this);
                StringListener[] next = old == null ? new StringListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(StringListener l) {
            synchronized (this) {
                StringListener[] old = (StringListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                StringListener[] next = new StringListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        StringListener addOnceListener(StringListener l) {
            StringListener[] wrapper = new StringListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange(from, to); };
            return addListener(wrapper[0]);
        }

        @Override void mark(Object prev) { if (tryMark()) snapshot = (String) prev; }
        @Override void fireImmediate(Object from) {
            String f = (String) from, to = getValue();
            clearDirty();
            StringListener[] ls = (StringListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(f, to);
        }

        @Override void fireListeners() {
            String from = snapshot, to = getValue();
            clearDirty();
            StringListener[] ls = (StringListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static final class ObjectSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(ObjectSlot.class, "value", Object.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(ObjectSlot.class, "listeners", ObjectListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        Object value;
        Object snapshot;
        ObjectListener[] listeners = null;

        ObjectSlot(String name, int index, Object initial, boolean concurrent) {
            super(name, index, concurrent);
            this.value = initial;
        }

        Object getValue()           { return VALUE.getOpaque(this); }
        void   setValue(Object v)   { VALUE.setOpaque(this, v); }

        <T> ObjectListener<T> addListener(ObjectListener<T> l) {
            synchronized (this) {
                ObjectListener[] old = (ObjectListener[]) LISTENERS.getAcquire(this);
                ObjectListener[] next = old == null ? new ObjectListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(ObjectListener<?> l) {
            synchronized (this) {
                ObjectListener[] old = (ObjectListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                ObjectListener[] next = new ObjectListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        @SuppressWarnings("unchecked")
        <T> ObjectListener<T> addOnceListener(ObjectListener<T> l) {
            ObjectListener[] wrapper = new ObjectListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange((T) from, (T) to); };
            return addListener(wrapper[0]);
        }

        @Override void mark(Object prev) { if (tryMark()) snapshot = prev; }
        @Override void fireImmediate(Object from) {
            Object to = getValue();
            clearDirty();
            ObjectListener[] ls = (ObjectListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }

        @Override void fireListeners() {
            Object from = snapshot, to = getValue();
            clearDirty();
            ObjectListener[] ls = (ObjectListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static final class EnumSlot extends StateSlot {
        private static final VarHandle VALUE;
        private static final VarHandle LISTENERS;
        static {
            try {
                VALUE = MethodHandles.lookup().findVarHandle(EnumSlot.class, "value", Enum.class);
                LISTENERS = MethodHandles.lookup().findVarHandle(EnumSlot.class, "listeners", EnumListener[].class);
            }
            catch (Exception e) { throw new ExceptionInInitializerError(e); }
        }

        Enum value;
        Enum snapshot;
        final Class type;
        EnumListener[] listeners = null;

        EnumSlot(String name, int index, Class type, Enum initial, boolean concurrent) {
            super(name, index, concurrent);
            this.type = type;
            this.value = initial;
        }

        Enum getValue()          { return (Enum) VALUE.getOpaque(this); }
        void setValue(Enum v)    { VALUE.setOpaque(this, v); }

        <E extends Enum<E>> EnumListener<E> addListener(EnumListener<E> l) {
            synchronized (this) {
                EnumListener[] old = (EnumListener[]) LISTENERS.getAcquire(this);
                EnumListener[] next = old == null ? new EnumListener[]{l} : Arrays.copyOf(old, old.length + 1);
                if (old != null) next[old.length] = l;
                LISTENERS.setRelease(this, next);
            }
            return l;
        }

        void removeListener(EnumListener<?> l) {
            synchronized (this) {
                EnumListener[] old = (EnumListener[]) LISTENERS.getAcquire(this);
                if (old == null) return;
                int idx = -1;
                for (int i = 0; i < old.length; i++) if (old[i] == l) { idx = i; break; }
                if (idx < 0) return;
                if (old.length == 1) { LISTENERS.setRelease(this, null); return; }
                EnumListener[] next = new EnumListener[old.length - 1];
                System.arraycopy(old, 0, next, 0, idx);
                System.arraycopy(old, idx + 1, next, idx, old.length - idx - 1);
                LISTENERS.setRelease(this, next);
            }
        }

        @SuppressWarnings("unchecked")
        <E extends Enum<E>> EnumListener<E> addOnceListener(EnumListener<E> l) {
            EnumListener[] wrapper = new EnumListener[1];
            wrapper[0] = (from, to) -> { removeListener(wrapper[0]); l.onChange((E) from, (E) to); };
            return addListener(wrapper[0]);
        }

        @Override void mark(Object prev) { if (tryMark()) snapshot = (Enum) prev; }
        @Override void fireImmediate(Object from) {
            Enum f = (Enum) from, to = getValue();
            clearDirty();
            EnumListener[] ls = (EnumListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(f, to);
        }

        @Override void fireListeners() {
            Enum from = snapshot, to = getValue();
            clearDirty();
            EnumListener[] ls = (EnumListener[]) LISTENERS.getAcquire(this);
            if (ls == null) return;
            for (int i = 0; i < ls.length; i++) ls[i].onChange(from, to);
        }
    }
}
