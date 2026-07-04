# stb_truetype FFM Bindings

Auto-generated FFM bindings for [stb_truetype.h](https://github.com/nothings/stb/blob/master/stb_truetype.h)
(nothings/stb) - a small, single-header TrueType/OpenType glyph rasterizer.

Used by `vulkan-ffm-foundation`'s `FontRegistry` to fill in glyph metrics and
rasterize glyph bitmaps into its CPU-side atlas allocator before uploading to
a GPU-resident font atlas image.

## Setup

1. **Build the stb_truetype shared library**:
   ```bash
   build-stb-truetype.bat
   ```
   (Requires CMake and curl.) This downloads the upstream single-header
   `stb_truetype.h`, generates a one-line implementation shim that defines
   `STB_TRUETYPE_IMPLEMENTATION`, and compiles it into `stb-truetype.dll`.

2. **Generate bindings**:
   ```bash
   generate-stb-truetype-bindings.bat
   ```

3. **Build**:
   ```bash
   mvn clean compile
   ```

## Usage

```java
import io.github.yetyman.stbtruetype.generated.*;
import java.lang.foreign.*;

try (Arena arena = Arena.ofConfined()) {
    byte[] fontBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("font.ttf"));
    MemorySegment fontData = arena.allocateFrom(ValueLayout.JAVA_BYTE, fontBytes);

    MemorySegment fontInfo = stbtt_fontinfo.allocate(arena);
    int ok = StbTrueTypeFFM.stbtt_InitFont(fontInfo, fontData, 0);
    if (ok == 0) throw new RuntimeException("Failed to init font");

    float scale = StbTrueTypeFFM.stbtt_ScaleForPixelHeight(fontInfo, 32.0f);

    MemorySegment wPtr = arena.allocate(ValueLayout.JAVA_INT);
    MemorySegment hPtr = arena.allocate(ValueLayout.JAVA_INT);
    MemorySegment xoffPtr = arena.allocate(ValueLayout.JAVA_INT);
    MemorySegment yoffPtr = arena.allocate(ValueLayout.JAVA_INT);

    MemorySegment bitmap = StbTrueTypeFFM.stbtt_GetCodepointBitmap(
        fontInfo, scale, scale, 'A', wPtr, hPtr, xoffPtr, yoffPtr);
    int w = wPtr.get(ValueLayout.JAVA_INT, 0);
    int h = hPtr.get(ValueLayout.JAVA_INT, 0);
    // bitmap is a w*h single-channel (8-bit alpha coverage) buffer, row-major.

    StbTrueTypeFFM.stbtt_FreeBitmap(bitmap, MemorySegment.NULL);
}
```

## Features Exposed

- Font parsing / init (`stbtt_InitFont`, `stbtt_GetFontOffsetForIndex`, `stbtt_GetNumberOfFonts`)
- Codepoint/glyph index lookup (`stbtt_FindGlyphIndex`)
- Vertical metrics: ascent, descent, line gap (`stbtt_GetFontVMetrics`)
- Horizontal metrics: advance width, left side bearing (`stbtt_GetCodepointHMetrics`, `stbtt_GetGlyphHMetrics`)
- Kerning (`stbtt_GetCodepointKernAdvance`, `stbtt_GetGlyphKernAdvance`)
- Single-glyph bitmap rasterization, including subpixel-shifted variants
- Whole-font atlas packing helpers (`stbtt_PackBegin`/`stbtt_PackFontRange`/`stbtt_PackEnd`) built
  on stb_truetype's integrated rect-packer

## Notes

- This is deliberately a small rasterizer: no hinting, no variable font axes,
  no complex script shaping (ligatures, bidi). For those, a FreeType or
  HarfBuzz bindings module would be a separate, heavier addition.
- No text-on-path / curve-following layout is provided here - that is a
  layout-level concern built on top of these glyph metrics and bitmaps, not
  something stb_truetype itself does.
