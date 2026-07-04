#ifndef STB_TRUETYPE_WRAPPER_H
#define STB_TRUETYPE_WRAPPER_H

// Self-contained jextract wrapper - no system header includes.
// Declares the subset of the stb_truetype.h (nothings/stb) public API that
// VulkanFFM's FontRegistry needs: font init, codepoint/glyph lookup, glyph
// metrics, kerning, scale helpers, and single-channel bitmap rasterization.
//
// The actual implementation is compiled into a shared library by
// build-stb-truetype.bat, which pulls the upstream single-header library and
// compiles it with STB_TRUETYPE_IMPLEMENTATION defined. This header only
// mirrors the function signatures and struct layouts jextract needs to see;
// it must stay binary-compatible with the real stb_truetype.h.

#ifdef __cplusplus
extern "C" {
#endif

typedef unsigned char  stbtt_uint8;
typedef signed char    stbtt_int8;
typedef unsigned short stbtt_uint16;
typedef signed short   stbtt_int16;
typedef unsigned int   stbtt_uint32;
typedef signed int     stbtt_int32;

// ---- Core font handle ----

// stbtt__buf must be declared before stbtt_fontinfo embeds it by value.
// Layout matches upstream stb_truetype.h exactly (data ptr, cursor, size).
typedef struct
{
    unsigned char* data;
    int cursor;
    int size;
} stbtt__buf;

typedef struct
{
    void*          userdata;
    unsigned char* data;
    int            fontstart;

    int numGlyphs;

    int loca, head, glyf, hhea, hmtx, kern, gpos, svg;
    int index_map;
    int indexToLocFormat;

    stbtt__buf cff;
    stbtt__buf charstrings;
    stbtt__buf gsubrs;
    stbtt__buf subrs;
    stbtt__buf fontdicts;
    stbtt__buf fdselect;
} stbtt_fontinfo;

typedef struct
{
    int x0, y0, x1, y1;
} stbtt_bbox;

typedef struct
{
    unsigned short x0, y0, x1, y1;
    float xoff, yoff, xoff2, yoff2;
} stbtt_packedchar;

typedef struct
{
    float first_unicode_codepoint_in_range;
    int   array_of_unicode_codepoints;
    int   num_chars;
    stbtt_packedchar* chardata_for_range;
    unsigned char h_oversample, v_oversample;
} stbtt_pack_range;

typedef struct
{
    void* pack_info;
    int width;
    int height;
    int stride_in_bytes;
    int padding;
    int skip_missing;
    unsigned int h_oversample, v_oversample;
    unsigned char* pixels;
    void* nodes;
} stbtt_pack_context;

typedef struct
{
    float x0, y0, s0, t0;
    float x1, y1, s1, t1;
} stbtt_aligned_quad;

// ---- Font enumeration / init ----

int  stbtt_GetFontOffsetForIndex(const unsigned char* data, int index);
int  stbtt_GetNumberOfFonts(const unsigned char* data);
int  stbtt_InitFont(stbtt_fontinfo* info, const unsigned char* data, int offset);

// ---- Codepoint / glyph lookup ----

int  stbtt_FindGlyphIndex(const stbtt_fontinfo* info, int unicode_codepoint);

// ---- Scale helpers ----

float stbtt_ScaleForPixelHeight(const stbtt_fontinfo* info, float pixels);
float stbtt_ScaleForMappingEmToPixels(const stbtt_fontinfo* info, float pixels);

// ---- Vertical metrics ----

void stbtt_GetFontVMetrics(const stbtt_fontinfo* info, int* ascent, int* descent, int* lineGap);
int  stbtt_GetFontVMetricsOS2(const stbtt_fontinfo* info, int* typoAscent, int* typoDescent, int* typoLineGap);
void stbtt_GetFontBoundingBox(const stbtt_fontinfo* info, int* x0, int* y0, int* x1, int* y1);

// ---- Horizontal metrics ----

void stbtt_GetCodepointHMetrics(const stbtt_fontinfo* info, int codepoint, int* advanceWidth, int* leftSideBearing);
int  stbtt_GetCodepointKernAdvance(const stbtt_fontinfo* info, int ch1, int ch2);
void stbtt_GetGlyphHMetrics(const stbtt_fontinfo* info, int glyph_index, int* advanceWidth, int* leftSideBearing);
int  stbtt_GetGlyphKernAdvance(const stbtt_fontinfo* info, int glyph1, int glyph2);

// ---- Glyph bounding box ----

int  stbtt_GetCodepointBox(const stbtt_fontinfo* info, int codepoint, int* x0, int* y0, int* x1, int* y1);
int  stbtt_GetGlyphBox(const stbtt_fontinfo* info, int glyph_index, int* x0, int* y0, int* x1, int* y1);

// ---- Single-glyph bitmap rasterization (8-bit alpha coverage) ----

unsigned char* stbtt_GetCodepointBitmap(const stbtt_fontinfo* info, float scale_x, float scale_y, int codepoint, int* width, int* height, int* xoff, int* yoff);
unsigned char* stbtt_GetGlyphBitmap(const stbtt_fontinfo* info, float scale_x, float scale_y, int glyph, int* width, int* height, int* xoff, int* yoff);
unsigned char* stbtt_GetCodepointBitmapSubpixel(const stbtt_fontinfo* info, float scale_x, float scale_y, float shift_x, float shift_y, int codepoint, int* width, int* height, int* xoff, int* yoff);
unsigned char* stbtt_GetGlyphBitmapSubpixel(const stbtt_fontinfo* info, float scale_x, float scale_y, float shift_x, float shift_y, int glyph, int* width, int* height, int* xoff, int* yoff);

void stbtt_MakeCodepointBitmap(const stbtt_fontinfo* info, unsigned char* output, int out_w, int out_h, int out_stride, float scale_x, float scale_y, int codepoint);
void stbtt_MakeGlyphBitmap(const stbtt_fontinfo* info, unsigned char* output, int out_w, int out_h, int out_stride, float scale_x, float scale_y, int glyph);
void stbtt_MakeCodepointBitmapSubpixel(const stbtt_fontinfo* info, unsigned char* output, int out_w, int out_h, int out_stride, float scale_x, float scale_y, float shift_x, float shift_y, int codepoint);
void stbtt_MakeGlyphBitmapSubpixel(const stbtt_fontinfo* info, unsigned char* output, int out_w, int out_h, int out_stride, float scale_x, float scale_y, float shift_x, float shift_y, int glyph);

void stbtt_GetCodepointBitmapBox(const stbtt_fontinfo* font, int codepoint, float scale_x, float scale_y, int* ix0, int* iy0, int* ix1, int* iy1);
void stbtt_GetGlyphBitmapBox(const stbtt_fontinfo* font, int glyph, float scale_x, float scale_y, int* ix0, int* iy0, int* ix1, int* iy1);

void stbtt_FreeBitmap(unsigned char* bitmap, void* userdata);

// ---- Rectangle-pack atlas API (stb_rect_pack integration built into stb_truetype) ----
// Used for building a whole-font atlas in one pass instead of one glyph at a time.

int  stbtt_PackBegin(stbtt_pack_context* spc, unsigned char* pixels, int width, int height, int stride_in_bytes, int padding, void* alloc_context);
void stbtt_PackEnd(stbtt_pack_context* spc);

void stbtt_PackSetOversampling(stbtt_pack_context* spc, unsigned int h_oversample, unsigned int v_oversample);
void stbtt_PackSetSkipMissingCodepoints(stbtt_pack_context* spc, int skip);

int  stbtt_PackFontRange(stbtt_pack_context* spc, const unsigned char* fontdata, int font_index, float font_size, int first_unicode_char_in_range, int num_chars_in_range, stbtt_packedchar* chardata_for_range);
int  stbtt_PackFontRanges(stbtt_pack_context* spc, const unsigned char* fontdata, int font_index, stbtt_pack_range* ranges, int num_ranges);

void stbtt_GetPackedQuad(const stbtt_packedchar* chardata, int pw, int ph, int char_index, float* xpos, float* ypos, stbtt_aligned_quad* q, int align_to_integer);

#ifdef __cplusplus
}
#endif

#endif // STB_TRUETYPE_WRAPPER_H
