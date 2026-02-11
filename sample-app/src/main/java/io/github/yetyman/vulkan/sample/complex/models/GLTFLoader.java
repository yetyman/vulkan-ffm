package io.github.yetyman.vulkan.sample.complex.models;

import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkPhysicalDevice;
import io.github.yetyman.vulkan.highlevel.VulkanMesh;
import io.github.yetyman.vulkan.highlevel.VkVertexFormat;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.util.Logger;
import java.lang.foreign.*;
import java.util.concurrent.*;
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
    
    private final Arena arena;
    private final VkDevice device;

    public GLTFLoader(Arena arena, VkDevice device) {
        this.arena = arena;
        this.device = device;
    }
    
    /**
     * Load glTF model from file path - can be called from any thread
     * @param filePath Path to .gltf or .glb file
     * @return CompletableFuture that resolves to VulkanMesh when loaded
     */
    public CompletableFuture<VulkanMesh> loadModel(String filePath) {
        Logger.debug("Queuing load for: " + filePath);
        CompletableFuture<VulkanMesh> promise = new CompletableFuture<>();
        
        // Queue loading on background thread
        loadingThread.submit(() -> {
            try {
                Logger.debug("Starting load: " + filePath);
                VulkanMesh mesh = loadModelSync(filePath);
                Logger.debug("Load complete: " + filePath);
                promise.complete(mesh);
            } catch (Exception e) {
                Logger.error("Load failed: " + filePath + " - " + e.getMessage());
                e.printStackTrace();
                promise.completeExceptionally(e);
            }
        });
        
        return promise;
    }
    
    private VulkanMesh loadModelSync(String filePath) throws Exception {
        // Parse glTF file
        Logger.debug("Parsing glTF file: " + filePath);
        GLTFData gltfData = parseGLTF(filePath);
        Logger.debug("Parsed " + gltfData.vertexCount + " vertices, " + gltfData.indexCount + " indices");
        
        // Create vertex format (position + normal + texcoord)
        VkVertexFormat format = VkVertexFormat.builder()
            .perVertexBinding(0, 32)  // 8 floats * 4 bytes = 32 bytes per vertex
            .vec3Attribute(0, 0, 0)   // position at offset 0
            .vec3Attribute(1, 0, 12)  // normal at offset 12
            .vec2Attribute(2, 0, 24)  // texcoord at offset 24
            .build();
        
        // Create VulkanMesh
        VulkanMesh mesh = VulkanMesh.builder()
            .device(device)
            .vertexFormat(format)
            .vertexBuffer(0, gltfData.vertexData, gltfData.vertexCount)
            .indexBuffer(gltfData.indexData, gltfData.indexCount)
            .build(arena);
        
        Logger.debug("VulkanMesh created");
        return mesh;
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
        
        // Allocate vertex data in native memory
        MemorySegment vertexData = Arena.ofAuto().allocate(vertexCount * 32L); // 8 floats * 4 bytes = 32 bytes per vertex
        
        // Copy all vertex data
        for (int i = 0; i < vertexCount; i++) {
            long offset = i * 32L;
            
            // Position
            vertexData.set(ValueLayout.JAVA_FLOAT, offset + 0, positionsBuffer.get(i * 3 + 0));
            vertexData.set(ValueLayout.JAVA_FLOAT, offset + 4, positionsBuffer.get(i * 3 + 1));
            vertexData.set(ValueLayout.JAVA_FLOAT, offset + 8, positionsBuffer.get(i * 3 + 2));
            
            // Normal
            if (normalsBuffer != null) {
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 12, normalsBuffer.get(i * 3 + 0));
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 16, normalsBuffer.get(i * 3 + 1));
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 20, normalsBuffer.get(i * 3 + 2));
            } else {
                // Default normal (up)
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 12, 0.0f);
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 16, 1.0f);
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 20, 0.0f);
            }
            
            // Texture coordinates
            if (texCoordsBuffer != null) {
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 24, texCoordsBuffer.get(i * 2 + 0));
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 28, texCoordsBuffer.get(i * 2 + 1));
            } else {
                // Default texcoord
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 24, 0.0f);
                vertexData.set(ValueLayout.JAVA_FLOAT, offset + 28, 0.0f);
            }
        }
        
        // Get indices
        AccessorModel indicesAccessor = primitive.getIndices();
        AccessorData indicesData = indicesAccessor.getAccessorData();
        
        int indexCount;
        MemorySegment indexData;
        
        if (indicesAccessor.getComponentType() == 5123) { // UNSIGNED_SHORT
            var shortBuffer = indicesData.createByteBuffer().asShortBuffer();
            indexCount = shortBuffer.remaining();
            indexData = Arena.ofAuto().allocate(indexCount * 4L); // Convert to uint32
            for (int i = 0; i < indexCount; i++) {
                indexData.setAtIndex(ValueLayout.JAVA_INT, i, shortBuffer.get(i) & 0xFFFF);
            }
        } else { // UNSIGNED_INT
            IntBuffer intBuffer = indicesData.createByteBuffer().asIntBuffer();
            indexCount = intBuffer.remaining();
            indexData = Arena.ofAuto().allocate(indexCount * 4L);
            for (int i = 0; i < indexCount; i++) {
                indexData.setAtIndex(ValueLayout.JAVA_INT, i, intBuffer.get(i));
            }
        }
        
        return new GLTFData(vertexData, vertexCount, indexData, indexCount);
    }
    
    public void shutdown() {
        loadingThread.shutdown();
    }
    
    /**
     * Simple data holder for parsed glTF data
     */
    private static class GLTFData {
        final MemorySegment vertexData;
        final int vertexCount;
        final MemorySegment indexData;
        final int indexCount;
        
        GLTFData(MemorySegment vertexData, int vertexCount, MemorySegment indexData, int indexCount) {
            this.vertexData = vertexData;
            this.vertexCount = vertexCount;
            this.indexData = indexData;
            this.indexCount = indexCount;
        }
    }
}