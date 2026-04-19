package com.moud.client.fabric.editor.panels;

import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.CanvasEditor2D;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.client.fabric.render.hud.LayoutComputer;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import org.joml.Vector2f;

import java.util.*;
import java.util.stream.Collectors;

class Canvas2DSync {
    static final int VIRTUAL_SCREEN_W = 1920;
    static final int VIRTUAL_SCREEN_H = 1080;

    private final EditorRuntime runtime;
    private final CanvasEditor2D canvas2d;
    private final Map<Long, SceneCanvasObject> objects;

    Canvas2DSync(EditorRuntime runtime, CanvasEditor2D canvas2d, HashMap<Long, SceneCanvasObject> objects) {
        this.runtime = runtime;
        this.canvas2d = canvas2d;
        this.objects = objects;
    }

    void render2DCanvas(Ui ui, UiRenderer r, UiContext ctx, Theme theme, int x, int y, int w, int h, boolean interactive) {
        if (runtime == null || runtime.state() == null || runtime.state().scene == null) return;

        EditorState state = runtime.state();
        canvas2d.setGridSize(runtime.gridSnapStep());
        canvas2d.setSnapToGrid(runtime.gridSnapEnabled());

        boolean isUI = state.scene.nodes().stream().anyMatch(n -> n != null && "CanvasLayer".equals(n.type()));
        double fitZoom = w > 0 && h > 0 ? Math.max(0.05, Math.min((double) w / VIRTUAL_SCREEN_W, (double) h / VIRTUAL_SCREEN_H) * 0.9) : 0.5;
        double defaultZoom = isUI ? fitZoom : 32.0;

        String sid = state.activeSceneId == null ? "" : state.activeSceneId;
        double[] cam = state.sceneCanvasStates.computeIfAbsent(sid, k -> new double[]{0.0, 0.0, defaultZoom});

        if (Float.isFinite((float) cam[2]) && cam[2] > 0f) {
            canvas2d.setAutoCenterOnFirstRender(false);
            canvas2d.setZoom((float) cam[2]);
            canvas2d.setPanOffset((float) cam[0], (float) cam[1]);
        } else {
            canvas2d.setZoom((float) defaultZoom);
            canvas2d.setAutoCenterOnFirstRender(!isUI);
            if (isUI) {
                canvas2d.setPanOffset((float) (w / 2.0 - VIRTUAL_SCREEN_W * defaultZoom / 2.0),
                        (float) (h / 2.0 - VIRTUAL_SCREEN_H * defaultZoom / 2.0));
            }
        }

        syncCanvasObjects(state);
        syncCanvasSelectionFromEditor(state);

        if (runtime.consumeFrameSelected2DRequest()) {
            canvas2d.frameSelection(w, h);
        }

        canvas2d.render(r, ctx, ui != null ? ui.input() : null, theme, x, y, w, h, interactive);

        if (isUI) {
            clampPanToFrame(w, h);
            renderScreenFrameOverlay(r, x, y, w, h);
        }

        cam[0] = canvas2d.panOffset().x;
        cam[1] = canvas2d.panOffset().y;
        cam[2] = canvas2d.zoom();

        syncEditorSelectionFromCanvas(state);
    }

    void syncCanvasObjects(EditorState state) {
        if (state.scene.nodes() == null) return;

        Map<Long, Vector2f> worldCache = new HashMap<>();
        Map<Long, int[]> rects = computeControlRects(state);
        Set<Long> processedIds = new HashSet<>();

        for (var node : state.scene.nodes()) {
            if (node == null || node.nodeId() <= 0L || !isCanvas2DNode(node.type())) continue;

            long id = node.nodeId();
            SceneCanvasObject obj = objects.computeIfAbsent(id, k -> {
                var newObj = new SceneCanvasObject(k);
                canvas2d.addObject(newObj);
                return newObj;
            });

            obj.bind(runtime, canvas2d, (HashMap<Long, SceneCanvasObject>) objects);

            if (CanvasNodeTypes.CONTROL_TYPES.contains(node.type())) {
                obj.syncFromSnapshot(node, null, rects.get(id));
            } else {
                obj.syncFromSnapshot(node, worldPos2D(state, id, worldCache), null);
            }
            processedIds.add(id);
        }

        objects.entrySet().removeIf(entry -> {
            if (!processedIds.contains(entry.getKey())) {
                canvas2d.removeObject(entry.getValue());
                return true;
            }
            return false;
        });
    }

    static Map<Long, int[]> computeControlRects(EditorState state) {
        Map<Long, int[]> rects = new HashMap<>();
        if (state == null || state.scene == null) return rects;

        var childrenByParent = state.scene.nodes().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(SceneSnapshot.NodeSnapshot::parentId));

        var root = primaryRoot(state.scene.nodes());
        if (root != null && "2d".equalsIgnoreCase(getProp(root, "scene_mode"))) {
            buildControlRectsUnder(root, 0, 0, VIRTUAL_SCREEN_W, VIRTUAL_SCREEN_H, childrenByParent, rects);
        }

        state.scene.nodes().stream()
                .filter(n -> n != null && "CanvasLayer".equals(n.type()))
                .forEach(n -> buildControlRectsUnder(n, 0, 0, VIRTUAL_SCREEN_W, VIRTUAL_SCREEN_H, childrenByParent, rects));

        return rects;
    }

    private static SceneSnapshot.NodeSnapshot primaryRoot(Collection<SceneSnapshot.NodeSnapshot> nodes) {
        if (nodes == null) return null;
        SceneSnapshot.NodeSnapshot first = null;
        for (var n : nodes) {
            if (n == null || n.parentId() != 0L) continue;
            if (first == null) first = n;
            if ("Root".equals(n.type())) return n;
        }
        return first;
    }

    static void buildControlRectsUnder(SceneSnapshot.NodeSnapshot parent, int px, int py, int pw, int ph,
                                       Map<Long, List<SceneSnapshot.NodeSnapshot>> childMap, Map<Long, int[]> rects) {
        var children = childMap.getOrDefault(parent.nodeId(), List.of());
        if (children.isEmpty()) return;

        boolean isContainer = LayoutComputer.isLayoutContainer(parent.type());
        var layout = isContainer ? LayoutComputer.compute(parent, children, px, py, pw, ph) : null;

        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            if (child == null || !CanvasNodeTypes.CONTROL_TYPES.contains(child.type())) continue;

            int[] rect;
            if (isContainer && layout != null && i < layout.size()) {
                var cr = layout.get(i);
                rect = new int[]{cr.x(), cr.y(), cr.w(), cr.h()};
            } else {
                rect = resolveControlRect(child, px, py, pw, ph);
            }

            rects.put(child.nodeId(), rect);
            buildControlRectsUnder(child, rect[0], rect[1], rect[2], rect[3], childMap, rects);
        }
    }

    static int[] resolveControlRect(SceneSnapshot.NodeSnapshot node, int px, int py, int pw, int ph) {
        float al = parseFloat(getProp(node, "anchor_left"), 0f);
        float ar = parseFloat(getProp(node, "anchor_right"), 0f);
        float at = parseFloat(getProp(node, "anchor_top"), 0f);
        float ab = parseFloat(getProp(node, "anchor_bottom"), 0f);
        float ml = parseFloat(getProp(node, "margin_left"), 0f);
        float mr = parseFloat(getProp(node, "margin_right"), 0f);
        float mt = parseFloat(getProp(node, "margin_top"), 0f);
        float mb = parseFloat(getProp(node, "margin_bottom"), 0f);
        float nx = parseFloat(getProp(node, "x"), 0f);
        float ny = parseFloat(getProp(node, "y"), 0f);
        float nw = parseFloat(getProp(node, "w"), 100f);
        float nh = parseFloat(getProp(node, "h"), 30f);
        float sx = parseFloat(getProp(node, "sx"), 1f);
        float sy = parseFloat(getProp(node, "sy"), 1f);

        int rx = (int) (px + al * pw + ml + nx);
        int ry = (int) (py + at * ph + mt + ny);

        int rw = Math.abs(ar - al) > 0.001f ? Math.max(1, (int) (px + ar * pw + mr) - rx) : Math.max(1, (int) (nw * sx));
        int rh = Math.abs(ab - at) > 0.001f ? Math.max(1, (int) (py + ab * ph + mb) - ry) : Math.max(1, (int) (nh * sy));

        return new int[]{rx, ry, rw, rh};
    }

    void syncCanvasSelectionFromEditor(EditorState state) {
        var expected = new HashSet<CanvasEditor2D.CanvasObject>();

        if (state.selectedIds != null && !state.selectedIds.isEmpty()) {
            state.selectedIds.forEach(id -> {
                if (objects.containsKey(id)) expected.add(objects.get(id));
            });
        } else if (state.selectedId > 0L && objects.containsKey(state.selectedId)) {
            expected.add(objects.get(state.selectedId));
        }

        if (!expected.equals(canvas2d.selection())) {
            canvas2d.setSelection(expected);
        }
    }

    void syncEditorSelectionFromCanvas(EditorState state) {
        var sel = canvas2d.selection();
        if (sel == null) return;

        var nextIds = new LinkedHashSet<Long>();
        long primary = 0L;

        for (var obj : sel) {
            if (obj instanceof SceneCanvasObject sco && sco.nodeId > 0L) {
                nextIds.add(sco.nodeId);
                if (primary == 0L) primary = sco.nodeId;
            }
        }

        if (!nextIds.equals(state.selectedIds)) {
            state.selectedIds.clear();
            state.selectedIds.addAll(nextIds);
        }
        state.selectedId = primary;
    }

    void onCanvasTransformCommitted(String desc, Map<CanvasEditor2D.CanvasObject, CanvasEditor2D.TransformSnapshot> before,
                                    Map<CanvasEditor2D.CanvasObject, CanvasEditor2D.TransformSnapshot> after) {
        if (runtime == null || runtime.state() == null || runtime.state().scene == null) return;
        var state = runtime.state();

        var undoOps = new ArrayList<SceneOp>();
        var redoOps = new ArrayList<SceneOp>();

        for (var e : before.entrySet()) {
            if (!(e.getKey() instanceof SceneCanvasObject sco) || e.getValue() == null) continue;

            var a = after.get(sco);
            if (a == null) continue;

            sco.flushPending();
            var node = state.scene.getNode(sco.nodeId);
            if (node == null) continue;

            var b = e.getValue();
            var pBefore = parentWorldFromMapsOrState(state, node.parentId(), before, null);
            var pAfter = parentWorldFromMapsOrState(state, node.parentId(), after, pBefore);

            float bx = b.x() - (pBefore == null ? 0f : pBefore.x);
            float by = b.y() - (pBefore == null ? 0f : pBefore.y);
            float ax = a.x() - (pAfter == null ? 0f : pAfter.x);
            float ay = a.y() - (pAfter == null ? 0f : pAfter.y);

            if (Math.abs(bx - ax) > 1e-6f || Math.abs(by - ay) > 1e-6f || Math.abs(b.rotationDeg() - a.rotationDeg()) > 1e-4f ||
                    Math.abs(b.scaleX() - a.scaleX()) > 1e-6f || Math.abs(b.scaleY() - a.scaleY()) > 1e-6f) {

                long id = sco.nodeId;
                undoOps.addAll(List.of(
                        new SceneOp.SetProperty(id, "x", String.valueOf(bx)),
                        new SceneOp.SetProperty(id, "y", String.valueOf(by)),
                        new SceneOp.SetProperty(id, "sx", String.valueOf(b.scaleX())),
                        new SceneOp.SetProperty(id, "sy", String.valueOf(b.scaleY())),
                        new SceneOp.SetProperty(id, "rz", String.valueOf(b.rotationDeg()))
                ));

                redoOps.addAll(List.of(
                        new SceneOp.SetProperty(id, "x", String.valueOf(ax)),
                        new SceneOp.SetProperty(id, "y", String.valueOf(ay)),
                        new SceneOp.SetProperty(id, "sx", String.valueOf(a.scaleX())),
                        new SceneOp.SetProperty(id, "sy", String.valueOf(a.scaleY())),
                        new SceneOp.SetProperty(id, "rz", String.valueOf(a.rotationDeg()))
                ));
            }
        }

        if (!undoOps.isEmpty()) runtime.history().push(undoOps, redoOps);
    }

    static Vector2f parentWorldFromMapsOrState(EditorState state, long pid, Map<CanvasEditor2D.CanvasObject, CanvasEditor2D.TransformSnapshot> map, Vector2f fallback) {
        if (pid <= 0L) return new Vector2f();

        if (map != null) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof SceneCanvasObject sco && sco.nodeId == pid) {
                    return new Vector2f(entry.getValue().x(), entry.getValue().y());
                }
            }
        }
        return fallback != null ? new Vector2f(fallback) : worldPos2D(state, pid, new HashMap<>());
    }

    static Vector2f worldPos2D(EditorState state, long nodeId, Map<Long, Vector2f> cache) {
        if (state == null || state.scene == null || nodeId <= 0L) return new Vector2f();
        if (cache.containsKey(nodeId)) return cache.get(nodeId);

        var node = state.scene.getNode(nodeId);
        if (node == null) return new Vector2f();

        var out = new Vector2f(parseFloat(getProp(node, "x"), 0f), parseFloat(getProp(node, "y"), 0f));
        if (node.parentId() != 0L) out.add(worldPos2D(state, node.parentId(), cache));

        cache.put(nodeId, out);
        return out;
    }

    private void clampPanToFrame(int w, int h) {
        float zoom = canvas2d.zoom();
        if (zoom <= 1e-6f) return;

        float fw = VIRTUAL_SCREEN_W * zoom;
        float fh = VIRTUAL_SCREEN_H * zoom;

        var pan = canvas2d.panOffset();
        float px = Math.max(w - fw * 1.5f, Math.min(fw * 0.5f + w, pan.x));
        float py = Math.max(h - fh * 1.5f, Math.min(fh * 0.5f + h, pan.y));

        if (px != pan.x || py != pan.y) canvas2d.setPanOffset(px, py);
    }

    private void renderScreenFrameOverlay(UiRenderer r, int x, int y, int w, int h) {
        var tl = new Vector2f();
        var br = new Vector2f();
        canvas2d.worldToScreen(0f, 0f, w, h, tl);
        canvas2d.worldToScreen(VIRTUAL_SCREEN_W, VIRTUAL_SCREEN_H, w, h, br);

        int sx = x + (int) tl.x, sy = y + (int) tl.y;
        int sw = (int) (br.x - tl.x), sh = (int) (br.y - tl.y);
        if (sw < 1 || sh < 1) return;

        int borderCol = 0xFFFFE066;
        r.drawLine(sx, sy, sx + sw, sy, 2, borderCol);
        r.drawLine(sx, sy + sh, sx + sw, sy + sh, 2, borderCol);
        r.drawLine(sx, sy, sx, sy + sh, 2, borderCol);
        r.drawLine(sx + sw, sy, sx + sw, sy + sh, 2, borderCol);

        int safeX = sx + (int) (sw * 0.05), safeY = sy + (int) (sh * 0.05);
        int safeW = (int) (sw * 0.90), safeH = (int) (sh * 0.90);
        int safeCol = 0x66A0A0FF;

        r.drawLine(safeX, safeY, safeX + safeW, safeY, 1, safeCol);
        r.drawLine(safeX, safeY + safeH, safeX + safeW, safeY + safeH, 1, safeCol);
        r.drawLine(safeX, safeY, safeX, safeY + safeH, 1, safeCol);
        r.drawLine(safeX + safeW, safeY, safeX + safeW, safeY + safeH, 1, safeCol);

        r.drawText("1920 × 1080", sx + 6, sy - 14, borderCol);
        r.drawText("safe", safeX + 4, safeY + 2, safeCol);
    }

    static boolean isCanvas2DNode(String typeId) {
        return typeId != null && CanvasNodeTypes.CANVAS_2D_TYPES.contains(typeId);
    }

    static String getProp(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || node.properties() == null) return null;
        for (var p : node.properties()) {
            if (p != null && key.equals(p.key())) return p.value();
        }
        return null;
    }

    static float parseFloat(String s, float def) {
        if (s == null) return def;
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }
}