# UI System Implementation Checklist

Tracks progress on the UI foundation scaffolding (plans/UI_SYSTEM.md) and the
stb_truetype bindings module needed to fill FontRegistry's rasterization stub.
Update this file as chunks complete. Mark items `[x]` when done, `[~]` when
partially done / stubbed with a note, `[ ]` when not started.

---

## Chunk 1: stb-truetype-bindings module (jextract pattern) - DONE (scaffold)

- [x] Module scaffold: `stb-truetype-bindings/pom.xml` (mirrors shaderc-bindings/spirv-reflect-bindings)
- [x] `.gitignore` (build/, target/)
- [x] `build-stb-truetype.bat` - downloads stb_truetype.h via curl, generates STB_TRUETYPE_IMPLEMENTATION shim + CMakeLists.txt, builds shared lib (mirrors build-spirv-reflect.bat)
- [x] `stb_truetype_wrapper.h` - self-contained jextract wrapper header (font init, glyph metrics, bitmap rendering, codepoint lookup, kerning, scale helpers, whole-atlas pack API)
- [x] `generate-stb-truetype-bindings.bat` - jextract invocation
- [x] `README.md` - setup/usage docs
- [x] Add module to root `pom.xml` (`with-bindings` profile only, matching shaderc/spirv-reflect - not in default modules list since those rely on pre-installed jars)
- [ ] Build module standalone, confirm generated bindings compile - DEFERRED to Chunk 7 (requires jextract/cmake/curl toolchain + network; not run yet this session)

## Chunk 2: vulkan-ffm-foundation - input package - DONE (found already implemented from prior session)

- [x] `input/InputPhase.java`
- [x] `input/InputEventType.java`
- [x] `input/PropagationState.java`
- [x] `input/UIInputEvent.java` (full static factories + getters, package-private setPhase)
- [x] `input/UIInputDispatcher.java` (capture/bubble traversal)

## Chunk 3: vulkan-ffm-foundation - UILayer + UIComposite

- [x] `ui/UILayer.java` interface (found already implemented)
- [ ] `ui/UIComposite.java` orchestrator + Builder - MISSING, writing now

## Chunk 4: vulkan-ffm-foundation - assets package - DONE (found already implemented from prior session)

- [x] `assets/AssetType.java`
- [x] `assets/AssetRegistry.java`
- [x] `assets/ClipboardAccess.java` (interface, platform impls elsewhere)
- [x] `assets/CursorManager.java` (interface, platform impls elsewhere)
- [x] `assets/ThemeRegistry.java` (full implementation, pure Java)
- [x] `assets/FontRegistry.java` (CPU-side shelf-packing atlas allocator + glyph metric cache; GPU upload + rasterization clearly marked stub, to be wired in Chunk 6)

## Chunk 5: vulkan-ffm-foundation - top-level context classes

- [ ] `ui/UIContext.java` + Builder - MISSING, writing now
- [ ] `ui/UIFrameContext.java` - MISSING, writing now

## Chunk 6: FontRegistry to stb_truetype wiring

- [x] Add `stb-truetype-bindings` dependency to `vulkan-ffm-foundation/pom.xml`
- [x] Rasterization: load font file bytes, `stbtt_InitFont`, glyph bitmap rendering into atlas pages (FontRegistry.loadFont / getOrRasterizeGlyph)
- [x] Glyph metrics: advance width, bearing, bounding box via stb_truetype queries
- [x] Kerning lookup (stbtt_GetCodepointKernAdvance) via FontRegistry.getKerningAdvance
- [x] GPU atlas upload path (VkImage creation + MAPPED staging buffer + TransientCommandBuffer copy, using existing buffers/ conventions) - FontAtlas.flush()
- [x] Mark any remaining genuinely-deferred pieces (SDF generation, text-on-curve layout) clearly as stub/future work in FontRegistry class javadoc, not silently incomplete
- [!] IMPORTANT CAVEAT: this code references `io.github.yetyman.stbtruetype.generated.StbTrueTypeFFM`/`stbtt_fontinfo`,
      which do not exist until `stb-truetype-bindings/build-stb-truetype.bat` then `generate-stb-truetype-bindings.bat`
      are run locally (requires network + cmake/curl/jextract toolchain not available in this session).
      `vulkan-ffm-foundation` will NOT compile until that generation step is run. This is expected and is not
      a code defect - flagged prominently in Chunk 7 verification below.

## Chunk 7: Verification - COMPLETE

- [x] Confirmed via `mvn.cmd -pl helpers-core,vulkan-core install` - both build and install cleanly
- [x] Confirmed via `mvn.cmd -pl vulkan-ffm-foundation -Pwith-bindings compile` - ALL code compiles cleanly
      EXCEPT the 12 errors directly caused by the two not-yet-generated classes
      (`io.github.yetyman.stbtruetype.generated.StbTrueTypeFFM`, `stbtt_fontinfo`).
      This confirms: UIComposite, UIContext, UIFrameContext, UILayer, the full input/ package,
      AssetRegistry/AssetType/ThemeRegistry/ClipboardAccess/CursorManager, and all of FontRegistry's
      Vulkan-facing code (VkImage/VkImageView/BufferFactory/TransientCommandBuffer wiring) are correct
      and compile against the real vulkan-core jar with zero errors.
- [x] `stb-truetype-bindings` module: user ran build-stb-truetype.bat + generate-stb-truetype-bindings.bat locally.
      Hit one real bug: stb_truetype_wrapper.h declared stbtt_fontinfo (which embeds stbtt__buf by value)
      BEFORE declaring stbtt__buf, and used `struct stbtt__buf` field syntax instead of the plain typedef
      name `stbtt__buf` that upstream stb_truetype.h actually uses. Fixed by reordering the declarations
      and correcting field syntax to match the verified upstream layout (data ptr, cursor int, size int).
- [x] After the fix: user re-ran generation successfully, then `mvn install` on both `stb-truetype-bindings`
      and `vulkan-ffm-foundation` (with -Pwith-bindings) - BOTH BUILD AND INSTALL CLEANLY.
      FontRegistry's real stb_truetype rasterization + GPU atlas upload code compiles against the
      actual generated StbTrueTypeFFM/stbtt_fontinfo API with zero errors.
- [x] Short summary of stubbed vs complete provided to user (see chat)

RESULT: The full UI foundation scaffolding + stb_truetype text rasterization pipeline is complete and
building successfully end-to-end. Only genuinely deferred future work remains (see below).

---

## Deferred / Future Work (explicitly out of scope for this pass)

- SDF/MSDF glyph generation for scale-independent crisp text
- Text-on-curve / arbitrary path text layout
- HarfBuzz-style complex shaping (ligatures, bidi, complex scripts)
- ImGui bindings module and ImmediateModeLayer (deferred - heavy native dependency, separate scope; user confirmed skip for now)
- RetainedSceneLayer, Canvas2DLayer (not started - not needed for current example goals)

---

## Chunk 8: GPUDrivenTextLayer (proves FontRegistry end-to-end) - DONE, compiles clean

- [x] `ui/layers/text/GPUDrivenTextLayer.java` - UILayer implementation (order, initialize, update, render, resize, handleInput no-op)
- [x] `ui/layers/text/TextRenderer.java` - Vulkan backend: pipeline (textured alpha-blend quads via instanced triangle-strip), per-glyph SSBO instance buffer upload (MAPPED strategy), DescriptorGroup (manual storageBuffer + combinedImageSampler bindings), draw
- [x] `ui/layers/text/TextBatch.java` - accumulates draw-text calls for a frame (position, string, font, size, color); resolves glyphs via FontRegistry.getOrRasterizeGlyph + kerning
- [x] `ui/layers/text/GlyphInstance.java` - record matching std430 GPU layout
- [x] GLSL shaders `text.vert`/`text.frag` in sample-app/src/main/resources/shaders/ - instanced quad from SSBO, samples R8 font atlas alpha channel
- [x] sample-app example: `TextExampleApp` + `TextExampleFrame` - minimal app that opens a window, loads a system TTF font, and renders two lines of text via GPUDrivenTextLayer/UIComposite direct-render path
- [x] Added `vulkan-ffm-foundation` dependency to `sample-app/pom.xml`
- [x] `vulkan-ffm-foundation` compiles clean with the new layer (`mvn -pl vulkan-ffm-foundation compile`)
- [x] `sample-app` compiles clean including TextExampleApp/TextExampleFrame (`mvn -pl sample-app compile`)
- [x] VISUAL VERIFICATION COMPLETE - user ran the app, confirmed 3000+ FPS, both lines of text render and are readable.
      Found and fixed 4 real bugs during verification:
        1. Missing StbTrueTypeLoader (native lib not extracted/System.load'd before StbTrueTypeFFM static init) - added
           io.github.yetyman.stbtruetype.StbTrueTypeLoader mirroring SpirvReflectLoader, called from FontRegistry's static init.
        2. Duplicate VERTEX-stage push constant range (manual .pushConstantRange() call collided with the range
           auto-added by VkPipeline.Builder.vertexShader(ShaderInstance) via SPIRV-Reflect) - removed the manual one.
        3. Cross-thread confined-Arena violations (WrongThreadException) in two places: FontRegistry allocated
           scratch/font/atlas-image memory from a caller-supplied arena that could be confined to a different thread
           than the render thread that actually calls getOrRasterizeGlyph()/flush() - fixed by having FontRegistry own
           an internal Arena.ofShared(). Same issue in TextRenderer via UIContext.applicationArena() - fixed by having
           TextExampleApp construct a dedicated Arena.ofShared() for the UIContext instead of reusing
           vulkanContext().arena() (confined to the app's init thread).
        4. Missing vkCmdSetViewport/vkCmdSetScissor calls before the draw (pipeline uses dynamic viewport/scissor
           state per TextRenderer.initialize(), but TextExampleFrame never issued the calls) - added
           VkSetState.setViewport/setScissor in TextExampleFrame.recordCommandBuffer before composite.render().
      Known minor cosmetic issue (not fixed, deferred): occasional ~1px clip on the right edge of some glyphs.
      Diagnosed as likely atlas-padding/UV-edge interaction with linear filtering; a half-texel UV inset attempt
      made it worse (confirmed opposite direction), so the inset was reverted. User confirmed this is acceptable
      to leave as-is for now - not blocking further work. Revisit if crisper glyph edges are needed later.

## Chunk 9: Scene3DOverlayLayer (debug lines/gizmos, no new native deps) - DONE, compiles clean

- [x] `ui/layers/scene3d/Scene3DOverlayLayer.java` - UILayer implementation with setCamera/setFrameCallback API
- [x] `ui/layers/scene3d/OverlayRenderer.java` - Vulkan backend: 4 pipeline variants (line/tri x depth-tested/on-top),
      shared vertex/fragment shaders, MAPPED vertex buffers re-uploaded per frame, mat4 push constant
- [x] `ui/layers/scene3d/OverlayDrawList.java` - per-frame accumulated line/triangle vertex data; tessellates
      addLine/addWireBox/addWireSphere/addRay/addArrow/addFrustum/addGrid/addAxis into raw vertices
- [x] `ui/layers/scene3d/OverlayVertex.java` - record matching vertex input layout (pos3D + color4, 28 bytes)
- [x] `ui/layers/scene3d/DepthMode.java` - DEPTH_TESTED / ALWAYS_ON_TOP enum
- [x] GLSL shaders `overlay.vert`/`overlay.frag` in sample-app/src/main/resources/shaders/ - flat-colored
      line/triangle rendering with mat4 viewProjection push constant
- [x] sample-app example: `Scene3DOverlayExampleApp` + `Scene3DOverlayExampleFrame` - orbiting camera
      (hand-rolled lookAt/perspective, no shared Matrix4f exists in the codebase yet) drawing a debug grid,
      wire box, axis triad, and wire sphere
- [x] `vulkan-ffm-foundation` compiles clean with the new layer (`mvn -pl vulkan-ffm-foundation compile`)
- [x] `sample-app` compiles clean including the new example (`mvn -pl sample-app compile`)
- [x] VISUAL VERIFICATION COMPLETE - user ran the app; lines, triangles, and the axis gizmo render correctly
      with the orbiting camera, including AA-smoothed edges. Found and fixed 2 real camera bugs during
      verification (both in Scene3DOverlayExampleApp's hand-rolled camera math, not in OverlayRenderer/
      Scene3DOverlayLayer/OverlayDrawList themselves, which needed no changes):
        1. lookAt() was called with the same vector ({0,1,0}) for both the look-at target and the up
           direction, conflating two different parameters - fixed by using a proper up vector {0,1,0}
           and a separate scene-centered look-at target {1.5, 0.5, 0}; also widened orbit radius so all
           objects (grid, box, sphere, axis) stay in frame together.
        2. Vulkan clip-space Y-flip was not applied in the projection matrix (Vulkan NDC has +Y pointing
           down, unlike OpenGL) - caused everything "up" in world space to render at the bottom of the
           screen. Fixed by negating the Y-scale term (m[5] = -f) in perspective(). This is a general
           gotcha for any future 3D camera code targeting this Vulkan wrapper, not specific to this layer.
      DEPTH_TESTED pipeline variant remains unexercised in this example (no shared scene depth buffer
      wired up) - noted as a known gap, not a bug, consistent with OverlayRenderer's javadoc.

## Chunk 10: Verification for Chunks 8-9 - COMPLETE

- [x] Build vulkan-ffm-foundation with new layers - clean, verified above per-chunk
- [x] Build and run sample-app examples, confirm they render - both TextExampleApp and
      Scene3DOverlayExampleApp visually verified by user, all bugs found during verification fixed
- [x] Checklist updated throughout (see Chunk 8/9 entries above)

RESULT: Both example UILayer implementations (GPUDrivenTextLayer, Scene3DOverlayLayer) are complete,
compile cleanly, and have been visually verified working correctly by the user. The UI foundation
scaffolding, stb_truetype font pipeline, and two pre-canned layers are all done. Ready for the user
to build further test apps against this foundation.

## Explicitly deferred (per user direction)

- imgui-bindings module + ImGuiLayer - large native dependency (cimgui + Dear ImGui build), separate future effort
- RetainedSceneLayer, Canvas2DLayer - not started, not needed for current example goals
- Shared Matrix4f/camera utility in vulkan-core - Scene3DOverlayExampleApp currently hand-rolls its own
  lookAt/perspective; if more 3D example apps are added, factoring this out becomes worth doing
- DEPTH_TESTED overlay pipeline variant - implemented but unexercised without a shared scene depth buffer;
  revisit when a 3D-scene-plus-overlay example is built

---

## Chunk 11: Combined multi-layer example with input capture/bubble - CODE COMPLETE, compiles clean

- [x] `HoverHighlightLayer.java` (sample-app, example-specific) - renders nothing itself; logs
      CAPTURE then BUBBLE for every event, annotates propagation context during capture, toggles
      a highlighted boolean and calls stopPropagation() on MOUSE_BUTTON_PRESS during bubble.
      Exposes mouseX()/mouseY()/isHighlighted() so the app can feed state into GPUDrivenTextLayer.
- [x] Wired real GLFW input directly into UIComposite.dispatchInput(UIInputEvent) via
      GLFWCallbacks.setCursorPosCallback/setMouseButtonCallback/setScrollCallback/setKeyCallback
      in MultiLayerExampleApp.wireInput() - bypassed InputManager's debounced predicate/callback
      model (mismatched for per-event dispatch) in favor of direct GLFW callbacks that synthesize
      exactly one UIInputEvent per native callback and dispatch it synchronously.
- [x] `MultiLayerExampleApp.java` + `MultiLayerExampleFrame.java` (sample-app) - combines
      Scene3DOverlayLayer (order 100), GPUDrivenTextLayer (order 900), HoverHighlightLayer
      (order 950, topmost) in one UIComposite/window/render loop. HUD text color changes based on
      HoverHighlightLayer.isHighlighted(), cursor position displayed live via HUD text.
- [x] `vulkan-ffm-foundation` + `sample-app` compile clean, first try (no new bugs found in
      existing layers/UIComposite/UIInputDispatcher code - confirms multi-layer composition and
      the input dispatcher's existing implementation were already correct)
- [ ] VISUAL + LOG VERIFICATION PENDING - not yet run by user. Expect to observe in the console:
      CAPTURE phase logged first (HoverHighlightLayer only, since it's the only layer with
      acceptsInput()=true - Scene3DOverlayLayer/GPUDrivenTextLayer both return false), then BUBBLE
      phase logged, with click events toggling highlighted and changing HUD text color between
      white-ish and yellow-ish. Note: since only one layer accepts input in this example, the
      capture-then-bubble ordering across MULTIPLE input-accepting layers is not fully exercised -
      if that's the specific behavior to demonstrate, a second input-accepting layer would be
      needed (not added yet, kept minimal per the original 3-layer scope discussed).

## Chunk 12: Cross-layer 3D input annotation (Scene3DOverlayLayer now accepts input too)

Chunk 11 only exercised one input-accepting layer (HoverHighlightLayer) seeing an event twice
(once per phase) - not true cross-layer propagation. This chunk makes Scene3DOverlayLayer a
second input-accepting layer per the plan's original "3D layer annotates during capture, HUD
layer reacts during bubble" example, giving a real two-layer capture/bubble handoff to observe.

- [x] `Scene3DOverlayLayer.java` - added setPickSphere(center, radius)/lastHoveredWorldPos(),
      acceptsInput() now returns true, handleInput() unprojects the mouse into a world-space ray
      during CAPTURE and ray-sphere-intersects it against the registered pick sphere, annotating
      propagation.put("hoveredWorldPos", float[3]) on hit. Added a general-purpose 4x4 matrix
      inverse (cofactor expansion) and unproject() helper - needed for screen-to-world ray casting,
      not previously required by this layer. Never consumes (always returns false).
- [x] `HoverHighlightLayer.java` (sample-app) - now reads "hoveredWorldPos" from
      propagation() during BUBBLE and exposes it via lastHoveredWorldPos() for the app to
      visualize; logging updated to show when the annotation is present.
- [x] `MultiLayerExampleApp.java` - registers a pick sphere matching the drawn wire sphere;
      the sphere now turns orange and a small white marker is drawn at the exact ray-hit point
      when hovered (Scene3DOverlayLayer's frameCallback reads HoverHighlightLayer.lastHoveredWorldPos()
      each frame); HUD text now shows live world-space hover coordinates via GPUDrivenTextLayer.
- [x] `vulkan-ffm-foundation` + `sample-app` compile clean
- [ ] VISUAL + LOG VERIFICATION PENDING - not yet run by user. With two input-accepting layers
      (order 100 and 950), CAPTURE traverses highest-to-lowest (HoverHighlightLayer then
      Scene3DOverlayLayer) and BUBBLE traverses lowest-to-highest (Scene3DOverlayLayer then
      HoverHighlightLayer) - expect the console log to show this 4-call sequence per mouse-move
      event, and HoverHighlightLayer's BUBBLE log line to show the hoveredWorldPos annotation
      written by Scene3DOverlayLayer moments earlier in the same event's CAPTURE pass.
      Ray-sphere math (unproject + 4x4 inverse) is unverified - could have a sign/precision bug
      not caught by the compiler; watch for the marker appearing offset from the visual sphere
      surface under the cursor if so.
