package io.github.yetyman.vulkan.sample.complex.models;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.util.Logger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.lang.foreign.Arena;

/**
 * Manages LOD models and instances - independent of rendering
 */
public class ModelRegistry {
    private final List<LODModel> models = new ArrayList<>();
    private final InstanceData instanceData;
    private final ModelData[] modelDataArray;
    private final GLTFLoader gltfLoader;
    
    public ModelRegistry(Arena arena, int maxInstances, int maxModelData, VkDevice device, VkPhysicalDevice physicalDevice) {
        Arena sharedArena = Arena.ofShared();
        this.instanceData = new InstanceData(sharedArena, maxInstances, device, physicalDevice);
        this.modelDataArray = new ModelData[maxModelData];
        this.gltfLoader = new GLTFLoader(arena, device, physicalDevice);
    }
    
    public void setVulkanDevice(io.github.yetyman.vulkan.VkDevice device, io.github.yetyman.vulkan.VkPhysicalDevice physicalDevice) {
        // Device is now set in constructor
    }
    
    public int addModel(LODModel model) {
        models.add(model);
        return models.size() - 1;
    }
    
    public int addInstance(ModelData modelData) {
        int instanceId = instanceData.addInstance(modelData);
        modelDataArray[instanceId] = modelData;
        return instanceId;
    }
    
    public void removeInstance(int instanceId) {
        instanceData.removeInstance(instanceId);
    }
    
    public void updateInstance(int instanceId, ModelData newModelData) {
        modelDataArray[instanceId] = newModelData;
        instanceData.updateInstance(instanceId, newModelData);
    }
    
    public int getInstanceCount() {
        return instanceData.getCount();
    }
    
    public InstanceData getInstanceData() {
        return instanceData;
    }
    
    public ModelData[] getModelDataArray() {
        return modelDataArray;
    }
    
    public CompletableFuture<ModelData> loadGLTFModel(String filePath) {
        return gltfLoader.loadModel(filePath);
    }
    
    public void cleanup() {
        gltfLoader.shutdown();
        if (instanceData != null) {
            instanceData.cleanup();
        }
        Logger.info("ModelRegistry cleanup complete");
    }
}
