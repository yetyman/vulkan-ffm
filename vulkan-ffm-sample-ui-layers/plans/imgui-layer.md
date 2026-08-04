# ImGui Layer Plan

## Status: Deferred

Dear ImGui integration via cimgui FFM bindings for debug/dev tooling. This is a substantial native dependency (cimgui + Dear ImGui build) and is scoped as a separate future effort.

---

## Strategy: Wrap Dear ImGui via cimgui

Dear ImGui is a C++ immediate-mode UI library. `cimgui` is the official C binding wrapper that exposes Dear ImGui's API as a flat C ABI — suitable for jextract FFM binding generation.

### Why cimgui and not a pure-Java reimplementation

- ImGui is battle-tested, optimized, and has massive community/plugin support
- The rendering backend (vertex buffer upload + textured draw) is trivial in Vulkan
- A pure-Java reimplementation would be 50k+ lines of layout/widget code for marginal benefit
- The FFM call overhead is negligible — ImGui's hot path is CPU layout, not FFM calls

---

## Required: imgui-bindings Module

```
imgui-bindings/
  pom.xml
  generate-imgui-bindings.bat
  cimgui_wrapper.h
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
          ImVec2.java, ImVec4.java  -- struct accessors
          ImFontAtlas.java          -- struct accessor
      resources/
        natives/
          imgui.dll                  -- Windows (built from cimgui)
          libcimgui.so              -- Linux
          libcimgui.dylib           -- macOS
```

### Native Library Build

cimgui must be compiled from source with the Vulkan backend DISABLED (we provide our own Vulkan rendering backend). Only the core ImGui + cimgui C wrapper is needed.

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

---

## ImGuiLayer Design

### Responsibilities

- ImGui context lifecycle (create/destroy)
- Font atlas upload to GPU texture
- Input bridging from UIInputEvent to ImGui IO struct
- Per-frame vertex/index buffer upload from ImDrawData
- Pipeline: textured alpha-blended triangles with dynamic scissor
- Push constants: orthographic projection matrix (mat4, 64 bytes)
- Descriptor set: single combined image sampler (font atlas)

### Key Classes

| Class | Role |
|-------|------|
| `ImGuiLayer` | UILayer implementation — setFrameCallback for user-defined ImGui commands |
| `ImGuiRenderer` | Vulkan backend: font atlas texture, pipeline, buffer upload, draw |
| `ImGuiInputBridge` | Maps UIInputEvent to ImGui IO state (mouse pos, buttons, keys, scroll) |

### Input Handling

During BUBBLE phase, bridge UIInputEvent data into ImGui IO struct, then check `WantCaptureMouse` / `WantCaptureKeyboard` to decide whether to consume the event.

### Vertex Format

ImDrawVert: `{ vec2 pos; vec2 uv; uint col; }` = 20 bytes (packed ABGR color).

### Shaders

Vertex shader: transforms pos by orthographic projection push constant, unpacks ABGR vertex color.
Fragment shader: samples font atlas texture, multiplies by vertex color.

### Buffer Strategy

Mapped buffer, re-uploaded every frame (ImGui vertices are transient). Could upgrade to ring buffer for multi-frame-in-flight.

---

## Prerequisites Before Implementation

1. Build cimgui native library for all target platforms
2. Generate imgui-bindings module via jextract
3. Verify bindings compile and native lib loads
4. Then implement ImGuiLayer/ImGuiRenderer/ImGuiInputBridge
