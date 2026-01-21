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
    private int currentLODIndex = -1;
    private static final float HYSTERESIS = 1.5f; // Prevent thrashing between levels
    
    public record LODSelection(LODLevel level, float blendFactor) {}
    private static final float TRANSITION_RANGE = 5.0f;
    
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
     * Select optimal LOD level with blend factor for geomorphing
     */
    public LODSelection selectLODWithBlend(float distance) {
        for (int i = 0; i < lodLevels.size() - 1; i++) {
            LODLevel current = lodLevels.get(i);
            LODLevel next = lodLevels.get(i + 1);
            
            if (distance < current.maxDistance() - TRANSITION_RANGE) {
                return new LODSelection(current, 0.0f);
            }
            
            if (distance <= current.maxDistance() + TRANSITION_RANGE) {
                float transitionStart = current.maxDistance() - TRANSITION_RANGE;
                float transitionEnd = current.maxDistance() + TRANSITION_RANGE;
                float blend = smoothstep(transitionStart, transitionEnd, distance);
                return new LODSelection(current, blend);
            }
        }
        
        return new LODSelection(lodLevels.get(lodLevels.size() - 1), 0.0f);
    }
    
    private float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
    
    public LODLevel selectLOD(float distance) {
        int targetIndex = lodLevels.size() - 1;
        for (int i = 0; i < lodLevels.size(); i++) {
            if (distance <= lodLevels.get(i).maxDistance()) {
                targetIndex = i;
                break;
            }
        }
        
        if (currentLODIndex != -1 && currentLODIndex != targetIndex) {
            float boundaryDist = (targetIndex < currentLODIndex) 
                ? lodLevels.get(targetIndex).maxDistance()
                : lodLevels.get(currentLODIndex).maxDistance();
            
            if (targetIndex < currentLODIndex && distance > boundaryDist - HYSTERESIS) {
                targetIndex = currentLODIndex;
            } else if (targetIndex > currentLODIndex && distance < boundaryDist + HYSTERESIS) {
                targetIndex = currentLODIndex;
            }
        }
        
        if (targetIndex != currentLODIndex) {
            int oldIndex = currentLODIndex;
            currentLODIndex = targetIndex;
            LODLevel lod = lodLevels.get(targetIndex);
            io.github.yetyman.vulkan.util.Logger.info("[LOD CHANGE] " + oldIndex + " → " + targetIndex + 
                " | Distance: " + String.format("%.1f", distance) + "m" +
                " | Triangles: " + lod.triangleCount() + 
                " (" + (int)(lod.detailFactor() * 100) + "%)");
        }
        
        return lodLevels.get(currentLODIndex);
    }    
    public int getLODCount() {
        return lodLevels.size();
    }
    
    public LODLevel getLOD(int index) {
        return lodLevels.get(index);
    }
}