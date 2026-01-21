package io.github.yetyman.vulkan.sample.complex.models.decimation;

/**
 * Generates morph targets for smooth LOD transitions
 */
public class MorphTargetGenerator {
    
    /**
     * Generate morph targets: for each vertex in currentLOD, find where it goes in nextLOD
     */
    public static float[] generateMorphTargets(float[] currentVertices, float[] nextVertices) {
        int currentCount = currentVertices.length / 8;
        float[] morphTargets = new float[currentCount * 3]; // Only positions (x,y,z)
        
        for (int i = 0; i < currentCount; i++) {
            int offset = i * 8;
            float x = currentVertices[offset];
            float y = currentVertices[offset + 1];
            float z = currentVertices[offset + 2];
            
            // Find closest vertex in next LOD
            int closestIdx = findClosestVertex(x, y, z, nextVertices);
            
            // Store target position
            morphTargets[i * 3] = nextVertices[closestIdx * 8];
            morphTargets[i * 3 + 1] = nextVertices[closestIdx * 8 + 1];
            morphTargets[i * 3 + 2] = nextVertices[closestIdx * 8 + 2];
        }
        
        return morphTargets;
    }
    
    private static int findClosestVertex(float x, float y, float z, float[] vertices) {
        int vertexCount = vertices.length / 8;
        int closest = 0;
        float minDist = Float.MAX_VALUE;
        
        for (int i = 0; i < vertexCount; i++) {
            int offset = i * 8;
            float dx = vertices[offset] - x;
            float dy = vertices[offset + 1] - y;
            float dz = vertices[offset + 2] - z;
            float dist = dx*dx + dy*dy + dz*dz;
            
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        
        return closest;
    }
}
