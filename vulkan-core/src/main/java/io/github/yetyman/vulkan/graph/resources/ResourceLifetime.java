package io.github.yetyman.vulkan.graph.resources;

/**
 * Tracks the first-write to last-read interval for a resource version within the graph.
 * Used by the aliaser to determine which transient resources can share physical memory.
 */
public class ResourceLifetime {

    private int firstWritePass = -1;
    private int lastReadPass = -1;

    /** Records a write at the given pass index */
    public void recordWrite(int passIndex) {
        if (firstWritePass < 0 || passIndex < firstWritePass) {
            firstWritePass = passIndex;
        }
        // A write also extends the "alive" range
        if (passIndex > lastReadPass) {
            lastReadPass = passIndex;
        }
    }

    /** Records a read at the given pass index */
    public void recordRead(int passIndex) {
        if (passIndex > lastReadPass) {
            lastReadPass = passIndex;
        }
    }

    /** @return pass index of first write, or -1 if never written */
    public int firstWritePass() { return firstWritePass; }

    /** @return pass index of last read (or write), or -1 if never used */
    public int lastReadPass() { return lastReadPass; }

    /** @return true if this lifetime overlaps with another */
    public boolean overlaps(ResourceLifetime other) {
        if (firstWritePass < 0 || other.firstWritePass < 0) return false;
        return firstWritePass <= other.lastReadPass && other.firstWritePass <= lastReadPass;
    }

    /** @return true if this lifetime has been populated with at least one usage */
    public boolean isValid() {
        return firstWritePass >= 0;
    }
}
