package com.moud.client.fabric.render.hud;

import com.moud.client.fabric.editor.net.HudEditBus;
import com.moud.client.fabric.editor.overlay.EditorContext;
import com.moud.client.fabric.editor.overlay.EditorOverlayBus;
import com.moud.client.fabric.scene.ClientPropertyOverrides;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HudSelectionOverlay {
    private static final Set<String> CONTROL_TYPES = Set.of(
            "CanvasItem", "Control", "HBoxContainer", "VBoxContainer", "GridContainer",
            "MarginContainer", "ScrollContainer", "PanelContainer", "Label", "RichTextLabel",
            "TextureRect", "AnimatedTextureRect", "ColorRect", "ProgressBar", "Button",
            "TextureButton", "CheckBox", "HSlider", "VSlider", "LineEdit"
    );

    private static final int HANDLE_SIZE = 10;
    private static final int BORDER_ARGB = 0xFFFFBB33;
    private static final int HANDLE_FILL_ARGB = 0xFFFFBB33;
    private static final int HANDLE_BORDER_ARGB = 0xFF202020;
    private static final int ROT_HANDLE_ARGB = 0xFF66CCFF;
    private static final int ROT_OFFSET_PX = 22;

    private enum Handle { NONE, BODY, NW, N, NE, E, SE, S, SW, W, ROT }

    private static long dragNodeId = 0L;
    private static Handle dragHandle = Handle.NONE;
    private static double dragMouseRawX, dragMouseRawY, dragStartAngleRad;
    private static float dragStartX, dragStartY, dragStartW, dragStartH, dragStartRz;

    private static boolean pLeft, pRight, pUp, pDown;

    private HudSelectionOverlay() {}

    public static boolean isDragging() {
        return dragHandle != Handle.NONE;
    }

    public static boolean handleInput(double mx, double my, boolean down, boolean pressedEdge, boolean releasedEdge) {
        var ctx = EditorOverlayBus.get();
        if (ctx == null || !ctx.isActive()) {
            cancelDrag();
            return false;
        }

        long selId = ctx.selectedNodeId();
        var target = findControlTarget(selId);

        if (dragHandle != Handle.NONE && target != null && dragNodeId == selId) {
            if (down) { updateDrag(mx, my); return true; }
            if (releasedEdge) { commitDrag(target); endDrag(); return true; }
            cancelDrag();
            return false;
        }

        if (!pressedEdge || !ctx.isMouseOverViewport(mx, my)) return false;

        if (target != null) {
            var rect = resolveAbsoluteRect(target);
            if (rect != null) {
                var hit = hitTest(rect, (int) mx, (int) my);
                if (hit != Handle.NONE) {
                    if (isLayoutManaged(target)) {
                        var overlay = ctx.overlay();
                        if (overlay != null && overlay.getRuntime() != null) {
                            overlay.getRuntime().requestToast("Layout containers manage child positions automatically.", false, 2000);
                        }
                        return true;
                    }
                    beginDrag(target, hit, mx, my);
                    return true;
                }
            }
        }

        long picked = pickControlAt(mx, my);
        if (picked == 0L) {
            if (selId != 0L) {
                ctx.setSelectedNodeId(0L);
                if (ctx.overlay() != null) ctx.overlay().onHudPickSelected(0L);
            }
            return false;
        }

        ctx.setSelectedNodeId(picked);
        if (ctx.overlay() != null) ctx.overlay().onHudPickSelected(picked);

        var pickedNode = findNodeById(picked);
        if (pickedNode != null && !isLayoutManaged(pickedNode)) {
            beginDrag(pickedNode, Handle.BODY, mx, my);
        }
        return true;
    }

    private static long pickControlAt(double mx, double my) {
        var rects = HudCanvasRenderer.lastControlRects;
        if (rects == null || rects.isEmpty()) return 0L;

        long bestId = 0L;
        int bestArea = Integer.MAX_VALUE;

        for (var e : rects.entrySet()) {
            var r = e.getValue();
            if (mx >= r.x() && mx < r.x() + r.w() && my >= r.y() && my < r.y() + r.h()) {
                int area = r.w() * r.h();
                if (area <= bestArea) {
                    bestArea = area;
                    bestId = e.getKey();
                }
            }
        }
        return bestId;
    }

    public static void handleNudgeInput(long window) {
        var ctx = EditorOverlayBus.get();
        if (ctx == null || !ctx.isActive() || (ctx.overlay() != null && ctx.overlay().isAnyTextFieldFocused())) {
            pLeft = pRight = pUp = pDown = false;
            return;
        }

        var target = findControlTarget(ctx.selectedNodeId());
        if (target == null || isLayoutManaged(target)) {
            pLeft = pRight = pUp = pDown = false;
            return;
        }

        boolean l = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
        boolean u = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
        boolean d = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        int dx = (r && !pRight ? 1 : 0) - (l && !pLeft ? 1 : 0);
        int dy = (d && !pDown ? 1 : 0) - (u && !pUp ? 1 : 0);

        pLeft = l; pRight = r; pUp = u; pDown = d;

        if (dx == 0 && dy == 0) return;

        int step = shift ? 8 : 1;
        float nx = rawProp(target, "x", 0f) + (dx * step);
        float ny = rawProp(target, "y", 0f) + (dy * step);

        var ops = new ArrayList<SceneOp>(2);
        if (dx != 0) ops.add(new SceneOp.SetProperty(target.nodeId(), "x", Float.toString(nx)));
        if (dy != 0) ops.add(new SceneOp.SetProperty(target.nodeId(), "y", Float.toString(ny)));

        if (!ops.isEmpty()) HudEditBus.pushAll(ops);
    }

    public static void render(DrawContext dc, MinecraftClient client) {
        var ctx = EditorOverlayBus.get();
        if (ctx == null || !ctx.isActive()) return;

        var target = findControlTarget(ctx.selectedNodeId());
        if (target == null) return;

        int[] rect = resolveAbsoluteRect(target);
        if (rect == null) return;

        float scale = (float) client.getWindow().getScaleFactor();
        dc.getMatrices().push();
        dc.getMatrices().scale(1f / scale, 1f / scale, 1f);
        try {
            drawBorder(dc, rect);
            drawHandles(dc, rect);
        } finally {
            dc.getMatrices().pop();
        }
    }

    private static void beginDrag(SceneSnapshot.NodeSnapshot node, Handle handle, double mx, double my) {
        dragNodeId = node.nodeId();
        dragHandle = handle;
        dragMouseRawX = mx;
        dragMouseRawY = my;
        dragStartX = rawProp(node, "x", 0f);
        dragStartY = rawProp(node, "y", 0f);
        dragStartW = rawProp(node, "w", 100f);
        dragStartH = rawProp(node, "h", 30f);
        dragStartRz = rawProp(node, "rz", 0f);

        if (handle == Handle.ROT) {
            var r = HudCanvasRenderer.lastControlRects.get(node.nodeId());
            dragStartAngleRad = r != null ? Math.atan2(my - (r.y() + r.h() / 2.0), mx - (r.x() + r.w() / 2.0)) : 0;
        }
    }

    private static void updateDrag(double mx, double my) {
        float dx = (float) (mx - dragMouseRawX);
        float dy = (float) (my - dragMouseRawY);

        switch (dragHandle) {
            case BODY -> { put("x", dragStartX + dx); put("y", dragStartY + dy); }
            case NW   -> { put("x", dragStartX + dx); put("y", dragStartY + dy); put("w", Math.max(4f, dragStartW - dx)); put("h", Math.max(4f, dragStartH - dy)); }
            case N    -> { put("y", dragStartY + dy); put("h", Math.max(4f, dragStartH - dy)); }
            case NE   -> { put("y", dragStartY + dy); put("w", Math.max(4f, dragStartW + dx)); put("h", Math.max(4f, dragStartH - dy)); }
            case E    -> { put("w", Math.max(4f, dragStartW + dx)); }
            case SE   -> { put("w", Math.max(4f, dragStartW + dx)); put("h", Math.max(4f, dragStartH + dy)); }
            case S    -> { put("h", Math.max(4f, dragStartH + dy)); }
            case SW   -> { put("x", dragStartX + dx); put("w", Math.max(4f, dragStartW - dx)); put("h", Math.max(4f, dragStartH + dy)); }
            case W    -> { put("x", dragStartX + dx); put("w", Math.max(4f, dragStartW - dx)); }
            case ROT  -> {
                var r = HudCanvasRenderer.lastControlRects.get(dragNodeId);
                if (r != null) {
                    double now = Math.atan2(my - (r.y() + r.h() / 2.0), mx - (r.x() + r.w() / 2.0));
                    put("rz", dragStartRz + (float) Math.toDegrees(now - dragStartAngleRad));
                }
            }
            case NONE -> {}
        }
    }

    private static void commitDrag(SceneSnapshot.NodeSnapshot node) {
        var ops = new ArrayList<SceneOp>(5);
        flush(node.nodeId(), "x", ops);
        flush(node.nodeId(), "y", ops);
        flush(node.nodeId(), "w", ops);
        flush(node.nodeId(), "h", ops);
        flush(node.nodeId(), "rz", ops);
        if (!ops.isEmpty()) HudEditBus.pushAll(ops);
    }

    private static void cancelDrag() {
        if (dragNodeId != 0L) {
            ClientPropertyOverrides.put(dragNodeId, "x", null);
            ClientPropertyOverrides.put(dragNodeId, "y", null);
            ClientPropertyOverrides.put(dragNodeId, "w", null);
            ClientPropertyOverrides.put(dragNodeId, "h", null);
            ClientPropertyOverrides.put(dragNodeId, "rz", null);
        }
        endDrag();
    }

    private static void endDrag() {
        dragNodeId = 0L;
        dragHandle = Handle.NONE;
    }

    private static void put(String key, float value) {
        ClientPropertyOverrides.put(dragNodeId, key, Float.toString(value));
    }

    private static void flush(long id, String key, List<SceneOp> ops) {
        var v = ClientPropertyOverrides.get(id, key);
        if (v == null) return;
        ops.add(new SceneOp.SetProperty(id, key, v));
        ClientPropertyOverrides.put(id, key, null);
    }

    private static Handle hitTest(int[] rect, int mx, int my) {
        int x = rect[0], y = rect[1], w = rect[2], h = rect[3], hs = HANDLE_SIZE, half = hs / 2;
        int cx = x + w / 2, cy = y - ROT_OFFSET_PX, rr = half + 2;

        if (Math.pow(mx - cx, 2) + Math.pow(my - cy, 2) <= rr * rr) return Handle.ROT;

        if (in(mx, my, x - half, y - half, hs, hs)) return Handle.NW;
        if (in(mx, my, cx - half, y - half, hs, hs)) return Handle.N;
        if (in(mx, my, x + w - half, y - half, hs, hs)) return Handle.NE;
        if (in(mx, my, x - half, y + h / 2 - half, hs, hs)) return Handle.W;
        if (in(mx, my, x + w - half, y + h / 2 - half, hs, hs)) return Handle.E;
        if (in(mx, my, x - half, y + h - half, hs, hs)) return Handle.SW;
        if (in(mx, my, cx - half, y + h - half, hs, hs)) return Handle.S;
        if (in(mx, my, x + w - half, y + h - half, hs, hs)) return Handle.SE;
        if (in(mx, my, x, y, w, h)) return Handle.BODY;

        return Handle.NONE;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void drawBorder(DrawContext dc, int[] rect) {
        int x = rect[0], y = rect[1], w = rect[2], h = rect[3];
        dc.fill(x, y, x + w, y + 2, BORDER_ARGB);
        dc.fill(x, y + h - 2, x + w, y + h, BORDER_ARGB);
        dc.fill(x, y, x + 2, y + h, BORDER_ARGB);
        dc.fill(x + w - 2, y, x + w, y + h, BORDER_ARGB);
    }

    private static void drawHandles(DrawContext dc, int[] rect) {
        int x = rect[0], y = rect[1], w = rect[2], h = rect[3], hs = HANDLE_SIZE, half = hs / 2;
        int cx = x + w / 2, cy = y - ROT_OFFSET_PX, rr = half + 1;

        dc.fill(cx, cy, cx + 1, y, ROT_HANDLE_ARGB);
        dc.fill(cx - rr, cy - rr, cx + rr, cy + rr, ROT_HANDLE_ARGB);
        dc.fill(cx - rr + 1, cy - rr + 1, cx + rr - 1, cy + rr - 1, 0xFF1A4A66);

        int[][] handles = {
                {x - half, y - half}, {cx - half, y - half}, {x + w - half, y - half},
                {x - half, y + h / 2 - half}, {x + w - half, y + h / 2 - half},
                {x - half, y + h - half}, {cx - half, y + h - half}, {x + w - half, y + h - half}
        };

        for (int[] p : handles) {
            int hx = p[0], hy = p[1];
            dc.fill(hx, hy, hx + hs, hy + hs, HANDLE_FILL_ARGB);
            dc.fill(hx, hy, hx + hs, hy + 1, HANDLE_BORDER_ARGB);
            dc.fill(hx, hy + hs - 1, hx + hs, hy + hs, HANDLE_BORDER_ARGB);
            dc.fill(hx, hy, hx + 1, hy + hs, HANDLE_BORDER_ARGB);
            dc.fill(hx + hs - 1, hy, hx + hs, hy + hs, HANDLE_BORDER_ARGB);
        }
    }

    private static SceneSnapshot.NodeSnapshot findNodeById(long id) {
        if (id <= 0L) return null;
        return ClientSceneBus.copyNodes().stream()
                .filter(n -> n != null && n.nodeId() == id).findFirst().orElse(null);
    }

    private static SceneSnapshot.NodeSnapshot findControlTarget(long id) {
        var node = findNodeById(id);
        return node != null && CONTROL_TYPES.contains(node.type()) ? node : null;
    }

    private static boolean isLayoutManaged(SceneSnapshot.NodeSnapshot node) {
        var parent = findNodeById(node.parentId());
        return parent != null && LayoutComputer.isLayoutContainer(parent.type());
    }

    private static int[] resolveAbsoluteRect(SceneSnapshot.NodeSnapshot node) {
        var r = HudCanvasRenderer.lastControlRects.get(node.nodeId());
        return r != null ? new int[]{r.x(), r.y(), r.w(), r.h()} : null;
    }

    private static float rawProp(SceneSnapshot.NodeSnapshot node, String key, float def) {
        if (node == null || node.properties() == null) return def;
        for (var p : node.properties()) {
            if (p != null && key.equals(p.key()) && p.value() != null) {
                try { return Float.parseFloat(p.value()); }
                catch (NumberFormatException ignored) {}
            }
        }
        return def;
    }
}