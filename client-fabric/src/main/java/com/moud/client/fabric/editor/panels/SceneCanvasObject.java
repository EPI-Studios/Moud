package com.moud.client.fabric.editor.panels;

import com.miry.graphics.Texture;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.CanvasEditor2D;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.render.MoudTextures;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;

import static com.moud.client.fabric.editor.panels.Canvas2DSync.CONTROL_TYPES;
import static com.moud.client.fabric.editor.panels.Canvas2DSync.getProp;
import static com.moud.client.fabric.editor.panels.Canvas2DSync.parseFloat;
import static com.moud.client.fabric.editor.panels.Canvas2DSync.worldPos2D;

final class SceneCanvasObject implements CanvasEditor2D.CanvasObject {
    final long nodeId;
    private final Vector2f pos = new Vector2f();
    private final Vector2f scale = new Vector2f(1, 1);
    private final Vector2f size = new Vector2f(1, 1);
    private float rotationDeg;
    private String typeId = "";
    private final HashMap<Long, Vector2f> scratchWorldCache = new HashMap<>();

    private String cachedText = "";
    private float cachedFillR = 1, cachedFillG = 1, cachedFillB = 1, cachedFillA = 1;
    private float cachedValue = 50, cachedMinValue = 0, cachedMaxValue = 100;
    private boolean cachedChecked = false;

    private String cachedTexRef = "";
    private Texture cachedTexture = null;

    private float pendingLocalX;
    private float pendingLocalY;
    private float pendingScaleX = 1.0f;
    private float pendingScaleY = 1.0f;
    private float pendingRotDeg;
    private boolean pendingDirty;
    private long lastSendAtMs;

    private long parentId;

    private EditorRuntime runtime;
    private CanvasEditor2D canvas2d;
    private HashMap<Long, SceneCanvasObject> canvasObjectsById;

    SceneCanvasObject(long nodeId) {
        this.nodeId = nodeId;
    }

    void bind(EditorRuntime runtime, CanvasEditor2D canvas2d, HashMap<Long, SceneCanvasObject> canvasObjectsById) {
        this.runtime = runtime;
        this.canvas2d = canvas2d;
        this.canvasObjectsById = canvasObjectsById;
    }

    void syncFromSnapshot(SceneSnapshot.NodeSnapshot node,
                          Vector2f world, int[] controlRect) {
        if (node == null) return;
        parentId = node.parentId();
        typeId = node.type() != null ? node.type() : "";
        float lx = parseFloat(getProp(node, "x"), 0.0f);
        float ly = parseFloat(getProp(node, "y"), 0.0f);
        float sx = parseFloat(getProp(node, "sx"), 1.0f);
        float sy = parseFloat(getProp(node, "sy"), 1.0f);
        float rz = parseFloat(getProp(node, "rz"), 0.0f);
        pendingLocalX = lx;
        pendingLocalY = ly;
        pendingScaleX = sx;
        pendingScaleY = sy;
        pendingRotDeg = rz;
        pendingDirty = false;
        scale.set(sx, sy);
        rotationDeg = rz;

        if (CONTROL_TYPES.contains(typeId)) {
            if (controlRect != null) {
                float rw = Math.max(1, controlRect[2]);
                float rh = Math.max(1, controlRect[3]);
                size.set(rw, rh);
                pos.set(controlRect[0] + rw * 0.5f, controlRect[1] + rh * 0.5f);
            } else {
                float w = parseFloat(getProp(node, "w"), 100.0f);
                float h = parseFloat(getProp(node, "h"), 30.0f);
                size.set(Math.max(1, w), Math.max(1, h));
                if (world != null) pos.set(world.x + w * 0.5f, world.y + h * 0.5f);
            }
        } else {
            if (world != null) pos.set(world);
            if ("Sprite2D".equals(typeId)) {
                float w = parseFloat(getProp(node, "w"), 64.0f);
                float h = parseFloat(getProp(node, "h"), 64.0f);
                size.set(Math.max(1, w), Math.max(1, h));
            } else {
                size.set(1, 1);
            }
        }
        cachedText = nvl(getProp(node, "text"), "");
        String fillR = nvl(getProp(node, "fill_color_r"), getProp(node, "color_r"));
        String fillG = nvl(getProp(node, "fill_color_g"), getProp(node, "color_g"));
        String fillB = nvl(getProp(node, "fill_color_b"), getProp(node, "color_b"));
        String fillA = nvl(getProp(node, "fill_color_a"), getProp(node, "color_a"));
        cachedFillR = parseFloat(fillR, 1.0f);
        cachedFillG = parseFloat(fillG, 1.0f);
        cachedFillB = parseFloat(fillB, 1.0f);
        cachedFillA = parseFloat(fillA, 1.0f);
        cachedValue    = parseFloat(getProp(node, "value"),     50.0f);
        cachedMinValue = parseFloat(getProp(node, "min_value"),  0.0f);
        cachedMaxValue = parseFloat(getProp(node, "max_value"), 100.0f);
        cachedChecked  = "true".equalsIgnoreCase(getProp(node, "checked"));

        String newTexRef = switch (typeId) {
            case "TextureRect", "Sprite2D" -> nvl(getProp(node, "texture"), "");
            case "TextureButton" -> nvl(nvl(getProp(node, "texture_normal"), getProp(node, "texture_hover")), "");
            default -> "";
        };
        if (!newTexRef.equals(cachedTexRef)) {
            cachedTexRef = newTexRef;
            cachedTexture = null;
        }
    }

    private Texture resolveTexture() {
        if (cachedTexRef == null || cachedTexRef.isBlank()) return null;
        if (cachedTexture != null) return cachedTexture;
        Identifier id = MoudTextures.resolve(cachedTexRef);
        if (id == null || TextureManager.MISSING_IDENTIFIER.equals(id)) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getTextureManager() == null) return null;
        AbstractTexture mcTex = mc.getTextureManager().getOrDefault(id, null);
        if (mcTex == null) return null;
        int glId = mcTex.getGlId();
        if (glId <= 0) return null;
        cachedTexture = Texture.wrapExternal(glId, 1, 1, false);
        return cachedTexture;
    }

    private static String nvl(String a, String b) {
        return a != null ? a : b;
    }

    @Override
    public Vector2f position() {
        return pos;
    }

    @Override
    public float rotation() {
        return rotationDeg;
    }

    @Override
    public Vector2f scale() {
        return scale;
    }

    @Override
    public Vector2f size() {
        return size;
    }

    @Override
    public void setPosition(Vector2f worldPos) {
        if (worldPos == null || runtime == null) return;
        EditorState state = runtime.state();
        if (state == null) return;

        float halfW = CONTROL_TYPES.contains(typeId) ? size.x * scale.x * 0.5f : 0f;
        float halfH = CONTROL_TYPES.contains(typeId) ? size.y * scale.y * 0.5f : 0f;
        float newTLx = worldPos.x - halfW;
        float newTLy = worldPos.y - halfH;

        float parentTLx = 0f, parentTLy = 0f;
        if (parentId != 0L) {
            SceneCanvasObject parentObj = canvasObjectsById.get(parentId);
            if (parentObj != null) {
                if (CONTROL_TYPES.contains(parentObj.typeId)) {
                    float phw = parentObj.size().x * parentObj.scale().x * 0.5f;
                    float phh = parentObj.size().y * parentObj.scale().y * 0.5f;
                    parentTLx = parentObj.position().x - phw;
                    parentTLy = parentObj.position().y - phh;
                } else {
                    parentTLx = parentObj.position().x;
                    parentTLy = parentObj.position().y;
                }
            } else {
                scratchWorldCache.clear();
                Vector2f pw = worldPos2D(state, parentId, scratchWorldCache);
                parentTLx = pw.x;
                parentTLy = pw.y;
            }
        }

        float lx = newTLx - parentTLx;
        float ly = newTLy - parentTLy;
        pendingLocalX = lx;
        pendingLocalY = ly;
        pos.set(worldPos);
        pendingDirty = true;
        sendThrottled(false);
    }

    @Override
    public void setRotation(float degrees) {
        rotationDeg = degrees;
        pendingRotDeg = degrees;
        pendingDirty = true;
        sendThrottled(false);
    }

    @Override
    public void setScale(Vector2f next) {
        if (next == null) return;
        scale.set(next);
        pendingScaleX = next.x;
        pendingScaleY = next.y;
        pendingDirty = true;
        sendThrottled(false);
    }

    private void sendThrottled(boolean force) {
        if (!pendingDirty || runtime == null) return;
        long now = System.currentTimeMillis();
        if (!force && (now - lastSendAtMs) < 50L) {
            return;
        }
        lastSendAtMs = now;
        pendingDirty = false;

        EditorState state = runtime.state();
        if (state == null) return;
        var net = runtime.net();
        if (net == null) return;

        ArrayList<SceneOp> ops = new ArrayList<>(5);
        ops.add(new SceneOp.SetProperty(nodeId, "x", Float.toString(pendingLocalX)));
        ops.add(new SceneOp.SetProperty(nodeId, "y", Float.toString(pendingLocalY)));
        ops.add(new SceneOp.SetProperty(nodeId, "sx", Float.toString(pendingScaleX)));
        ops.add(new SceneOp.SetProperty(nodeId, "sy", Float.toString(pendingScaleY)));
        ops.add(new SceneOp.SetProperty(nodeId, "rz", Float.toString(pendingRotDeg)));

        net.sendOps(runtime.session(), state, ops);
    }

    void flushPending() {
        sendThrottled(true);
    }

    @Override
    public boolean contains(float x, float y) {
        float hx = (size.x * scale.x) * 0.5f;
        float hy = (size.y * scale.y) * 0.5f;
        return x >= pos.x - hx && y >= pos.y - hy && x <= pos.x + hx && y <= pos.y + hy;
    }

    @Override
    public void render(UiRenderer r, Theme theme, int px, int py) {
        if ("Sprite2D".equals(typeId)) {
            float z = canvas2d.zoom();
            int sw = Math.max(4, (int) (size.x * scale.x * z));
            int sh = Math.max(4, (int) (size.y * scale.y * z));
            int rx = px - sw / 2;
            int ry = py - sh / 2;
            Texture tex = resolveTexture();
            if (tex != null) {
                r.drawTexturedRect(tex, rx, ry, sw, sh, 0xFFFFFFFF);
            } else {
                int c = Theme.mulAlpha(Theme.toArgb(theme.accent), 0.25f);
                r.drawRect(rx, ry, sw, sh, c);
                r.drawRectOutline(rx, ry, sw, sh, 1, Theme.mulAlpha(Theme.toArgb(theme.accent), 0.8f));
            }
            return;
        }
        if (!CONTROL_TYPES.contains(typeId)) {
            int c = Theme.mulAlpha(Theme.toArgb(theme.accent), 0.8f);
            r.drawRect(px - 4, py - 4, 8, 8, c);
            return;
        }
        float z = canvas2d.zoom();
        int sw = Math.max(4, (int) (size.x * scale.x * z));
        int sh = Math.max(4, (int) (size.y * scale.y * z));
        int rx = px - sw / 2;
        int ry = py - sh / 2;
        renderControl(r, theme, rx, ry, sw, sh);
    }

    private void renderControl(UiRenderer r, Theme theme, int rx, int ry, int sw, int sh) {
        final int BG       = 0xCC1E1E2E;
        final int BG_DIM   = 0x441E1E2E;
        final int BG_GHOST = 0x221E1E2E;
        final int BORDER   = 0xFF555577;
        final int TEXT     = 0xFFEEEEFF;
        final int TEXT_DIM = 0xFF888899;
        final int ACCENT   = 0xFF4040A0;
        final int CHECK    = 0xFF66BB66;
        final int TRACK    = 0xFF333344;
        final int THUMB    = 0xFF8080CC;

        switch (typeId) {
            case "ColorRect" -> {
                r.drawRect(rx, ry, sw, sh, floatsToArgb(cachedFillR, cachedFillG, cachedFillB, cachedFillA));
            }
            case "Label", "RichTextLabel" -> {
                r.drawRect(rx, ry, sw, sh, BG);
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
                if (!cachedText.isEmpty() && sw > 8 && sh > 4)
                    r.drawText(cachedText, rx + 3, r.baselineForBox(ry, sh), TEXT);
            }
            case "Button" -> {
                r.drawRect(rx, ry, sw, sh, ACCENT);
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
                String label = cachedText.isEmpty() ? "Button" : cachedText;
                if (sw > 8 && sh > 4)
                    r.drawText(label, rx + 3, r.baselineForBox(ry, sh), TEXT);
            }
            case "TextureButton", "TextureRect" -> {
                Texture tex = resolveTexture();
                if (tex != null) {
                    r.drawTexturedRect(tex, rx, ry, sw, sh, 0xFFFFFFFF);
                } else {
                    r.drawRect(rx, ry, sw, sh, BG);
                    r.drawLine(rx + 4, ry + 4, rx + sw - 4, ry + sh - 4, 1, BORDER);
                    r.drawLine(rx + sw - 4, ry + 4, rx + 4, ry + sh - 4, 1, BORDER);
                }
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
            }
            case "CheckBox" -> {
                int boxSz = Math.min(sh - 4, 12);
                int bx = rx + 2;
                int by = ry + (sh - boxSz) / 2;
                r.drawRect(bx, by, boxSz, boxSz, BG);
                r.drawRectOutline(bx, by, boxSz, boxSz, 1, BORDER);
                if (cachedChecked)
                    r.drawRect(bx + 2, by + 2, boxSz - 4, boxSz - 4, CHECK);
                if (!cachedText.isEmpty() && sw > boxSz + 8)
                    r.drawText(cachedText, bx + boxSz + 4, r.baselineForBox(ry, sh), TEXT);
            }
            case "ProgressBar" -> {
                float frac = cachedMaxValue > cachedMinValue
                        ? Math.max(0, Math.min(1, (cachedValue - cachedMinValue) / (cachedMaxValue - cachedMinValue))) : 0;
                r.drawRect(rx, ry, sw, sh, TRACK);
                r.drawRect(rx, ry, Math.max(1, (int)(sw * frac)), sh,
                        floatsToArgb(cachedFillR, cachedFillG, cachedFillB, cachedFillA));
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
            }
            case "HSlider" -> {
                int trackY = ry + sh / 2 - 2;
                r.drawRect(rx, trackY, sw, 4, TRACK);
                float frac = cachedMaxValue > cachedMinValue
                        ? Math.max(0, Math.min(1, (cachedValue - cachedMinValue) / (cachedMaxValue - cachedMinValue))) : 0;
                int thumbX = rx + (int)(frac * (sw - 8));
                r.drawRect(thumbX, ry + 2, 8, sh - 4, THUMB);
            }
            case "VSlider" -> {
                int trackX = rx + sw / 2 - 2;
                r.drawRect(trackX, ry, 4, sh, TRACK);
                float frac = cachedMaxValue > cachedMinValue
                        ? Math.max(0, Math.min(1, (cachedValue - cachedMinValue) / (cachedMaxValue - cachedMinValue))) : 0;
                int thumbY = ry + (int)((1f - frac) * (sh - 8));
                r.drawRect(rx + 2, thumbY, sw - 4, 8, THUMB);
            }
            case "LineEdit" -> {
                r.drawRect(rx, ry, sw, sh, BG);
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
                String display = cachedText.isEmpty() ? "" : cachedText;
                if (sw > 8 && sh > 4) {
                    r.drawText(display, rx + 3, r.baselineForBox(ry, sh),
                            cachedText.isEmpty() ? TEXT_DIM : TEXT);
                    int cx = rx + 3 + Math.min(sw - 8, display.length() * 5);
                    r.drawLine(cx, ry + 3, cx, ry + sh - 3, 1, TEXT);
                }
            }
            case "HBoxContainer" -> {
                r.drawRect(rx, ry, sw, sh, BG_DIM);
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
            }
            case "VBoxContainer" -> {
                r.drawRect(rx, ry, sw, sh, BG_DIM);
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
            }
            case "GridContainer" -> {
                r.drawRect(rx, ry, sw, sh, BG_DIM);
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
            }
            default -> {
                r.drawRect(rx, ry, sw, sh, BG_GHOST);
                r.drawRectOutline(rx, ry, sw, sh, 1, BORDER);
            }
        }
    }

    private static int floatsToArgb(float fr, float fg, float fb, float fa) {
        int a = Math.max(0, Math.min(255, (int) (fa * 255)));
        int ri = Math.max(0, Math.min(255, (int) (fr * 255)));
        int gi = Math.max(0, Math.min(255, (int) (fg * 255)));
        int bi = Math.max(0, Math.min(255, (int) (fb * 255)));
        return (a << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
