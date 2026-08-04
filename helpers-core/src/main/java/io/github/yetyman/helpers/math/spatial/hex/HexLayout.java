package io.github.yetyman.helpers.math.spatial.hex;

import io.github.yetyman.helpers.math.Vec2;

/**
 * Converts between hex coordinates and pixel (world) coordinates.
 * Supports both pointy-top and flat-top orientations.
 */
public class HexLayout {

    public enum Orientation { POINTY_TOP, FLAT_TOP }

    private final Orientation orientation;
    private final float size;
    private final Vec2 origin;

    // Forward matrix (hex to pixel)
    private final float f0, f1, f2, f3;
    // Inverse matrix (pixel to hex)
    private final float b0, b1, b2, b3;

    public HexLayout(Orientation orientation, float size, Vec2 origin) {
        this.orientation = orientation;
        this.size = size;
        this.origin = origin;

        if (orientation == Orientation.POINTY_TOP) {
            f0 = (float) Math.sqrt(3.0); f1 = (float) Math.sqrt(3.0) / 2f; f2 = 0f; f3 = 3f / 2f;
            b0 = (float) Math.sqrt(3.0) / 3f; b1 = -1f / 3f; b2 = 0f; b3 = 2f / 3f;
        } else {
            f0 = 3f / 2f; f1 = 0f; f2 = (float) Math.sqrt(3.0) / 2f; f3 = (float) Math.sqrt(3.0);
            b0 = 2f / 3f; b1 = 0f; b2 = -1f / 3f; b3 = (float) Math.sqrt(3.0) / 3f;
        }
    }

    public Orientation orientation() { return orientation; }
    public float size() { return size; }
    public Vec2 origin() { return origin; }

    /** Converts hex coordinate to pixel position (center of the hex). */
    public Vec2 hexToPixel(HexCoord hex) {
        float x = (f0 * hex.q + f1 * hex.r) * size + origin.x;
        float y = (f2 * hex.q + f3 * hex.r) * size + origin.y;
        return new Vec2(x, y);
    }

    /** Converts pixel position to fractional hex coordinate, then rounds to nearest hex. */
    public HexCoord pixelToHex(Vec2 pixel) {
        float px = (pixel.x - origin.x) / size;
        float py = (pixel.y - origin.y) / size;
        float fq = b0 * px + b1 * py;
        float fr = b2 * px + b3 * py;
        return hexRound(fq, fr);
    }

    /** Returns the 6 corner positions of a hex in pixel space. */
    public Vec2[] hexCorners(HexCoord hex) {
        Vec2 center = hexToPixel(hex);
        Vec2[] corners = new Vec2[6];
        for (int i = 0; i < 6; i++) {
            float angle = (float) (Math.PI / 180.0 * (60.0 * i + (orientation == Orientation.POINTY_TOP ? 30.0 : 0.0)));
            corners[i] = new Vec2(center.x + size * (float) Math.cos(angle), center.y + size * (float) Math.sin(angle));
        }
        return corners;
    }

    private static HexCoord hexRound(float fq, float fr) {
        float fs = -fq - fr;
        int rq = Math.round(fq), rr = Math.round(fr), rs = Math.round(fs);
        float dq = Math.abs(rq - fq), dr = Math.abs(rr - fr), ds = Math.abs(rs - fs);
        if (dq > dr && dq > ds) rq = -rr - rs;
        else if (dr > ds) rr = -rq - rs;
        return new HexCoord(rq, rr);
    }
}
