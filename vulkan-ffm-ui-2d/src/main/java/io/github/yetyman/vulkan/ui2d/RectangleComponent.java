package io.github.yetyman.vulkan.ui2d;

import io.github.yetyman.vulkan.foundation.ecs.Component;
import io.github.yetyman.vulkan.foundation.ecs.Node;
import io.github.yetyman.vulkan.foundation.ecs.PropertyNotifier;

/**
 * Data component defining a 2D axis-aligned rectangle with nine-slice rendering support.
 *
 * Contains position, size, color, and nine-slice inset configuration.
 * Uses PropertyNotifier for change tracking so renderers and the spatial grid can
 * react to updates incrementally.
 */
public class RectangleComponent implements Component {

    /** Properties that can be observed for changes. */
    public enum Prop {
        POSITION,   // x or y changed
        SIZE,       // width or height changed
        COLOR,      // any color channel changed
        NINE_SLICE, // inset values changed
        BOUNDS      // convenience: position OR size changed (fires alongside POSITION/SIZE)
    }

    private final PropertyNotifier<Prop> notifier = new PropertyNotifier<>(Prop.class);

    // Position (top-left corner, in pixels or normalized coords depending on usage)
    private float x;
    private float y;

    // Size
    private float width;
    private float height;

    // Color (RGBA, 0-1)
    private float r = 1f, g = 1f, b = 1f, a = 1f;

    // Nine-slice insets (border sizes in pixels)
    private float insetLeft;
    private float insetRight;
    private float insetTop;
    private float insetBottom;

    // Nine-slice texture region (UV coordinates)
    private float uvX, uvY, uvW = 1f, uvH = 1f;

    public RectangleComponent() {}

    public RectangleComponent(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // --- PropertyNotifier access ---

    public PropertyNotifier<Prop> properties() { return notifier; }

    // --- Position ---

    public float x() { return x; }
    public float y() { return y; }

    public RectangleComponent setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        notifier.fire(Prop.POSITION);
        notifier.fire(Prop.BOUNDS);
        return this;
    }

    public RectangleComponent setX(float x) {
        this.x = x;
        notifier.fire(Prop.POSITION);
        notifier.fire(Prop.BOUNDS);
        return this;
    }

    public RectangleComponent setY(float y) {
        this.y = y;
        notifier.fire(Prop.POSITION);
        notifier.fire(Prop.BOUNDS);
        return this;
    }

    // --- Size ---

    public float width() { return width; }
    public float height() { return height; }

    public RectangleComponent setSize(float width, float height) {
        this.width = width;
        this.height = height;
        notifier.fire(Prop.SIZE);
        notifier.fire(Prop.BOUNDS);
        return this;
    }

    public RectangleComponent setWidth(float width) {
        this.width = width;
        notifier.fire(Prop.SIZE);
        notifier.fire(Prop.BOUNDS);
        return this;
    }

    public RectangleComponent setHeight(float height) {
        this.height = height;
        notifier.fire(Prop.SIZE);
        notifier.fire(Prop.BOUNDS);
        return this;
    }

    /**
     * Sets position and size together (one notification batch).
     */
    public RectangleComponent setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        notifier.fire(Prop.POSITION);
        notifier.fire(Prop.SIZE);
        notifier.fire(Prop.BOUNDS);
        return this;
    }

    // --- Color ---

    public float r() { return r; }
    public float g() { return g; }
    public float b() { return b; }
    public float a() { return a; }

    public RectangleComponent setColor(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        notifier.fire(Prop.COLOR);
        return this;
    }

    public RectangleComponent setColor(float r, float g, float b) {
        return setColor(r, g, b, 1f);
    }

    // --- Nine-slice insets ---

    public float insetLeft() { return insetLeft; }
    public float insetRight() { return insetRight; }
    public float insetTop() { return insetTop; }
    public float insetBottom() { return insetBottom; }

    public RectangleComponent setInsets(float left, float right, float top, float bottom) {
        this.insetLeft = left;
        this.insetRight = right;
        this.insetTop = top;
        this.insetBottom = bottom;
        notifier.fire(Prop.NINE_SLICE);
        return this;
    }

    public RectangleComponent setUniformInset(float inset) {
        return setInsets(inset, inset, inset, inset);
    }

    /** @return true if this rectangle has non-zero nine-slice insets. */
    public boolean hasNineSlice() {
        return insetLeft > 0 || insetRight > 0 || insetTop > 0 || insetBottom > 0;
    }

    // --- Nine-slice texture region (UV) ---

    public float uvX() { return uvX; }
    public float uvY() { return uvY; }
    public float uvW() { return uvW; }
    public float uvH() { return uvH; }

    public RectangleComponent setTextureRegion(float uvX, float uvY, float uvW, float uvH) {
        this.uvX = uvX;
        this.uvY = uvY;
        this.uvW = uvW;
        this.uvH = uvH;
        notifier.fire(Prop.NINE_SLICE);
        return this;
    }

    // --- Utility ---

    /** @return true if the given point is inside this rectangle. */
    public boolean contains(float px, float py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    @Override
    public void close(Node node) {
        notifier.clearBulkObserver();
        notifier.clear();
    }

    @Override
    public String toString() {
        return "RectangleComponent{" + x + "," + y + " " + width + "x" + height + "}";
    }
}
