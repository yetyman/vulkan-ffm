package io.github.yetyman.vulkan.layers.gizmo;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.command.VkSetState;
import io.github.yetyman.vulkan.layers.scene3d.OverlayDrawList;
import io.github.yetyman.vulkan.layers.scene3d.OverlayRenderer;
import io.github.yetyman.vulkan.ui.UIContext;
import io.github.yetyman.vulkan.ui.UIFrameContext;
import io.github.yetyman.vulkan.ui.UILayer;
import io.github.yetyman.vulkan.ui.input.InputEvent;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A generic UILayer that renders a collection of {@link Gizmo} instances each frame
 * using the existing {@link OverlayRenderer} line/triangle pipeline.
 *
 * <p>Gizmos are registered via {@link #addGizmo(Gizmo)} or {@link #setGizmoSupplier(Supplier)}.
 * The supplier approach allows the gizmo set to be rebuilt each frame (e.g. from dynamic
 * buffer readbacks), while addGizmo() is for static/persistent gizmos.
 *
 * <p>This layer renders in 2D screen space by default (orthographic projection mapping
 * pixel coordinates to NDC). Set a custom view-projection via {@link #setViewProjection(float[])}
 * if 3D gizmos are needed.
 *
 * <p>Usage:
 * <pre>{@code
 * GizmoOverlayLayer gizmoLayer = new GizmoOverlayLayer();
 * gizmoLayer.setGizmoSupplier(() -> buildGizmosFromBufferReadback());
 * // add to UIComposite
 * }</pre>
 */
public class GizmoOverlayLayer implements UILayer {

    private static final int DEFAULT_ORDER = 200; // above scene layers, below text HUD

    private final int order;
    private UIContext ctx;
    private OverlayRenderer renderer;
    private OverlayDrawList drawList;

    private final List<Gizmo> staticGizmos = new ArrayList<>();
    private Supplier<List<Gizmo>> gizmoSupplier;
    private float[] viewProjection;
    private long frameNumber;

    public GizmoOverlayLayer() {
        this(DEFAULT_ORDER);
    }

    public GizmoOverlayLayer(int order) {
        this.order = order;
    }

    /**
     * Adds a persistent gizmo that is rendered every frame until removed.
     * Thread-safe for adds before initialize(); after that, synchronize externally.
     */
    public void addGizmo(Gizmo gizmo) {
        staticGizmos.add(gizmo);
    }

    /** Removes a previously added static gizmo. */
    public void removeGizmo(Gizmo gizmo) {
        staticGizmos.remove(gizmo);
    }

    /** Clears all static gizmos. */
    public void clearGizmos() {
        staticGizmos.clear();
    }

    /**
     * Sets a dynamic gizmo supplier that is called each frame to produce the gizmo list.
     * When set, these gizmos are rendered IN ADDITION to any static gizmos.
     */
    public void setGizmoSupplier(Supplier<List<Gizmo>> supplier) {
        this.gizmoSupplier = supplier;
    }

    /**
     * Sets a custom view-projection matrix (column-major 4x4, 16 floats).
     * If null, uses an orthographic projection mapping pixel coordinates to NDC.
     */
    public void setViewProjection(float[] vp) {
        this.viewProjection = vp;
    }

    @Override
    public String name() {
        return "gizmo-overlay";
    }

    @Override
    public int order() {
        return order;
    }

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
        frameNumber = frame.frameNumber();

        // Render static gizmos
        for (Gizmo gizmo : staticGizmos) {
            gizmo.render(drawList, frameNumber);
        }

        // Render dynamic gizmos from supplier
        if (gizmoSupplier != null) {
            List<Gizmo> dynamic = gizmoSupplier.get();
            if (dynamic != null) {
                for (Gizmo gizmo : dynamic) {
                    gizmo.render(drawList, frameNumber);
                }
            }
        }
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        if (drawList.isEmpty()) return;

        // Reset viewport and scissor to full window - previous layers may have set per-region viewports
        VkSetState.setViewport(cmd, 0, 0, 0, ctx.width(), ctx.height(), 0.0f, 1.0f);
        VkSetState.setScissor(cmd, 0, 0, 0, ctx.width(), ctx.height());

        float[] vp = viewProjection != null ? viewProjection : buildOrthoProjection();
        renderer.render(cmd, frameArena, drawList, vp);
    }

    @Override
    public void resize(int width, int height) {
        // Ortho projection is rebuilt each frame from ctx dimensions, no action needed
    }

    @Override
    public boolean handleInput(InputEvent event) {
        return false; // Gizmo layer is display-only, does not consume input
    }

    @Override
    public boolean acceptsInput() {
        return false;
    }

    @Override
    public void close() {
        if (renderer != null) renderer.close();
    }

    /**
     * Builds an orthographic projection that maps pixel coordinates (0,0)-(width,height)
     * to Vulkan NDC (-1,-1)-(1,1) with Y flipped for Vulkan's down-positive NDC.
     * Z is mapped [0, 1] (Vulkan depth range).
     *
     * Column-major 4x4 matrix.
     */
    private float[] buildOrthoProjection() {
        float w = ctx.width();
        float h = ctx.height();
        // Orthographic: x maps [0,w] -> [-1,1], y maps [0,h] -> [-1,1] (Y down = positive in Vulkan NDC)
        float[] m = new float[16];
        m[0] = 2.0f / w;         // scale X
        m[5] = 2.0f / h;         // scale Y (Vulkan Y is already top-down in NDC)
        m[10] = 1.0f;            // scale Z
        m[12] = -1.0f;           // translate X: 0 -> -1
        m[13] = -1.0f;           // translate Y: 0 -> -1
        m[15] = 1.0f;
        return m;
    }
}
