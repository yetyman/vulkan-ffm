package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkBuffer;
import io.github.yetyman.vulkan.enums.VkBufferUsageFlagBits;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Contiguous instance data for parallel processing
 */
public class InstanceData {
    private final MemorySegment modelDataPtrs; // 1 long per instance (pointer to ModelData)
    private final VkBuffer matricesBuffer;     // VkBuffer for GPU-ready matrices
    private final MemorySegment matrices;      // Host-visible memory for matrices
    private final boolean[] activeInstances;   // Track which instances are active
    private final int capacity;
    private int count = 0;
    
    public InstanceData(Arena arena, int maxInstances, MemorySegment device, MemorySegment physicalDevice) {
        this.capacity = maxInstances;
        this.modelDataPtrs = arena.allocate(maxInstances * Long.BYTES);
        
        // Create proper VkBuffer for matrices
        long bufferSize = maxInstances * 16 * Float.BYTES;
        this.matricesBuffer = VkBuffer.builder()
            .device(device)
            .physicalDevice(physicalDevice)
            .size(bufferSize)
            .vertexBuffer()
            .hostVisible()
            .build(arena);
        
        // Map the buffer memory for CPU access
        this.matrices = mapBufferMemory(device, matricesBuffer, bufferSize);
        this.activeInstances = new boolean[maxInstances];
    }
    
    private MemorySegment mapBufferMemory(MemorySegment device, VkBuffer buffer, long size) {
        try {
            MemorySegment mappedPtr = Arena.ofShared().allocate(ValueLayout.ADDRESS);
            io.github.yetyman.vulkan.VulkanExtensions.mapMemory(device, buffer.memory(), 0, size, 0, mappedPtr).check();
            MemorySegment mappedAddress = mappedPtr.get(ValueLayout.ADDRESS, 0);
            return MemorySegment.ofAddress(mappedAddress.address()).reinterpret(size);
        } catch (Exception e) {
            throw new RuntimeException("Failed to map buffer memory", e);
        }
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
        return matricesBuffer.handle(); // Return the encoded VkBuffer handle
    }
    
    public void cleanup() {
        // Clean up the VkBuffer
        if (matricesBuffer != null) {
            matricesBuffer.close();
        }
        System.out.println("[OK] InstanceData cleanup complete");
    }
}