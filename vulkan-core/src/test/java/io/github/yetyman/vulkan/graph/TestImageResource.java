package io.github.yetyman.vulkan.graph;

import io.github.yetyman.vulkan.graph.resources.GraphImageResource;
import io.github.yetyman.vulkan.graph.resources.ResourceLifetime;

import java.lang.foreign.MemorySegment;

/**
 * Test-only GraphImageResource that doesn't require Vulkan hardware.
 */
class TestImageResource implements GraphImageResource {

    private final String name;
    private int currentLayout;
    private int lastAccessMask;
    private int lastStageMask;
    private int owningQueueFamily = ~0;
    private final ResourceLifetime lifetime = new ResourceLifetime();

    TestImageResource(String name, int initialLayout, int initialAccess, int initialStage) {
        this.name = name;
        this.currentLayout = initialLayout;
        this.lastAccessMask = initialAccess;
        this.lastStageMask = initialStage;
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

    @Override public boolean isTransient() { return true; }
    @Override public boolean isImported() { return false; }
    @Override public ResourceLifetime lifetime() { return lifetime; }

    @Override public int format() { return 37; } // VK_FORMAT_R8G8B8A8_UNORM
    @Override public int currentLayout() { return currentLayout; }
    @Override public int width() { return 1920; }
    @Override public int height() { return 1080; }
    @Override public int layers() { return 1; }
    @Override public int mipLevels() { return 1; }
    @Override public int sampleCount() { return 1; }

    @Override
    public void updateLayout(int layout) {
        this.currentLayout = layout;
    }
}
