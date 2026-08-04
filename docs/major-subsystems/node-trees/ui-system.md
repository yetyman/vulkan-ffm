# UI System

## Overview

The UI system is a layer-based composition framework for rendering and input. It stacks self-contained rendering subsystems (UILayers) into a composite, dispatches input through them via capture/bubble phases, and provides shared platform context.

It lives alongside the node-tree system in `vulkan-ffm-node-trees` but is independent — a layer can use a node tree internally, or not. The two systems compose; neither requires the other.

---

## UILayer Interface

A UILayer is a self-contained rendering/input subsystem. Each layer owns its own pipelines, vertex formats, descriptor sets, and rendering approach:

```java
public interface UILayer extends AutoCloseable {
    String name();
    int order();

    void initialize(UIContext ctx);
    void update(UIFrameContext frame);
    void render(VkCommandBuffer cmd, Arena frameArena);
    default void contributeToGraph(Object graphBuilder) {}
    void resize(int width, int height);

    boolean handleInput(UIInputEvent event);
    default boolean acceptsInput() { return true; }
    default boolean needsUpdate() { return true; }
}
```

### Order

- Lower values drawn first (background), higher drawn last (foreground)
- Input CAPTURE traverses highest-to-lowest (frontmost sees first)
- Input BUBBLE traverses lowest-to-highest (backmost reacts first)
- Render order is lowest-to-highest (painter's algorithm)

### Lifecycle

```
1. Construction (layer-specific config)
2. initialize(UIContext) -- one-time GPU resource creation
3. Per frame: update(UIFrameContext) then render() or contributeToGraph()
4. resize(w, h) -- on surface resize
5. close() -- GPU resource teardown
```

### Two Rendering Paths

Layers support both direct rendering and render graph contribution:

- **Direct:** `UIComposite.render(cmd, arena)` calls each layer's `render()` in order. Simple, no graph overhead.
- **Render graph:** `UIComposite.contributeToGraph(builder)` lets each layer add nodes. The graph handles barriers, ordering, and resource aliasing.

Layers can implement both (graph for production, direct for testing). The path is chosen at the application level.

---

## UIComposite — Layer Orchestrator

```java
UIComposite ui = UIComposite.builder()
    .context(uiCtx)
    .layer(overlayLayer)
    .layer(textLayer)
    .layer(hudLayer)
    .build();

ui.initialize();

// Per frame:
ui.update(frameContext);
ui.render(commandBuffer, frameArena);

// Input:
ui.dispatchInput(inputEvent);

// Resize:
ui.resize(newWidth, newHeight);

// Shutdown:
ui.close();
```

Layers are sorted by `order()` at build time. Adding/removing layers after build is not supported — rebuild the composite if the layer set changes.

Close order is reverse of initialization (highest order closes first).

---

## UIContext — Shared Platform Context

Created once at startup. Remains valid for the UI system's lifetime. Layers receive it in `initialize()` and hold a reference:

```java
UIContext ctx = UIContext.builder()
    .vulkan(vulkanContext)
    .assets(assetRegistry)
    .dimensions(width, height)
    .dpiScale(1.0f)
    .applicationArena(arena)
    .build();
```

| Accessor | Purpose |
|----------|---------|
| `vulkan()` | VulkanContext (device, queues) |
| `assets()` | AssetRegistry (service locator) |
| `width()` / `height()` | Current surface dimensions |
| `dpiScale()` | Display scaling factor |
| `applicationArena()` | Long-lived Arena for persistent allocations |

Dimensions are updated internally by UIComposite on resize.

---

## UIFrameContext — Per-Frame State

Provided to layers during `update()`. The `frameArena` is freed at end of frame:

```java
public class UIFrameContext {
    public Arena frameArena();
    public double deltaTime();    // seconds since last frame
    public long frameNumber();
    public UIContext ctx();
}
```

---

## Input Dispatch

### InputPhase

```java
public enum InputPhase {
    CAPTURE,  // top-down: highest order first (frontmost)
    BUBBLE    // bottom-up: lowest order first (backmost)
}
```

### UIInputDispatcher

Orchestrates the two-phase dispatch across layers:

```
CAPTURE: layers[n-1] -> layers[n-2] -> ... -> layers[0]
  (highest order first — frontmost layer annotates/intercepts first)

  -- propagation stopped flag resets, context persists --

BUBBLE:  layers[0] -> layers[1] -> ... -> layers[n-1]
  (lowest order first — backmost layer reacts first)
```

Layers with `acceptsInput() == false` are skipped entirely.

### PropagationState

Carried on every UIInputEvent. Provides:

- `stop()` — prevent further layers from seeing the event this phase
- `stopImmediate()` — same, plus prevents other handlers on same layer
- `markHandled()` — informational flag, does NOT stop propagation
- `put(key, value)` / `get(key, type)` — context dictionary for cross-layer annotation

Between phases: stopped flags reset, but context dictionary and handled flag persist.

### Context Annotation Pattern

The core pattern enabled by capture/bubble:

```java
// CAPTURE: 3D layer annotates world-space information
if (event.phase() == CAPTURE && event.type() == MOUSE_MOVE) {
    float[] worldPos = unproject(event.mouseX(), event.mouseY());
    event.propagation().put("worldPos", worldPos);
}

// BUBBLE: HUD layer reads that annotation
if (event.phase() == BUBBLE && event.type() == MOUSE_MOVE) {
    float[] pos = event.propagation().get("worldPos", float[].class);
    if (pos != null) {
        tooltip.showAt(pos);
    }
}
```

### InputEventType

```java
public enum InputEventType {
    KEY_PRESS, KEY_RELEASE, KEY_REPEAT, CHAR_INPUT,
    MOUSE_BUTTON_PRESS, MOUSE_BUTTON_RELEASE, MOUSE_MOVE,
    MOUSE_ENTER, MOUSE_LEAVE, SCROLL,
    GAMEPAD_BUTTON, GAMEPAD_AXIS,
    TOUCH_BEGIN, TOUCH_MOVE, TOUCH_END,
    FOCUS_GAINED, FOCUS_LOST,
    WINDOW_RESIZE, DROP_FILE
}
```

### UIInputEvent

Carries event type, phase, propagation state, and payload fields (mouse position, key code, modifiers, scroll, codepoint, timestamp). Created via static factories:

```java
UIInputEvent.mouseMove(x, y, dx, dy)
UIInputEvent.mouseButtonPress(button, x, y, modifiers)
UIInputEvent.keyPress(keyCode, scanCode, modifiers)
UIInputEvent.charInput(codepoint)
UIInputEvent.scroll(x, y, scrollX, scrollY)
```

---

## AssetRegistry — Service Locator

A typed container for shared services. Layers look up services during `initialize()` and cache references locally — no per-frame lookups.

```java
AssetRegistry assets = new AssetRegistry();
assets.register(FontRegistry.class, fontRegistry);
assets.register(ThemeRegistry.class, themeRegistry);
assets.register(ClipboardAccess.class, clipboard);

// In a layer:
FontRegistry fonts = ctx.assets().get(FontRegistry.class);
```

AssetRegistry is a **pure locator** — it holds references for lookup convenience but does NOT own lifecycle of registered services. The code that creates a service is responsible for closing it. Named variants supported via `AssetType.of(Class, String)` for multiple instances of the same type.

---

## Render Graph Integration (Aspirational)

Each layer's `contributeToGraph()` declares resource needs and execution logic:

```java
// Simple: one graphics pass
@Override
public void contributeToGraph(RenderGraphBuilder graph) {
    graph.addGraphicsPass(name(), ctx -> {
        render(ctx.commandBuffer(), ctx.frameArena());
    }).writes(compositeColorAttachment);
}

// Complex: compute + transfer + graphics
@Override
public void contributeToGraph(RenderGraphBuilder graph) {
    graph.addComputePass(name() + "-shape", ctx -> {
        dispatchTextShaping(ctx.commandBuffer());
    }).reads(textDataBuffer).writes(glyphInstanceBuffer);

    graph.addTransferPass(name() + "-atlas-upload", ctx -> {
        uploadDirtyAtlasPages(ctx.commandBuffer());
    }).writes(fontAtlasImage);

    graph.addGraphicsPass(name() + "-draw", ctx -> {
        renderGlyphs(ctx.commandBuffer(), ctx.frameArena());
    }).reads(glyphInstanceBuffer, fontAtlasImage).writes(compositeColorAttachment);
}
```

Currently stubbed (`Object graphBuilder` parameter). Will be typed once the render graph builder interface location is finalized.

---

## Relationship to Node Trees

The UILayer system and the node-tree system are **independent but composable**:

- A layer MAY use a node tree internally (for retained-mode widget hierarchies)
- A layer MAY ignore trees entirely (immediate-mode, raw GPU drawing)
- `TreeLayer` (in the nodetree package) is an optional bridge: a UILayer that owns a Tree and delegates input/render through it
- The node-tree's `CaptureBubbleTraversal` composes with UIInputDispatcher: during the layer-stack capture phase, a tree-owning layer calls `handleEventCapture` on its tree; during bubble, `handleEventBubble`

Neither system depends on the other. They live in the same module because they're both part of the application composition infrastructure.

---

## Open Questions / Future Work

- **Render graph integration:** Replace `Object graphBuilder` with typed `RenderGraphBuilder` once the graph's module location is finalized
- **Multi-window:** UIContext is per-window. Multiple UIComposite instances sharing an AssetRegistry but with separate UIContexts
- **Focus management:** Which layer (and element within) owns keyboard focus. Likely a small focus manager in AssetRegistry
- **Text input / IME:** Complex text input (composition, candidate selection) routing to the focused layer
- **Animation:** Shared timeline service in AssetRegistry that layers pull animated values from
- **Accessibility:** Screen reader integration, keyboard navigation — likely internal to retained-mode layers
