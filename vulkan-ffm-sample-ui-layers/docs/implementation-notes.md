# Implementation Notes

Historical record of implementation progress and bugs found during development of the sample UI layers.

---

## GPUDrivenTextLayer

### Build order
1. TextRenderer (pipeline, SSBO, DescriptorGroup, draw)
2. TextBatch (glyph resolution via FontRegistry, kerning, instance accumulation)
3. GlyphInstance (std430 record)
4. GPUDrivenTextLayer (UILayer lifecycle wiring)
5. text.vert / text.frag shaders
6. TextExampleApp + TextExampleFrame (sample-app)

### Bugs found during visual verification

1. **Missing StbTrueTypeLoader** — native lib not extracted/System.load'd before StbTrueTypeFFM static init. Fixed by adding `io.github.yetyman.stbtruetype.StbTrueTypeLoader` mirroring SpirvReflectLoader pattern, called from FontRegistry's static init.

2. **Duplicate push constant range** — manual `.pushConstantRange()` call collided with the range auto-added by `VkPipeline.Builder.vertexShader(ShaderInstance)` via SPIRV-Reflect. Fixed by removing the manual one.

3. **Cross-thread confined-Arena violations** (WrongThreadException) — FontRegistry allocated scratch/font/atlas-image memory from a caller-supplied arena that could be confined to a different thread than the render thread. Fixed by having FontRegistry own an internal `Arena.ofShared()`. Same issue in TextRenderer via `UIContext.applicationArena()` — fixed by having TextExampleApp construct a dedicated `Arena.ofShared()` for the UIContext.

4. **Missing vkCmdSetViewport/vkCmdSetScissor** — pipeline uses dynamic viewport/scissor state but the app never issued the calls before draw. Fixed by adding VkSetState calls in the frame recorder before `composite.render()`.

### Known minor issue (deferred)
Occasional ~1px clip on the right edge of some glyphs. Diagnosed as atlas-padding/UV-edge interaction with linear filtering. A half-texel UV inset attempt made it worse (confirmed opposite direction). Acceptable as-is.

---

## Scene3DOverlayLayer

### Build order
1. OverlayVertex (record: pos3D + color4)
2. DepthMode (enum)
3. OverlayDrawList (tessellation: line/wireBox/wireSphere/ray/arrow/frustum/grid/axis)
4. OverlayRenderer (4 pipeline variants, mapped vertex buffers, mat4 push constant)
5. Scene3DOverlayLayer (UILayer lifecycle, camera state, drawing API)
6. overlay.vert / overlay.frag shaders
7. Scene3DOverlayExampleApp + Scene3DOverlayExampleFrame (sample-app)

### Bugs found during visual verification

1. **lookAt() parameter confusion** — called with the same vector `{0,1,0}` for both look-at target and up direction. Fixed by using proper separate values and widening orbit radius.

2. **Missing Vulkan Y-flip** — projection matrix did not negate Y-scale for Vulkan NDC (+Y down). Fixed by negating `m[5]` in `perspective()`. General gotcha for any future 3D camera code targeting this Vulkan wrapper.

### Unexercised paths
- DEPTH_TESTED pipeline variant — implemented but no shared scene depth buffer wired up in the example.
- Gizmo system (Gizmo, GizmoMode, GizmoSpace) — planned but not built.

---

## Multi-Layer Input Example (Chunks 11-12)

### What was built
- `HoverHighlightLayer` (sample-app) — input-only layer that logs CAPTURE/BUBBLE, toggles highlighted state on click, reads cross-layer annotations
- `MultiLayerExampleApp` — combines Scene3DOverlayLayer (order 100) + GPUDrivenTextLayer (order 900) + HoverHighlightLayer (order 950)
- Direct GLFW callbacks wired into `UIComposite.dispatchInput()` (bypassed InputManager's debounced model)
- Scene3DOverlayLayer enhanced with `setPickSphere`/ray-sphere intersection during CAPTURE, annotating `propagation.put("hoveredWorldPos", ...)` for HoverHighlightLayer to read during BUBBLE

### Input dispatch ordering demonstrated
- CAPTURE: highest order first (950 -> 100)
- BUBBLE: lowest order first (100 -> 950)
- Cross-layer annotation: Scene3DOverlayLayer writes world-space hit position during CAPTURE, HoverHighlightLayer reads it during BUBBLE

### Added to Scene3DOverlayLayer for this example
- 4x4 matrix inverse (cofactor expansion)
- `unproject()` helper for screen-to-world ray casting
- `setPickSphere(center, radius)` / `lastHoveredWorldPos()`
- `acceptsInput()` now returns true
