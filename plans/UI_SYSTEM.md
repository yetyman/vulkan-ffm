# UI System Architecture Plan

## Overview

A modular, layered UI system that supports pre-canned optimized implementations (immediate-mode, retained scene graph, GPU-driven text, canvas 2D) alongside fully custom user-defined layers. Each layer is a self-contained rendering subsystem that owns its own pipelines, vertex formats, and draw strategies. Layers compose vertically — they share platform infrastructure but not rendering internals.

The scaffolding (interfaces, orchestrator, asset registry, input dispatch) lives in the `vulkan-ffm-foundation` module. Pre-canned layer implementations live either in `vulkan-ffm-foundation` (if platform-independent with no external deps) or in their own modules (if they require external native bindings like ImGui).

---

## Core Principles

- **Layers are modules, not strategies**: The outer composition boundary is coarse (entire rendering approach per layer). Fine-grained strategy pattern applies within a layer for sub-decisions.
- **Hot path stays hot**: No virtual dispatch in inner draw loops. Each layer's render path is a tight loop with direct Vulkan calls. Indirection exists only at layer boundaries (once per frame per layer, not per draw call).
- **Swap at code-write time, not runtime**: Choosing which layers to use is a composition decision at startup. Layers are not hot-swapped mid-frame.
- **Shared infrastructure, independent rendering**: All layers share an AssetRegistry (fonts, themes, clipboard, etc.) but own their GPU resources independently.
- **Capture/bubble input model**: Input events traverse layers in two passes — capture (top-down) then bubble (bottom-up) — with propagation control and context annotation.
- **Render graph integration**: Each layer contributes one or more nodes to the render graph. Complex layers may emit compute passes, transfer nodes, and multiple graphics passes.

---

## Module Structure

### New Module: `vulkan-ffm-foundation`

```
vulkan-ffm-foundation/
  pom.xml
  src/main/java/io/github/yetyman/vulkan/foundation/
    ui/
      UILayer.java
      UIComposite.java
      UIContext.java
      UIFrameContext.java
      assets/
        AssetRegistry.java
        AssetType.java
        FontRegistry.java
        ThemeRegistry.java
        ClipboardAccess.java
        CursorManager.java
      input/
        UIInputEvent.java
        InputPhase.java
        InputEventType.java
        PropagationState.java
        UIInputDispatcher.java
```

### Maven POM (vulkan-ffm-foundation)

```xml
<artifactId>vulkan-ffm-foundation</artifactId>

<dependencies>
    <dependency>
        <groupId>io.github.yetyman</groupId>
        <artifactId>vulkan-core</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>io.github.yetyman</groupId>
        <artifactId>helpers-core</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Dependency Graph After Addition

```
helpers-core          (no deps, pure Java utilities)
vulkan-bindings       (generated FFM, no deps)
shaderc-bindings      (generated FFM, no deps)
spirv-reflect-bindings(generated FFM, no deps)
glfw-bindings         (generated FFM, bundled natives)

vulkan-core           (depends on: vulkan-bindings, shaderc-bindings, spirv-reflect-bindings)
vulkan-ffm-foundation (depends on: vulkan-core, helpers-core)

imgui-bindings        (generated FFM, bundled natives — NEW, future)
sample-app            (depends on: vulkan-ffm-foundation, glfw-bindings, imgui-bindings, jgltf)
```

### Root POM Changes

Add `vulkan-ffm-foundation` to the default modules list:
```xml
<modules>
    <module>vulkan-core</module>
    <module>vulkan-ffm-foundation</module>
    <module>sample-app</module>
    <module>helpers-core</module>
</modules>
```

---

## Render Graph Location Note

The render graph (`graph/` package) currently lives in `vulkan-core`. It could logically move to `vulkan-ffm-foundation` since it is a higher-level orchestration system built on top of core Vulkan primitives. However, this is a separate migration decision — the UI system does not depend on the graph living in any particular module. The `UILayer.contributeToGraph()` method accepts a graph builder interface, which can be defined wherever the graph lives.

---

## UILayer Interface

```java
package io.github.yetyman.vulkan.foundation.ui;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.input.UIInputEvent;
import java.lang.foreign.Arena;

/**
 * A self-contained UI rendering subsystem.
 *
 * Each layer owns its own pipelines, vertex formats, descriptor sets, and rendering approach.
 * Layers are composed vertically by UIComposite and share platform infrastructure (AssetRegistry)
 * but never share rendering internals.
 *
 * Lifecycle:
 *   1. Construction (layer-specific config)
 *   2. initialize(UIContext) — one-time GPU resource creation
 *   3. Per frame: update(UIFrameContext) then render or graph contribution
 *   4. resize(w, h) — on surface resize
 *   5. close() — GPU resource teardown
 */
public interface UILayer extends AutoCloseable {

    /** Human-readable name for debug labels and render graph node naming. */
    String name();

    /**
     * Layer ordering — lower values drawn first (background), higher drawn last (foreground).
     * Input capture phase traverses highest-to-lowest. Bubble phase traverses lowest-to-highest.
     * Render order is lowest-to-highest (painter's algorithm at the layer level).
     */
    int order();

    /**
     * One-time initialization: create pipelines, allocate persistent buffers, load fonts/atlases.
     * Called once after UIComposite.build(). The UIContext remains valid for the layer's lifetime.
     */
    void initialize(UIContext ctx);

    /**
     * Per-frame update: process state changes, run layout, advance animations.
     * Called once per frame before render/graph contribution.
     * Must not record Vulkan commands — that happens in render() or contributeToGraph().
     */
    void update(UIFrameContext frame);

    /**
     * Contribute render graph nodes for this layer.
     * A layer may add any number of nodes (compute, transfer, graphics).
     * Called during graph construction. For static graphs, called once at startup.
     * For dynamic graphs, called when the graph is rebuilt.
     *
     * Layers that only use direct rendering may leave this as a no-op.
     */
    default void contributeToGraph(Object graphBuilder) {
        // Default no-op. Parameter type is Object until we decide where RenderGraphBuilder lives.
        // Will be replaced with proper type: RenderGraphBuilder
    }

    /**
     * Record draw commands into the provided command buffer.
     * Used in the direct rendering path (no render graph).
     * Called once per frame after update(). The layer should set its own viewport/scissor
     * and record all draw calls needed.
     *
     * Layers using exclusively the render graph path may leave this as a no-op.
     */
    default void render(VkCommandBuffer cmd, Arena frameArena) {}

    /** Handle resize of the rendering surface. Recreate size-dependent resources. */
    void resize(int width, int height);

    /**
     * Input handling — called during both capture and bubble phases.
     * The event's phase() indicates which pass is active.
     *
     * During capture (top-down): annotate event context, optionally stop propagation.
     * During bubble (bottom-up): react to event with full context, optionally consume.
     *
     * @return true if this layer consumed the event (stops propagation in current phase)
     */
    boolean handleInput(UIInputEvent event);

    /** Whether this layer participates in input at all. False skips dispatch entirely. */
    default boolean acceptsInput() { return true; }

    /** Whether this layer needs per-frame update calls. False skips update(). */
    default boolean needsUpdate() { return true; }
}
```

---

## UIComposite — Layer Orchestrator

```java
package io.github.yetyman.vulkan.foundation.ui;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.input.UIInputEvent;
import io.github.yetyman.vulkan.foundation.ui.input.UIInputDispatcher;
import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates a set of UILayers: initialization, update, rendering, input dispatch, resize.
 * Layers are sorted by order() at build time. Adding/removing layers after build is not supported
 * (rebuild the composite if layer set changes).
 */
public class UIComposite implements AutoCloseable {

    private final List<UILayer> layers; // sorted by order(), lowest first
    private final UIInputDispatcher inputDispatcher;
    private final UIContext ctx;

    private UIComposite(List<UILayer> layers, UIContext ctx) {
        this.layers = layers;
        this.ctx = ctx;
        this.inputDispatcher = new UIInputDispatcher();
    }

    public static Builder builder() { return new Builder(); }

    /** Initialize all layers in order (lowest first). */
    public void initialize() {
        for (UILayer layer : layers) {
            layer.initialize(ctx);
        }
    }

    /** Update all layers that need it. */
    public void update(UIFrameContext frame) {
        for (UILayer layer : layers) {
            if (layer.needsUpdate()) {
                layer.update(frame);
            }
        }
    }

    /** Contribute all layers to the render graph (lowest order first = drawn first). */
    public void contributeToGraph(Object graphBuilder) {
        for (UILayer layer : layers) {
            layer.contributeToGraph(graphBuilder);
        }
    }

    /** Direct render path — record all layers' commands in order (lowest first). */
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        for (UILayer layer : layers) {
            layer.render(cmd, frameArena);
        }
    }

    /** Dispatch input event through capture/bubble phases across all layers. */
    public void dispatchInput(UIInputEvent event) {
        inputDispatcher.dispatch(event, layers);
    }

    /** Notify all layers of resize. */
    public void resize(int width, int height) {
        for (UILayer layer : layers) {
            layer.resize(width, height);
        }
    }

    /** Close all layers in reverse order (highest first). */
    @Override
    public void close() {
        for (int i = layers.size() - 1; i >= 0; i--) {
            try {
                layers.get(i).close();
            } catch (Exception e) {
                // log and continue — don't let one layer's failure prevent others from closing
            }
        }
    }

    public List<UILayer> layers() { return layers; }
    public UIContext context() { return ctx; }

    public static class Builder {
        private final List<UILayer> layers = new ArrayList<>();
        private UIContext ctx;

        private Builder() {}

        public Builder layer(UILayer layer) { layers.add(layer); return this; }
        public Builder context(UIContext ctx) { this.ctx = ctx; return this; }

        public UIComposite build() {
            if (ctx == null) throw new IllegalStateException("UIContext not set");
            if (layers.isEmpty()) throw new IllegalStateException("No layers added");
            List<UILayer> sorted = new ArrayList<>(layers);
            sorted.sort(Comparator.comparingInt(UILayer::order));
            return new UIComposite(sorted, ctx);
        }
    }
}
```


---

## AssetRegistry — Typed Service Registry

A typed container for shared services with lifecycle management. Each registered service manages its own internal caching and resource ownership. Services can be singleton (one per application) or instanced (one per context/device/window). The registry itself does not enforce singleton vs instanced — that is a per-service decision made at registration time.

### AssetType — Type Token

```java
package io.github.yetyman.vulkan.foundation.ui.assets;

/**
 * Type-safe key for AssetRegistry lookups.
 * Supports both class-only keys and class+name disambiguation for multiple
 * instances of the same type (e.g. two FontRegistry instances for different devices).
 */
public final class AssetType<T> {
    private final Class<T> type;
    private final String name; // null for unnamed (class-only lookup)

    private AssetType(Class<T> type, String name) {
        this.type = type;
        this.name = name;
    }

    public static <T> AssetType<T> of(Class<T> type) {
        return new AssetType<>(type, null);
    }

    public static <T> AssetType<T> of(Class<T> type, String name) {
        return new AssetType<>(type, name);
    }

    public Class<T> type() { return type; }
    public String name() { return name; }

    // equals/hashCode based on type + name
}
```

### AssetRegistry

```java
package io.github.yetyman.vulkan.foundation.ui.assets;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed service registry for shared platform resources.
 *
 * Services are registered at startup and looked up by layers during their initialize() call.
 * Layers cache the reference locally — lookups are not on the hot path.
 *
 * Close order: reverse of registration order (last registered closes first).
 */
public class AssetRegistry implements AutoCloseable {

    private final Map<AssetType<?>, Object> services = new LinkedHashMap<>();

    /** Register a service by type token. */
    public <T> void register(AssetType<T> type, T service) {
        if (services.containsKey(type)) {
            throw new IllegalStateException("Service already registered: " + type.type().getSimpleName()
                + (type.name() != null ? "[" + type.name() + "]" : ""));
        }
        services.put(type, service);
    }

    /** Register a service by class (unnamed). */
    public <T> void register(Class<T> type, T service) {
        register(AssetType.of(type), service);
    }

    /** Generic typed lookup by type token. */
    @SuppressWarnings("unchecked")
    public <T> T get(AssetType<T> type) {
        Object service = services.get(type);
        if (service == null) {
            throw new IllegalStateException("Service not registered: " + type.type().getSimpleName()
                + (type.name() != null ? "[" + type.name() + "]" : ""));
        }
        return (T) service;
    }

    /** Generic class-based lookup (unnamed). */
    public <T> T get(Class<T> type) {
        return get(AssetType.of(type));
    }

    /** Check if a service is registered. */
    public <T> boolean has(Class<T> type) {
        return services.containsKey(AssetType.of(type));
    }

    public <T> boolean has(AssetType<T> type) {
        return services.containsKey(type);
    }

    // --- Convenience getters for well-known types ---

    public FontRegistry fonts() { return get(FontRegistry.class); }
    public ThemeRegistry themes() { return get(ThemeRegistry.class); }
    public ClipboardAccess clipboard() { return get(ClipboardAccess.class); }
    public CursorManager cursors() { return get(CursorManager.class); }

    /** Close all AutoCloseable services in reverse registration order. */
    @Override
    public void close() {
        var entries = new java.util.ArrayList<>(services.values());
        java.util.Collections.reverse(entries);
        for (Object service : entries) {
            if (service instanceof AutoCloseable ac) {
                try { ac.close(); } catch (Exception e) { /* log and continue */ }
            }
        }
        services.clear();
    }
}
```

### Static Selectors on Specialized Types

Each well-known service provides a static `from(AssetRegistry)` for self-documenting, discoverable access:

```java
public class FontRegistry implements AutoCloseable {
    public static final AssetType<FontRegistry> TYPE = AssetType.of(FontRegistry.class);

    /** Extract from registry. Call once at layer startup and cache. */
    public static FontRegistry from(AssetRegistry registry) {
        return registry.get(TYPE);
    }

    // ... font atlas management, SDF generation, glyph lookup ...
}

public class ThemeRegistry {
    public static final AssetType<ThemeRegistry> TYPE = AssetType.of(ThemeRegistry.class);

    public static ThemeRegistry from(AssetRegistry registry) {
        return registry.get(TYPE);
    }

    // ... color tokens, style definitions, theme switching ...
}
```

### Usage Pattern

```java
// At application startup:
AssetRegistry assets = new AssetRegistry();
assets.register(FontRegistry.class, new FontRegistry(device, arena));
assets.register(ThemeRegistry.class, ThemeRegistry.loadDefault());
assets.register(ClipboardAccess.class, new GLFWClipboardAccess(window));
assets.register(CursorManager.class, new GLFWCursorManager(window));
// Custom services:
assets.register(AssetType.of(AudioManager.class), new AudioManager());

// In a layer's initialize():
public void initialize(UIContext ctx) {
    this.fonts = FontRegistry.from(ctx.assets());
    this.themes = ThemeRegistry.from(ctx.assets());
    // cached for the layer's lifetime — no per-frame lookup
}
```

---

## UIContext — Shared Platform Context

```java
package io.github.yetyman.vulkan.foundation.ui;

import io.github.yetyman.vulkan.highlevel.VulkanContext;
import io.github.yetyman.vulkan.foundation.ui.assets.AssetRegistry;
import java.lang.foreign.Arena;

/**
 * Platform context shared by all UILayers.
 * Created once at startup. Remains valid for the UI system's lifetime.
 * Layers receive this in initialize() and hold a reference.
 */
public class UIContext {
    private final VulkanContext vulkan;
    private final AssetRegistry assets;
    private int width;
    private int height;
    private float dpiScale;
    private final Arena applicationArena; // lives for duration of the UI system

    private UIContext(Builder b) {
        this.vulkan = b.vulkan;
        this.assets = b.assets;
        this.width = b.width;
        this.height = b.height;
        this.dpiScale = b.dpiScale;
        this.applicationArena = b.applicationArena;
    }

    public VulkanContext vulkan() { return vulkan; }
    public AssetRegistry assets() { return assets; }
    public int width() { return width; }
    public int height() { return height; }
    public float dpiScale() { return dpiScale; }
    public Arena applicationArena() { return applicationArena; }

    /** Called by UIComposite on resize. */
    void updateDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    void updateDpiScale(float dpiScale) {
        this.dpiScale = dpiScale;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private VulkanContext vulkan;
        private AssetRegistry assets;
        private int width;
        private int height;
        private float dpiScale = 1.0f;
        private Arena applicationArena;

        private Builder() {}

        public Builder vulkan(VulkanContext vulkan) { this.vulkan = vulkan; return this; }
        public Builder assets(AssetRegistry assets) { this.assets = assets; return this; }
        public Builder dimensions(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder dpiScale(float dpiScale) { this.dpiScale = dpiScale; return this; }
        public Builder applicationArena(Arena arena) { this.applicationArena = arena; return this; }

        public UIContext build() {
            if (vulkan == null) throw new IllegalStateException("vulkan not set");
            if (assets == null) throw new IllegalStateException("assets not set");
            if (applicationArena == null) throw new IllegalStateException("applicationArena not set");
            return new UIContext(this);
        }
    }
}
```

---

## UIFrameContext — Per-Frame Transient State

```java
package io.github.yetyman.vulkan.foundation.ui;

import java.lang.foreign.Arena;

/**
 * Per-frame context provided to layers during update().
 * The frameArena is freed at the end of each frame — layers must not hold references
 * to memory allocated from it across frames.
 */
public class UIFrameContext {
    private final Arena frameArena;
    private final double deltaTime;   // seconds since last frame
    private final long frameNumber;
    private final UIContext ctx;

    public UIFrameContext(Arena frameArena, double deltaTime, long frameNumber, UIContext ctx) {
        this.frameArena = frameArena;
        this.deltaTime = deltaTime;
        this.frameNumber = frameNumber;
        this.ctx = ctx;
    }

    public Arena frameArena() { return frameArena; }
    public double deltaTime() { return deltaTime; }
    public long frameNumber() { return frameNumber; }
    public UIContext ctx() { return ctx; }
}
```

---

## Input System — Capture/Bubble Dual Pass

### InputPhase

```java
package io.github.yetyman.vulkan.foundation.ui.input;

public enum InputPhase {
    /** Top-down traversal. Layers annotate context, may stop propagation early. */
    CAPTURE,
    /** Bottom-up traversal. Layers react to event with full context from capture. */
    BUBBLE
}
```

### InputEventType

```java
package io.github.yetyman.vulkan.foundation.ui.input;

public enum InputEventType {
    KEY_PRESS,
    KEY_RELEASE,
    KEY_REPEAT,
    CHAR_INPUT,         // text input (Unicode codepoint)
    MOUSE_BUTTON_PRESS,
    MOUSE_BUTTON_RELEASE,
    MOUSE_MOVE,
    MOUSE_ENTER,        // cursor entered layer/window
    MOUSE_LEAVE,        // cursor left layer/window
    SCROLL,
    GAMEPAD_BUTTON,
    GAMEPAD_AXIS,
    TOUCH_BEGIN,
    TOUCH_MOVE,
    TOUCH_END,
    FOCUS_GAINED,
    FOCUS_LOST,
    WINDOW_RESIZE,      // informational, distinct from UILayer.resize()
    DROP_FILE           // file drag-and-drop
}
```

### PropagationState

```java
package io.github.yetyman.vulkan.foundation.ui.input;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks propagation state across capture and bubble phases.
 * Carries a context dictionary that layers annotate during capture
 * for downstream layers to read during bubble.
 *
 * Propagation control:
 *   stop()          — prevents further layers from seeing this event in the current phase
 *   stopImmediate() — same as stop(), plus prevents other handlers on the same layer
 *   markHandled()   — informational flag, does NOT stop propagation
 *
 * Between phases (capture -> bubble), the stopped flag resets but handled and context persist.
 */
public class PropagationState {
    private boolean stopped = false;
    private boolean stoppedImmediate = false;
    private boolean handled = false;
    private final Map<String, Object> context = new HashMap<>();

    public void stop() { stopped = true; }
    public void stopImmediate() { stoppedImmediate = true; stopped = true; }
    public boolean isStopped() { return stopped; }
    public boolean isStoppedImmediate() { return stoppedImmediate; }

    /** Mark as handled (informational — does not stop propagation). */
    public void markHandled() { handled = true; }
    public boolean isHandled() { return handled; }

    /** Annotate context during capture for downstream layers to read during bubble. */
    public void put(String key, Object value) { context.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object val = context.get(key);
        return val != null ? type.cast(val) : null;
    }

    public boolean has(String key) { return context.containsKey(key); }

    /** Reset propagation flags for bubble phase. Context and handled state persist. */
    void resetForBubble() {
        stopped = false;
        stoppedImmediate = false;
    }
}
```

### UIInputEvent

```java
package io.github.yetyman.vulkan.foundation.ui.input;

/**
 * Input event passed through capture/bubble phases.
 *
 * Contains:
 *   - Event type and phase
 *   - PropagationState with stop/context/handled
 *   - Event-specific payload fields (mouse position, key code, etc.)
 *
 * Layers inspect phase() to determine whether they are in capture or bubble.
 * Layers call propagation methods to control flow.
 */
public class UIInputEvent {
    private final InputEventType type;
    private final PropagationState propagation;
    private InputPhase phase;

    // Key event data
    private final int keyCode;
    private final int scanCode;
    private final int modifiers; // bitmask: SHIFT, CTRL, ALT, SUPER

    // Mouse event data
    private final float mouseX;
    private final float mouseY;
    private final float deltaX;   // for MOUSE_MOVE
    private final float deltaY;
    private final int mouseButton;

    // Scroll data
    private final float scrollX;
    private final float scrollY;

    // Text input
    private final int codepoint;

    // Timestamp
    private final long timestampNanos;

    // Constructor, getters, factory methods omitted for plan brevity
    // Full implementation will have static factories:
    //   UIInputEvent.keyPress(keyCode, scanCode, modifiers)
    //   UIInputEvent.mouseMove(x, y, dx, dy)
    //   UIInputEvent.mouseButton(button, x, y, pressed)
    //   UIInputEvent.scroll(x, y, scrollX, scrollY)
    //   UIInputEvent.charInput(codepoint)

    public InputEventType type() { return type; }
    public InputPhase phase() { return phase; }
    public PropagationState propagation() { return propagation; }

    void setPhase(InputPhase phase) { this.phase = phase; }

    // Convenience propagation methods (delegate to propagation state)
    public void stopPropagation() { propagation.stop(); }
    public void stopImmediatePropagation() { propagation.stopImmediate(); }
    public void markHandled() { propagation.markHandled(); }
    public boolean isHandled() { return propagation.isHandled(); }
}
```

### UIInputDispatcher

```java
package io.github.yetyman.vulkan.foundation.ui.input;

import io.github.yetyman.vulkan.foundation.ui.UILayer;
import java.util.List;

/**
 * Orchestrates capture/bubble input dispatch across layers.
 *
 * Capture phase: highest order (frontmost) to lowest (backmost).
 *   - Layers annotate context, can stop propagation.
 *   - Use case: 3D layer adds world-space hit coordinates.
 *
 * Bubble phase: lowest order (backmost) to highest (frontmost).
 *   - Layers react to event with full context from capture.
 *   - Use case: HUD layer reads world coords, shows tooltip.
 *
 * Between phases: propagation stopped flag resets, context persists.
 */
public class UIInputDispatcher {

    public void dispatch(UIInputEvent event, List<UILayer> layers) {
        // --- Capture phase: highest order first (index layers.size()-1 down to 0) ---
        event.setPhase(InputPhase.CAPTURE);
        for (int i = layers.size() - 1; i >= 0; i--) {
            UILayer layer = layers.get(i);
            if (!layer.acceptsInput()) continue;
            boolean consumed = layer.handleInput(event);
            if (consumed || event.propagation().isStopped()) break;
        }

        // Reset stop flags for bubble. Context + handled persist.
        event.propagation().resetForBubble();

        // --- Bubble phase: lowest order first (index 0 up to layers.size()-1) ---
        event.setPhase(InputPhase.BUBBLE);
        for (int i = 0; i < layers.size(); i++) {
            UILayer layer = layers.get(i);
            if (!layer.acceptsInput()) continue;
            boolean consumed = layer.handleInput(event);
            if (consumed || event.propagation().isStopped()) break;
        }
    }
}
```

### Context Annotation Example

```java
// During capture, Scene3D layer adds world-space information:
@Override
public boolean handleInput(UIInputEvent event) {
    if (event.phase() == InputPhase.CAPTURE && event.type() == InputEventType.MOUSE_MOVE) {
        Vec3 worldPos = unprojectScreenToWorld(event.mouseX(), event.mouseY());
        if (worldPos != null) {
            event.propagation().put("worldPos", worldPos);
            Entity entity = spatialQuery(worldPos);
            if (entity != null) {
                event.propagation().put("hoveredEntity", entity);
                event.propagation().put("hitNormal", entity.lastHitNormal());
            }
        }
    }
    return false; // annotate only, don't consume
}

// During bubble, HUD layer reads that context:
@Override
public boolean handleInput(UIInputEvent event) {
    if (event.phase() == InputPhase.BUBBLE && event.type() == InputEventType.MOUSE_MOVE) {
        Entity hovered = event.propagation().get("hoveredEntity", Entity.class);
        if (hovered != null) {
            tooltip.show(hovered.displayName(), event.mouseX(), event.mouseY());
            return false; // consumed display, but don't block further layers
        }
    }
    if (event.phase() == InputPhase.BUBBLE && event.type() == InputEventType.MOUSE_BUTTON_PRESS) {
        if (isInsideButton(event.mouseX(), event.mouseY())) {
            activateButton();
            event.stopPropagation(); // consume: lower layers should not see this click
            return true;
        }
    }
    return false;
}
```

---

## Render Graph Integration

### How Layers Contribute Nodes

Each layer's `contributeToGraph()` declares its resource needs and execution logic. The render graph handles barriers, execution order, and resource aliasing automatically. A layer is free to add any number of nodes of any type.

```java
// Simple layer: one graphics pass
@Override
public void contributeToGraph(RenderGraphBuilder graph) {
    graph.addGraphicsPass(name(), ctx -> {
        render(ctx.commandBuffer(), ctx.frameArena());
    }).reads(/* none */).writes(compositeColorAttachment);
}

// Complex layer: compute + transfer + graphics
@Override
public void contributeToGraph(RenderGraphBuilder graph) {
    // 1. Compute: text shaping, glyph instance buffer generation
    graph.addComputePass(name() + "-shape", ctx -> {
        dispatchTextShaping(ctx.commandBuffer());
    }).reads(textDataBuffer).writes(glyphInstanceBuffer);

    // 2. Transfer: upload dirty font atlas pages
    graph.addTransferPass(name() + "-atlas-upload", ctx -> {
        uploadDirtyAtlasPages(ctx.commandBuffer());
    }).writes(fontAtlasImage);

    // 3. Graphics: render glyph quads
    graph.addGraphicsPass(name() + "-draw", ctx -> {
        renderGlyphs(ctx.commandBuffer(), ctx.frameArena());
    }).reads(glyphInstanceBuffer, fontAtlasImage).writes(compositeColorAttachment);
}
```

### Dirty Rect Optimization

Layers that track dirty regions can optimize by:

1. **Setting scissor** to the dirty rect before drawing:
   ```java
   VkRect2D scissor = VkRect2D.builder()
       .offset(dirtyRect.x(), dirtyRect.y())
       .extent(dirtyRect.width(), dirtyRect.height())
       .build(frameArena);
   Vulkan.cmdSetScissor(cmd.handle(), 0, 1, scissor);
   ```

2. **Using LOAD_OP_LOAD** to preserve previous frame content:
   ```java
   // In contributeToGraph or render pass setup:
   // VK_ATTACHMENT_LOAD_OP_LOAD preserves existing content
   // Only overdraw the dirty region via scissor
   ```

3. **Skipping render entirely** when nothing is dirty:
   ```java
   @Override
   public void render(VkCommandBuffer cmd, Arena frameArena) {
       if (!isDirty()) return; // no draw calls recorded, zero cost
       // ... render only dirty regions ...
       clearDirty();
   }
   ```

4. **Partial buffer uploads**: Only re-upload vertex data for elements that changed, not the entire layer's vertex buffer.

This is entirely layer-internal — the framework does not impose a dirty-rect system. Layers that benefit from it (retained mode) implement their own tracking. Layers that redraw every frame (immediate mode) ignore it.

### Direct Rendering vs Render Graph

The system supports both paths:

- **Direct rendering**: `UIComposite.render(cmd, arena)` calls each layer's `render()` in order. Simple, no graph overhead. Suitable for applications that don't use the render graph elsewhere.
- **Render graph**: `UIComposite.contributeToGraph(builder)` adds nodes. The graph handles interleaving UI passes with 3D scene passes, automatic barriers, and resource aliasing. Required when UI layers and 3D rendering share resources or need precise synchronization.

Layers can implement both paths (graph for production, direct for simple testing). The choice of which path to use is made at the application level, not per-layer.

---

## Pre-Canned Layer Summary

| Layer | Rendering Approach | Module Location | Dependencies |
|-------|-------------------|-----------------|--------------|
| `ImmediateModeLayer` | ImGui-style begin/end, vertex buffer per frame | `vulkan-ffm-foundation` or `imgui-bindings` | cimgui native lib (if wrapping real ImGui) |
| `RetainedSceneLayer` | Node tree, dirty layout, batched draws | `vulkan-ffm-foundation` | None external |
| `GPUDrivenTextLayer` | Compute shaping, SDF atlas, instanced quads | `vulkan-ffm-foundation` | None external |
| `Canvas2DLayer` | Vector path tessellation, anti-aliased fills | `vulkan-ffm-foundation` | None external |
| `Scene3DOverlayLayer` | Line/wireframe/gizmo rendering in world space | `vulkan-ffm-foundation` | None external |

Layers with external native dependencies (ImGui bindings) get their own module. Platform-independent layers with no external deps live in `vulkan-ffm-foundation`.

---

## Open Questions / Future Considerations

- **Accessibility**: Screen reader integration, keyboard navigation, focus management — likely lives in RetainedSceneLayer's node system as a concern, not a separate layer.
- **Animation system**: Shared timeline (from the timeout/timeline system in NEXT_STEPS) could drive UI animations. Each layer pulls current animated values from a shared animator service in AssetRegistry.
- **Serialization**: Retained scene layers may want to serialize/deserialize UI trees. Internal to RetainedSceneLayer.
- **Multi-window**: UIContext is per-window. Multi-window means multiple UIComposite instances sharing an AssetRegistry but with separate UIContext.
- **Text input / IME**: Complex text input (composition, candidate selection) routes to the focused layer. Special event type that bypasses normal capture/bubble.
- **Focus management**: Which layer (and which element within a layer) owns keyboard focus. Likely a small focus manager service in AssetRegistry that layers query.
- **Render graph migration**: The render graph could move from vulkan-core to vulkan-ffm-foundation in a future refactor. The UI system interfaces are designed to not depend on its location.
