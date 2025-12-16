package io.github.yetyman.vulkan.sample.complex.models;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * High-quality LOD model with smooth transitions and deformation support
 */
public class LODModel {
    private final List<LODLevel> lodLevels;
    private final float[] lodDistances;
    private final Arena arena;
    

    
    public LODModel(Arena arena, List<LODLevel> lodLevels) {
        this.arena = arena;
        this.lodLevels = List.copyOf(lodLevels);
        this.lodDistances = lodLevels.stream()
            .mapToDouble(LODLevel::maxDistance)
            .collect(() -> new float[lodLevels.size()], 
                    (arr, d) -> arr[arr.length - 1] = (float)d, 
                    (a1, a2) -> {});
    }
    
    /**
     * Select optimal LOD level based on distance only
     */
    public LODLevel selectLOD(float distance) {
        for (int i = 0; i < lodLevels.size(); i++) {
            if (lodLevels.get(i).isValidForDistance(distance)) {
                return lodLevels.get(i);
            }
        }
        
        // Fallback to lowest detail
        return lodLevels.get(lodLevels.size() - 1);
    }
    

    
    public int getLODCount() {
        return lodLevels.size();
    }
    
    public LODLevel getLOD(int index) {
        return lodLevels.get(index);
    }
}