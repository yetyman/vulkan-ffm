package io.github.yetyman.vulkan.sample.complex.models;

import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import java.lang.foreign.Arena;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Async glTF model loader with promise-based API
 */
public class GLTFLoader {
    private final ExecutorService loadingThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GLTFLoader");
        t.setDaemon(true);
        return t;
    });
    
    private final AtomicInteger nextModelId = new AtomicInteger(0);
    
    public GLTFLoader(Arena arena) {
        // Don't store the arena - create new ones in background thread
    }
    
    /**
     * Load glTF model from file path - can be called from any thread
     * @param filePath Path to .gltf or .glb file
     * @return CompletableFuture that resolves to ModelData when loaded
     */
    public CompletableFuture<ModelData> loadModel(String filePath) {
        System.out.println("[GLTF] Queuing load for: " + filePath);
        CompletableFuture<ModelData> promise = new CompletableFuture<>();
        
        // Queue loading on background thread
        loadingThread.submit(() -> {
            try {
                System.out.println("[GLTF] Starting load: " + filePath);
                ModelData modelData = loadModelSync(filePath);
                System.out.println("[GLTF] Load complete: " + filePath);
                promise.complete(modelData);
            } catch (Exception e) {
                System.err.println("[GLTF] Load failed: " + filePath + " - " + e.getMessage());
                e.printStackTrace();
                promise.completeExceptionally(e);
            }
        });
        
        return promise;
    }
    
    private ModelData loadModelSync(String filePath) throws Exception {
        // Create ModelData with unique ID
        int modelId = nextModelId.getAndIncrement();
        ModelData modelData = new ModelData(modelId);
        System.out.println("[GLTF] Created ModelData with ID: " + modelId);
        
        // Parse glTF file
        System.out.println("[GLTF] Parsing glTF file: " + filePath);
        GLTFData gltfData = parseGLTF(filePath);
        System.out.println("[GLTF] Parsed " + gltfData.vertices.length + " vertices, " + gltfData.indices.length + " indices");
        
        // Convert to LOD model using background thread arena
        System.out.println("[GLTF] Generating LOD levels...");
        try (Arena backgroundArena = Arena.ofConfined()) {
            LODConverter lodConverter = new LODConverter(backgroundArena);
            LODModel lodModel = lodConverter.generateLODModel(gltfData.vertices, gltfData.indices);
            System.out.println("[GLTF] Generated " + lodModel.getLODCount() + " LOD levels");
            
            // Create initial transform
            TransformationMatrix transform = new TransformationMatrix();
            
            // Load into ModelData
            modelData.loadModel(lodModel, transform);
            System.out.println("[GLTF] ModelData loaded and ready");
        }
        
        return modelData;
    }
    
    private GLTFData parseGLTF(String filePath) throws Exception {
        // Load glTF model from classpath resources
        GltfModelReader reader = new GltfModelReader();
        
        // Get resource URL from classpath
        var resourceUrl = getClass().getResource(filePath);
        if (resourceUrl == null) {
            throw new RuntimeException("Resource not found: " + filePath);
        }
        
        GltfModel gltfModel = reader.read(resourceUrl.toURI());
        
        // Extract first mesh data (robust parsing)
        SceneModel scene = gltfModel.getSceneModels().get(0);
        System.out.println("[GLTF] Scene has " + scene.getNodeModels().size() + " nodes");
        
        // Find first node with a mesh
        MeshModel meshModel = null;
        for (NodeModel node : scene.getNodeModels()) {
            if (!node.getMeshModels().isEmpty()) {
                meshModel = node.getMeshModels().get(0);
                break;
            }
            // Check child nodes recursively
            for (NodeModel child : node.getChildren()) {
                if (!child.getMeshModels().isEmpty()) {
                    meshModel = child.getMeshModels().get(0);
                    break;
                }
            }
            if (meshModel != null) break;
        }
        
        if (meshModel == null) {
            throw new RuntimeException("No mesh found in glTF file");
        }
        
        System.out.println("[GLTF] Found mesh with " + meshModel.getMeshPrimitiveModels().size() + " primitives");
        MeshPrimitiveModel primitive = meshModel.getMeshPrimitiveModels().get(0);
        
        // Get vertex positions
        AccessorModel positionsAccessor = primitive.getAttributes().get("POSITION");
        AccessorData positionsData = positionsAccessor.getAccessorData();
        FloatBuffer positionsBuffer = positionsData.createByteBuffer().asFloatBuffer();
        
        float[] vertices = new float[positionsBuffer.remaining()];
        positionsBuffer.get(vertices);
        
        // Get indices
        AccessorModel indicesAccessor = primitive.getIndices();
        AccessorData indicesData = indicesAccessor.getAccessorData();
        
        int[] indices;
        if (indicesAccessor.getComponentType() == 5123) { // UNSIGNED_SHORT
            var shortBuffer = indicesData.createByteBuffer().asShortBuffer();
            indices = new int[shortBuffer.remaining()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = shortBuffer.get(i) & 0xFFFF;
            }
        } else { // UNSIGNED_INT
            IntBuffer intBuffer = indicesData.createByteBuffer().asIntBuffer();
            indices = new int[intBuffer.remaining()];
            intBuffer.get(indices);
        }
        
        return new GLTFData(vertices, indices);
    }
    
    public void shutdown() {
        loadingThread.shutdown();
    }
    
    /**
     * Simple data holder for parsed glTF data
     */
    private static class GLTFData {
        final float[] vertices;
        final int[] indices;
        
        GLTFData(float[] vertices, int[] indices) {
            this.vertices = vertices;
            this.indices = indices;
        }
    }
}