package io.github.yetyman.vulkan.foundation.ui.assets;

import io.github.yetyman.vulkan.VkDevice;
import io.github.yetyman.vulkan.VkImage;
import io.github.yetyman.vulkan.VkImageView;
import io.github.yetyman.vulkan.VkQueue;
import io.github.yetyman.vulkan.VkCommandPool;
import io.github.yetyman.vulkan.commands.TransientCommandBuffer;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.enums.VkFormat;
import io.github.yetyman.vulkan.enums.VkImageAspectFlagBits;
import io.github.yetyman.vulkan.enums.VkImageLayout;
import io.github.yetyman.vulkan.enums.VkImageUsageFlagBits;
import io.github.yetyman.vulkan.enums.VkPipelineStageFlagBits;
import io.github.yetyman.vulkan.enums.VkAccessFlagBits;

import io.github.yetyman.stbtruetype.StbTrueTypeLoader;
import io.github.yetyman.stbtruetype.generated.StbTrueTypeFFM;
import io.github.yetyman.stbtruetype.generated.stbtt_fontinfo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Font atlas and glyph metric management, shared across UILayers that render text
 * (GPUDrivenTextLayer, ImGuiLayer's font backend, Canvas2DLayer, etc).
 *
 * Rasterization is delegated to stb_truetype (via the stb-truetype-bindings FFM module).
 * Owns one or more rectangle-packed texture atlases per registered font. Glyphs are inserted
 * on demand (lazy rasterization) and packed via a shelf (row) allocator. Callers query glyph
 * UV rects and advance metrics by (fontId, codepoint, pixelSize).
 *
 * GPU-side atlas upload copies dirty CPU-side bitmap regions into a single-channel (R8_UNORM
 * alpha coverage) VkImage using a MAPPED staging buffer plus a one-shot TransientCommandBuffer.
 *
 * Thread safety: owns an internal Arena.ofShared() so rasterization/upload calls can be made
 * from any thread (e.g. a render thread distinct from the one that constructed this registry).
 * facesByFont/atlasesByFont are plain HashMaps, not thread-safe for concurrent mutation - callers
 * should confine loadFont()/createAtlas() calls to a single thread (typically app init) even
 * though getOrRasterizeGlyph() is safe to call from a render thread.
 *
 * Not implemented (explicitly deferred, tracked in plans/UI_SYSTEM_CHECKLIST.md):
 *   - SDF/MSDF glyph generation for scale-independent crisp text at arbitrary zoom
 *   - Text-on-curve / arbitrary path text layout
 *   - Complex script shaping (ligatures, bidi) - stb_truetype has no shaping engine
 */
public class FontRegistry implements AutoCloseable {
    static {
        StbTrueTypeLoader.load();
    }

    public static final AssetType<FontRegistry> TYPE = AssetType.of(FontRegistry.class);

    /** Extracts from registry. Call once at layer startup and cache. */
    public static FontRegistry from(AssetRegistry registry) {
        return registry.get(TYPE);
    }

    private final VkDevice device;
    private final VkQueue transferQueue;
    private final VkCommandPool transferCommandPool;
    private final Arena arena;
    private final Map<String, FontFace> facesByFont = new HashMap<>();
    private final Map<String, FontAtlas> atlasesByFont = new HashMap<>();

    /**
     * @param device              device that owns GPU atlas resources
     * @param transferQueue       queue used to submit staging-buffer-to-image atlas uploads
     * @param transferCommandPool command pool (on transferQueue's family) used to allocate
     *                            the transient command buffers for atlas uploads
     *
     * Owns an internal Arena.ofShared() for all of its native allocations (font file bytes,
     * stbtt_fontinfo structs, atlas VkImage/VkImageView, and per-call scratch pointers), closed
     * in close(). A shared arena is required because glyph rasterization and atlas uploads are
     * typically invoked from a rendering thread that is not the thread that constructed this
     * registry (e.g. via a UILayer's render() call from a GraphicsLoop worker thread) - a
     * confined arena tied to the constructing thread would throw WrongThreadException.
     */
    public FontRegistry(VkDevice device, VkQueue transferQueue, VkCommandPool transferCommandPool) {
        this.device = device;
        this.transferQueue = transferQueue;
        this.transferCommandPool = transferCommandPool;
        this.arena = Arena.ofShared();
    }

    /** @return the device this registry allocates GPU atlas resources on. */
    public VkDevice device() { return device; }

    // -------------------------------------------------------------------------
    // Font loading (stb_truetype)
    // -------------------------------------------------------------------------

    /**
     * Loads a TrueType/OpenType font from raw file bytes and registers it under fontId,
     * creating a backing atlas of the given dimensions for its rasterized glyphs.
     *
     * @param fontId      caller-chosen identifier used to look up this font's glyphs/atlas
     * @param fontBytes   raw .ttf/.otf file contents
     * @param atlasWidth  atlas texture width in pixels
     * @param atlasHeight atlas texture height in pixels
     * @return the created FontAtlas (also retrievable later via getAtlas(fontId))
     */
    public FontAtlas loadFont(String fontId, byte[] fontBytes, int atlasWidth, int atlasHeight) {
        if (facesByFont.containsKey(fontId)) {
            throw new IllegalStateException("Font already registered for fontId: " + fontId);
        }

        MemorySegment fontData = arena.allocate(fontBytes.length);
        MemorySegment.copy(fontBytes, 0, fontData, ValueLayout.JAVA_BYTE, 0, fontBytes.length);

        MemorySegment fontInfo = stbtt_fontinfo.allocate(arena);
        int offset = StbTrueTypeFFM.stbtt_GetFontOffsetForIndex(fontData, 0);
        if (offset < 0) {
            throw new IllegalArgumentException("No valid font found at index 0 for fontId: " + fontId);
        }
        int ok = StbTrueTypeFFM.stbtt_InitFont(fontInfo, fontData, offset);
        if (ok == 0) {
            throw new IllegalArgumentException("stbtt_InitFont failed for fontId: " + fontId);
        }

        MemorySegment ascentPtr = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment descentPtr = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment lineGapPtr = arena.allocate(ValueLayout.JAVA_INT);
        StbTrueTypeFFM.stbtt_GetFontVMetrics(fontInfo, ascentPtr, descentPtr, lineGapPtr);

        FontFace face = new FontFace(
            fontData, fontInfo,
            ascentPtr.get(ValueLayout.JAVA_INT, 0),
            descentPtr.get(ValueLayout.JAVA_INT, 0),
            lineGapPtr.get(ValueLayout.JAVA_INT, 0)
        );
        facesByFont.put(fontId, face);

        return createAtlas(fontId, atlasWidth, atlasHeight);
    }

    /** @return the loaded font face for fontId, or null if loadFont was never called for it. */
    public FontFace getFace(String fontId) { return facesByFont.get(fontId); }

    /**
     * Registers a font family under fontId with the given atlas dimensions, creating a new atlas.
     * Subsequent glyph requests for this fontId pack into this atlas.
     * Normally called implicitly by loadFont(); exposed directly for callers that manage
     * rasterization themselves (e.g. pre-baked atlases) and only need the packer/GPU-upload path.
     */
    public FontAtlas createAtlas(String fontId, int atlasWidth, int atlasHeight) {
        if (atlasesByFont.containsKey(fontId)) {
            throw new IllegalStateException("Atlas already registered for fontId: " + fontId);
        }
        FontAtlas atlas = new FontAtlas(fontId, atlasWidth, atlasHeight);
        atlasesByFont.put(fontId, atlas);
        return atlas;
    }

    /** @return the atlas registered for fontId, or null if createAtlas was never called for it. */
    public FontAtlas getAtlas(String fontId) { return atlasesByFont.get(fontId); }

    /**
     * Rasterizes (if not already cached) and returns the glyph for (fontId, codepoint, pixelSize),
     * packing it into fontId's atlas. Requires loadFont(fontId, ...) to have been called first.
     */
    public FontRegistry.GlyphInfo getOrRasterizeGlyph(String fontId, int codepoint, float pixelSize) {
        FontFace face = facesByFont.get(fontId);
        if (face == null) throw new IllegalStateException("No font loaded for fontId: " + fontId);
        FontAtlas atlas = atlasesByFont.get(fontId);
        if (atlas == null) throw new IllegalStateException("No atlas registered for fontId: " + fontId);

        GlyphInfo cached = atlas.getGlyph(codepoint, pixelSize);
        if (cached != null) return cached;

        float scale = StbTrueTypeFFM.stbtt_ScaleForPixelHeight(face.fontInfo, pixelSize);

        MemorySegment advPtr = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment lsbPtr = arena.allocate(ValueLayout.JAVA_INT);
        StbTrueTypeFFM.stbtt_GetCodepointHMetrics(face.fontInfo, codepoint, advPtr, lsbPtr);
        float advanceWidth = advPtr.get(ValueLayout.JAVA_INT, 0) * scale;

        MemorySegment wPtr = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment hPtr = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment xoffPtr = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment yoffPtr = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment bitmapPtr = StbTrueTypeFFM.stbtt_GetCodepointBitmap(
            face.fontInfo, scale, scale, codepoint, wPtr, hPtr, xoffPtr, yoffPtr);

        int bw = wPtr.get(ValueLayout.JAVA_INT, 0);
        int bh = hPtr.get(ValueLayout.JAVA_INT, 0);
        int bearingX = xoffPtr.get(ValueLayout.JAVA_INT, 0);
        int bearingY = yoffPtr.get(ValueLayout.JAVA_INT, 0);

        byte[] bitmap;
        if (bw > 0 && bh > 0 && !bitmapPtr.equals(MemorySegment.NULL)) {
            MemorySegment bitmapView = bitmapPtr.reinterpret((long) bw * bh);
            bitmap = new byte[bw * bh];
            MemorySegment.copy(bitmapView, ValueLayout.JAVA_BYTE, 0, bitmap, 0, bitmap.length);
            StbTrueTypeFFM.stbtt_FreeBitmap(bitmapPtr, MemorySegment.NULL);
        } else {
            // Whitespace or otherwise empty glyph (e.g. ' ') - zero-size bitmap is valid.
            bw = 0;
            bh = 0;
            bitmap = new byte[0];
        }

        return atlas.insertGlyph(codepoint, pixelSize, bw, bh, bitmap, advanceWidth, bearingX, bearingY);
    }

    /**
     * @return the kerning advance (in pixels, already scaled) to apply between two consecutive
     * codepoints at the given pixel size. Zero if the font has no kerning table entry for the pair.
     */
    public float getKerningAdvance(String fontId, int codepointA, int codepointB, float pixelSize) {
        FontFace face = facesByFont.get(fontId);
        if (face == null) throw new IllegalStateException("No font loaded for fontId: " + fontId);
        float scale = StbTrueTypeFFM.stbtt_ScaleForPixelHeight(face.fontInfo, pixelSize);
        return StbTrueTypeFFM.stbtt_GetCodepointKernAdvance(face.fontInfo, codepointA, codepointB) * scale;
    }

    @Override
    public void close() {
        for (FontAtlas atlas : atlasesByFont.values()) {
            atlas.close();
        }
        atlasesByFont.clear();
        facesByFont.clear();
        arena.close();
    }

    /**
     * A loaded font file plus its stbtt_fontinfo handle and unscaled vertical metrics.
     * Vertical metrics are in font design units; multiply by the scale from
     * stbtt_ScaleForPixelHeight(fontInfo, pixelSize) to get pixel-space values.
     */
    public static class FontFace {
        private final MemorySegment fontData;
        private final MemorySegment fontInfo;
        private final int ascent;
        private final int descent;
        private final int lineGap;

        private FontFace(MemorySegment fontData, MemorySegment fontInfo, int ascent, int descent, int lineGap) {
            this.fontData = fontData;
            this.fontInfo = fontInfo;
            this.ascent = ascent;
            this.descent = descent;
            this.lineGap = lineGap;
        }

        /** @return raw font file bytes backing this face - kept alive for the lifetime of fontInfo. */
        public MemorySegment fontData() { return fontData; }

        /** @return the stbtt_fontinfo struct handle for direct StbTrueTypeFFM calls. */
        public MemorySegment fontInfo() { return fontInfo; }

        /** @return unscaled ascent in font design units. */
        public int ascent() { return ascent; }

        /** @return unscaled descent in font design units (typically negative). */
        public int descent() { return descent; }

        /** @return unscaled line gap in font design units. */
        public int lineGap() { return lineGap; }
    }

    /**
     * A single rectangle-packed glyph atlas texture, plus the CPU-side glyph metric cache.
     * Packing uses a shelf (row) allocator: glyphs are placed left-to-right in the current
     * row, advancing to a new row when the current row is full.
     */
    public class FontAtlas implements AutoCloseable {
        private final String fontId;
        private final int width;
        private final int height;
        private final Map<Long, GlyphInfo> glyphs = new HashMap<>();

        // CPU-side staging bitmap mirroring the full atlas - single-channel alpha coverage.
        private final byte[] cpuPixels;

        // Shelf packer state
        private final List<Shelf> shelves = new ArrayList<>();
        private int cursorY = 0;

        // Gap reserved between adjacent packed glyphs so linear texture filtering does not
        // sample texels belonging to a neighboring glyph at UV edges (bilinear bleed).
        private static final int GLYPH_PADDING = 1;

        // GPU resources - created lazily on first flush()
        private VkImage atlasImage;
        private VkImageView atlasView;
        private boolean everUploaded = false;
        private boolean dirty = false;
        private int dirtyMinX = Integer.MAX_VALUE;
        private int dirtyMinY = Integer.MAX_VALUE;
        private int dirtyMaxX = 0;
        private int dirtyMaxY = 0;

        private FontAtlas(String fontId, int width, int height) {
            this.fontId = fontId;
            this.width = width;
            this.height = height;
            this.cpuPixels = new byte[width * height];
        }

        public String fontId() { return fontId; }
        public int width() { return width; }
        public int height() { return height; }

        /** @return the packed GPU atlas image, or null if flush() has not yet been called. */
        public VkImage image() { return atlasImage; }

        /** @return the packed GPU atlas image view, or null if flush() has not yet been called. */
        public VkImageView imageView() { return atlasView; }

        /**
         * Looks up cached glyph metrics/UVs for (codepoint, pixelSize). Returns null on cache miss;
         * callers should use FontRegistry.getOrRasterizeGlyph() instead of calling this directly
         * unless they are managing rasterization themselves.
         */
        public GlyphInfo getGlyph(int codepoint, float pixelSize) {
            return glyphs.get(glyphKey(codepoint, pixelSize));
        }

        /**
         * Packs a rasterized glyph bitmap into the atlas and caches its metrics.
         * bitmap is a single-channel (alpha coverage) buffer of size bitmapWidth * bitmapHeight,
         * row-major, matching stb_truetype's native output format. A zero-size bitmap (empty
         * width/height, e.g. whitespace glyphs) is packed as a zero-area rect with no pixel copy.
         *
         * @return the packed GlyphInfo with atlas pixel rect and normalized UVs
         */
        public GlyphInfo insertGlyph(int codepoint, float pixelSize, int bitmapWidth, int bitmapHeight,
                                      byte[] bitmap, float advanceWidth, float bearingX, float bearingY) {
            int px = 0;
            int py = 0;
            if (bitmapWidth > 0 && bitmapHeight > 0) {
                int[] rect = pack(bitmapWidth, bitmapHeight);
                px = rect[0];
                py = rect[1];

                for (int row = 0; row < bitmapHeight; row++) {
                    int srcOffset = row * bitmapWidth;
                    int dstOffset = (py + row) * width + px;
                    System.arraycopy(bitmap, srcOffset, cpuPixels, dstOffset, bitmapWidth);
                }
                markDirty(px, py, bitmapWidth, bitmapHeight);
            }

            GlyphInfo info = new GlyphInfo(
                px, py, bitmapWidth, bitmapHeight,
                px / (float) width, py / (float) height,
                (px + bitmapWidth) / (float) width, (py + bitmapHeight) / (float) height,
                advanceWidth, bearingX, bearingY
            );
            glyphs.put(glyphKey(codepoint, pixelSize), info);
            return info;
        }

        /** @return true if glyphs have been packed since the last flush(). */
        public boolean isDirty() { return dirty; }

        /**
         * Uploads dirty atlas regions to the GPU texture, creating the VkImage/VkImageView
         * on first call. Copies the full backing CPU bitmap through a MAPPED staging buffer
         * and a one-shot TransientCommandBuffer; the copy region is the full image on first
         * upload (to establish defined contents everywhere) and the tracked dirty rect's
         * containing rows thereafter.
         */
        public void flush() {
            if (!dirty && everUploaded) return;

            if (atlasImage == null) {
                atlasImage = VkImage.builder()
                    .device(device)
                    .dimensions(width, height, 1)
                    .format(VkFormat.VK_FORMAT_R8_UNORM.value())
                    .usage(VkImageUsageFlagBits.VK_IMAGE_USAGE_SAMPLED_BIT.value()
                        | VkImageUsageFlagBits.VK_IMAGE_USAGE_TRANSFER_DST_BIT.value())
                    .build(arena);
                atlasView = VkImageView.builder()
                    .device(device)
                    .image(atlasImage.handle())
                    .format(VkFormat.VK_FORMAT_R8_UNORM.value())
                    .aspectMask(VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value())
                    .build(arena);
            }

            // Stage the full CPU bitmap (simplest correct approach; the atlas is small relative
            // to typical GPU texture budgets and glyph insertion is not a hot per-frame path).
            IBuffer staging = BufferFactory.create(
                MemoryStrategy.MAPPED, null, cpuPixels.length, BufferUsage.TRANSFER, device, transferQueue);
            try {
                staging.write(ByteBuffer.wrap(cpuPixels), 0, transferQueue);

                try (TransientCommandBuffer cmd = TransientCommandBuffer.begin(transferCommandPool, transferQueue, arena)) {
                    cmd.transitionImageLayout(
                        atlasImage.handle(),
                        VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED.value(),
                        VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value(),
                        0,
                        VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT.value());

                    cmd.copyBufferToImage(staging.handle(), atlasImage.handle(), width, height);

                    cmd.transitionImageLayout(
                        atlasImage.handle(),
                        VkImageLayout.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.value(),
                        VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value(),
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value(),
                        VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT.value(),
                        VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value());

                    cmd.submitAndWait();
                }

                atlasImage.updateState(
                    VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL.value(),
                    VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value(),
                    VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value(),
                    io.github.yetyman.vulkan.VkQueueFamily.VK_QUEUE_FAMILY_IGNORED);
            } finally {
                staging.close();
            }

            everUploaded = true;
            dirty = false;
            dirtyMinX = Integer.MAX_VALUE;
            dirtyMinY = Integer.MAX_VALUE;
            dirtyMaxX = 0;
            dirtyMaxY = 0;
        }

        @Override
        public void close() {
            if (atlasView != null) atlasView.close();
            if (atlasImage != null) atlasImage.close();
            glyphs.clear();
            shelves.clear();
        }

        private void markDirty(int x, int y, int w, int h) {
            dirty = true;
            dirtyMinX = Math.min(dirtyMinX, x);
            dirtyMinY = Math.min(dirtyMinY, y);
            dirtyMaxX = Math.max(dirtyMaxX, x + w);
            dirtyMaxY = Math.max(dirtyMaxY, y + h);
        }

        /** Shelf (row) packer: returns [x, y] top-left of the packed rect, or throws if the atlas is full.
         *  Reserves GLYPH_PADDING extra pixels on the right and bottom of the footprint so adjacent
         *  glyphs never share a texel edge, without affecting the returned (unpadded) glyph origin. */
        private int[] pack(int w, int h) {
            int paddedW = w + GLYPH_PADDING;
            int paddedH = h + GLYPH_PADDING;
            for (Shelf shelf : shelves) {
                if (shelf.height >= paddedH && shelf.cursorX + paddedW <= width) {
                    int x = shelf.cursorX;
                    shelf.cursorX += paddedW;
                    return new int[]{x, shelf.y};
                }
            }
            // Start a new shelf
            if (cursorY + paddedH > height) {
                throw new IllegalStateException(
                    "Font atlas '" + fontId + "' is full: cannot pack " + w + "x" + h + " glyph");
            }
            Shelf shelf = new Shelf(cursorY, paddedH);
            shelf.cursorX = paddedW;
            shelves.add(shelf);
            cursorY += paddedH;
            return new int[]{0, shelf.y};
        }

        private long glyphKey(int codepoint, float pixelSize) {
            // Pack codepoint (21 bits used by Unicode) with quantized pixel size into one long key.
            int quantizedSize = Math.round(pixelSize * 4.0f); // quarter-pixel precision
            return ((long) codepoint << 32) | (quantizedSize & 0xFFFFFFFFL);
        }

        private class Shelf {
            final int y;
            final int height;
            int cursorX;

            Shelf(int y, int height) {
                this.y = y;
                this.height = height;
                this.cursorX = 0;
            }
        }
    }

    /** Packed glyph metrics and atlas UV rect for a single (codepoint, pixelSize) glyph. */
    public record GlyphInfo(
        int atlasX, int atlasY, int atlasWidth, int atlasHeight,
        float u0, float v0, float u1, float v1,
        float advanceWidth, float bearingX, float bearingY
    ) {}
}
