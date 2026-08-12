package io.github.yetyman.vulkan.layers.lodscene;

import io.github.yetyman.helpers.math.Mat4;
import io.github.yetyman.helpers.math.geometry.AABB;
import io.github.yetyman.vulkan.mesh.Mesh;
import io.github.yetyman.vulkan.mesh.lod.RepresentationNode;
import io.github.yetyman.vulkan.mesh.lod.RepresentationStructure;
import io.github.yetyman.vulkan.mesh.lod.TransitionMode;
import io.github.yetyman.vulkan.mesh.lod.TransitionState;

import java.util.List;

/**
 * One object in the LOD scene hierarchy. Holds a world transform, a LOD chain of uploaded
 * meshes, and per-instance LOD selection state (current level + active transition).
 *
 * <p>This is sample/provisional code. A production scene system would likely manage transforms
 * via a node tree, decouple LOD chains from scene graph nodes, and support instancing.
 */
public final class SceneObject {

    private final String name;
    private final List<Mesh> lodMeshes;
    private final RepresentationStructure.Flat lodStructure;
    private final AABB baseBounds;
    private Mat4 transform;

    // Per-instance LOD state
    private int currentLod = 0;
    private TransitionState activeTransition;

    /**
     * @param name         display name for debugging/HUD
     * @param lodMeshes    uploaded meshes ordered finest (0) to coarsest (last)
     * @param lodStructure the representation structure matching lodMeshes
     * @param baseBounds   untransformed AABB of the original geometry
     * @param transform    initial world transform
     */
    public SceneObject(String name, List<Mesh> lodMeshes,
                       RepresentationStructure.Flat lodStructure,
                       AABB baseBounds, Mat4 transform) {
        if (lodMeshes == null || lodMeshes.isEmpty()) {
            throw new IllegalArgumentException("at least one LOD mesh required");
        }
        if (lodStructure.nodeCount() != lodMeshes.size()) {
            throw new IllegalArgumentException("lodStructure node count must match lodMeshes size");
        }
        this.name = name;
        this.lodMeshes = List.copyOf(lodMeshes);
        this.lodStructure = lodStructure;
        this.baseBounds = baseBounds;
        this.transform = transform;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String name() { return name; }
    public List<Mesh> lodMeshes() { return lodMeshes; }
    public RepresentationStructure.Flat lodStructure() { return lodStructure; }
    public AABB baseBounds() { return baseBounds; }
    public Mat4 transform() { return transform; }
    public int currentLod() { return currentLod; }
    public int lodLevelCount() { return lodMeshes.size(); }
    public TransitionState activeTransition() { return activeTransition; }

    /**
     * @return the mesh to render this frame (current LOD, stable during transitions)
     */
    public Mesh currentMesh() { return lodMeshes.get(currentLod); }

    /**
     * @return triangle count of the currently rendered LOD level
     */
    public long currentTriangleCount() {
        return lodStructure.node(currentLod).triangleCount();
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    public void setTransform(Mat4 t) { this.transform = t; }

    /**
     * Requests a LOD level change. If different from current, starts a dither transition.
     *
     * @param desiredLod the desired LOD index
     * @param dt         delta time for transition advancement
     */
    public void requestLod(int desiredLod, float dt) {
        if (desiredLod < 0 || desiredLod >= lodMeshes.size()) return;

        if (desiredLod != currentLod && activeTransition == null) {
            activeTransition = new TransitionState(
                    new TransitionMode.Dither(0.25f),
                    currentLod, desiredLod);
        }

        if (activeTransition != null) {
            activeTransition.advance(dt);
            if (activeTransition.isComplete()) {
                currentLod = activeTransition.toNodeIndex();
                activeTransition = null;
            }
        }
    }

    /**
     * Closes all LOD meshes owned by this object.
     */
    public void close() {
        for (Mesh mesh : lodMeshes) {
            mesh.close();
        }
    }
}
