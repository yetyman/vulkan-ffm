package io.github.yetyman.vulkan.sample.complex.models;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Contiguous instance data for parallel processing
 */
public class InstanceData {
    private final MemorySegment modelDataPtrs; // 1 long per instance (pointer to ModelData)
    private final MemorySegment matrices;      // 16 floats per instance (GPU-ready)
    private final boolean[] activeInstances;   // Track which instances are active
    private final int capacity;
    private int count = 0;
    
    public InstanceData(Arena arena, int maxInstances) {
        this.capacity = maxInstances;
        this.modelDataPtrs = arena.allocate(maxInstances * Long.BYTES);
        this.matrices = arena.allocate(maxInstances * 16 * Float.BYTES);
        this.activeInstances = new boolean[maxInstances];
    }
    
    public int addInstance(ModelData modelData) {
        // Find first inactive slot
        int instanceId = -1;
        for (int i = 0; i < capacity; i++) {
            if (!activeInstances[i]) {
                instanceId = i;
                break;
            }
        }
        if (instanceId == -1) return -1; // No free slots
        
        activeInstances[instanceId] = true;
        if (instanceId >= count) count = instanceId + 1;
        
        // Store pointer to ModelData (using modelId as simple pointer)
        modelDataPtrs.setAtIndex(ValueLayout.JAVA_LONG, instanceId, modelData.getModelId());
        
        return instanceId;
    }
    
    public void getPosition(int instanceId, float[] out, ModelData[] modelDataArray) {
        long modelDataId = modelDataPtrs.getAtIndex(ValueLayout.JAVA_LONG, instanceId);
        ModelData modelData = modelDataArray[(int)modelDataId];
        if (modelData != null && modelData.isLoaded()) {
            float[] pos = modelData.getTransform().getPosition();
            System.arraycopy(pos, 0, out, 0, 3);
        }
    }
    
    public ModelData getModelData(int instanceId, ModelData[] modelDataArray) {
        long modelDataId = modelDataPtrs.getAtIndex(ValueLayout.JAVA_LONG, instanceId);
        return modelDataArray[(int)modelDataId];
    }
    
    public void removeInstance(int instanceId) {
        if (instanceId >= 0 && instanceId < capacity) {
            activeInstances[instanceId] = false;
        }
    }
    
    public boolean isActive(int instanceId) {
        return instanceId >= 0 && instanceId < capacity && activeInstances[instanceId];
    }
    
    public MemorySegment getMatrix(int instanceId) {
        return matrices.asSlice(instanceId * 16 * Float.BYTES, 16 * Float.BYTES);
    }
    
    public void syncMatrixFromModelData(int instanceId, ModelData[] modelDataArray) {
        long modelDataId = modelDataPtrs.getAtIndex(ValueLayout.JAVA_LONG, instanceId);
        ModelData modelData = modelDataArray[(int)modelDataId];
        if (modelData != null && modelData.isLoaded()) {
            float[] matrix = modelData.getTransform().getMatrix();
            int matOffset = instanceId * 16;
            for (int i = 0; i < 16; i++) {
                matrices.setAtIndex(ValueLayout.JAVA_FLOAT, matOffset + i, matrix[i]);
            }
        }
    }
    
    public void updateInstance(int instanceId, ModelData newModelData) {
        if (!isActive(instanceId)) return;
        modelDataPtrs.setAtIndex(ValueLayout.JAVA_LONG, instanceId, newModelData.getModelId());
    }
    
    public int getCount() { return count; }
    public int getCapacity() { return capacity; }
    
    public MemorySegment getMatricesBuffer() {
        return matrices;
    }
}