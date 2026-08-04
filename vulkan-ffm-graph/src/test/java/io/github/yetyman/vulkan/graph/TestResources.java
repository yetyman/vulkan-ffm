package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.resources.GraphResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;

import java.lang.foreign.MemorySegment;

/**
 * Lightweight test-only GraphResource implementations that don't require Vulkan hardware.
 */
final class TestResources {

    private TestResources() {}

    static GraphResource transientBuffer(String name) {
        return new FakeResource(name, true, false);
    }

    static GraphResource importedBuffer(String name) {
        return new FakeResource(name, false, true);
    }

    static GraphResource persistentBuffer(String name) {
        return new FakeResource(name, false, false);
    }

    private static class FakeResource implements GraphResource {
        private final String name;
        private final boolean transientRes;
        private final boolean imported;
        private final ResourceLifetime lifetime = new ResourceLifetime();
        private int lastAccessMask;
        private int lastStageMask;
        private int owningQueueFamily = ~0;

        FakeResource(String name, boolean transientRes, boolean imported) {
            this.name = name;
            this.transientRes = transientRes;
            this.imported = imported;
        }

        @Override public String name() { return name; }
        @Override public MemorySegment handle() { return MemorySegment.NULL; }
        @Override public int lastAccessMask() { return lastAccessMask; }
        @Override public int lastStageMask() { return lastStageMask; }
        @Override public int owningQueueFamily() { return owningQueueFamily; }

        @Override
        public void updateState(int accessMask, int stageMask, int queueFamily) {
            this.lastAccessMask = accessMask;
            this.lastStageMask = stageMask;
            this.owningQueueFamily = queueFamily;
        }

        @Override public boolean isTransient() { return transientRes; }
        @Override public boolean isImported() { return imported; }
        @Override public ResourceLifetime lifetime() { return lifetime; }
    }
}
