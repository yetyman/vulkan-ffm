package io.github.yetyman.vulkan.sample.complex.models;

import java.lang.foreign.MemorySegment;

/**
 * Batch state for efficient bulk operations
 */
public class BatchState {
    private final boolean[] instanceEnabled;
    private final boolean[] matrixDirty;
    private final int[] enabledInstances;
    private final int[] dirtyInstances;
    private int enabledCount = 0;
    private int dirtyCount = 0;
    
    public BatchState(int maxInstances) {
        this.instanceEnabled = new boolean[maxInstances];
        this.matrixDirty = new boolean[maxInstances];
        this.enabledInstances = new int[maxInstances];
        this.dirtyInstances = new int[maxInstances];
    }
    
    public void markInstanceEnabled(int instanceId, boolean enabled) {
        instanceEnabled[instanceId] = enabled;
    }
    
    public void markMatrixDirty(int instanceId) {
        matrixDirty[instanceId] = true;
    }
    
    public void buildBatches() {
        enabledCount = 0;
        dirtyCount = 0;
        
        for (int i = 0; i < instanceEnabled.length; i++) {
            if (instanceEnabled[i]) {
                enabledInstances[enabledCount++] = i;
            }
            if (matrixDirty[i]) {
                dirtyInstances[dirtyCount++] = i;
                matrixDirty[i] = false; // Clear dirty flag
            }
        }
    }
    
    public int[] getEnabledInstances() { return enabledInstances; }
    public int[] getDirtyInstances() { return dirtyInstances; }
    public int getEnabledCount() { return enabledCount; }
    public int getDirtyCount() { return dirtyCount; }
}