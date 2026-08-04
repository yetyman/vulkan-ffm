# Gizmo System Plan

## Status: Not Yet Implemented

Interactive 3D transform handles (translate/rotate/scale) for Scene3DOverlayLayer. Standard editor-viewport interaction tool for manipulating selected objects in world space.

---

## Overview

A gizmo renders as colored axis handles in 3D space and responds to mouse interaction via ray-casting and constrained drag projection. It does NOT modify transforms directly — it reports delta transforms that the application applies to its own data model.

This keeps the gizmo generic: it works with any transform representation (mat4, position+rotation+scale, dual quaternion, etc.) because the application decides how to apply the delta.

---

## Modes

```java
public enum GizmoMode { TRANSLATE, ROTATE, SCALE }
public enum GizmoSpace { LOCAL, WORLD }
public enum GizmoAxis { X, Y, Z, XY, XZ, YZ, XYZ, SCREEN }
```

### Translate
- Three axis arrows (RGB = XYZ) extending from object position
- Three small plane squares between axis pairs (XY, XZ, YZ) for plane-constrained movement
- Center cube for free (screen-plane) movement
- Drag projects mouse movement onto the constrained axis or plane in world space

### Rotate
- Three circles (RGB = XYZ) in the planes perpendicular to each axis
- One screen-space outer circle (white) for view-aligned rotation
- Drag computes angular delta around the constrained axis

### Scale
- Three axis lines with cube endpoints (RGB = XYZ)
- Center cube for uniform scale
- Drag computes scale factor along the constrained axis (or uniform)

---

## Visual Design

- Handle size is auto-scaled by distance to camera (constant screen-space size regardless of zoom)
- Hovered handle highlights (brighter color or thickened)
- During drag: active axis stays highlighted, others dim
- RGB color convention: X=red, Y=green, Z=blue, uniform/screen=white
- Rendered as overlay primitives (lines + triangles) via OverlayDrawList
- Default depth mode: DEPTH_TESTED (occluded by scene geometry, so you can't accidentally grab a handle behind an object)

---

## Interaction Model

### Hit Testing

Ray-cast from camera through mouse pixel into world space, test against each handle's collision geometry:

| Handle | Collision geometry |
|--------|-------------------|
| Axis arrow (translate) | Cylinder along axis + cone tip |
| Plane square (translate) | Small quad in the axis-pair plane |
| Center cube | Cube at gizmo position |
| Rotation ring | Torus around axis (or thick circle approximation) |
| Scale axis | Cylinder along axis + cube endpoint |

Priority: closest hit wins. Returns `GizmoHit` with axis, distance, and world-space hit point.

### Drag Projection

Once a drag begins on a specific axis/plane:

**Translate (axis):** Project mouse movement onto the 3D axis line projected into screen space. Convert screen-space delta back to world-space delta along the axis.

**Translate (plane):** Intersect the camera ray with the constraint plane. Delta = new intersection point - drag start intersection point.

**Rotate:** Project mouse movement perpendicular to the axis (in screen space) into an angular delta. Alternatively: intersect camera ray with the rotation plane, compute angle between start and current intersection relative to gizmo center.

**Scale:** Same as translate-axis projection, but output is a scale factor (1.0 + proportional delta) rather than a position offset.

### Snapping (optional)

- Translation snap: grid-aligned (e.g. snap to 0.25 units)
- Rotation snap: angle-aligned (e.g. snap to 15 degrees)
- Scale snap: factor-aligned (e.g. snap to 0.1 increments)
- Snapping is off by default, enabled via `gizmo.setSnap(translation, rotation, scale)`

---

## API

```java
public class Gizmo {
    // Configuration
    public void setMode(GizmoMode mode);
    public void setSpace(GizmoSpace space);  // LOCAL or WORLD
    public void setSnap(float translationSnap, float rotationSnapDegrees, float scaleSnap);

    // Per-frame update (called by Scene3DOverlayLayer)
    public void update(float[] objectTransform4x4, GizmoMode mode,
                       float[] viewMatrix, float[] projMatrix, float[] cameraPos);

    // Hit testing (called during CAPTURE phase)
    public GizmoHit testHit(float[] rayOrigin, float[] rayDirection);

    // Drag lifecycle (called during BUBBLE phase)
    public void beginDrag(GizmoHit hit, float mouseX, float mouseY);
    public void updateDrag(float mouseX, float mouseY,
                           float[] viewMatrix, float[] projMatrix,
                           int screenWidth, int screenHeight);
    public void endDrag();
    public boolean isDragging();

    // Results (read after updateDrag)
    public float[] deltaTranslation();      // vec3
    public float deltaRotationAngle();      // radians
    public float[] deltaRotationAxis();     // vec3 (unit)
    public float[] deltaScale();            // vec3 (1.0 = no change)

    // Rendering (called by OverlayDrawList during tessellation)
    public void tessellateInto(TessellatedResult result);
}
```

```java
public record GizmoHit(
    GizmoAxis axis,    // which handle was hit
    float distance,    // ray parameter at hit point
    float[] hitPoint   // world-space hit position
) {}
```

---

## Integration with Scene3DOverlayLayer

The gizmo participates in the existing capture/bubble input flow:

```java
// In Scene3DOverlayLayer.handleInput():

// CAPTURE phase: ray-cast against gizmo handles, annotate propagation context
if (event.phase() == InputPhase.CAPTURE && event.type() == MOUSE_MOVE) {
    float[] ray = unprojectMouseRay(event.mouseX(), event.mouseY());
    GizmoHit hit = gizmo.testHit(rayOrigin, rayDirection);
    if (hit != null) {
        event.propagation().put("gizmoHit", hit);
        event.propagation().put("gizmoAxis", hit.axis());
    }
}

// BUBBLE phase: handle drag start/move/end
if (event.phase() == InputPhase.BUBBLE) {
    if (event.type() == MOUSE_BUTTON_PRESS) {
        GizmoHit hit = event.propagation().get("gizmoHit", GizmoHit.class);
        if (hit != null) {
            gizmo.beginDrag(hit, event.mouseX(), event.mouseY());
            event.stopPropagation();
            return true;  // consumed
        }
    }
    if (event.type() == MOUSE_MOVE && gizmo.isDragging()) {
        gizmo.updateDrag(event.mouseX(), event.mouseY(), view, proj, w, h);
        event.stopPropagation();
        return true;
    }
    if (event.type() == MOUSE_BUTTON_RELEASE && gizmo.isDragging()) {
        gizmo.endDrag();
        event.stopPropagation();
        return true;
    }
}
```

### Application usage

```java
overlay.setFrameCallback(() -> {
    if (selectedObject != null) {
        Gizmo gizmo = overlay.drawGizmo(selectedObject.transform(), GizmoMode.TRANSLATE);

        // Apply delta from last frame's drag (if any)
        float[] delta = gizmo.deltaTranslation();
        if (delta[0] != 0 || delta[1] != 0 || delta[2] != 0) {
            selectedObject.translate(delta[0], delta[1], delta[2]);
        }
    }
});
```

---

## Tessellation Details

### Translate gizmo geometry

Per axis (X/Y/Z):
- Shaft: 2 vertices (line from center to tip - handleScale length)
- Arrow head: cone approximated as 8-12 triangles at the tip

Per plane pair (XY/XZ/YZ):
- Small filled quad (2 triangles) at 30% of handleScale from center

Center:
- Small cube (12 triangles) at gizmo position

### Rotate gizmo geometry

Per axis:
- Circle of 32-64 line segments in the perpendicular plane
- Thickened (rendered as thin triangle strip) for better visibility

Screen-space ring:
- Circle facing camera, radius = handleScale

### Scale gizmo geometry

Per axis:
- Shaft: 2 vertices (line from center)
- Endpoint: small cube (12 triangles) at the tip

---

## Dependencies

- 4x4 matrix inverse (already added to Scene3DOverlayLayer for ray unprojection)
- Ray-cylinder, ray-plane, ray-sphere intersection math
- Screen-to-world and world-to-screen projection utilities
- No external dependencies beyond what Scene3DOverlayLayer already has

---

## Future Extensions

- Multi-object gizmo (operate on selection centroid, apply to all selected)
- Custom handle shapes (application-defined collision + visual geometry)
- Undo/redo integration (gizmo reports begin/end of drag as discrete operations)
- Axis locking (disable specific axes)
- Visual pivot point offset (gizmo drawn at custom point, not object center)
