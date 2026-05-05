package io.github.yetyman.structures.state;

import io.github.yetyman.structures.state.StateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StateRegistryTest {

    // ======================================================================
    // Helpers
    // ======================================================================

    private StateRegistry reg;

    private StateRegistry sealed(StateRegistry r) { r.seal(); return r; }

    // ======================================================================
    // Slot definition & seal
    // ======================================================================

    @Nested
    class SlotDefinition {

        @Test
        void duplicateNameThrows() {
            reg = new StateRegistry();
            reg.addState("x", 0);
            assertThrows(IllegalArgumentException.class, () -> reg.addState("x", 1.0f));
        }

        @Test
        void addAfterSealThrows() {
            reg = new StateRegistry();
            reg.seal();
            assertThrows(IllegalStateException.class, () -> reg.addState("x", 0));
        }

        @Test
        void unknownSlotThrows() {
            reg = sealed(new StateRegistry());
            assertThrows(IllegalArgumentException.class, () -> reg.getInt("nope"));
        }

        @Test
        void wrongTypeThrows() {
            reg = new StateRegistry();
            reg.addState("x", 0);
            reg.seal();
            assertThrows(IllegalArgumentException.class, () -> reg.getFloat("x"));
        }

        @Test
        void constrainedStringRequiresValues() {
            reg = new StateRegistry();
            assertThrows(IllegalArgumentException.class, () -> reg.addState("s", new String[]{}));
        }
    }

    // ======================================================================
    // Primitive slot get/set (immediate, no batch)
    // ======================================================================

    @Nested
    class ImmediateSetGet {

        @Test
        void boolSlot() {
            reg = new StateRegistry();
            StateSlot.BoolSlot slot = reg.addState("b", false);
            reg.seal();

            assertFalse(reg.getBoolean("b"));
            reg.set(slot, true);
            assertTrue(reg.getBoolean("b"));
        }

        @Test
        void intSlot() {
            reg = new StateRegistry();
            StateSlot.IntSlot slot = reg.addState("i", 10);
            reg.seal();

            assertEquals(10, reg.getInt("i"));
            reg.set(slot, 42);
            assertEquals(42, reg.getInt("i"));
            reg.set("i", 99);
            assertEquals(99, reg.getInt("i"));
        }

        @Test
        void floatSlot() {
            reg = new StateRegistry();
            StateSlot.FloatSlot slot = reg.addState("f", 1.0f);
            reg.seal();

            assertEquals(1.0f, reg.getFloat("f"));
            reg.set(slot, 2.5f);
            assertEquals(2.5f, reg.getFloat("f"));
        }

        @Test
        void longSlot() {
            reg = new StateRegistry();
            StateSlot.LongSlot slot = reg.addState("l", 100L);
            reg.seal();

            assertEquals(100L, reg.getLong("l"));
            reg.set(slot, Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, reg.getLong("l"));
        }

        @Test
        void doubleSlot() {
            reg = new StateRegistry();
            StateSlot.DoubleSlot slot = reg.addState("d", 3.14);
            reg.seal();

            assertEquals(3.14, reg.getDouble("d"));
            reg.set(slot, 2.71);
            assertEquals(2.71, reg.getDouble("d"));
        }

        @Test
        void doublesSlot() {
            reg = new StateRegistry();
            StateSlot.DoublesSlot slot = reg.addStateArr("pos", 1.0, 2.0, 3.0);
            reg.seal();

            assertArrayEquals(new double[]{1.0, 2.0, 3.0}, reg.getDoubles("pos"));
            reg.setArrRef(slot, new double[]{4.0, 5.0});
            assertArrayEquals(new double[]{4.0, 5.0}, reg.getDoubles("pos"));
        }

        @Test
        void intsSlot() {
            reg = new StateRegistry();
            StateSlot.IntsSlot slot = reg.addStateArr("ids", 1, 2, 3);
            reg.seal();

            assertArrayEquals(new int[]{1, 2, 3}, reg.getInts("ids"));
            reg.setArrSafe(slot, new int[]{10, 20});
            assertArrayEquals(new int[]{10, 20}, reg.getInts("ids"));
        }

        @Test
        void constrainedStringSlot() {
            reg = new StateRegistry();
            reg.addState("mode", "fast", "slow", "auto");
            reg.seal();

            assertEquals("fast", reg.getString("mode"));
            reg.set("mode", "slow");
            assertEquals("slow", reg.getString("mode"));
            assertThrows(IllegalArgumentException.class, () -> reg.set("mode", "invalid"));
        }

        @Test
        void unconstrainedStringSlot() {
            reg = new StateRegistry();
            reg.addStateString("name", "hello");
            reg.seal();

            assertEquals("hello", reg.getString("name"));
            reg.set("name", "world");
            assertEquals("world", reg.getString("name"));
        }

        @Test
        void objectSlot() {
            reg = new StateRegistry();
            reg.addStateObject("obj", List.of(1, 2));
            reg.seal();

            assertEquals(List.of(1, 2), reg.<List<Integer>>getObject("obj"));
            reg.setObject("obj", List.of(3));
            assertEquals(List.of(3), reg.<List<Integer>>getObject("obj"));
        }

        @Test
        void enumSlot() {
            reg = new StateRegistry();
            reg.addState("dir", Thread.State.class, Thread.State.NEW);
            reg.seal();

            assertEquals(Thread.State.NEW, reg.<Thread.State>getEnum("dir"));
            reg.set("dir", Thread.State.RUNNABLE);
            assertEquals(Thread.State.RUNNABLE, reg.<Thread.State>getEnum("dir"));
        }
    }

    // ======================================================================
    // Listeners — immediate (non-batch)
    // ======================================================================

    @Nested
    class ImmediateListeners {

        @Test
        void boolListenerFires() {
            reg = new StateRegistry();
            StateSlot.BoolSlot slot = reg.addState("b", false);
            reg.seal();

            AtomicReference<Boolean> captured = new AtomicReference<>();
            reg.on("b", (boolean from, boolean to) -> captured.set(to));
            reg.set(slot, true);
            assertTrue(captured.get());
        }

        @Test
        void intListenerFires() {
            reg = new StateRegistry();
            StateSlot.IntSlot slot = reg.addState("i", 0);
            reg.seal();

            AtomicInteger captured = new AtomicInteger(-1);
            reg.on("i", (int from, int to) -> captured.set(to));
            reg.set(slot, 42);
            assertEquals(42, captured.get());
        }

        @Test
        void floatListenerFires() {
            reg = new StateRegistry();
            StateSlot.FloatSlot slot = reg.addState("f", 0.0f);
            reg.seal();

            AtomicReference<Float> captured = new AtomicReference<>();
            reg.on("f", (float from, float to) -> captured.set(to));
            reg.set(slot, 3.14f);
            assertEquals(3.14f, captured.get());
        }

        @Test
        void stringListenerFires() {
            reg = new StateRegistry();
            reg.addStateString("s", "a");
            reg.seal();

            AtomicReference<String> captured = new AtomicReference<>();
            reg.on("s", (String from, String to) -> captured.set(to));
            reg.set("s", "b");
            assertEquals("b", captured.get());
        }

        @Test
        void constrainedStringListenerFires() {
            reg = new StateRegistry();
            reg.addState("mode", "on", "off");
            reg.seal();

            AtomicReference<String> captured = new AtomicReference<>();
            reg.on("mode", (String from, String to) -> captured.set(from + "→" + to));
            reg.set("mode", "off");
            assertEquals("on→off", captured.get());
        }

        @Test
        void objectListenerFires() {
            reg = new StateRegistry();
            reg.addStateObject("obj", "initial");
            reg.seal();

            AtomicReference<Object> captured = new AtomicReference<>();
            reg.onObject("obj", (Object from, Object to) -> captured.set(to));
            reg.setObject("obj", "updated");
            assertEquals("updated", captured.get());
        }

        @Test
        void enumListenerFires() {
            reg = new StateRegistry();
            reg.addState("st", Thread.State.class, Thread.State.NEW);
            reg.seal();

            AtomicReference<Thread.State> captured = new AtomicReference<>();
            reg.onEnum("st", (Thread.State from, Thread.State to) -> captured.set(to));
            reg.set("st", Thread.State.BLOCKED);
            assertEquals(Thread.State.BLOCKED, captured.get());
        }

        @Test
        void noFireOnSameValue() {
            reg = new StateRegistry();
            reg.addState("i", 5);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            reg.on("i", (int from, int to) -> count.incrementAndGet());
            reg.set("i", 5); // same value
            assertEquals(0, count.get());
        }

        @Test
        void onceListenerFiresOnlyOnce() {
            reg = new StateRegistry();
            reg.addState("i", 0);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            reg.once("i", (int from, int to) -> count.incrementAndGet());
            reg.set("i", 1);
            reg.set("i", 2);
            assertEquals(1, count.get());
        }

        @Test
        void onAnyFires() {
            reg = new StateRegistry();
            reg.addState("a", 0);
            reg.addState("b", 0.0f);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            reg.onAny(count::incrementAndGet);
            reg.set("a", 1);
            reg.set("b", 1.0f);
            assertEquals(2, count.get());
        }

        @Test
        void removeOnAny() {
            reg = new StateRegistry();
            reg.addState("a", 0);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            Runnable listener = count::incrementAndGet;
            reg.onAny(listener);
            reg.set("a", 1);
            assertEquals(1, count.get());

            reg.removeOnAny(listener);
            reg.set("a", 2);
            assertEquals(1, count.get());
        }
    }

    // ======================================================================
    // Batch — definition order
    // ======================================================================

    @Nested
    class BatchDefinitionOrder {

        @Test
        void listenersFireOnEndBatch() {
            reg = new StateRegistry(BatchStrategy.definitionOrder());
            StateSlot.IntSlot a = reg.addState("a", 0);
            StateSlot.FloatSlot b = reg.addState("b", 0.0f);
            reg.seal();

            List<String> order = new ArrayList<>();
            reg.on("a", (int from, int to) -> order.add("a"));
            reg.on("b", (float from, float to) -> order.add("b"));

            reg.beginBatch();
            try {
                reg.set(b, 1.0f);
                reg.set(a, 1);
                assertTrue(order.isEmpty(), "listeners should not fire during batch");
            } finally {
                reg.endBatch();
            }

            assertEquals(List.of("a", "b"), order, "definition order fires a before b");
        }

        @Test
        void batchSnapshotCapturesPreChangeValue() {
            reg = new StateRegistry(BatchStrategy.definitionOrder());
            StateSlot.IntSlot slot = reg.addState("x", 0);
            reg.seal();

            AtomicReference<String> captured = new AtomicReference<>();
            reg.on("x", (int from, int to) -> captured.set(from + "→" + to));

            reg.beginBatch();
            try {
                reg.set(slot, 1);
                reg.set(slot, 2);
                reg.set(slot, 3);
            } finally {
                reg.endBatch();
            }

            // snapshot is the pre-change value captured before the first set
            assertEquals("0→3", captured.get(), "from should be pre-batch value, to should be final");
        }

        @Test
        void nestedBatchFlushesOnOuterEnd() {
            reg = new StateRegistry(BatchStrategy.definitionOrder());
            reg.addState("x", 0);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            reg.on("x", (int from, int to) -> count.incrementAndGet());

            reg.beginBatch();
            try {
                reg.beginBatch();
                try {
                    reg.set("x", 1);
                } finally {
                    reg.endBatch();
                }
                assertEquals(0, count.get(), "inner endBatch should not flush");
            } finally {
                reg.endBatch();
            }
            assertEquals(1, count.get(), "outer endBatch should flush");
        }

        @Test
        void onAnyFiresAfterBatch() {
            reg = new StateRegistry(BatchStrategy.definitionOrder());
            reg.addState("a", 0);
            reg.addState("b", 0);
            reg.seal();

            AtomicInteger anyCount = new AtomicInteger(0);
            reg.onAny(anyCount::incrementAndGet);

            reg.beginBatch();
            try {
                reg.set("a", 1);
                reg.set("b", 2);
            } finally {
                reg.endBatch();
            }

            assertEquals(1, anyCount.get(), "onAny should fire once per flush cycle");
        }
    }

    // ======================================================================
    // Batch — change order
    // ======================================================================

    @Nested
    class BatchChangeOrder {

        @Test
        void listenersFireInSetOrder() {
            reg = new StateRegistry(BatchStrategy.changeOrder());
            StateSlot.IntSlot a = reg.addState("a", 0);
            StateSlot.FloatSlot b = reg.addState("b", 0.0f);
            reg.seal();

            List<String> order = new ArrayList<>();
            reg.on("a", (int from, int to) -> order.add("a"));
            reg.on("b", (float from, float to) -> order.add("b"));

            reg.beginBatch();
            try {
                reg.set(b, 1.0f);
                reg.set(a, 1);
            } finally {
                reg.endBatch();
            }

            assertEquals(List.of("b", "a"), order, "change order fires b before a");
        }

        @Test
        void duplicateSetOnlyFiresOnce() {
            reg = new StateRegistry(BatchStrategy.changeOrder());
            reg.addState("x", 0);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            reg.on("x", (int from, int to) -> count.incrementAndGet());

            reg.beginBatch();
            try {
                reg.set("x", 1);
                reg.set("x", 2);
                reg.set("x", 3);
            } finally {
                reg.endBatch();
            }

            assertEquals(1, count.get(), "slot should fire once per batch even if set multiple times");
        }
    }

    // ======================================================================
    // Listener re-entrancy
    // ======================================================================

    @Nested
    class Reentrancy {

        @Test
        void immediateReentrancyIsRecursive() {
            reg = new StateRegistry();
            StateSlot.IntSlot a = reg.addState("a", 0);
            StateSlot.IntSlot b = reg.addState("b", 0);
            reg.seal();

            List<String> order = new ArrayList<>();
            reg.on("a", (int from, int to) -> {
                order.add("a:" + to);
                if (to == 1) reg.set(b, 10);
            });
            reg.on("b", (int from, int to) -> order.add("b:" + to));

            reg.set(a, 1);

            // immediate: a fires, which sets b, which fires b immediately (recursive)
            assertEquals(List.of("a:1", "b:10"), order);
        }

        @Test
        void batchReentrancyIsDeferredAndFlushed() {
            reg = new StateRegistry(BatchStrategy.definitionOrder());
            StateSlot.IntSlot a = reg.addState("a", 0);
            StateSlot.IntSlot b = reg.addState("b", 0);
            reg.seal();

            List<String> order = new ArrayList<>();
            reg.on("a", (int from, int to) -> {
                order.add("a:" + to);
                if (to == 1) reg.set(b, 10); // re-dirty b during flush
            });
            reg.on("b", (int from, int to) -> order.add("b:" + to));

            reg.beginBatch();
            try {
                reg.set(a, 1);
            } finally {
                reg.endBatch();
            }

            // batch: a fires (pass 1), sets b which is batched, then b fires (pass 2)
            assertEquals(List.of("a:1", "b:10"), order);
        }
    }

    // ======================================================================
    // setIndex / push
    // ======================================================================

    @Nested
    class ArrayMutation {

        @Test
        void setIndexUpdatesElement() {
            reg = new StateRegistry();
            reg.addStateArr("pos", 1.0, 2.0, 3.0);
            reg.seal();

            reg.setIndex("pos", 1, 99.0);
            assertArrayEquals(new double[]{1.0, 99.0, 3.0}, reg.getDoubles("pos"));
        }

        @Test
        void setIndexFiresListener() {
            reg = new StateRegistry();
            StateSlot.DoublesSlot slot = reg.addStateArr("pos", 0.0, 0.0);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            reg.on("pos", (double[] from, double[] to) -> count.incrementAndGet());
            reg.setIndex(slot, 0, 5.0);
            assertEquals(1, count.get());
        }

        @Test
        void setIndexInts() {
            reg = new StateRegistry();
            reg.addStateArr("ids", 1, 2, 3);
            reg.seal();

            reg.setIndex("ids", 2, 99);
            assertArrayEquals(new int[]{1, 2, 99}, reg.getInts("ids"));
        }

        @Test
        void pushForcesListenerFire() {
            reg = new StateRegistry();
            StateSlot.DoublesSlot slot = reg.addStateArr("v", 0.0, 0.0);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            reg.on("v", (double[] from, double[] to) -> count.incrementAndGet());

            // mutate in-place without setIndex
            reg.getDoubles("v")[0] = 42.0;
            reg.push(slot);
            assertEquals(1, count.get());
        }
    }

    // ======================================================================
    // Silencing
    // ======================================================================

    @Nested
    class Silencing {

        @Test
        void silencedSlotDoesNotFireListeners() {
            reg = new StateRegistry();
            StateSlot.IntSlot slot = reg.addState("x", 0);
            reg.seal();

            AtomicInteger count = new AtomicInteger(0);
            reg.on("x", (int from, int to) -> count.incrementAndGet());

            StateSlot.SILENCED.setRelease(slot, true);
            reg.set(slot, 1);
            assertEquals(0, count.get());
            assertEquals(1, reg.getInt("x")); // value still updated

            StateSlot.SILENCED.setRelease(slot, false);
            reg.set(slot, 2);
            assertEquals(1, count.get());
        }

        @Test
        void silencedSlotSkippedInBatch() {
            reg = new StateRegistry(BatchStrategy.definitionOrder());
            StateSlot.IntSlot a = reg.addState("a", 0);
            StateSlot.IntSlot b = reg.addState("b", 0);
            reg.seal();

            List<String> fired = new ArrayList<>();
            reg.on("a", (int from, int to) -> fired.add("a"));
            reg.on("b", (int from, int to) -> fired.add("b"));

            StateSlot.SILENCED.setRelease(a, true);

            reg.beginBatch();
            try {
                reg.set(a, 1);
                reg.set(b, 1);
            } finally {
                reg.endBatch();
            }

            assertEquals(List.of("b"), fired);
        }
    }

    // ======================================================================
    // isDirty
    // ======================================================================

    @Nested
    class DirtyTracking {

        @Test
        void dirtyDuringBatch() {
            reg = new StateRegistry(BatchStrategy.definitionOrder());
            StateSlot.IntSlot slot = reg.addState("x", 0);
            reg.seal();

            assertFalse(reg.isDirty(slot));

            reg.beginBatch();
            try {
                reg.set(slot, 1);
                assertTrue(reg.isDirty(slot));
                assertTrue(reg.isDirty("x"));
            } finally {
                reg.endBatch();
            }

            assertFalse(reg.isDirty(slot), "dirty should be cleared after flush");
        }
    }

    // ======================================================================
    // Hard exit threshold
    // ======================================================================

    @Nested
    class HardExitThreshold {

        @Test
        void infiniteLoopTerminates() {
            reg = new StateRegistry(BatchStrategy.definitionOrder());
            StateSlot.IntSlot slot = reg.addState("x", 0);
            reg.seal();
            reg.setHardExitThreshold(5);

            AtomicInteger fireCount = new AtomicInteger(0);
            reg.on("x", (int from, int to) -> {
                fireCount.incrementAndGet();
                reg.set(slot, to + 1); // unconditionally re-dirty
            });

            reg.beginBatch();
            try {
                reg.set(slot, 1);
            } finally {
                reg.endBatch(); // should terminate after hardExitThreshold passes
            }

            assertTrue(fireCount.get() >= 5, "should have fired at least hardExitThreshold times");
            assertTrue(fireCount.get() < 100, "should not have run away");
        }

        @Test
        void gettersAndSetters() {
            reg = new StateRegistry();
            assertEquals(20, reg.getExcessivePassThreshold());
            assertEquals(100, reg.getHardExitThreshold());

            reg.setExcessivePassThreshold(50);
            reg.setHardExitThreshold(200);
            assertEquals(50, reg.getExcessivePassThreshold());
            assertEquals(200, reg.getHardExitThreshold());
        }
    }

    // ======================================================================
    // Slot handle accessors
    // ======================================================================

    @Nested
    class SlotHandleAccessors {

        @Test
        void allAccessorsReturnCorrectType() {
            reg = new StateRegistry();
            reg.addState("bool", false);
            reg.addState("int", 0);
            reg.addState("float", 0.0f);
            reg.addState("long", 0L);
            reg.addState("double", 0.0);
            reg.addStateArr("doubles", 1.0, 2.0);
            reg.addStateArr("ints", 1, 2);
            reg.addState("cstr", "a", "b");
            reg.addStateString("ustr", "x");
            reg.addStateObject("obj", null);
            reg.addState("enum", Thread.State.class, Thread.State.NEW);
            reg.seal();

            assertInstanceOf(StateSlot.BoolSlot.class, reg.getSlotBoolean("bool"));
            assertInstanceOf(StateSlot.IntSlot.class, reg.getSlotInt("int"));
            assertInstanceOf(StateSlot.FloatSlot.class, reg.getSlotFloat("float"));
            assertInstanceOf(StateSlot.LongSlot.class, reg.getSlotLong("long"));
            assertInstanceOf(StateSlot.DoubleSlot.class, reg.getSlotDouble("double"));
            assertInstanceOf(StateSlot.DoublesSlot.class, reg.getSlotDoubles("doubles"));
            assertInstanceOf(StateSlot.IntsSlot.class, reg.getSlotInts("ints"));
            assertInstanceOf(StateSlot.ConstrainedStringSlot.class, reg.getSlotConstrainedString("cstr"));
            assertInstanceOf(StateSlot.UnconstrainedStringSlot.class, reg.getSlotUnconstrainedString("ustr"));
            assertInstanceOf(StateSlot.ObjectSlot.class, reg.getSlotObject("obj"));
            assertInstanceOf(StateSlot.EnumSlot.class, reg.getSlotEnum("enum"));
        }

        @Test
        void handleBasedSetBypassesMapLookup() {
            reg = new StateRegistry();
            StateSlot.IntSlot slot = reg.addState("x", 0);
            reg.seal();

            AtomicInteger captured = new AtomicInteger(-1);
            reg.on("x", (int from, int to) -> captured.set(to));

            reg.set(slot, 77);
            assertEquals(77, captured.get());
            assertEquals(77, reg.getInt("x"));
        }
    }

    // ======================================================================
    // Defensive copy (setArrSafe)
    // ======================================================================

    @Nested
    class DefensiveCopy {

        @Test
        void setArrSafeDoublesIsolatesCallerArray() {
            reg = new StateRegistry();
            reg.addStateArr("d", 0.0);
            reg.seal();

            double[] arr = {1.0, 2.0};
            reg.setArrSafe("d", arr);
            arr[0] = 999.0;
            assertEquals(1.0, reg.getDoubles("d")[0], "internal array should not be affected");
        }

        @Test
        void setArrSafeIntsIsolatesCallerArray() {
            reg = new StateRegistry();
            reg.addStateArr("i", 0);
            reg.seal();

            int[] arr = {1, 2};
            reg.setArrSafe("i", arr);
            arr[0] = 999;
            assertEquals(1, reg.getInts("i")[0], "internal array should not be affected");
        }

        @Test
        void setArrRefSharesReference() {
            reg = new StateRegistry();
            reg.addStateArr("d", 0.0);
            reg.seal();

            double[] arr = {1.0, 2.0};
            reg.setArrRef("d", arr);
            arr[0] = 999.0;
            assertEquals(999.0, reg.getDoubles("d")[0], "ref should share the same array");
        }
    }

    // ======================================================================
    // Handle-based constrained string
    // ======================================================================

    @Nested
    class HandleBasedConstrainedString {

        @Test
        void rejectsInvalidValue() {
            reg = new StateRegistry();
            StateSlot.ConstrainedStringSlot slot = reg.addState("m", "a", "b");
            reg.seal();

            assertThrows(IllegalArgumentException.class, () -> reg.set(slot, "c"));
        }

        @Test
        void acceptsValidValue() {
            reg = new StateRegistry();
            StateSlot.ConstrainedStringSlot slot = reg.addState("m", "a", "b");
            reg.seal();

            reg.set(slot, "b");
            assertEquals("b", reg.getString("m"));
        }
    }
}
