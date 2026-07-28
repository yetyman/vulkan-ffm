package io.github.yetyman.vulkan.foundation.ecs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PropertyNotifier: enum-keyed property change notification.
 */
class PropertyNotifierTest {

    enum TestProp { X, Y, COLOR, TEXT }

    @Test
    void fireInvokesObserver() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        List<String> received = new ArrayList<>();

        notifier.observe(TestProp.X, () -> received.add("x-changed"));
        notifier.fire(TestProp.X);

        assertEquals(List.of("x-changed"), received);
    }

    @Test
    void fireOnlyInvokesMatchingProperty() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        List<String> received = new ArrayList<>();

        notifier.observe(TestProp.X, () -> received.add("x"));
        notifier.observe(TestProp.Y, () -> received.add("y"));

        notifier.fire(TestProp.X);

        assertEquals(List.of("x"), received);
    }

    @Test
    void multipleObserversOnSameProperty() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        List<String> received = new ArrayList<>();

        notifier.observe(TestProp.COLOR, () -> received.add("a"));
        notifier.observe(TestProp.COLOR, () -> received.add("b"));
        notifier.fire(TestProp.COLOR);

        assertEquals(List.of("a", "b"), received);
    }

    @Test
    void unobserveRemovesListener() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        List<String> received = new ArrayList<>();

        Runnable listener = () -> received.add("fired");
        notifier.observe(TestProp.TEXT, listener);
        notifier.unobserve(TestProp.TEXT, listener);
        notifier.fire(TestProp.TEXT);

        assertTrue(received.isEmpty());
    }

    @Test
    void fireWithNoObserversIsHarmless() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        assertDoesNotThrow(() -> notifier.fire(TestProp.X));
    }

    @Test
    void clearRemovesAllListeners() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        List<String> received = new ArrayList<>();

        notifier.observe(TestProp.X, () -> received.add("x"));
        notifier.observe(TestProp.Y, () -> received.add("y"));
        notifier.clear();
        notifier.fire(TestProp.X);
        notifier.fire(TestProp.Y);

        assertTrue(received.isEmpty());
    }

    @Test
    void clearSpecificPropertyOnlyAffectsThatProperty() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        List<String> received = new ArrayList<>();

        notifier.observe(TestProp.X, () -> received.add("x"));
        notifier.observe(TestProp.Y, () -> received.add("y"));
        notifier.clear(TestProp.X);
        notifier.fire(TestProp.X);
        notifier.fire(TestProp.Y);

        assertEquals(List.of("y"), received);
    }

    @Test
    void observerCount() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        assertEquals(0, notifier.observerCount(TestProp.X));

        notifier.observe(TestProp.X, () -> {});
        notifier.observe(TestProp.X, () -> {});
        assertEquals(2, notifier.observerCount(TestProp.X));
        assertEquals(0, notifier.observerCount(TestProp.Y));
    }

    @Test
    void hasObservers() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        assertFalse(notifier.hasObservers(TestProp.X));

        notifier.observe(TestProp.X, () -> {});
        assertTrue(notifier.hasObservers(TestProp.X));
        assertFalse(notifier.hasObservers(TestProp.Y));
    }

    @Test
    void fireMultipleProperties() {
        PropertyNotifier<TestProp> notifier = new PropertyNotifier<>(TestProp.class);
        List<String> received = new ArrayList<>();

        notifier.observe(TestProp.X, () -> received.add("x"));
        notifier.observe(TestProp.Y, () -> received.add("y"));
        notifier.observe(TestProp.COLOR, () -> received.add("color"));

        notifier.fire(TestProp.X, TestProp.COLOR);

        assertEquals(List.of("x", "color"), received);
    }

    // --- Integration: component using PropertyNotifier ---

    static class TestDataComponent implements Component {
        public enum Prop { VALUE, LABEL }

        private final PropertyNotifier<Prop> notifier = new PropertyNotifier<>(Prop.class);
        private int value;
        private String label = "";

        public int value() { return value; }
        public void setValue(int v) { this.value = v; notifier.fire(Prop.VALUE); }

        public String label() { return label; }
        public void setLabel(String l) { this.label = l; notifier.fire(Prop.LABEL); }

        public PropertyNotifier<Prop> properties() { return notifier; }
    }

    static class TestRenderComponent implements Component {
        int updateCount = 0;
        TestDataComponent data;

        @Override
        public java.util.List<Dependency<?>> requires() {
            return java.util.List.of(Dependency.selfRequired(TestDataComponent.class));
        }

        @Override
        public void resolveDependencies(Node node) {
            data = node.findComponent(TestDataComponent.class);
        }

        @Override
        public void afterResolve(Node node) {
            // Subscribe to property changes on the resolved dependency
            data.properties().observe(TestDataComponent.Prop.VALUE, () -> updateCount++);
        }

        @Override
        public void close(Node node) {
            if (data != null) {
                data.properties().clear();
            }
        }
    }

    @Test
    void renderComponentReceivesPropertyChanges() {
        try (Tree tree = new Tree()) {
            TestDataComponent data = new TestDataComponent();
            TestRenderComponent render = new TestRenderComponent();
            tree.root().addComponent(data);
            tree.root().addComponent(render);
            tree.initialize();

            assertEquals(0, render.updateCount);

            data.setValue(42);
            assertEquals(1, render.updateCount);

            data.setValue(99);
            assertEquals(2, render.updateCount);

            // Label change doesn't trigger render (it only observes VALUE)
            data.setLabel("hello");
            assertEquals(2, render.updateCount);
        }
    }
}
