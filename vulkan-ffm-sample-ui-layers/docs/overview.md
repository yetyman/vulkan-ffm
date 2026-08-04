# Sample UI Layers

## Purpose

This module contains concrete UILayer implementations that exercise the node-tree framework's layer composition, input dispatch, and rendering infrastructure. They are working implementations but are considered provisional — they may be promoted to a dedicated library module or rewritten as the framework matures.

## Module Dependencies

```
vulkan-ffm-node-trees     (UILayer interface, UIComposite, input dispatch)
    ^
    |
sample-ui-layers           (this module: concrete layers + FontRegistry + ThemeRegistry)
    ^                       depends on: stb-truetype-bindings, vulkan-core
    |
sample-app                 (example apps that compose these layers)
```

---

## Implemented Layers

### GPUDrivenTextLayer

**Package:** `ui/layers/text/`

GPU-accelerated text rendering via instanced quads and an stb_truetype-backed font atlas.

| Class | Role |
|-------|------|
| `GPUDrivenTextLayer` | UILayer implementation — lifecycle, update, render |
| `TextRenderer` | Vulkan backend: pipeline (instanced triangle-strip), SSBO upload, DescriptorGroup, draw |
| `TextBatch` | Accumulates draw-text calls per frame; resolves glyphs via FontRegistry + kerning |
| `GlyphInstance` | Record matching std430 GPU layout for per-glyph instance data |

**Rendering approach:**
- Font glyphs rasterized to an R8 atlas via stb_truetype (CPU-side, cached)
- Atlas uploaded to GPU as VkImage (MAPPED staging + TransientCommandBuffer copy)
- Per-frame: glyph instances written to SSBO (mapped buffer)
- Vertex shader generates quad corners from SSBO instance data (instanced triangle-strip)
- Fragment shader samples R8 atlas with alpha blending

**Shaders:** `text.vert` / `text.frag` (in sample-app resources)

### Scene3DOverlayLayer

**Package:** `ui/layers/scene3d/`

3D-space debug primitives (lines, wireframes, axes) rendered as an overlay.

| Class | Role |
|-------|------|
| `Scene3DOverlayLayer` | UILayer implementation — setCamera, setFrameCallback, drawing API, input (ray-sphere pick) |
| `OverlayRenderer` | Vulkan backend: 4 pipeline variants (line/tri x depth-tested/on-top), mapped vertex buffers |
| `OverlayDrawList` | Per-frame accumulated vertices; tessellates addLine/addWireBox/addWireSphere/addRay/addArrow/addGrid/addAxis |
| `OverlayVertex` | Record: pos3D (vec3) + color4 (vec4) = 28 bytes |
| `DepthMode` | Enum: DEPTH_TESTED / ALWAYS_ON_TOP |

**Drawing API (immediate-mode, per-frame):**
- `drawLine(from, to, color)`
- `drawWireBox(min, max, color)`
- `drawWireSphere(center, radius, color, segments)`
- `drawRay(origin, direction, length, color)`
- `drawArrow(from, to, color, headSize)`
- `drawFrustum(inverseViewProj, color)`
- `drawGrid(center, size, divisions, color)`
- `drawAxis(transform4x4, scale)`

**Input:** Supports ray-sphere intersection for pick detection. During CAPTURE phase, unprojects mouse to world ray and annotates propagation context with hit position for downstream layers to read during BUBBLE.

**Shaders:** `overlay.vert` / `overlay.frag` (in sample-app resources)

---

## Supporting Services

### FontRegistry

CPU-side font management with GPU atlas upload:
- Loads TTF font files via stb_truetype
- Rasterizes glyphs on demand into a shelf-packed atlas
- Caches glyph metrics (advance, bearing, bounding box)
- Kerning lookup
- GPU upload path (VkImage + staging buffer + TransientCommandBuffer)

### ThemeRegistry

Color tokens and style definitions. Pure Java, no native dependencies.

---

## Known Gaps / Deferred

- SDF/MSDF glyph generation (scale-independent crisp text)
- Text-on-curve layout
- HarfBuzz-style complex shaping (ligatures, bidi)
- Gizmo system (translate/rotate/scale handles) for Scene3DOverlayLayer
- DEPTH_TESTED pipeline variant unexercised (no shared scene depth buffer example)
- Shared Matrix4f/camera utility (Scene3DOverlayExampleApp hand-rolls its own)
