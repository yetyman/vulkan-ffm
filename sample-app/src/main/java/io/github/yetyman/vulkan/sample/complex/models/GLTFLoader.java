package io.github.yetyman.vulkan.sample.complex.models;

import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.sample.complex.models.decimation.MeshSimplifier;
import io.github.yetyman.vulkan.sample.complex.models.decimation.SimplifiedMesh;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.Arena;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Async glTF model loader with promise-based API.
 * 
 * Thread Safety: This class is thread-safe. loadModel() can be called from any thread.
 * All loading operations are queued and executed on a dedicated background thread.
 */
public class GLTFLoader {
    private final ExecutorService loadingThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GLTFLoader");
        t.setDaemon(true);
        return t;
    });
    
    private final AtomicInteger nextModelId = new AtomicInteger(0);
    
    public GLTFLoader(Arena arena, VkDevice device, VkPhysicalDevice physicalDevice) {
        this.device = device;
        this.physicalDevice = physicalDevice;
    }
    
    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;
    
    /**
     * Load glTF model from file path - can be called from any thread
     * @param filePath Path to .gltf or .glb file
     * @return CompletableFuture that resolves to ModelData when loaded
     */
    public CompletableFuture<ModelData> loadModel(String filePath) {
        Logger.debug("Queuing load for: " + filePath);
        CompletableFuture<ModelData> promise = new CompletableFuture<>();
        
        // Queue loading on background thread
        loadingThread.submit(() -> {
            try {
                Logger.debug("Starting load: " + filePath);
                ModelData modelData = loadModelSync(filePath);
                Logger.debug("Load complete: " + filePath);
                promise.complete(modelData);
            } catch (Exception e) {
                Logger.error("Load failed: " + filePath + " - " + e.getMessage());
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
        Logger.debug("Created ModelData with ID: " + modelId);
        
        // Parse glTF file
        Logger.debug("Parsing glTF file: " + filePath);
        GLTFData gltfData = parseGLTF(filePath);
        Logger.debug("Parsed " + gltfData.vertices.length + " vertices, " + gltfData.indices.length + " indices");
        
        // Convert to LOD model using background thread arena
        Logger.debug("Generating LOD levels...");
        try (Arena backgroundArena = Arena.ofConfined()) {
            LODConverter lodConverter = new LODConverter(backgroundArena);
            lodConverter.setVulkanDevice(device, physicalDevice);
            
            // Generate decimated meshes for all LOD levels
            MeshSimplifier simplifier = new MeshSimplifier();
            float[] ratios = {1.0f, 0.65f, 0.40f, 0.20f, 0.08f};
            float[][] lodVertices = new float[5][];
            int[][] lodIndices = new int[5][];
            
            for (int i = 0; i < 5; i++) {
                SimplifiedMesh mesh = (i == 0) ? new SimplifiedMesh(gltfData.vertices, gltfData.indices)
                                               : simplifier.simplify(gltfData.vertices, gltfData.indices, ratios[i]);
                lodVertices[i] = mesh.vertices();
                lodIndices[i] = mesh.indices();
                Logger.debug("LOD" + i + ": " + mesh.triangleCount() + " triangles");
            }
            
            LODModel lodModel = lodConverter.generateLODModel(gltfData.vertices, gltfData.indices);
            Logger.debug("Generated " + lodModel.getLODCount() + " LOD levels");
            
            // Create initial transform
            TransformationMatrix transform = new TransformationMatrix();
            
            // Load into ModelData with per-LOD geometry data
            modelData.loadModel(lodModel, transform, lodVertices, lodIndices);
            
            // Mark LOD0 as resident since it has GPU buffers from LODConverter
            modelData.setLODResident(0, true);
            
            Logger.debug("ModelData loaded and ready");
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
        Logger.debug("Scene has " + scene.getNodeModels().size() + " nodes");
        
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
        
        Logger.debug("Found mesh with " + meshModel.getMeshPrimitiveModels().size() + " primitives");
        MeshPrimitiveModel primitive = meshModel.getMeshPrimitiveModels().get(0);
        
        // Debug: Check what attributes are available
        Logger.debug("Available attributes: " + primitive.getAttributes().keySet());
        boolean hasNormals = primitive.getAttributes().containsKey("NORMAL");
        boolean hasTexCoords = primitive.getAttributes().containsKey("TEXCOORD_0");
        Logger.debug("Has normals: " + hasNormals + ", has texcoords: " + hasTexCoords);
        
        // TODO: Refactor to separate vertex attribute arrays (SoA) instead of interleaved (AoS) for better cache efficiency and flexibility
        // Get vertex data (position + normal + texcoord)
        AccessorModel positionsAccessor = primitive.getAttributes().get("POSITION");
        AccessorData positionsData = positionsAccessor.getAccessorData();
        FloatBuffer positionsBuffer = positionsData.createByteBuffer().asFloatBuffer();
        
        int vertexCount = positionsBuffer.remaining() / 3;
        float[] vertices = new float[vertexCount * 8]; // 8 floats per vertex (pos=3, normal=3, texcoord=2)
        
        // Get normals if available
        FloatBuffer normalsBuffer = null;
        if (hasNormals) {
            AccessorModel normalsAccessor = primitive.getAttributes().get("NORMAL");
            AccessorData normalsData = normalsAccessor.getAccessorData();
            normalsBuffer = normalsData.createByteBuffer().asFloatBuffer();
        }
        
        // Get texture coordinates if available
        FloatBuffer texCoordsBuffer = null;
        if (hasTexCoords) {
            AccessorModel texCoordsAccessor = primitive.getAttributes().get("TEXCOORD_0");
            AccessorData texCoordsData = texCoordsAccessor.getAccessorData();
            texCoordsBuffer = texCoordsData.createByteBuffer().asFloatBuffer();
        }
        
        // Copy all vertex data
        for (int i = 0; i < vertexCount; i++) {
            // Position
            vertices[i * 8 + 0] = positionsBuffer.get(i * 3 + 0);
            vertices[i * 8 + 1] = positionsBuffer.get(i * 3 + 1);
            vertices[i * 8 + 2] = positionsBuffer.get(i * 3 + 2);
            
            // Normal
            if (normalsBuffer != null) {
                vertices[i * 8 + 3] = normalsBuffer.get(i * 3 + 0);
                vertices[i * 8 + 4] = normalsBuffer.get(i * 3 + 1);
                vertices[i * 8 + 5] = normalsBuffer.get(i * 3 + 2);
            } else {
                // Default normal (up)
                vertices[i * 8 + 3] = 0.0f;
                vertices[i * 8 + 4] = 1.0f;
                vertices[i * 8 + 5] = 0.0f;
            }
            
            // Texture coordinates
            if (texCoordsBuffer != null) {
                vertices[i * 8 + 6] = texCoordsBuffer.get(i * 2 + 0);
                vertices[i * 8 + 7] = texCoordsBuffer.get(i * 2 + 1);
            } else {
                // Default texcoord
                vertices[i * 8 + 6] = 0.0f;
                vertices[i * 8 + 7] = 0.0f;
            }
        }
        
        // Calculate bounds
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;
        for (int i = 0; i < vertices.length; i += 8) {
            minX = Math.min(minX, vertices[i]);
            maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i+1]);
            maxY = Math.max(maxY, vertices[i+1]);
            minZ = Math.min(minZ, vertices[i+2]);
            maxZ = Math.max(maxZ, vertices[i+2]);
        }
        Logger.debug("Model bounds: X[" + minX + ", " + maxX + "] Y[" + minY + ", " + maxY + "] Z[" + minZ + ", " + maxZ + "]");
        
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