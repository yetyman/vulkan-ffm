package io.github.yetyman.vulkan.mesh.residency;

import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.buffers.GpuCompletion;
import io.github.yetyman.vulkan.mesh.source.Residency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The default {@link ResidencyTracker}. Drives loading and unloading through an app-supplied
 * {@link PartitionLoader}, tracks per-partition state/claims/recency/byte size in memory, and
 * delegates victim selection to a configurable {@link EvictionPolicy} (default
 * {@link LruEvictionPolicy}).
 *
 * <p>Not thread-safe. A tracker is expected to be driven from one thread (typically the frame
 * thread, once per frame for {@link #evict} and on demand for {@link #request}/{@link #release}),
 * matching every other single-threaded-owner type in this module ({@code TransferBatch},
 * {@code RetireQueue}). Guard externally if multiple threads must drive the same tracker.
 */
public final class DefaultResidencyTracker implements ResidencyTracker {

    private static final class Entry {
        Residency state = Residency.ABSENT;
        int claimCount = 0;
        long byteSize = 0;
        long lastTouchedTick = -1;
        GpuCompletion pending;
    }

    private final PartitionLoader loader;
    private final VkQueue queue;
    private final EvictionPolicy evictionPolicy;
    private final Map<PartitionRef, Entry> entries = new HashMap<>();
    private final List<ResidencyListener> listeners = new ArrayList<>();
    private final AtomicLong tickCounter = new AtomicLong(0);

    public DefaultResidencyTracker(PartitionLoader loader, VkQueue queue) {
        this(loader, queue, new LruEvictionPolicy());
    }

    public DefaultResidencyTracker(PartitionLoader loader, VkQueue queue, EvictionPolicy evictionPolicy) {
        if (loader == null) throw new IllegalArgumentException("loader required");
        if (queue == null) throw new IllegalArgumentException("queue required");
        if (evictionPolicy == null) throw new IllegalArgumentException("evictionPolicy required");
        this.loader = loader;
        this.queue = queue;
        this.evictionPolicy = evictionPolicy;
    }

    @Override
    public Residency stateOf(PartitionRef ref) {
        Entry e = entries.get(ref);
        return e == null ? Residency.ABSENT : e.state;
    }

    @Override
    public GpuCompletion request(PartitionRef ref, Priority priority) {
        Entry e = entries.computeIfAbsent(ref, r -> new Entry());
        e.claimCount++;
        e.lastTouchedTick = tickCounter.incrementAndGet();

        if (e.state == Residency.DEVICE || e.state == Residency.HOST_AND_DEVICE) {
            return GpuCompletion.completed();
        }
        if (e.state == Residency.PENDING) {
            // Already loading; caller shares the in-flight result rather than triggering a second load.
            return e.pending;
        }

        // ABSENT or EVICTING (a request arriving mid-eviction cancels the eviction in spirit: we
        // simply start a fresh load, since the loader owns whether the old allocation is reusable).
        Residency old = e.state;
        e.state = Residency.PENDING;
        fireChanged(ref, old, Residency.PENDING);

        PartitionLoader.LoadResult result = loader.load(ref, queue);
        e.byteSize = result.byteSize();
        e.pending = result.completion();

        GpuCompletion tracked = result.completion();
        tracked.onComplete(() -> {
            Entry current = entries.get(ref);
            if (current == null || current.state != Residency.PENDING) return; // released/evicted mid-flight
            Residency prior = current.state;
            current.state = Residency.DEVICE;
            current.pending = null;
            fireChanged(ref, prior, Residency.DEVICE);
        });

        return tracked;
    }

    @Override
    public void release(PartitionRef ref) {
        Entry e = entries.get(ref);
        if (e == null || e.claimCount <= 0) {
            throw new IllegalStateException("no outstanding claim to release for " + ref);
        }
        e.claimCount--;
    }

    @Override
    public long evict(long bytesNeeded) {
        List<PartitionRef> victims = evictionPolicy.selectVictims(bytesNeeded, view);
        long freed = 0;
        for (PartitionRef ref : victims) {
            Entry e = entries.get(ref);
            if (e == null) continue;
            if (e.claimCount > 0) continue; // never evict a claimed partition, regardless of policy
            if (e.state != Residency.DEVICE && e.state != Residency.HOST_AND_DEVICE) continue;

            Residency old = e.state;
            e.state = Residency.EVICTING;
            fireChanged(ref, old, Residency.EVICTING);

            loader.unload(ref);
            freed += e.byteSize;

            Residency mid = e.state;
            e.state = Residency.ABSENT;
            e.byteSize = 0;
            fireChanged(ref, mid, Residency.ABSENT);

            if (freed >= bytesNeeded) break;
        }
        return freed;
    }

    @Override
    public void addListener(ResidencyListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ResidencyListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        // Force-unload everything still resident. Mirrors TransferBatch.destroy()'s teardown
        // posture: called after external synchronization (device idle), so claims are ignored.
        for (Map.Entry<PartitionRef, Entry> me : entries.entrySet()) {
            Entry e = me.getValue();
            if (e.state == Residency.DEVICE || e.state == Residency.HOST_AND_DEVICE) {
                loader.unload(me.getKey());
            }
        }
        entries.clear();
        listeners.clear();
    }

    private void fireChanged(PartitionRef ref, Residency oldState, Residency newState) {
        for (ResidencyListener l : listeners) l.onResidencyChanged(ref, oldState, newState);
    }

    private final ResidencyView view = new ResidencyView() {
        @Override
        public List<PartitionRef> tracked() {
            return new ArrayList<>(entries.keySet());
        }

        @Override
        public Residency stateOf(PartitionRef ref) {
            return DefaultResidencyTracker.this.stateOf(ref);
        }

        @Override
        public long byteSizeOf(PartitionRef ref) {
            Entry e = entries.get(ref);
            return e == null ? 0 : e.byteSize;
        }

        @Override
        public int claimCountOf(PartitionRef ref) {
            Entry e = entries.get(ref);
            return e == null ? 0 : e.claimCount;
        }

        @Override
        public long lastTouchedTick(PartitionRef ref) {
            Entry e = entries.get(ref);
            return e == null ? -1 : e.lastTouchedTick;
        }
    };
}
