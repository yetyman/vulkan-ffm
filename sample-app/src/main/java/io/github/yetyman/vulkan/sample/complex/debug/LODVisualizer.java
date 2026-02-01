package io.github.yetyman.vulkan.sample.complex.debug;

public class LODVisualizer {
    private boolean wireframeMode = false;
    private boolean colorCodeLODs = false;
    private boolean splitScreenMode = false;
    
    public void toggleWireframe() { wireframeMode = !wireframeMode; }
    public void toggleColorCoding() { colorCodeLODs = !colorCodeLODs; }
    public void toggleSplitScreen() { splitScreenMode = !splitScreenMode; }
    
    public boolean isWireframeEnabled() { return wireframeMode; }
    public boolean isColorCodingEnabled() { return colorCodeLODs; }
    public boolean isSplitScreenEnabled() { return splitScreenMode; }
    
    public float[] getLODColor(int lodLevel) {
        return switch(lodLevel) {
            case 0 -> new float[]{1.0f, 0.0f, 0.0f}; // Red - highest detail
            case 1 -> new float[]{1.0f, 0.5f, 0.0f}; // Orange
            case 2 -> new float[]{1.0f, 1.0f, 0.0f}; // Yellow
            case 3 -> new float[]{0.0f, 1.0f, 0.0f}; // Green
            default -> new float[]{0.0f, 0.0f, 1.0f}; // Blue - lowest detail
        };
    }
    
    public float getSplitScreenOffset(int lodIndex) {
        return (lodIndex - 2) * 3.0f; // Space models 3 units apart
    }
}
