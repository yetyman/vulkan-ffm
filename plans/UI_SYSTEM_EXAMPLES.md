# UI System — Example Layer Implementations

## Overview

This document details the first two concrete UILayer implementations:

1. **ImGui Layer** — Dear ImGui integration via FFM bindings for debug/dev tooling
2. **Scene3D Overlay Layer** — 3D-space wireframe, gizmo, and annotation rendering

These represent the two most immediately useful layers: one for rapid developer UI and one for 3D scene interaction tooling. Together they exercise the full UILayer contract including input dispatch, render graph contribution, and AssetRegistry integration.

---

# 1. ImGui Layer

## Strategy: Wrap Dear ImGui via cimgui

Dear ImGui is a C++ immediate-mode UI library. `cimgui` is the official C binding wrapper that exposes Dear ImGui's API as a flat C ABI — suitable for jextract FFM binding generation.

We follow the same pattern as other bindings modules: generate FFM bindings from the C header, bundle the native library, and expose a Java-idiomatic wrapper on top.

### Why cimgui and not a pure-Java reimplementation

- ImGui is battle-tested, optimized, and has massive community/plugin support
- The rendering backend (vertex buffer upload + textured draw) is trivial in Vulkan
- A pure-Java reimplementation would be 50k+ lines of layout/widget code for marginal benefit
- The FFM call overhead is negligible — ImGui's hot path is CPU layout, not FFM calls

---

## Module: `imgui-bindings`

### Directory Structure

```
imgui-bindings/
  pom.xml
  generate-imgui-bindings.bat
  cimgui_wrapper.h                  -- jextract input header
  .gitignore
  src/
    main/
      java/
        io/github/yetyman/imgui/generated/
          CimguiFFM.java            -- jextract-generated flat C bindings
          ImGuiIO.java              -- struct accessor
          ImDrawVert.java           -- struct accessor
          ImDrawList.java           -- struct accessor
          ImDrawCmd.java            -- struct accessor
          ImDrawData.java           -- struct accessor
          ImVec2.java               -- struct accessor
          ImVec4.java               -- struct accessor
          ImFontAtlas.java          -- struct accessor
          // ... all cimgui structs
      resources/
        natives/
          imgui.dll                  -- Windows (built from cimgui)
          libcimgui.so              -- Linux
          libcimgui.dylib           -- macOS
```

### Maven POM (`imgui-bindings/pom.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.yetyman</groupId>
        <artifactId>VulkanFFM</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>imgui-bindings</artifactId>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
```

### Native Library Build

cimgui must be compiled from source with the Vulkan backend **disabled** (we provide our own Vulkan rendering backend). Only the core ImGui + cimgui C wrapper is needed.

```
build-cimgui.bat:
  1. Clone cimgui (includes Dear ImGui as submodule)
  2. Build with CMake:
     - IMGUI_IMPL_VULKAN=OFF (we do our own Vulkan rendering)
     - IMGUI_IMPL_GLFW=OFF (we handle input ourselves)
     - IMGUI_IMPL_WIN32=OFF
     - Build as shared library (.dll/.so/.dylib)
  3. Copy output to src/main/resources/natives/
```

### jextract Generation

```
generate-imgui-bindings.bat:
  jextract --source
    --target-package io.github.yetyman.imgui.generated
    --output src/main/java
    -l imgui
    --header-class-name CimguiFFM
    cimgui_wrapper.h
```

The wrapper header includes:
```c
#include "cimgui.h"
// May need forward declarations for opaque types
```

### Native Library Loader

```java
package io.github.yetyman.imgui.generated;

// Same pattern as glfw-bindings NativeLibraryLoader
public class ImGuiLibraryLoader {
    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        // Extract from JAR resources/natives/ to temp dir, System.load()
        // Platform detection: windows -> imgui.dll, linux -> libcimgui.so, mac -> libcimgui.dylib
        loaded = true;
    }
}
```

---

## ImGui Vulkan Rendering Backend

The rendering backend translates ImGui's draw data into Vulkan commands. This lives in `vulkan-ffm-foundation` (not in `imgui-bindings`) because it depends on both vulkan-core and imgui-bindings.

### Package Location

```
vulkan-ffm-foundation/src/main/java/io/github/yetyman/vulkan/foundation/ui/layers/imgui/
  ImGuiLayer.java               -- UILayer implementation
  ImGuiRenderer.java            -- Vulkan rendering backend
  ImGuiInputBridge.java         -- maps UIInputEvent to ImGui IO state
  ImGuiFontAtlas.java           -- font atlas texture management
```

### vulkan-ffm-foundation POM addition

```xml
<dependency>
    <groupId>io.github.yetyman</groupId>
    <artifactId>imgui-bindings</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## ImGuiLayer — Full Implementation Plan

### Class Structure

```java
package io.github.yetyman.vulkan.foundation.ui.layers.imgui;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.*;
import io.github.yetyman.vulkan.foundation.ui.input.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Dear ImGui integration layer.
 *
 * Provides immediate-mode debug/dev UI via cimgui FFM bindings.
 * Renders ImGui's draw data using a custom Vulkan backend (no imgui_impl_vulkan).
 *
 * Usage:
 *   In update(): call ImGui begin/end, define windows/widgets.
 *   In render(): upload vertex/index buffers, record draw commands.
 *
 * The layer handles:
 *   - ImGui context lifecycle (create/destroy)
 *   - Font atlas upload to GPU texture
 *   - Input bridging from UIInputEvent to ImGui IO
 *   - Vertex/index buffer management (dynamic, re-uploaded each frame)
 *   - Pipeline creation (textured alpha-blended triangles with scissor)
 *   - Multi-viewport support (future, optional)
 */
public class ImGuiLayer implements UILayer {

    private static final int DEFAULT_ORDER = 1000; // high = drawn last (on top)

    private final int order;
    private UIContext ctx;
    private ImGuiRenderer renderer;
    private ImGuiInputBridge inputBridge;
    private MemorySegment imguiContext; // ImGuiContext* owned by this layer

    // Per-frame callback for user-defined ImGui commands
    private Runnable frameCallback;

    public ImGuiLayer() { this(DEFAULT_ORDER); }
    public ImGuiLayer(int order) { this.order = order; }

    /** Set the per-frame callback that defines ImGui windows/widgets. */
    public void setFrameCallback(Runnable callback) { this.frameCallback = callback; }

    @Override public String name() { return "imgui"; }
    @Override public int order() { return order; }

    @Override
    public void initialize(UIContext ctx) {
        this.ctx = ctx;

        // Create ImGui context
        imguiContext = CimguiFFM.igCreateContext(MemorySegment.NULL);
        CimguiFFM.igSetCurrentContext(imguiContext);

        // Configure ImGui IO
        MemorySegment io = CimguiFFM.igGetIO();
        // Set display size, DPI scale, key mappings
        configureIO(io, ctx.width(), ctx.height(), ctx.dpiScale());

        // Create renderer (pipeline, font atlas texture, buffer pools)
        renderer = new ImGuiRenderer(ctx);
        renderer.initialize();

        // Create input bridge
        inputBridge = new ImGuiInputBridge(io);
    }

    @Override
    public void update(UIFrameContext frame) {
        CimguiFFM.igSetCurrentContext(imguiContext);

        // Update display size (in case of mid-frame DPI change)
        MemorySegment io = CimguiFFM.igGetIO();
        setDisplaySize(io, ctx.width(), ctx.height(), ctx.dpiScale());
        setDeltaTime(io, (float) frame.deltaTime());

        // Begin new ImGui frame
        CimguiFFM.igNewFrame();

        // Execute user-defined ImGui commands
        if (frameCallback != null) {
            frameCallback.run();
        }

        // Finalize ImGui frame (builds draw data)
        CimguiFFM.igEndFrame();
        CimguiFFM.igRender();
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        CimguiFFM.igSetCurrentContext(imguiContext);
        MemorySegment drawData = CimguiFFM.igGetDrawData();

        if (drawData.equals(MemorySegment.NULL)) return;

        // Upload vertex/index buffers and record draw commands
        renderer.render(cmd, frameArena, drawData);
    }

    @Override
    public void contributeToGraph(Object graphBuilder) {
        // Single graphics pass: upload buffers + draw
        // graphBuilder.addGraphicsPass(name(), ctx -> {
        //     render(ctx.commandBuffer(), ctx.frameArena());
        // }).writes(compositeColorAttachment);
    }

    @Override
    public void resize(int width, int height) {
        // ImGui display size updated in next update() call via IO
    }

    @Override
    public boolean handleInput(UIInputEvent event) {
        if (event.phase() != InputPhase.BUBBLE) return false;

        // Bridge UIInputEvent to ImGui IO state
        inputBridge.processEvent(event);

        // If ImGui wants to capture this input, consume it
        MemorySegment io = CimguiFFM.igGetIO();
        boolean wantsMouse = ImGuiIO.WantCaptureMouse(io) != 0;
        boolean wantsKeyboard = ImGuiIO.WantCaptureKeyboard(io) != 0;

        return switch (event.type()) {
            case MOUSE_BUTTON_PRESS, MOUSE_BUTTON_RELEASE, MOUSE_MOVE, SCROLL -> wantsMouse;
            case KEY_PRESS, KEY_RELEASE, KEY_REPEAT, CHAR_INPUT -> wantsKeyboard;
            default -> false;
        };
    }

    @Override
    public void close() {
        if (renderer != null) renderer.close();
        if (imguiContext != null && !imguiContext.equals(MemorySegment.NULL)) {
            CimguiFFM.igDestroyContext(imguiContext);
        }
    }
}
```

---

## ImGuiRenderer — Vulkan Backend

```java
package io.github.yetyman.vulkan.foundation.ui.layers.imgui;

/**
 * Vulkan rendering backend for Dear ImGui.
 *
 * Responsibilities:
 *   - Font atlas texture creation (R8 or RGBA8, uploaded once + on font rebuild)
 *   - Pipeline creation: vertex input (pos2D, UV, color32), alpha-blended, dynamic scissor
 *   - Per-frame vertex/index buffer upload from ImDrawData
 *   - Command recording: iterate ImDrawList/ImDrawCmd, set scissor, draw indexed
 *
 * Design decisions:
 *   - Single pipeline for all ImGui rendering (textured alpha-blend)
 *   - Dynamic state: scissor (set per ImDrawCmd), viewport (set once per frame)
 *   - Vertex format: ImDrawVert { ImVec2 pos; ImVec2 uv; ImU32 col; } = 20 bytes
 *   - Push constants: orthographic projection matrix (mat4, 64 bytes)
 *   - Descriptor set: single combined image sampler (font atlas)
 *   - Buffer strategy: mapped buffer, re-uploaded every frame (ImGui vertices are transient)
 *     Could upgrade to ring buffer for multi-frame-in-flight
 */
public class ImGuiRenderer implements AutoCloseable {

    private UIContext ctx;

    // GPU resources
    private VkPipeline pipeline;
    private VkDescriptorSetLayout descriptorLayout;
    private VkDescriptorPool descriptorPool;
    private MemorySegment descriptorSet;     // font atlas binding
    private VkImage fontAtlasImage;
    private VkImageView fontAtlasView;
    private VkSampler fontSampler;

    // Per-frame dynamic buffers
    private ManagedBuffer vertexBuffer;      // grown as needed
    private ManagedBuffer indexBuffer;       // grown as needed
    private int vertexBufferCapacity;
    private int indexBufferCapacity;

    public ImGuiRenderer(UIContext ctx) { this.ctx = ctx; }

    public void initialize() {
        createFontAtlas();
        createPipeline();
        createDescriptors();
        allocateBuffers(65536, 65536); // initial capacity
    }

    public void render(VkCommandBuffer cmd, Arena frameArena, MemorySegment drawData) {
        int totalVertexCount = ImDrawData.TotalVtxCount(drawData);
        int totalIndexCount = ImDrawData.TotalIdxCount(drawData);
        if (totalVertexCount == 0) return;

        // Grow buffers if needed
        ensureBufferCapacity(totalVertexCount, totalIndexCount);

        // Upload all vertex/index data from ImDrawData's ImDrawLists
        uploadBuffers(drawData, frameArena);

        // Set pipeline and global state
        bindPipeline(cmd, frameArena);

        // Set orthographic projection via push constants
        pushProjectionMatrix(cmd, frameArena, drawData);

        // Bind font atlas descriptor
        bindDescriptors(cmd, frameArena);

        // Bind vertex/index buffers
        bindBuffers(cmd);

        // Iterate draw lists and draw commands
        int globalVtxOffset = 0;
        int globalIdxOffset = 0;
        int cmdListCount = ImDrawData.CmdListsCount(drawData);
        MemorySegment cmdLists = ImDrawData.CmdLists(drawData);

        for (int listIdx = 0; listIdx < cmdListCount; listIdx++) {
            MemorySegment cmdList = /* deref cmdLists[listIdx] */;
            int cmdCount = ImDrawList.CmdBuffer_Size(cmdList);
            MemorySegment cmdBuffer = ImDrawList.CmdBuffer_Data(cmdList);

            for (int cmdIdx = 0; cmdIdx < cmdCount; cmdIdx++) {
                MemorySegment drawCmd = /* cmdBuffer + cmdIdx * ImDrawCmd.sizeof() */;
                int elemCount = ImDrawCmd.ElemCount(drawCmd);
                int idxOffset = ImDrawCmd.IdxOffset(drawCmd) + globalIdxOffset;
                int vtxOffset = ImDrawCmd.VtxOffset(drawCmd) + globalVtxOffset;

                // Scissor from ClipRect (transformed by framebuffer scale)
                float clipMinX = ImDrawCmd.ClipRect_x(drawCmd);
                float clipMinY = ImDrawCmd.ClipRect_y(drawCmd);
                float clipMaxX = ImDrawCmd.ClipRect_z(drawCmd);
                float clipMaxY = ImDrawCmd.ClipRect_w(drawCmd);
                setScissor(cmd, frameArena, clipMinX, clipMinY, clipMaxX, clipMaxY);

                // TextureId: if non-null and different from font atlas, bind different descriptor
                // (for user textures — advanced feature, font atlas is the common case)
                MemorySegment textureId = ImDrawCmd.TextureId(drawCmd);
                if (!textureId.equals(fontAtlasView.handle())) {
                    // bind user texture descriptor (requires descriptor set per texture)
                }

                // Draw indexed
                Vulkan.cmdDrawIndexed(cmd.handle(), elemCount, 1, idxOffset, vtxOffset, 0);
            }

            globalVtxOffset += ImDrawList.VtxBuffer_Size(cmdList);
            globalIdxOffset += ImDrawList.IdxBuffer_Size(cmdList);
        }
    }

    @Override
    public void close() {
        // Destroy in reverse creation order:
        // vertexBuffer, indexBuffer, descriptorPool, descriptorLayout,
        // fontSampler, fontAtlasView, fontAtlasImage, pipeline
    }

    // --- Private implementation methods ---

    private void createFontAtlas() {
        // 1. igFontAtlas_GetTexDataAsRGBA32 or GetTexDataAsAlpha8
        // 2. Create VkImage (R8_UNORM or R8G8B8A8_UNORM)
        // 3. Upload via staging buffer
        // 4. Create VkImageView + VkSampler (linear filtering, clamp)
        // 5. igFontAtlas_SetTexID(fontAtlasView.handle()) so ImGui references it
    }

    private void createPipeline() {
        // Vertex shader: transforms pos by orthographic projection push constant
        // Fragment shader: samples font atlas texture, multiplies by vertex color
        // Vertex input: binding 0, stride 20 (ImDrawVert)
        //   - location 0: vec2 pos (offset 0)
        //   - location 1: vec2 uv  (offset 8)
        //   - location 2: uint col (offset 16, unorm unpack in shader)
        // Blend: srcAlpha, oneMinusSrcAlpha (standard alpha blend)
        // Dynamic state: scissor, viewport
        // Depth: disabled (2D overlay)
        // Cull: none
    }

    private void createDescriptors() {
        // Layout: set 0, binding 0 = combined image sampler (fragment stage)
        // Pool: 1 set
        // Write: font atlas image view + sampler
    }

    private void pushProjectionMatrix(VkCommandBuffer cmd, Arena arena, MemorySegment drawData) {
        // Orthographic projection from ImDrawData display pos/size:
        //   left = DisplayPos.x
        //   right = DisplayPos.x + DisplaySize.x
        //   top = DisplayPos.y
        //   bottom = DisplayPos.y + DisplaySize.y
        // Push as mat4 (64 bytes) to vertex shader
    }
}
```

---

## ImGuiInputBridge

```java
package io.github.yetyman.vulkan.foundation.ui.layers.imgui;

import io.github.yetyman.vulkan.foundation.ui.input.*;
import java.lang.foreign.MemorySegment;

/**
 * Bridges UIInputEvent data into ImGui's IO struct.
 *
 * ImGui expects input state to be written directly to ImGuiIO fields:
 *   - MousePos, MouseDown[5], MouseWheel
 *   - KeysDown[512], KeyMap[ImGuiKey_COUNT]
 *   - AddInputCharacter() for text input
 *
 * This bridge translates each UIInputEvent into the corresponding IO writes.
 * Called during the bubble phase of input dispatch.
 */
public class ImGuiInputBridge {

    private final MemorySegment io; // ImGuiIO*

    public ImGuiInputBridge(MemorySegment io) { this.io = io; }

    public void processEvent(UIInputEvent event) {
        switch (event.type()) {
            case MOUSE_MOVE -> {
                ImGuiIO.MousePos_x(io, event.mouseX());
                ImGuiIO.MousePos_y(io, event.mouseY());
            }
            case MOUSE_BUTTON_PRESS -> {
                int button = mapMouseButton(event.mouseButton());
                if (button >= 0 && button < 5) {
                    setMouseDown(io, button, true);
                }
            }
            case MOUSE_BUTTON_RELEASE -> {
                int button = mapMouseButton(event.mouseButton());
                if (button >= 0 && button < 5) {
                    setMouseDown(io, button, false);
                }
            }
            case SCROLL -> {
                ImGuiIO.MouseWheel(io, ImGuiIO.MouseWheel(io) + event.scrollY());
                ImGuiIO.MouseWheelH(io, ImGuiIO.MouseWheelH(io) + event.scrollX());
            }
            case KEY_PRESS, KEY_REPEAT -> {
                int imguiKey = mapKeyToImGui(event.keyCode());
                if (imguiKey >= 0) {
                    setKeyDown(io, imguiKey, true);
                }
                updateModifiers(io, event.modifiers());
            }
            case KEY_RELEASE -> {
                int imguiKey = mapKeyToImGui(event.keyCode());
                if (imguiKey >= 0) {
                    setKeyDown(io, imguiKey, false);
                }
                updateModifiers(io, event.modifiers());
            }
            case CHAR_INPUT -> {
                CimguiFFM.ImGuiIO_AddInputCharacter(io, event.codepoint());
            }
            default -> {} // ignore other event types
        }
    }

    private int mapMouseButton(int glfwButton) {
        // GLFW button 0 = left = ImGui 0
        // GLFW button 1 = right = ImGui 1
        // GLFW button 2 = middle = ImGui 2
        return glfwButton; // direct mapping works for 0-4
    }

    private int mapKeyToImGui(int keyCode) {
        // Map platform key codes to ImGuiKey enum values
        // This mapping depends on the windowing system (GLFW key codes in our case)
        // Full mapping table: Tab, Arrow keys, Home/End, Delete, Backspace,
        // Enter, Escape, A-Z (for Ctrl+A etc.), etc.
        // stub: return the mapping
        return -1;
    }

    private void updateModifiers(MemorySegment io, int modifiers) {
        ImGuiIO.KeyCtrl(io, (modifiers & 0x02) != 0 ? 1 : 0);  // GLFW_MOD_CONTROL
        ImGuiIO.KeyShift(io, (modifiers & 0x01) != 0 ? 1 : 0);  // GLFW_MOD_SHIFT
        ImGuiIO.KeyAlt(io, (modifiers & 0x04) != 0 ? 1 : 0);    // GLFW_MOD_ALT
        ImGuiIO.KeySuper(io, (modifiers & 0x08) != 0 ? 1 : 0);  // GLFW_MOD_SUPER
    }
}
```

---

## ImGui Shader Source

### Vertex Shader (`imgui.vert`)

```glsl
#version 450

layout(push_constant) uniform PushConstants {
    mat4 projection;
} pc;

layout(location = 0) in vec2 inPos;
layout(location = 1) in vec2 inUV;
layout(location = 2) in uint inColor; // packed RGBA8

layout(location = 0) out vec2 fragUV;
layout(location = 1) out vec4 fragColor;

void main() {
    gl_Position = pc.projection * vec4(inPos, 0.0, 1.0);
    fragUV = inUV;
    // Unpack ABGR (ImGui's native format) to vec4
    fragColor = vec4(
        float((inColor >> 0) & 0xFF) / 255.0,
        float((inColor >> 8) & 0xFF) / 255.0,
        float((inColor >> 16) & 0xFF) / 255.0,
        float((inColor >> 24) & 0xFF) / 255.0
    );
}
```

### Fragment Shader (`imgui.frag`)

```glsl
#version 450

layout(set = 0, binding = 0) uniform sampler2D fontAtlas;

layout(location = 0) in vec2 fragUV;
layout(location = 1) in vec4 fragColor;

layout(location = 0) out vec4 outColor;

void main() {
    outColor = fragColor * texture(fontAtlas, fragUV);
}
```


---

# 2. Scene3D Overlay Layer

## Purpose

Renders 3D-space primitives on top of (or integrated with) the main 3D scene: wireframes, gizmos, line drawings, billboarded labels, bounding box visualizations, debug rays, and transform handles. This is the standard "editor viewport overlay" or "debug visualization" system.

### Why It Belongs in vulkan-ffm-foundation

- No external dependencies (all rendering is custom line/triangle drawing)
- Platform-independent (pure Vulkan + math)
- Useful across all application types that have a 3D scene
- Relatively small code footprint

---

## Package Location

```
vulkan-ffm-foundation/src/main/java/io/github/yetyman/vulkan/foundation/ui/layers/scene3d/
  Scene3DOverlayLayer.java      -- UILayer implementation
  OverlayRenderer.java          -- Vulkan line/triangle rendering backend
  OverlayDrawList.java          -- accumulated 3D draw commands for a frame
  OverlayCommand.java           -- individual draw command (lines, tris, text)
  Gizmo.java                    -- transform gizmo (translate/rotate/scale)
  GizmoMode.java                -- enum: TRANSLATE, ROTATE, SCALE
  GizmoSpace.java               -- enum: LOCAL, WORLD
  OverlayVertex.java            -- vertex format: pos3D + color4
  OverlayTextEntry.java         -- world-space text label descriptor
```

---

## Scene3DOverlayLayer — Full Implementation Plan

```java
package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

import io.github.yetyman.vulkan.VkCommandBuffer;
import io.github.yetyman.vulkan.foundation.ui.*;
import io.github.yetyman.vulkan.foundation.ui.input.*;
import java.lang.foreign.Arena;

/**
 * 3D overlay layer: renders debug primitives, gizmos, and annotations in world space.
 *
 * Drawing API (immediate-mode style, called between update() and render()):
 *   overlay.drawLine(from, to, color)
 *   overlay.drawWireBox(min, max, color)
 *   overlay.drawWireSphere(center, radius, color, segments)
 *   overlay.drawRay(origin, direction, length, color)
 *   overlay.drawArrow(from, to, color, headSize)
 *   overlay.drawFrustum(viewProj, color)
 *   overlay.drawGrid(center, size, divisions, color)
 *   overlay.drawAxis(transform, scale) // RGB = XYZ
 *   overlay.drawText(worldPos, text, color) // billboarded
 *   overlay.drawGizmo(transform, mode) // interactive transform handle
 *
 * All draw calls accumulate into an OverlayDrawList, uploaded and rendered each frame.
 * The draw list is cleared after render().
 *
 * Depth modes:
 *   - DEPTH_TESTED: occluded by scene geometry (default for gizmos)
 *   - ALWAYS_ON_TOP: drawn over everything (default for debug lines)
 *   - DEPTH_TESTED_FADED: depth tested but occluded portions drawn at reduced alpha
 *
 * Input: the gizmo system participates in capture/bubble for hover detection
 * and drag interaction.
 */
public class Scene3DOverlayLayer implements UILayer {

    private static final int DEFAULT_ORDER = 100; // above main 3D scene, below 2D HUD

    private final int order;
    private UIContext ctx;
    private OverlayRenderer renderer;
    private OverlayDrawList drawList;
    private Gizmo activeGizmo;

    // Camera state (must be set externally each frame before drawing)
    private float[] viewMatrix = new float[16];       // column-major 4x4
    private float[] projectionMatrix = new float[16]; // column-major 4x4
    private float[] cameraPosition = new float[3];

    // Per-frame callback for user-defined overlay draws
    private Runnable frameCallback;

    public Scene3DOverlayLayer() { this(DEFAULT_ORDER); }
    public Scene3DOverlayLayer(int order) { this.order = order; }

    /** Set camera matrices for this frame. Must be called before update(). */
    public void setCamera(float[] view, float[] projection, float[] cameraPos) {
        System.arraycopy(view, 0, viewMatrix, 0, 16);
        System.arraycopy(projection, 0, projectionMatrix, 0, 16);
        System.arraycopy(cameraPos, 0, cameraPosition, 0, 3);
    }

    /** Set the per-frame callback that draws overlay primitives. */
    public void setFrameCallback(Runnable callback) { this.frameCallback = callback; }

    // --- Drawing API (call during or after update, before render) ---

    public void drawLine(float[] from, float[] to, float[] color) {
        drawList.addLine(from, to, color, DepthMode.ALWAYS_ON_TOP);
    }

    public void drawLine(float[] from, float[] to, float[] color, DepthMode depthMode) {
        drawList.addLine(from, to, color, depthMode);
    }

    public void drawWireBox(float[] min, float[] max, float[] color) {
        drawList.addWireBox(min, max, color, DepthMode.ALWAYS_ON_TOP);
    }

    public void drawWireBox(float[] min, float[] max, float[] color, DepthMode depthMode) {
        drawList.addWireBox(min, max, color, depthMode);
    }

    public void drawWireSphere(float[] center, float radius, float[] color, int segments) {
        drawList.addWireSphere(center, radius, color, segments, DepthMode.ALWAYS_ON_TOP);
    }

    public void drawRay(float[] origin, float[] direction, float length, float[] color) {
        drawList.addRay(origin, direction, length, color);
    }

    public void drawArrow(float[] from, float[] to, float[] color, float headSize) {
        drawList.addArrow(from, to, color, headSize);
    }

    public void drawFrustum(float[] inverseViewProj, float[] color) {
        drawList.addFrustum(inverseViewProj, color);
    }

    public void drawGrid(float[] center, float size, int divisions, float[] color) {
        drawList.addGrid(center, size, divisions, color);
    }

    public void drawAxis(float[] transform4x4, float scale) {
        drawList.addAxis(transform4x4, scale);
    }

    public void drawText(float[] worldPos, String text, float[] color) {
        drawList.addText(worldPos, text, color);
    }

    public Gizmo drawGizmo(float[] transform4x4, GizmoMode mode) {
        if (activeGizmo == null) {
            activeGizmo = new Gizmo();
        }
        activeGizmo.update(transform4x4, mode, viewMatrix, projectionMatrix, cameraPosition);
        drawList.addGizmo(activeGizmo);
        return activeGizmo;
    }

    // --- UILayer interface ---

    @Override public String name() { return "scene3d-overlay"; }
    @Override public int order() { return order; }

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

        // Execute user-defined draw callback
        if (frameCallback != null) {
            frameCallback.run();
        }
    }

    @Override
    public void render(VkCommandBuffer cmd, Arena frameArena) {
        if (drawList.isEmpty()) return;
        renderer.render(cmd, frameArena, drawList, viewMatrix, projectionMatrix);
    }

    @Override
    public void contributeToGraph(Object graphBuilder) {
        // Typically two passes:
        // 1. Depth-tested primitives (read scene depth, write overlay color)
        // 2. Always-on-top primitives (no depth test, write overlay color)
        //
        // graphBuilder.addGraphicsPass(name() + "-depth-tested", ctx -> {
        //     renderer.renderDepthTested(ctx.commandBuffer(), ctx.frameArena(), drawList, ...);
        // }).reads(sceneDepthAttachment).writes(compositeColorAttachment);
        //
        // graphBuilder.addGraphicsPass(name() + "-on-top", ctx -> {
        //     renderer.renderOnTop(ctx.commandBuffer(), ctx.frameArena(), drawList, ...);
        // }).writes(compositeColorAttachment);
    }

    @Override
    public void resize(int width, int height) {
        // No size-dependent resources (projection handled by camera matrices)
    }

    @Override
    public boolean handleInput(UIInputEvent event) {
        if (activeGizmo == null) return false;

        if (event.phase() == InputPhase.CAPTURE) {
            // During capture: perform ray-cast against gizmo handles
            // Annotate which axis/ring is hovered
            if (event.type() == InputEventType.MOUSE_MOVE) {
                float[] rayOrigin = unprojectRay(event.mouseX(), event.mouseY(), 0);
                float[] rayDir = unprojectRay(event.mouseX(), event.mouseY(), 1);
                GizmoHit hit = activeGizmo.testHit(rayOrigin, rayDir);
                if (hit != null) {
                    event.propagation().put("gizmoHit", hit);
                    event.propagation().put("gizmoAxis", hit.axis());
                }
            }
            return false; // don't consume during capture
        }

        if (event.phase() == InputPhase.BUBBLE) {
            // During bubble: handle drag start/move/end on gizmo
            if (event.type() == InputEventType.MOUSE_BUTTON_PRESS) {
                GizmoHit hit = event.propagation().get("gizmoHit", GizmoHit.class);
                if (hit != null) {
                    activeGizmo.beginDrag(hit, event.mouseX(), event.mouseY());
                    event.stopPropagation();
                    return true;
                }
            }
            if (event.type() == InputEventType.MOUSE_MOVE && activeGizmo.isDragging()) {
                activeGizmo.updateDrag(event.mouseX(), event.mouseY(),
                    viewMatrix, projectionMatrix, ctx.width(), ctx.height());
                event.stopPropagation();
                return true;
            }
            if (event.type() == InputEventType.MOUSE_BUTTON_RELEASE && activeGizmo.isDragging()) {
                activeGizmo.endDrag();
                event.stopPropagation();
                return true;
            }
        }

        return false;
    }

    @Override
    public void close() {
        if (renderer != null) renderer.close();
    }
}
```

---

## OverlayRenderer — Vulkan Backend

```java
package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

/**
 * Vulkan rendering backend for 3D overlay primitives.
 *
 * Two pipelines:
 *   1. Depth-tested lines/triangles (reads scene depth buffer)
 *   2. Always-on-top lines/triangles (depth test disabled)
 *
 * Vertex format: OverlayVertex { vec3 pos; vec4 color; } = 28 bytes
 * Topology: VK_PRIMITIVE_TOPOLOGY_LINE_LIST for lines,
 *           VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST for filled shapes (gizmo handles, arrows)
 *
 * Strategy:
 *   - Collect all lines into one vertex buffer, all triangles into another
 *   - Sort by depth mode (two passes: depth-tested first, on-top second)
 *   - Single draw call per topology per depth mode (4 draw calls max)
 *   - Line width via VK_EXT_line_rasterization or geometry shader fallback
 *
 * Push constants: ViewProjection matrix (mat4, 64 bytes)
 */
public class OverlayRenderer implements AutoCloseable {

    private UIContext ctx;

    // Pipelines
    private VkPipeline lineDepthTestedPipeline;
    private VkPipeline lineOnTopPipeline;
    private VkPipeline triDepthTestedPipeline;
    private VkPipeline triOnTopPipeline;

    // Dynamic buffers (grown as needed, re-uploaded each frame)
    private ManagedBuffer lineVertexBuffer;
    private ManagedBuffer triVertexBuffer;
    private int lineVertexCapacity;
    private int triVertexCapacity;

    public OverlayRenderer(UIContext ctx) { this.ctx = ctx; }

    public void initialize() {
        createPipelines();
        allocateBuffers(4096, 4096); // initial vertex count capacity
    }

    public void render(VkCommandBuffer cmd, Arena frameArena,
                       OverlayDrawList drawList, float[] view, float[] proj) {
        // 1. Tessellate draw list into raw vertices
        //    - Lines: each line = 2 vertices
        //    - Wire boxes: 12 lines = 24 vertices
        //    - Wire spheres: segments * 3 circles of segments lines
        //    - Arrows: line + triangle head
        //    - Gizmos: axis lines + cone/ring/box handles (triangles)
        //    - Grid: divisions * 2 * 2 lines

        // 2. Split vertices by topology (line vs tri) and depth mode
        OverlayBatch depthTestedLines = drawList.getDepthTestedLines();
        OverlayBatch onTopLines = drawList.getOnTopLines();
        OverlayBatch depthTestedTris = drawList.getDepthTestedTris();
        OverlayBatch onTopTris = drawList.getOnTopTris();

        // 3. Upload vertex buffers
        uploadBatch(depthTestedLines, onTopLines, lineVertexBuffer, frameArena);
        uploadBatch(depthTestedTris, onTopTris, triVertexBuffer, frameArena);

        // 4. Compute ViewProjection matrix
        float[] viewProj = multiplyMat4(proj, view);

        // 5. Draw depth-tested pass
        if (!depthTestedLines.isEmpty()) {
            bindPipelineAndPush(cmd, frameArena, lineDepthTestedPipeline, viewProj);
            bindAndDraw(cmd, lineVertexBuffer, depthTestedLines.offset(), depthTestedLines.vertexCount());
        }
        if (!depthTestedTris.isEmpty()) {
            bindPipelineAndPush(cmd, frameArena, triDepthTestedPipeline, viewProj);
            bindAndDraw(cmd, triVertexBuffer, depthTestedTris.offset(), depthTestedTris.vertexCount());
        }

        // 6. Draw always-on-top pass
        if (!onTopLines.isEmpty()) {
            bindPipelineAndPush(cmd, frameArena, lineOnTopPipeline, viewProj);
            bindAndDraw(cmd, lineVertexBuffer, onTopLines.offset(), onTopLines.vertexCount());
        }
        if (!onTopTris.isEmpty()) {
            bindPipelineAndPush(cmd, frameArena, triOnTopPipeline, viewProj);
            bindAndDraw(cmd, triVertexBuffer, onTopTris.offset(), onTopTris.vertexCount());
        }
    }

    @Override
    public void close() {
        // Destroy buffers, pipelines
    }

    private void createPipelines() {
        // All four pipelines share the same shaders but differ in:
        //   - Depth test enable/disable
        //   - Primitive topology (LINE_LIST vs TRIANGLE_LIST)
        //
        // Vertex shader: transform pos by ViewProjection push constant
        // Fragment shader: output vertex color directly (no texturing)
        //
        // Dynamic state: viewport, scissor (viewport set once, scissor = full framebuffer)
        // Blend: alpha blend (for faded depth-tested-occluded rendering, future)
        // Line width: 1.0 default, VK_EXT_line_rasterization for wider lines (optional)
    }
}
```

---

## OverlayDrawList

```java
package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates 3D overlay draw commands for one frame.
 * Commands are tessellated into raw vertices during render().
 * Cleared after each frame.
 *
 * Internally separates commands by topology (line vs tri) and depth mode
 * for efficient batched rendering (minimal pipeline switches).
 */
public class OverlayDrawList {

    private final List<OverlayCommand> commands = new ArrayList<>();
    private boolean empty = true;

    public void clear() {
        commands.clear();
        empty = true;
    }

    public boolean isEmpty() { return empty; }

    public void addLine(float[] from, float[] to, float[] color, DepthMode mode) {
        commands.add(OverlayCommand.line(from, to, color, mode));
        empty = false;
    }

    public void addWireBox(float[] min, float[] max, float[] color, DepthMode mode) {
        commands.add(OverlayCommand.wireBox(min, max, color, mode));
        empty = false;
    }

    public void addWireSphere(float[] center, float radius, float[] color, int segments, DepthMode mode) {
        commands.add(OverlayCommand.wireSphere(center, radius, color, segments, mode));
        empty = false;
    }

    public void addRay(float[] origin, float[] direction, float length, float[] color) {
        float[] end = { origin[0] + direction[0] * length,
                        origin[1] + direction[1] * length,
                        origin[2] + direction[2] * length };
        commands.add(OverlayCommand.line(origin, end, color, DepthMode.ALWAYS_ON_TOP));
        empty = false;
    }

    public void addArrow(float[] from, float[] to, float[] color, float headSize) {
        commands.add(OverlayCommand.arrow(from, to, color, headSize));
        empty = false;
    }

    public void addFrustum(float[] inverseViewProj, float[] color) {
        commands.add(OverlayCommand.frustum(inverseViewProj, color));
        empty = false;
    }

    public void addGrid(float[] center, float size, int divisions, float[] color) {
        commands.add(OverlayCommand.grid(center, size, divisions, color));
        empty = false;
    }

    public void addAxis(float[] transform4x4, float scale) {
        commands.add(OverlayCommand.axis(transform4x4, scale));
        empty = false;
    }

    public void addText(float[] worldPos, String text, float[] color) {
        commands.add(OverlayCommand.text(worldPos, text, color));
        empty = false;
    }

    public void addGizmo(Gizmo gizmo) {
        commands.add(OverlayCommand.gizmo(gizmo));
        empty = false;
    }

    /**
     * Tessellate all commands into raw vertex batches, split by topology and depth mode.
     * Called by OverlayRenderer during render().
     */
    public TessellatedResult tessellate() {
        TessellatedResult result = new TessellatedResult();
        for (OverlayCommand cmd : commands) {
            cmd.tessellateInto(result);
        }
        return result;
    }
}
```

---

## Gizmo — Interactive Transform Handle

```java
package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

/**
 * 3D transform gizmo with translate/rotate/scale modes.
 *
 * Visual representation:
 *   TRANSLATE: three axis arrows (RGB = XYZ) + three plane squares + center cube
 *   ROTATE: three circles (RGB = XYZ) + screen-space outer circle
 *   SCALE: three axis lines with cube endpoints + center cube
 *
 * Interaction:
 *   - Hit testing: ray-cast against each handle's collision geometry
 *   - Dragging: project mouse movement onto the constrained axis/plane
 *   - Snapping: optional grid/angle snapping during drag
 *
 * The gizmo does NOT modify the transform directly — it reports delta transforms
 * that the application applies. This keeps it generic (works with any transform representation).
 */
public class Gizmo {

    private GizmoMode mode = GizmoMode.TRANSLATE;
    private GizmoSpace space = GizmoSpace.WORLD;
    private float[] transform = new float[16]; // current object transform
    private float[] position = new float[3];   // extracted position
    private float handleScale = 1.0f;          // auto-scaled by distance to camera

    // Drag state
    private boolean dragging = false;
    private GizmoAxis dragAxis;
    private float[] dragStartMousePos = new float[2];
    private float[] dragStartTransform = new float[16];

    // Result of last drag operation
    private float[] deltaTranslation = new float[3];
    private float deltaRotationAngle;
    private float[] deltaRotationAxis = new float[3];
    private float[] deltaScale = new float[3];

    public void update(float[] objectTransform, GizmoMode mode,
                       float[] view, float[] proj, float[] cameraPos) {
        this.mode = mode;
        System.arraycopy(objectTransform, 0, this.transform, 0, 16);
        extractPosition(objectTransform, position);
        // Auto-scale handles based on distance to camera (constant screen size)
        float dist = distance(position, cameraPos);
        handleScale = dist * 0.15f; // 15% of camera distance
    }

    public GizmoHit testHit(float[] rayOrigin, float[] rayDir) {
        // Test ray against each handle's collision geometry
        // Priority: closest hit wins
        // Returns null if no hit
        return switch (mode) {
            case TRANSLATE -> testTranslateHandles(rayOrigin, rayDir);
            case ROTATE -> testRotateHandles(rayOrigin, rayDir);
            case SCALE -> testScaleHandles(rayOrigin, rayDir);
        };
    }

    public void beginDrag(GizmoHit hit, float mouseX, float mouseY) {
        dragging = true;
        dragAxis = hit.axis();
        dragStartMousePos[0] = mouseX;
        dragStartMousePos[1] = mouseY;
        System.arraycopy(transform, 0, dragStartTransform, 0, 16);
    }

    public void updateDrag(float mouseX, float mouseY,
                           float[] view, float[] proj, int screenW, int screenH) {
        // Project mouse delta onto the constrained axis/plane in screen space
        // Compute delta transform based on mode:
        //   TRANSLATE: delta position along axis
        //   ROTATE: delta angle around axis
        //   SCALE: delta scale factor along axis
    }

    public void endDrag() {
        dragging = false;
    }

    public boolean isDragging() { return dragging; }
    public float[] deltaTranslation() { return deltaTranslation; }
    public float deltaRotationAngle() { return deltaRotationAngle; }
    public float[] deltaRotationAxis() { return deltaRotationAxis; }
    public float[] deltaScale() { return deltaScale; }

    /** Tessellate gizmo handles into the draw list's vertex batches. */
    public void tessellateInto(TessellatedResult result) {
        switch (mode) {
            case TRANSLATE -> tessellateTranslateGizmo(result);
            case ROTATE -> tessellateRotateGizmo(result);
            case SCALE -> tessellateScaleGizmo(result);
        }
    }

    // --- Private tessellation methods ---

    private void tessellateTranslateGizmo(TessellatedResult result) {
        // X axis: red arrow from position along +X
        // Y axis: green arrow from position along +Y
        // Z axis: blue arrow from position along +Z
        // XY plane: small yellow square at position in XY plane
        // XZ plane: small cyan square at position in XZ plane
        // YZ plane: small magenta square at position in YZ plane
        // Center: white cube at position
        // All scaled by handleScale
    }

    private void tessellateRotateGizmo(TessellatedResult result) {
        // X rotation: red circle in YZ plane around position
        // Y rotation: green circle in XZ plane around position
        // Z rotation: blue circle in XY plane around position
        // Screen-space circle: white circle facing camera
        // All scaled by handleScale, segmented into line strips
    }

    private void tessellateScaleGizmo(TessellatedResult result) {
        // Similar to translate but with cube endpoints instead of arrow heads
    }
}
```

---

## Overlay Shader Source

### Vertex Shader (`overlay.vert`)

```glsl
#version 450

layout(push_constant) uniform PushConstants {
    mat4 viewProjection;
} pc;

layout(location = 0) in vec3 inPos;
layout(location = 1) in vec4 inColor;

layout(location = 0) out vec4 fragColor;

void main() {
    gl_Position = pc.viewProjection * vec4(inPos, 1.0);
    fragColor = inColor;
}
```

### Fragment Shader (`overlay.frag`)

```glsl
#version 450

layout(location = 0) in vec4 fragColor;
layout(location = 0) out vec4 outColor;

void main() {
    outColor = fragColor;
}
```

---

## DepthMode Enum

```java
package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

public enum DepthMode {
    /** Standard depth test — occluded by scene geometry. */
    DEPTH_TESTED,
    /** No depth test — always drawn on top of everything. */
    ALWAYS_ON_TOP,
    /** Depth tested, but occluded portions drawn at reduced alpha (x-ray style). */
    DEPTH_TESTED_FADED
}
```

---

## GizmoMode / GizmoSpace / GizmoAxis Enums

```java
package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

public enum GizmoMode { TRANSLATE, ROTATE, SCALE }
public enum GizmoSpace { LOCAL, WORLD }
public enum GizmoAxis { X, Y, Z, XY, XZ, YZ, XYZ, SCREEN }
```

---

## GizmoHit Record

```java
package io.github.yetyman.vulkan.foundation.ui.layers.scene3d;

/**
 * Result of a ray-cast hit test against gizmo handles.
 */
public record GizmoHit(
    GizmoAxis axis,   // which axis/plane/handle was hit
    float distance,   // ray distance to hit point
    float[] hitPoint  // world-space hit position
) {}
```


---

# Integration Example — Both Layers Together

## Application Setup

```java
// --- Asset Registry setup ---
AssetRegistry assets = new AssetRegistry();
assets.register(FontRegistry.class, new FontRegistry(device, arena));
assets.register(ThemeRegistry.class, ThemeRegistry.loadDefault());
assets.register(ClipboardAccess.class, new GLFWClipboardAccess(window));

// --- UI Context ---
UIContext uiCtx = UIContext.builder()
    .vulkan(vulkanContext)
    .assets(assets)
    .dimensions(width, height)
    .dpiScale(1.0f)
    .applicationArena(arena)
    .build();

// --- Layer creation ---
Scene3DOverlayLayer overlay = new Scene3DOverlayLayer(100);
ImGuiLayer imgui = new ImGuiLayer(1000);

// --- Composite ---
UIComposite ui = UIComposite.builder()
    .context(uiCtx)
    .layer(overlay)
    .layer(imgui)
    .build();

ui.initialize();
```

## Per-Frame Usage

```java
// Set camera for 3D overlay
overlay.setCamera(viewMatrix, projMatrix, cameraPos);

// Define overlay draws
overlay.setFrameCallback(() -> {
    // Draw world grid
    overlay.drawGrid(new float[]{0,0,0}, 100.0f, 20, new float[]{0.3f, 0.3f, 0.3f, 0.5f});

    // Draw selected object's bounding box
    if (selectedObject != null) {
        overlay.drawWireBox(selectedObject.aabbMin(), selectedObject.aabbMax(),
            new float[]{1,1,0,1});
        overlay.drawGizmo(selectedObject.transform(), GizmoMode.TRANSLATE);
    }

    // Draw debug rays
    for (DebugRay ray : debugRays) {
        overlay.drawRay(ray.origin(), ray.direction(), ray.length(),
            new float[]{1, 0.5f, 0, 1});
    }
});

// Define ImGui windows
imgui.setFrameCallback(() -> {
    CimguiFFM.igBegin(arena.allocateFrom("Debug"), MemorySegment.NULL, 0);
    CimguiFFM.igText(arena.allocateFrom("FPS: " + fps));
    CimguiFFM.igText(arena.allocateFrom("Draw calls: " + drawCalls));
    CimguiFFM.igText(arena.allocateFrom("Triangles: " + triangleCount));

    if (selectedObject != null) {
        CimguiFFM.igSeparator();
        CimguiFFM.igText(arena.allocateFrom("Selected: " + selectedObject.name()));
        // Position editor
        float[] pos = selectedObject.position();
        MemorySegment posPtr = arena.allocateFrom(ValueLayout.JAVA_FLOAT, pos);
        if (CimguiFFM.igDragFloat3(arena.allocateFrom("Position"), posPtr, 0.1f, -1000, 1000,
                arena.allocateFrom("%.2f"), 0) != 0) {
            selectedObject.setPosition(posPtr.get(ValueLayout.JAVA_FLOAT, 0),
                posPtr.get(ValueLayout.JAVA_FLOAT, 4),
                posPtr.get(ValueLayout.JAVA_FLOAT, 8));
        }
    }
    CimguiFFM.igEnd();
});

// --- Frame update and render ---
UIFrameContext frame = new UIFrameContext(frameArena, deltaTime, frameNumber, uiCtx);
ui.update(frame);
ui.render(commandBuffer, frameArena);
```

## Input Dispatch Integration

```java
// In the application's input callback (GLFW or similar):
glfwSetCursorPosCallback(window, (w, x, y) -> {
    UIInputEvent event = UIInputEvent.mouseMove((float)x, (float)y, dx, dy);
    ui.dispatchInput(event);
});

glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
    UIInputEvent event = action == GLFW_PRESS
        ? UIInputEvent.mouseButtonPress(button, mouseX, mouseY, mods)
        : UIInputEvent.mouseButtonRelease(button, mouseX, mouseY, mods);
    ui.dispatchInput(event);
});

glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
    UIInputEvent event = switch (action) {
        case GLFW_PRESS -> UIInputEvent.keyPress(key, scancode, mods);
        case GLFW_RELEASE -> UIInputEvent.keyRelease(key, scancode, mods);
        case GLFW_REPEAT -> UIInputEvent.keyRepeat(key, scancode, mods);
        default -> null;
    };
    if (event != null) ui.dispatchInput(event);
});

glfwSetCharCallback(window, (w, codepoint) -> {
    ui.dispatchInput(UIInputEvent.charInput(codepoint));
});

glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
    ui.dispatchInput(UIInputEvent.scroll(mouseX, mouseY, (float)xoffset, (float)yoffset));
});
```

## Input Flow for Gizmo + ImGui

With the capture/bubble model, a typical mouse click flows like this:

1. **Capture phase** (highest order first = ImGui first):
   - ImGui layer: no-ops during capture (only processes during bubble)
   - Scene3D overlay: ray-casts against gizmo, annotates `"gizmoHit"` in context

2. **Bubble phase** (lowest order first = Scene3D overlay first):
   - Scene3D overlay: reads `"gizmoHit"` from context, begins drag if hit, stops propagation
   - ImGui layer: never sees the event (propagation stopped)

If the click is NOT on a gizmo:

1. **Capture phase**: Scene3D annotates world position but no gizmo hit
2. **Bubble phase**:
   - Scene3D overlay: no gizmo hit, passes through
   - ImGui layer: if mouse is over an ImGui window (`WantCaptureMouse`), consumes it

---

# Implementation Order

## Phase 1: Scaffolding (vulkan-ffm-foundation)

1. Create `vulkan-ffm-foundation` module with Maven POM
2. Implement core interfaces:
   - `UILayer`
   - `UIComposite`
   - `UIContext` + `UIFrameContext`
   - `AssetRegistry` + `AssetType`
3. Implement input system:
   - `UIInputEvent` + factory methods
   - `InputPhase`, `InputEventType`
   - `PropagationState`
   - `UIInputDispatcher`
4. Stub `FontRegistry`, `ThemeRegistry`, `ClipboardAccess`, `CursorManager`

## Phase 2: Scene3D Overlay Layer

5. Implement `OverlayDrawList` + `OverlayCommand` + tessellation
6. Implement `OverlayRenderer` (pipelines, buffer upload, draw commands)
7. Write overlay shaders (`overlay.vert`, `overlay.frag`)
8. Implement `Scene3DOverlayLayer` (draw API + UILayer contract)
9. Implement `Gizmo` (tessellation + hit testing + drag interaction)
10. Integration test: overlay draws visible in sample-app

## Phase 3: ImGui Layer

11. Set up `imgui-bindings` module (build cimgui, jextract, native loader)
12. Add `imgui-bindings` dependency to `vulkan-ffm-foundation`
13. Implement `ImGuiRenderer` (font atlas, pipeline, buffer upload, draw loop)
14. Write ImGui shaders (`imgui.vert`, `imgui.frag`)
15. Implement `ImGuiInputBridge` (key/mouse/char mapping)
16. Implement `ImGuiLayer` (context lifecycle, frame callback, input capture)
17. Integration test: ImGui windows visible + interactive in sample-app

## Phase 4: Polish

18. Full input dispatch integration in sample-app (GLFW callbacks -> UIComposite)
19. Both layers working together with correct input priority
20. Performance validation: ensure ImGui + overlay add < 0.5ms per frame
21. Documentation: usage examples in sample-app README

---

# Dependencies Summary

| Module | Depends On | External Deps |
|--------|-----------|---------------|
| `imgui-bindings` | (none) | cimgui native library (bundled) |
| `vulkan-ffm-foundation` | `vulkan-core`, `helpers-core`, `imgui-bindings` | None |
| `sample-app` | `vulkan-ffm-foundation`, `glfw-bindings`, `jgltf-model` | GLFW native (bundled) |

Note: `imgui-bindings` is an optional dependency of `vulkan-ffm-foundation`. If ImGui support is not needed, the dependency can be marked `<optional>true</optional>` or the ImGui layer package excluded. The core scaffolding (UILayer, UIComposite, input system) has no dependency on imgui-bindings.
