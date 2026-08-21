package io.github.yetyman.vulkan.layers.text;

import io.github.yetyman.vulkan.assets.FontRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates drawText() calls for a single frame into a flat list of GlyphInstance quads,
 * resolving glyph metrics/atlas UVs via FontRegistry as text is added. Cleared at the start
 * of each frame by GPUDrivenTextLayer.update().
 *
 * <p>Internally stores glyph data in a flat float array (12 floats per glyph) to avoid
 * per-glyph object allocation. The legacy {@link #instances()} accessor converts to records
 * for API compatibility.
 */
public class TextBatch {
    private static final int FLOATS_PER_GLYPH = 12; // posMin(2) + posMax(2) + uvMin(2) + uvMax(2) + color(4)
    private static final int INITIAL_CAPACITY = 512;

    private final FontRegistry fonts;
    private float[] data;
    private int glyphCount;

    public TextBatch(FontRegistry fonts) {
        this.fonts = fonts;
        this.data = new float[INITIAL_CAPACITY * FLOATS_PER_GLYPH];
    }

    /** Clears all accumulated glyph instances. Called once per frame before drawText(). */
    public void clear() {
        glyphCount = 0;
    }

    /** @return the number of glyphs accumulated this frame. */
    public int glyphCount() {
        return glyphCount;
    }

    /** @return the raw float data (12 floats per glyph). */
    public float[] data() {
        return data;
    }

    /** @return the accumulated glyph instances as records (legacy compatibility). */
    public List<GlyphInstance> instances() {
        List<GlyphInstance> list = new ArrayList<>(glyphCount);
        for (int i = 0; i < glyphCount; i++) {
            int off = i * FLOATS_PER_GLYPH;
            list.add(new GlyphInstance(
                    data[off], data[off+1], data[off+2], data[off+3],
                    data[off+4], data[off+5], data[off+6], data[off+7],
                    data[off+8], data[off+9], data[off+10], data[off+11]));
        }
        return list;
    }

    /**
     * Appends the glyph quads for a single line of text, rasterizing glyphs on demand.
     *
     * @param fontId    font previously registered via FontRegistry.loadFont()
     * @param text      text to render (single line, left-to-right)
     * @param x         baseline start X in screen pixels
     * @param y         baseline Y in screen pixels
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
        FontRegistry.KerningContext kern = fonts.kerningContext(fontId, pixelSize);

        for (int i = 0; i < text.length(); i++) {
            int codepoint = text.charAt(i);

            if (prevCodepoint >= 0) {
                cursorX += kern.advance(prevCodepoint, codepoint);
            }

            FontRegistry.GlyphInfo glyph = fonts.getOrRasterizeGlyph(fontId, codepoint, pixelSize);

            if (glyph.atlasWidth() > 0 && glyph.atlasHeight() > 0) {
                float glyphMinX = cursorX + glyph.bearingX();
                float glyphMinY = y + glyph.bearingY();
                float glyphMaxX = glyphMinX + glyph.atlasWidth();
                float glyphMaxY = glyphMinY + glyph.atlasHeight();

                ensureCapacity(glyphCount + 1);
                int off = glyphCount * FLOATS_PER_GLYPH;
                data[off]     = glyphMinX;
                data[off + 1] = glyphMinY;
                data[off + 2] = glyphMaxX;
                data[off + 3] = glyphMaxY;
                data[off + 4] = glyph.u0();
                data[off + 5] = glyph.v0();
                data[off + 6] = glyph.u1();
                data[off + 7] = glyph.v1();
                data[off + 8] = r;
                data[off + 9] = g;
                data[off + 10] = b;
                data[off + 11] = a;
                glyphCount++;
            }

            cursorX += glyph.advanceWidth();
            prevCodepoint = codepoint;
        }

        return cursorX - x;
    }

    /** @return true if no glyphs have been accumulated this frame. */
    public boolean isEmpty() {
        return glyphCount == 0;
    }

    private void ensureCapacity(int required) {
        int needed = required * FLOATS_PER_GLYPH;
        if (needed <= data.length) return;
        int newLen = data.length;
        while (newLen < needed) newLen *= 2;
        float[] newData = new float[newLen];
        System.arraycopy(data, 0, newData, 0, glyphCount * FLOATS_PER_GLYPH);
        data = newData;
    }
}
