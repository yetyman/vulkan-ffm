package io.github.yetyman.vulkan.sample.simple;

import io.github.yetyman.structures.input.KeyboardState;
import io.github.yetyman.structures.input.MouseState;
import io.github.yetyman.vulkan.*;
import io.github.yetyman.vulkan.buffers.IBuffer;
import io.github.yetyman.vulkan.buffers.BufferFactory;
import io.github.yetyman.vulkan.buffers.BufferUsage;
import io.github.yetyman.vulkan.buffers.MemoryStrategy;
import io.github.yetyman.vulkan.enums.*;
import io.github.yetyman.vulkan.shaders.DescriptorGroup;
import io.github.yetyman.vulkan.shaders.ShaderLoader;
import io.github.yetyman.vulkan.shaders.ShaderInstance;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Visualizes all mouse kinematic state and keyboard state.
 *
 * Layout:
 *   Left ~40% — mouse bar panel.
 *     Each vec2 slot is a "plus" shape: one H bar (X) and one V bar (Y) sharing the same
 *     center point. Groups are offset diagonally so related X/Y pairs are spatially correlated.
 *     Bars grow from center — positive extends right/down, negative extends left/up.
 *     Mouse buttons shown as color quads above the bars.
 *     Cursor position quad follows the actual cursor.
 *
 *   Right ~60% — keyboard grid.
 *     Main QWERTY block, aux row (ins/del/home/end/pgup/pgdn), numpad, arrow cluster
 *     all laid out to the right.
 */
public class InputVisFrame extends SimpleGraphicsFrame {

    // -------------------------------------------------------------------------
    // Keyboard layout
    // -------------------------------------------------------------------------

    // Main block
    private static final String[] ROW_FUNC  = {"ESC","F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"};
    private static final String[] ROW_NUM   = {"`","1","2","3","4","5","6","7","8","9","0","-","=","BKSP"};
    private static final String[] ROW_TAB   = {"TAB","Q","W","E","R","T","Y","U","I","O","P","[","]","\\"};
    private static final String[] ROW_CAPS  = {"CAPS","A","S","D","F","G","H","J","K","L",";","'","ENTER"};
    private static final String[] ROW_SHIFT = {"LSHIFT","Z","X","C","V","B","N","M",",",".","/","RSHIFT"};
    private static final String[] ROW_CTRL  = {"LCTRL","LSUPER","LALT","SPACE","RALT","RSUPER","MENU","RCTRL"};

    // Aux cluster (ins/del/home/end/pgup/pgdn) — 2 columns × 3 rows
    private static final String[] AUX_COL0 = {"INS","HOME","PGUP"};
    private static final String[] AUX_COL1 = {"DEL","END","PGDN"};

    // Arrow cluster
    private static final String[] ARR_ROW0 = {"UP"};
    private static final String[] ARR_ROW1 = {"LEFT","DOWN","RIGHT"};

    // Numpad
    private static final String[] NP_ROW0 = {"NUMLK","NP/","NP*","NP-"};
    private static final String[] NP_ROW1 = {"NP7","NP8","NP9","NP+"};
    private static final String[] NP_ROW2 = {"NP4","NP5","NP6"};
    private static final String[] NP_ROW3 = {"NP1","NP2","NP3","NPENT"};
    private static final String[] NP_ROW4 = {"NP0","NP."};

    // Extra: print screen / scroll lock / pause / backslash
    private static final String[] ROW_SYS  = {"PRTSC","SCRLK","PAUSE"};

    static final List<String> ALL_KEYS;
    static {
        ALL_KEYS = new ArrayList<>();
        for (String[] row : new String[][]{
                ROW_FUNC, ROW_NUM, ROW_TAB, ROW_CAPS, ROW_SHIFT, ROW_CTRL,
                AUX_COL0, AUX_COL1, ARR_ROW0, ARR_ROW1,
                NP_ROW0, NP_ROW1, NP_ROW2, NP_ROW3, NP_ROW4,
                ROW_SYS})
            for (String k : row) ALL_KEYS.add(k);
    }

    // -------------------------------------------------------------------------
    // Bar panel layout — NDC, window 1400×800
    // -------------------------------------------------------------------------

    // Each "plus" group has a center (cx, cy). H bar extends horizontally, V bar vertically.
    // Groups are offset diagonally: each successive group shifts by (DIAG_X, DIAG_Y).
    private static final float BAR_MAX    = 0.09f;  // max half-extent of a bar
    private static final float BAR_THICK  = 0.010f; // half-thickness of each bar
    private static final float DIAG_X     = 0.028f; // horizontal shift per group
    private static final float DIAG_Y     = 0.110f; // vertical shift per group
    private static final float GROUP0_X   = -0.94f; // center X of first group
    private static final float GROUP0_Y   = -0.88f; // center Y of first group (NDC top)

    // Button quads
    private static final float BTN_HW    = 0.020f;
    private static final float BTN_HH    = 0.020f;
    private static final float BTN_GAP   = 0.008f;
    private static final int   BTN_COUNT = 5;

    // Cursor quad
    private static final float CUR_HW = 0.016f;
    private static final float CUR_HH = 0.016f;

    // Vector arrows
    private static final float  VECTOR_THICKNESS = 0.006f; // NDC half-thickness of arrow shaft
    private static final double VECTOR_MAX_LEN   = 0.25;   // NDC max arrow length

    // -------------------------------------------------------------------------
    // Saturation scales
    // -------------------------------------------------------------------------

    private static final double S_POS   = 1400.0;
    private static final double S_DELTA = 40.0;
    private static final double S_VEL   = 3000.0;
    private static final double S_ACC   = 80000.0;
    private static final double S_JERK  = 5e7;
    private static final double S_SDEL  = 10.0;
    private static final double S_SVEL  = 200.0;
    private static final double S_SACC  = 5000.0;

    // -------------------------------------------------------------------------
    // Instance count
    // -------------------------------------------------------------------------
    // 15 vec2 groups × 2 bars + 1 cursor + BTN_COUNT buttons + ALL_KEYS.size() keys
    // 11 vec2 groups × 2 bars + 1 cursor + 2 vector arrows + BTN_COUNT buttons + ALL_KEYS.size() keys
    // groups: pos, delta, vel, vel.smooth, acc, acc.smooth, sDelta, sVel, sVel.smooth, sAcc, sAcc.smooth
    private static final int BAR_GROUPS    = 11;
    private static final int VECTOR_COUNT  = 3; // velocity + acceleration + jerk arrows
    private static final int INSTANCE_COUNT = BAR_GROUPS * 2 + 1 + VECTOR_COUNT + BTN_COUNT + ALL_KEYS.size();
    private static final int FLOATS_PER    = 9;
    private static final int BUFFER_BYTES  = INSTANCE_COUNT * FLOATS_PER * Float.BYTES;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final MouseState    mouse;
    private final KeyboardState keyboard;

    private ShaderInstance  vertShader;
    private ShaderInstance  fragShader;
    private IBuffer         instanceBuffer;
    private DescriptorGroup descriptorGroup;
    private ByteBuffer      stagingBuf;

    public InputVisFrame(Arena arena, VkDevice device, VkQueue queue,
                         MemorySegment surface, int width, int height,
                         MouseState mouse, KeyboardState keyboard) {
        super(arena, device, queue, surface, width, height, 2);
        this.mouse    = mouse;
        this.keyboard = keyboard;
    }

    // -------------------------------------------------------------------------
    // SimpleGraphicsFrame contract
    // -------------------------------------------------------------------------

    @Override
    protected VkPipeline createPipeline() {
        vertShader = ShaderLoader.load("/shaders/input_vis.vert", device);
        fragShader = ShaderLoader.load("/shaders/input_vis.frag", device);

        instanceBuffer = BufferFactory.create(
                MemoryStrategy.MAPPED, null, BUFFER_BYTES,
                BufferUsage.STORAGE, device, queue);

        stagingBuf = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());

        descriptorGroup = DescriptorGroup.builder()
                .device(device)
                .stageFlags(VkShaderStageFlagBits.VK_SHADER_STAGE_VERTEX_BIT.value())
                .storageBuffer(0, instanceBuffer)
                .build(arena);

        VkPipeline.Builder builder = VkPipeline.builder()
                .device(device)
                .vertexShader(vertShader)
                .fragmentShader(fragShader)
                .triangleTopology()
                .dynamicViewport()
                .dynamicScissor()
                .alphaBlend()
                .descriptorSetLayouts(descriptorGroup.layoutHandle());

        if (useDynamicRendering) {
            builder.dynamicRendering(0, VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value());
        } else {
            builder.renderPass(renderPass.handle());
        }

        VkPipeline p = builder.build(arena);
        vertShader.pipelineLayout(p.layout());
        return p;
    }

    @Override
    protected void onDraw(VkCommandBuffer commandBuffer, SegmentAllocator frameAllocator) {
        uploadInstances();
        descriptorGroup.set().bind(commandBuffer,
                VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS.value(),
                pipeline.layout(), 0, frameAllocator);
    }

    @Override protected int vertexCount()   { return 6; }
    @Override protected int instanceCount() { return INSTANCE_COUNT; }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    private void uploadInstances() {
        stagingBuf.clear();

        int g = 0; // group index — drives diagonal offset

        // Mouse kinematics — position, delta, vel raw/smooth, acc raw/smooth, jerk raw/smooth
        double[] pos   = mouse.position.getValue();
        double[] delta = mouse.delta.getValue();
        double[] vel   = mouse.velocity.getValue();
        double[] velS  = mouse.velocitySmooth.getValue();
        double[] acc   = mouse.acceleration.getValue();
        double[] accS  = mouse.accelerationSmooth.getValue();

        double[] sDel  = mouse.scrollDelta.getValue();
        double[] sVel  = mouse.scrollVelocity.getValue();
        double[] sVelS = mouse.scrollVelocitySmooth.getValue();
        double[] sAcc  = mouse.scrollAcceleration.getValue();
        double[] sAccS = mouse.scrollAccelerationSmooth.getValue();

        writePlus(g++, pos[0],   pos[1],   S_POS,  S_POS,  1.0f, 1.0f, 1.0f);   // white
        writePlus(g++, delta[0], delta[1], S_DELTA,S_DELTA,0.6f, 0.8f, 1.0f);   // light blue
        writePlus(g++, vel[0],   vel[1],   S_VEL,  S_VEL,  0.0f, 1.0f, 1.0f);   // cyan
        writePlus(g++, velS[0],  velS[1],  S_VEL,  S_VEL,  0.0f, 0.6f, 0.6f);   // teal
        writePlus(g++, acc[0],   acc[1],   S_ACC,  S_ACC,  1.0f, 0.0f, 1.0f);   // magenta
        writePlus(g++, accS[0],  accS[1],  S_ACC,  S_ACC,  0.6f, 0.0f, 0.7f);   // purple
        writePlus(g++, sDel[0],  sDel[1],  S_SDEL, S_SDEL, 1.0f, 1.0f, 0.0f);   // yellow
        writePlus(g++, sVel[0],  sVel[1],  S_SVEL, S_SVEL, 0.5f, 1.0f, 0.0f);   // lime
        writePlus(g++, sVelS[0], sVelS[1], S_SVEL, S_SVEL, 0.3f, 0.6f, 0.0f);   // dark lime
        writePlus(g++, sAcc[0],  sAcc[1],  S_SACC, S_SACC, 1.0f, 0.4f, 0.6f);   // pink
        writePlus(g++, sAccS[0], sAccS[1], S_SACC, S_SACC, 0.6f, 0.2f, 0.4f);   // dark pink

        // Cursor quad — follows actual cursor position
        double[] curPos = mouse.position.getValue();
        float cx = toNdcX((float)curPos[0]);
        float cy = toNdcY((float)curPos[1]);
        writeInstance(cx, cy, CUR_HW, CUR_HH, 1f, 1f, 1f, 0.9f, 0f);

        // Velocity vector arrow — cyan, rooted at cursor
        double[] velVec = mouse.velocitySmooth.getValue();
        double velMag = Math.sqrt(velVec[0]*velVec[0]+velVec[1]*velVec[1]);
        double velLen = Math.min(velMag / S_VEL, 1.0) * VECTOR_MAX_LEN;
        double velNdcVx = velVec[0] / width * 2.0, velNdcVy = velVec[1] / height * 2.0;
        double velNdcMag = Math.sqrt(velNdcVx*velNdcVx + velNdcVy*velNdcVy);
        float velTipX = cx, velTipY = cy;
        if (velNdcMag > 1e-6) {
            velTipX = cx + (float)(velNdcVx / velNdcMag * velLen);
            velTipY = cy + (float)(velNdcVy / velNdcMag * velLen);
        }
        writeVector(cx, cy, velVec[0], velVec[1], S_VEL, 0f, 1f, 1f, 0.85f);

        // Acceleration vector arrow — magenta, rooted at tip of velocity arrow
        double[] accVec = mouse.accelerationSmooth.getValue();
        double accMag = Math.sqrt(accVec[0]*accVec[0]+accVec[1]*accVec[1]);
        double accLen = Math.min(accMag / S_ACC, 1.0) * VECTOR_MAX_LEN;
        double accNdcVx = accVec[0] / width * 2.0, accNdcVy = accVec[1] / height * 2.0;
        double accNdcMag = Math.sqrt(accNdcVx*accNdcVx + accNdcVy*accNdcVy);
        float accTipX = velTipX, accTipY = velTipY;
        if (accNdcMag > 1e-6) {
            accTipX = velTipX + (float)(accNdcVx / accNdcMag * accLen);
            accTipY = velTipY + (float)(accNdcVy / accNdcMag * accLen);
        }
        writeVector(velTipX, velTipY, accVec[0], accVec[1], S_ACC, 1f, 0f, 1f, 0.85f);

        // Jerk vector arrow — orange, rooted at tip of acceleration arrow
        double[] jrkVec = mouse.jerkSmooth.getValue();
        writeVector(accTipX, accTipY, jrkVec[0], jrkVec[1], S_JERK, 1f, 0.5f, 0f, 0.85f);

        // Button quads — anchored from left edge with padding
        float btnStartX = -0.97f + BTN_HW;
        float btnY      = GROUP0_Y - BTN_HH - 0.04f;
        for (int i = 0; i < BTN_COUNT; i++) {
            float bx   = btnStartX + i * (BTN_HW * 2 + BTN_GAP) + BTN_HW;
            boolean dn = i < mouse.button.length &&
                    mouse.button[i].getValue() == MouseState.ButtonState.DOWN;
            if (dn) writeInstance(bx, btnY, BTN_HW, BTN_HH, 1f, 0.9f, 0.1f, 1f);
            else    writeInstance(bx, btnY, BTN_HW, BTN_HH, 0.25f, 0.25f, 0.25f, 0.9f);
        }

        // -------------------------------------------------------------------------
        // Keyboard grid — right side, everything right-aligned to screen edge
        // -------------------------------------------------------------------------
        float KW  = 0.023f;
        float KH  = 0.030f;
        float KG  = 0.004f;
        float KS  = KW * 2 + KG;

        float kbLeft = -0.34f;
        float y0     = -0.88f; // leave room above for sys row

        writeKeyRow(ROW_SYS,   kbLeft + (14 - ROW_SYS.length) * KS, y0 - (KH*2+KG), KW, KH, KG);
        writeKeyRow(ROW_FUNC,  kbLeft, y0 + 0 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(ROW_NUM,   kbLeft, y0 + 1 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(ROW_TAB,   kbLeft, y0 + 2 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(ROW_CAPS,  kbLeft, y0 + 3 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(ROW_SHIFT, kbLeft, y0 + 4 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(ROW_CTRL,  kbLeft, y0 + 5 * (KH*2+KG), KW, KH, KG);

        float auxLeft = kbLeft + 14 * KS + KG * 2;
        for (int r = 0; r < AUX_COL0.length; r++) {
            float ay = y0 + (r + 1) * (KH*2+KG);
            writeKey(AUX_COL0[r], auxLeft + KW,      ay, KW, KH);
            writeKey(AUX_COL1[r], auxLeft + KW + KS, ay, KW, KH);
        }

        float arrY0 = y0 + 4 * (KH*2+KG);
        writeKey(ARR_ROW0[0], auxLeft + KW + KS,  arrY0,           KW, KH);
        writeKey(ARR_ROW1[0], auxLeft + KW,        arrY0 + KH*2+KG, KW, KH);
        writeKey(ARR_ROW1[1], auxLeft + KW + KS,   arrY0 + KH*2+KG, KW, KH);
        writeKey(ARR_ROW1[2], auxLeft + KW + KS*2, arrY0 + KH*2+KG, KW, KH);

        float npLeft = auxLeft + 3 * KS + KG * 2;
        writeKeyRow(NP_ROW0, npLeft, y0 + 1 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(NP_ROW1, npLeft, y0 + 2 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(NP_ROW2, npLeft, y0 + 3 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(NP_ROW3, npLeft, y0 + 4 * (KH*2+KG), KW, KH, KG);
        writeKeyRow(NP_ROW4, npLeft, y0 + 5 * (KH*2+KG), KW, KH, KG);

        stagingBuf.flip();
        instanceBuffer.write(stagingBuf, 0, queue);
    }

    /**
     * Writes a "plus" shape for a vec2 value at diagonal group index g.
     * H bar (X component) and V bar (Y component) share the same center point.
     * Positive values extend right/down, negative extend left/up.
     */
    private void writePlus(int g, double vx, double vy,
                            double scaleX, double scaleY,
                            float r, float gr, float b) {
        float cx = GROUP0_X + g * DIAG_X;
        float cy = GROUP0_Y + g * DIAG_Y;

        // H bar — horizontal, represents X
        float hExt = (float)(Math.min(Math.abs(vx) / scaleX, 1.0) * BAR_MAX);
        float hHw  = Math.max(hExt, BAR_THICK * 0.4f);
        float hCx  = cx + (vx >= 0 ? hExt : -hExt);
        writeInstance(hCx, cy, hHw, BAR_THICK, r, gr, b, 0.9f);

        // V bar — vertical, represents Y
        float vExt = (float)(Math.min(Math.abs(vy) / scaleY, 1.0) * BAR_MAX);
        float vHh  = Math.max(vExt, BAR_THICK * 0.4f);
        float vCy  = cy + (vy >= 0 ? vExt : -vExt);
        writeInstance(cx, vCy, BAR_THICK, vHh, r, gr, b, 0.9f);
    }

    private void writeKeyRow(String[] keys, float left, float cy, float kw, float kh, float kg) {
        for (int i = 0; i < keys.length; i++)
            writeKey(keys[i], left + kw + i * (kw * 2 + kg), cy, kw, kh);
    }

    private void writeKey(String name, float cx, float cy, float kw, float kh) {
        boolean held = keyboard.keyState(name) == KeyboardState.KeyState.DOWN;
        if (held) writeInstance(cx, cy, kw, kh, 1f, 0.9f, 0.1f, 1f);
        else      writeInstance(cx, cy, kw, kh, 0.2f, 0.2f, 0.2f, 0.85f);
    }

    /**
     * Writes a vector arrow quad rooted at (rootX, rootY), pointing in the direction of (vx, vy).
     * Length scales with magnitude up to VECTOR_MAX_LEN. The quad is centered at the midpoint
     * of the arrow so it spans from root to tip.
     */
    private double vectorAngle(double vx, double vy) {
        // Convert to NDC space first so angle is correct on non-square windows
        double ndcVx = vx / width  * 2.0;
        double ndcVy = vy / height * 2.0;
        return Math.atan2(ndcVy, ndcVx);
    }

    private void writeVector(float rootX, float rootY, double vx, double vy,
                              double scale, float r, float g, float b, float a) {
        double mag = Math.sqrt(vx*vx + vy*vy);
        if (mag < 1e-6) {
            writeInstance(rootX, rootY, 0f, 0f, r, g, b, 0f, 0f); // invisible placeholder
            return;
        }
        // NDC-space direction
        double ndcVx = vx / width  * 2.0;
        double ndcVy = vy / height * 2.0;
        double ndcMag = Math.sqrt(ndcVx*ndcVx + ndcVy*ndcVy);
        double len = Math.min(mag / scale, 1.0) * VECTOR_MAX_LEN;
        double nx = ndcVx / ndcMag; // unit vector in NDC space
        double ny = ndcVy / ndcMag;
        float halfLen  = (float)(len * 0.5);
        float cx = rootX + (float)(nx * halfLen);
        float cy = rootY + (float)(ny * halfLen);
        float ndcAngle = (float) Math.atan2(ny, nx);
        writeInstance(cx, cy, halfLen, VECTOR_THICKNESS, r, g, b, a, ndcAngle);
    }

    private void writeInstance(float cx, float cy, float hw, float hh,
                               float r, float g, float b, float a, float angle) {
        stagingBuf.putFloat(cx).putFloat(cy).putFloat(hw).putFloat(hh)
                  .putFloat(r).putFloat(g).putFloat(b).putFloat(a).putFloat(angle);
    }

    private void writeInstance(float cx, float cy, float hw, float hh,
                               float r, float g, float b, float a) {
        writeInstance(cx, cy, hw, hh, r, g, b, a, 0f);
    }

    private float toNdcX(float screenX) { return (screenX / width)  * 2f - 1f; }
    private float toNdcY(float screenY) { return (screenY / height) * 2f - 1f; }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    @Override
    protected void cleanupResources() {
        super.cleanupResources();
        if (descriptorGroup != null) descriptorGroup.close();
        if (instanceBuffer  != null) instanceBuffer.close();
        if (vertShader      != null) vertShader.close();
        if (fragShader      != null) fragShader.close();
    }
}
