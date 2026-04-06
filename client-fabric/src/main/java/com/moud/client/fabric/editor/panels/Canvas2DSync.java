package com.moud.client.fabric.editor.panels;

import com.miry.ui.Ui;
import com.miry.ui.UiContext;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.CanvasEditor2D;
import com.moud.client.fabric.editor.state.EditorRuntime;
import com.moud.client.fabric.editor.state.EditorState;
import com.moud.net.protocol.SceneOp;
import com.moud.net.protocol.SceneSnapshot;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class Canvas2DSync {
    static final Set<String> CANVAS_2D_TYPES = Set.of(
            "Node2D", "Sprite2D", "Camera2D",
            "CanvasItem", "Control", "CanvasLayer",
            "HBoxContainer", "VBoxContainer", "GridContainer",
            "MarginContainer", "ScrollContainer", "PanelContainer",
            "Label", "RichTextLabel", "TextureRect", "ColorRect", "ProgressBar",
            "Button", "TextureButton", "CheckBox", "HSlider", "VSlider", "LineEdit");

    static final Set<String> CONTROL_TYPES = Set.of(
            "CanvasItem", "Control",
            "HBoxContainer", "VBoxContainer", "GridContainer",
            "MarginContainer", "ScrollContainer", "PanelContainer",
            "Label", "RichTextLabel", "TextureRect", "ColorRect", "ProgressBar",
            "Button", "TextureButton", "CheckBox", "HSlider", "VSlider", "LineEdit");

    static final int VIRTUAL_SCREEN_W = 1920;
    static final int VIRTUAL_SCREEN_H = 1080;

    private final EditorRuntime runtime;
    private final CanvasEditor2D canvas2d;
    private final HashMap<Long, SceneCanvasObject> canvasObjectsById;

    Canvas2DSync(EditorRuntime runtime, CanvasEditor2D canvas2d, HashMap<Long, SceneCanvasObject> canvasObjectsById) {
        this.runtime = runtime;
        this.canvas2d = canvas2d;
        this.canvasObjectsById = canvasObjectsById;
    }

    void render2DCanvas(Ui ui,
                        UiRenderer r,
                        UiContext uiContext,
                        Theme theme,
                        int x,
                        int y,
                        int w,
                        int h,
                        boolean interactive) {
        if (runtime == null) {
            return;
        }
        EditorState state = runtime.state();
        if (state == null || state.scene == null) {
            return;
        }

        canvas2d.setGridSize(runtime.gridSnapStep());
        canvas2d.setSnapToGrid(runtime.gridSnapEnabled());

        boolean hasUiLayers = state.scene != null && state.scene.nodes() != null &&
                state.scene.nodes().stream().anyMatch(n -> n != null && "CanvasLayer".equals(n.type()));
        double uiFitZoom = w > 0 ? Math.max(0.1, (double) w / VIRTUAL_SCREEN_W) : 0.5;
        double defaultZoom = hasUiLayers ? uiFitZoom : 32.0;

        String sceneId = state.activeSceneId == null ? "" : state.activeSceneId;
        double[] saved = state.sceneCanvasStates.computeIfAbsent(sceneId, ignored -> new double[]{0.0, 0.0, defaultZoom});

        float savedPanX = (float) saved[0];
        float savedPanY = (float) saved[1];
        float savedZoom = (float) saved[2];
        if (Float.isFinite(savedZoom) && savedZoom > 0.0f) {
            canvas2d.setAutoCenterOnFirstRender(false);
            canvas2d.setZoom(savedZoom);
            canvas2d.setPanOffset(savedPanX, savedPanY);
        } else {
            canvas2d.setAutoCenterOnFirstRender(true);
            canvas2d.setZoom((float) defaultZoom);
        }

        syncCanvasObjects(state);
        syncCanvasSelectionFromEditor(state);

        if (runtime.consumeFrameSelected2DRequest()) {
            canvas2d.frameSelection(w, h);
        }

        canvas2d.render(r, uiContext, ui != null ? ui.input() : null, theme, x, y, w, h, interactive);

        Vector2f pan = canvas2d.panOffset();
        saved[0] = pan.x;
        saved[1] = pan.y;
        saved[2] = canvas2d.zoom();

        syncEditorSelectionFromCanvas(state);
    }

    void syncCanvasObjects(EditorState state) {
        if (state == null || state.scene == null || state.scene.nodes() == null) {
            return;
        }
        HashMap<Long, SceneCanvasObject> next = new HashMap<>();
        HashMap<Long, Vector2f> worldCache = new HashMap<>();
        HashMap<Long, int[]> controlRects = computeControlRects(state);
        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null || node.nodeId() <= 0L || !isCanvas2DNode(node.type())) {
                continue;
            }
            SceneCanvasObject obj = canvasObjectsById.get(node.nodeId());
            if (obj == null) {
                obj = new SceneCanvasObject(node.nodeId());
                canvasObjectsById.put(node.nodeId(), obj);
                canvas2d.addObject(obj);
            }
            obj.bind(runtime, canvas2d, canvasObjectsById);
            if (CONTROL_TYPES.contains(node.type())) {
                obj.syncFromSnapshot(node, null, controlRects.get(node.nodeId()));
            } else {
                Vector2f world = worldPos2D(state, node.nodeId(), worldCache);
                obj.syncFromSnapshot(node, world, null);
            }
            next.put(node.nodeId(), obj);
        }

        for (var entry : new HashMap<>(canvasObjectsById).entrySet()) {
            long id = entry.getKey();
            if (!next.containsKey(id)) {
                SceneCanvasObject obj = entry.getValue();
                canvas2d.removeObject(obj);
                canvasObjectsById.remove(id);
            }
        }
    }

    static HashMap<Long, int[]> computeControlRects(EditorState state) {
        HashMap<Long, int[]> rects = new HashMap<>();
        if (state == null || state.scene == null) return rects;

        HashMap<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent = new HashMap<>();
        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null) continue;
            childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node);
        }

        for (SceneSnapshot.NodeSnapshot node : state.scene.nodes()) {
            if (node == null || !"CanvasLayer".equals(node.type())) continue;
            buildControlRectsUnder(node.nodeId(), 0, 0, VIRTUAL_SCREEN_W, VIRTUAL_SCREEN_H,
                    childrenByParent, rects);
        }
        return rects;
    }

    static void buildControlRectsUnder(long parentId,
                                       int parentX, int parentY,
                                       int parentW, int parentH,
                                       HashMap<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent,
                                       HashMap<Long, int[]> rects) {
        for (SceneSnapshot.NodeSnapshot child : childrenByParent.getOrDefault(parentId, List.of())) {
            if (child == null || child.type() == null || !CONTROL_TYPES.contains(child.type())) continue;
            int[] rect = resolveControlRect(child, parentX, parentY, parentW, parentH);
            rects.put(child.nodeId(), rect);
            buildControlRectsUnder(child.nodeId(), rect[0], rect[1], rect[2], rect[3],
                    childrenByParent, rects);
        }
    }

    static int[] resolveControlRect(SceneSnapshot.NodeSnapshot node,
                                   int parentX, int parentY,
                                   int parentW, int parentH) {
        float al = parseFloat(getProp(node, "anchor_left"),   0f);
        float ar = parseFloat(getProp(node, "anchor_right"),  0f);
        float at = parseFloat(getProp(node, "anchor_top"),    0f);
        float ab = parseFloat(getProp(node, "anchor_bottom"), 0f);
        float ml = parseFloat(getProp(node, "margin_left"),   0f);
        float mr = parseFloat(getProp(node, "margin_right"),  0f);
        float mt = parseFloat(getProp(node, "margin_top"),    0f);
        float mb = parseFloat(getProp(node, "margin_bottom"), 0f);
        float nx = parseFloat(getProp(node, "x"), 0f);
        float ny = parseFloat(getProp(node, "y"), 0f);
        float nw = parseFloat(getProp(node, "w"), 100f);
        float nh = parseFloat(getProp(node, "h"), 30f);
        float sx = parseFloat(getProp(node, "sx"), 1f);
        float sy = parseFloat(getProp(node, "sy"), 1f);

        int rx = (int)(parentX + al * parentW + ml + nx);
        int ry = (int)(parentY + at * parentH + mt + ny);
        int rw, rh;
        if (Math.abs(ar - al) > 0.001f) {
            rw = Math.max(1, (int)(parentX + ar * parentW + mr) - rx);
        } else {
            rw = Math.max(1, (int)(nw * sx));
        }
        if (Math.abs(ab - at) > 0.001f) {
            rh = Math.max(1, (int)(parentY + ab * parentH + mb) - ry);
        } else {
            rh = Math.max(1, (int)(nh * sy));
        }
        return new int[]{rx, ry, rw, rh};
    }

    void syncCanvasSelectionFromEditor(EditorState state) {
        if (state == null) {
            return;
        }
        HashSet<CanvasEditor2D.CanvasObject> next = new HashSet<>();
        if (state.selectedIds != null && !state.selectedIds.isEmpty()) {
            for (long id : state.selectedIds) {
                SceneCanvasObject obj = canvasObjectsById.get(id);
                if (obj != null) {
                    next.add(obj);
                }
            }
        } else if (state.selectedId > 0L) {
            SceneCanvasObject obj = canvasObjectsById.get(state.selectedId);
            if (obj != null) {
                next.add(obj);
            }
        }
        if (!next.equals(canvas2d.selection())) {
            canvas2d.setSelection(next);
        }
    }

    void syncEditorSelectionFromCanvas(EditorState state) {
        if (state == null) {
            return;
        }
        var sel = canvas2d.selection();
        if (sel == null) {
            return;
        }

        LinkedHashSet<Long> nextIds = new LinkedHashSet<>();
        long primary = 0L;
        for (CanvasEditor2D.CanvasObject obj : sel) {
            if (obj instanceof SceneCanvasObject sco) {
                long id = sco.nodeId;
                if (id > 0L) {
                    nextIds.add(id);
                    if (primary == 0L) {
                        primary = id;
                    }
                }
            }
        }

        if (!nextIds.equals(state.selectedIds)) {
            state.selectedIds.clear();
            state.selectedIds.addAll(nextIds);
        }
        state.selectedId = primary;
    }

    void onCanvasTransformCommitted(String description,
                                   Map<CanvasEditor2D.CanvasObject, CanvasEditor2D.TransformSnapshot> before,
                                   Map<CanvasEditor2D.CanvasObject, CanvasEditor2D.TransformSnapshot> after) {
        if (runtime == null) {
            return;
        }
        EditorState state = runtime.state();
        if (state == null || state.scene == null) {
            return;
        }

        HashMap<Long, CanvasEditor2D.TransformSnapshot> beforeById = new HashMap<>();
        HashMap<Long, CanvasEditor2D.TransformSnapshot> afterById = new HashMap<>();
        for (var e : before.entrySet()) {
            if (e.getKey() instanceof SceneCanvasObject sco && e.getValue() != null) {
                beforeById.put(sco.nodeId, e.getValue());
            }
        }
        for (var e : after.entrySet()) {
            if (e.getKey() instanceof SceneCanvasObject sco && e.getValue() != null) {
                afterById.put(sco.nodeId, e.getValue());
                sco.flushPending();
            }
        }

        ArrayList<SceneOp> undoOps = new ArrayList<>();
        ArrayList<SceneOp> redoOps = new ArrayList<>();

        for (var e : beforeById.entrySet()) {
            long nodeId = e.getKey();
            CanvasEditor2D.TransformSnapshot b = e.getValue();
            CanvasEditor2D.TransformSnapshot a = afterById.get(nodeId);
            if (a == null) {
                continue;
            }
            SceneSnapshot.NodeSnapshot node = state.scene.getNode(nodeId);
            if (node == null) {
                continue;
            }

            long parentId = node.parentId();
            Vector2f parentBefore = parentWorldFromMapsOrState(state, parentId, beforeById, null);
            Vector2f parentAfter = parentWorldFromMapsOrState(state, parentId, afterById, parentBefore);

            float bx = b.x() - (parentBefore == null ? 0.0f : parentBefore.x);
            float by = b.y() - (parentBefore == null ? 0.0f : parentBefore.y);
            float ax = a.x() - (parentAfter == null ? 0.0f : parentAfter.x);
            float ay = a.y() - (parentAfter == null ? 0.0f : parentAfter.y);

            boolean changed = Math.abs(bx - ax) > 1e-6f
                    || Math.abs(by - ay) > 1e-6f
                    || Math.abs(b.rotationDeg() - a.rotationDeg()) > 1e-4f
                    || Math.abs(b.scaleX() - a.scaleX()) > 1e-6f
                    || Math.abs(b.scaleY() - a.scaleY()) > 1e-6f;
            if (!changed) {
                continue;
            }

            undoOps.add(new SceneOp.SetProperty(nodeId, "x", Float.toString(bx)));
            undoOps.add(new SceneOp.SetProperty(nodeId, "y", Float.toString(by)));
            undoOps.add(new SceneOp.SetProperty(nodeId, "sx", Float.toString(b.scaleX())));
            undoOps.add(new SceneOp.SetProperty(nodeId, "sy", Float.toString(b.scaleY())));
            undoOps.add(new SceneOp.SetProperty(nodeId, "rz", Float.toString(b.rotationDeg())));

            redoOps.add(new SceneOp.SetProperty(nodeId, "x", Float.toString(ax)));
            redoOps.add(new SceneOp.SetProperty(nodeId, "y", Float.toString(ay)));
            redoOps.add(new SceneOp.SetProperty(nodeId, "sx", Float.toString(a.scaleX())));
            redoOps.add(new SceneOp.SetProperty(nodeId, "sy", Float.toString(a.scaleY())));
            redoOps.add(new SceneOp.SetProperty(nodeId, "rz", Float.toString(a.rotationDeg())));
        }

        if (!undoOps.isEmpty()) {
            runtime.history().push(undoOps, redoOps);
        }
    }

    static Vector2f parentWorldFromMapsOrState(EditorState state,
                                              long parentId,
                                              Map<Long, CanvasEditor2D.TransformSnapshot> map,
                                              Vector2f fallback) {
        if (parentId <= 0L) {
            return new Vector2f();
        }
        CanvasEditor2D.TransformSnapshot s = map != null ? map.get(parentId) : null;
        if (s != null) {
            return new Vector2f(s.x(), s.y());
        }
        if (fallback != null) {
            return new Vector2f(fallback);
        }
        return worldPos2D(state, parentId, new HashMap<>());
    }

    static Vector2f worldPos2D(EditorState state, long nodeId, Map<Long, Vector2f> cache) {
        if (state == null || state.scene == null || nodeId <= 0L) {
            return new Vector2f();
        }
        Vector2f cached = cache.get(nodeId);
        if (cached != null) {
            return cached;
        }
        SceneSnapshot.NodeSnapshot node = state.scene.getNode(nodeId);
        if (node == null) {
            return new Vector2f();
        }
        float lx = parseFloat(getProp(node, "x"), 0.0f);
        float ly = parseFloat(getProp(node, "y"), 0.0f);
        Vector2f out = new Vector2f(lx, ly);
        if (node.parentId() != 0L) {
            Vector2f parent = worldPos2D(state, node.parentId(), cache);
            out.add(parent);
        }
        cache.put(nodeId, out);
        return out;
    }

    static boolean isCanvas2DNode(String typeId) {
        return typeId != null && CANVAS_2D_TYPES.contains(typeId);
    }

    static String getProp(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || key == null || node.properties() == null) {
            return null;
        }
        for (SceneSnapshot.Property p : node.properties()) {
            if (p != null && key.equals(p.key())) {
                return p.value();
            }
        }
        return null;
    }

    static float parseFloat(String s, float def) {
        if (s == null) return def;
        try {
            return Float.parseFloat(s);
        } catch (Exception ignored) {
            return def;
        }
    }
}
