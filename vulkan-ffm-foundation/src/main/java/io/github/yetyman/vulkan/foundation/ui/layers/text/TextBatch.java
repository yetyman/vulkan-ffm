package io.github.yetyman.vulkan.foundation.ui.layers.text;

import io.github.yetyman.vulkan.foundation.ui.assets.FontRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates drawText() calls for a single frame into a flat list of GlyphInstance quads,
 * resolving glyph metrics/atlas UVs via FontRegistry as text is added. Cleared at the start
 * of each frame by GPUDrivenTextLayer.update().
 *
 * Text layout here is simple left-to-right, single-line, baseline-anchored placement using
 * FontRegistry's advance width and kerning. No line wrapping, bidi, or complex shaping -
 * that is out of scope (see plans/UI_SYSTEM_CHECKLIST.md deferred section).
 */
public class TextBatch {
    private final FontRegistry fonts;
    private final List<GlyphInstance> instances = new ArrayList<>();

    public TextBatch(FontRegistry fonts) {
        this.fonts = fonts;
    }

    /** Clears all accumulated glyph instances. Called once per frame before drawText(). */
    public void clear() {
        instances.clear();
    }

    /** @return the accumulated glyph instances for this frame, ready for GPU upload. */
    public List<GlyphInstance> instances() {
        return instances;
    }

    /**
     * Appends the glyph quads for a single line of text, rasterizing glyphs on demand.
     *
     * @param fontId    font previously registered via FontRegistry.loadFont()
     * @param text      text to render (single line, left-to-right)
     * @param x         baseline start X in screen pixels
     * @param y         baseline Y in screen pixels (stb_truetype convention: y increases downward,
     *                  glyph bitmaps are positioned above/at the baseline via their bearing offsets)
     * @param pixelSize font size in pixels
     * @param r         red   [0,1]
     * @param g         green [0,1]
     * @param b         blue  [0,1]
     * @param a         alpha [0,1]
     * @return the total advance width of the drawn text, in pixels
     */
    public float drawText(String fontId, String text, float x, float y, float pixelSize,
                           float r, float g, float b, float a) {
        float cursorX = x;
        int prevCodepoint = -1;

        for (int i = 0; i < text.length(); i++) {
            int codepoint = text.charAt(i);

            if (prevCodepoint >= 0) {
                cursorX += fonts.getKerningAdvance(fontId, prevCodepoint, codepoint, pixelSize);
            }

            FontRegistry.GlyphInfo glyph = fonts.getOrRasterizeGlyph(fontId, codepoint, pixelSize);

            if (glyph.atlasWidth() > 0 && glyph.atlasHeight() > 0) {
                float glyphMinX = cursorX + glyph.bearingX();
                float glyphMinY = y + glyph.bearingY();
                float glyphMaxX = glyphMinX + glyph.atlasWidth();
                float glyphMaxY = glyphMinY + glyph.atlasHeight();

                instances.add(new GlyphInstance(
                    glyphMinX, glyphMinY, glyphMaxX, glyphMaxY,
                    glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1(),
                    r, g, b, a
                ));
            }

            cursorX += glyph.advanceWidth();
            prevCodepoint = codepoint;
        }

        return cursorX - x;
    }

    /** @return true if no glyphs have been accumulated this frame. */
    public boolean isEmpty() {
        return instances.isEmpty();
    }
}
