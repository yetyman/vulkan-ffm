package io.github.yetyman.vulkan.mesh.partition;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An identity token for a per-partition integer metadata channel. Instances are compared by
 * reference identity (not by name), and carry a sequential integer ID for O(1) array-indexed
 * lookup in {@link PartitionMetadata}.
 *
 * <p>Keys are meant to be held as {@code static final} fields and shared across all meshes.
 * The key identifies the <em>concept</em> ("LOD node index"); the data lives in a
 * {@link PartitionMetadata} registry, one per partition set.
 *
 * <p>Two independent subsystems using the same key on the same {@link PartitionMetadata} are
 * guaranteed to read and write the same {@code int[]} — no silent divergence.
 *
 * <p>Sequential IDs enable direct array indexing. The ID space is shared across all int
 * channel keys in the JVM. The maximum ID determines the minimum array length in the registry;
 * arrays grow lazily.
 */
public final class IntChannelKey {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private final int id;
    private final String name;

    private IntChannelKey(String name) {
        this.id = NEXT_ID.getAndIncrement();
        this.name = name;
    }

    /**
     * Creates a new int channel key. Each call returns a distinct identity with a unique ID.
     * Store as a static final field.
     *
     * @param name diagnostic name (does not affect identity)
     */
    public static IntChannelKey of(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        return new IntChannelKey(name);
    }

    /** Sequential integer ID for O(1) array-indexed lookup. */
    public int id() { return id; }

    /** Diagnostic name. */
    public String name() { return name; }

    /** @return the byte size of one element (always 4 for int). */
    public int byteSize() { return 4; }

    @Override
    public String toString() {
        return "IntChannelKey[" + name + "#" + id + "]";
    }

    // Identity semantics: equals/hashCode are Object defaults (reference equality).

    /**
     * @return the current number of registered int channel keys (useful for sizing arrays)
     */
    public static int registeredCount() {
        return NEXT_ID.get();
    }
}
