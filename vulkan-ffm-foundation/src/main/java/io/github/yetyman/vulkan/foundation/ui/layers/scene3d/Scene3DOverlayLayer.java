package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.UIContext;
import io.github.yetyman.vulkan.foundation.ui.UIFrameContext;
import io.github.yetyman.vulkan.foundation.ui.UILayer;
import io.github.yetyman.vulkan.foundation.ui.input.InputEventType;
import io.github.yetyman.vulkan.foundation.ui.input.InputPhase;
import io.github.yetyman.vulkan.foundation.ui.input.UIInputEvent;

import java.lang.foreign.Arena;
import java.util.function.Consumer;

/**
 * UILayer that renders 3D-space debug primitives: lines, wire boxes/spheres, grids, axis
 * triads, arrows, and frustum wireframes. Pure Vulkan line/triangle rendering - no external
 * native dependencies.
 *
 * Usage:
 *   Scene3DOverlayLayer overlay = new Scene3DOverlayLayer();
 *   overlay.initialize(ctx);
 *   overlay.setCamera(viewMatrix, projMatrix);
 *   overlay.setFrameCallback(drawList -> {
 *       drawList.addGrid(new float[]{0,0,0}, 10f, 10, new float[]{0.4f,0.4f,0.4f,1f});
 *       drawList.addAxis(identityMatrix, 1.0f);
 *   });
 *
 * Camera matrices must be set (via setCamera) before update() is called each frame that draws
 * anything - the layer combines them into a single view-projection matrix passed to the
 * vertex shader as a push constant.
 *
 * Cross-layer input annotation: during the CAPTURE phase, MOUSE_MOVE events are unprojected
 * into a world-space ray and tested against an optionally-registered pickable sphere (see
 * setPickSphere). On hit, the world-space hit point is written into the event's propagation
 * context under the key "hoveredWorldPos" (a float[3]) for downstream layers to read during
 * BUBBLE - e.g. a HUD text layer displaying "hovering: (x, y, z)". This layer never consumes
 * the event (always returns false), matching the plan's original "3D layer annotates, HUD layer
 * reacts" example pattern.
 *
 * Full gizmo interaction (hover/drag transform handles) described in the original design docs
 * remains out of scope for this minimal layer - see plans/UI_SYSTEM_CHECKLIST.md deferred section.
 */
public class Scene3DOverlayLayer implements UILayer {

    private static final int DEFAULT_ORDER = 100; // above a 3D scene, below 2D HUD/text layers
    private static final String HOVERED_WORLD_POS_KEY = "hoveredWorldPos";

    private final int order;

    private UIContext ctx;
    private OverlayRenderer renderer;
    private OverlayDrawList drawList;

    private float[] viewMatrix = identity();
    private float[] projectionMatrix = identity();

    private Consumer<OverlayDrawList> frameCallback;

    // Optional single pickable sphere for ray-hit testing during CAPTURE. Null center = disabled.
    private float[] pickSphereCenter;
    private float pickSphereRadius;
    private float[] lastHoveredWorldPos;

    public Scene3DOverlayLayer() { this(DEFAULT_ORDER); }
    public Scene3DOverlayLayer(int order) { this.order = order; }

    /** Sets the camera matrices (column-major 4x4, 16 floats each) used for this and subsequent frames. */
    public void setCamera(float[] view, float[] projection) {
        System.arraycopy(view, 0, this.viewMatrix, 0, 16);
        System.arraycopy(projection, 0, this.projectionMatrix, 0, 16);
    }

    /** Sets the per-frame callback that issues draw calls against the draw list. */
    public void setFrameCallback(Consumer<OverlayDrawList> callback) {
        this.frameCallback = callback;
    }

    /**
     * Registers a single world-space sphere for mouse ray-hit testing during input CAPTURE.
     * Pass center=null to disable picking. This is intentionally minimal (one sphere, no
     * scene graph) - a real picking system belongs in a future RetainedSceneLayer.
     */
    public void setPickSphere(float[] center, float radius) {
        this.pickSphereCenter = center;
        this.pickSphereRadius = radius;
    }

    /** @return the last world-space ray-hit position from setPickSphere's target, or null if not currently hovered. */
    public float[] lastHoveredWorldPos() { return lastHoveredWorldPos; }

    @Override
    public String name() { return "scene3d-overlay"; }

    @Override
    public int order() { return order; }

    @Override
    public void initialize(UIContext ctx) {
        this.ctx = ctx;
        this.drawList = new OverlayDrawList();
        this.renderer = new OverlayRenderer(ctx);
        this.renderer.initialize();
    }

    @Override
    public void update(UIFrameContext frame) {
        drawList.clear();
        if (frameCallback != null) {
            frameCallback.accept(drawList);
        }
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        if (drawList.isEmpty()) return;
        float[] viewProjection = multiply(projectionMatrix, viewMatrix);
        renderer.render(cmd, frameArena, drawList, viewProjection);
    }

    @Override
    public void resize(int width, int height) {
        // No size-dependent GPU resources - projection is supplied externally via setCamera().
    }

    @Override
    public boolean handleInput(UIInputEvent event) {
        if (event.phase() == InputPhase.CAPTURE && event.type() == InputEventType.MOUSE_MOVE
                && pickSphereCenter != null) {
            float[] hit = raySphereHit(event.mouseX(), event.mouseY());
            lastHoveredWorldPos = hit;
            if (hit != null) {
                event.propagation().put(HOVERED_WORLD_POS_KEY, hit);
            }
        }
        return false; // annotate only, never consume - matches the plan's capture-annotates pattern
    }

    @Override
    public boolean acceptsInput() { return true; }

    @Override
    public void close() {
        if (renderer != null) renderer.close();
    }

    /**
     * Unprojects the given screen-space point into a world-space ray and tests it against the
     * registered pick sphere. Returns the world-space hit point on the sphere surface nearest
     * the ray origin, or null if the ray misses or no pick sphere is registered.
     */
    private float[] raySphereHit(float screenX, float screenY) {
        float[] viewProjection = multiply(projectionMatrix, viewMatrix);
        float[] inverse = invert(viewProjection);
        if (inverse == null) return null;

        float ndcX = (screenX / ctx.width()) * 2.0f - 1.0f;
        float ndcY = (screenY / ctx.height()) * 2.0f - 1.0f; // Vulkan NDC Y matches screen Y (both down-positive)

        float[] nearPoint = unproject(inverse, ndcX, ndcY, 0.0f);
        float[] farPoint = unproject(inverse, ndcX, ndcY, 1.0f);
        float[] rayOrigin = nearPoint;
        float[] rayDir = normalize(sub(farPoint, nearPoint));

        return intersectSphere(rayOrigin, rayDir, pickSphereCenter, pickSphereRadius);
    }

    private float[] intersectSphere(float[] origin, float[] dir, float[] center, float radius) {
        float[] oc = sub(origin, center);
        float b = dot(oc, dir);
        float c = dot(oc, oc) - radius * radius;
        float discriminant = b * b - c;
        if (discriminant < 0) return null;
        float t = -b - (float) Math.sqrt(discriminant);
        if (t < 0) return null;
        return new float[]{
            origin[0] + dir[0] * t,
            origin[1] + dir[1] * t,
            origin[2] + dir[2] * t
        };
    }

    private float[] unproject(float[] inverseViewProj, float ndcX, float ndcY, float ndcZ) {
        float w = inverseViewProj[3] * ndcX + inverseViewProj[7] * ndcY + inverseViewProj[11] * ndcZ + inverseViewProj[15];
        float x = inverseViewProj[0] * ndcX + inverseViewProj[4] * ndcY + inverseViewProj[8] * ndcZ + inverseViewProj[12];
        float y = inverseViewProj[1] * ndcX + inverseViewProj[5] * ndcY + inverseViewProj[9] * ndcZ + inverseViewProj[13];
        float z = inverseViewProj[2] * ndcX + inverseViewProj[6] * ndcY + inverseViewProj[10] * ndcZ + inverseViewProj[14];
        if (Math.abs(w) < 1e-8f) w = 1e-8f;
        return new float[]{x / w, y / w, z / w};
    }

    private static float[] identity() {
        return new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
    }

    /** Column-major 4x4 matrix multiply: returns a * b. */
    private static float[] multiply(float[] a, float[] b) {
        float[] result = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                result[col * 4 + row] = sum;
            }
        }
        return result;
    }

    /** General 4x4 matrix inverse via cofactor expansion. Returns null if the matrix is singular. */
    private static float[] invert(float[] m) {
        float[] inv = new float[16];

        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15]
            + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15]
            - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15]
            + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14]
            - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];

        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15]
            - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15]
            + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15]
            - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14]
            + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];

        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15]
            + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15]
            - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15]
            + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14]
            - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];

        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11]
            - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11]
            + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11]
            - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10]
            + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (Math.abs(det) < 1e-10f) return null;

        float invDet = 1.0f / det;
        for (int i = 0; i < 16; i++) inv[i] *= invDet;
        return inv;
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-8f) return new float[]{0, 0, 0};
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }
}
