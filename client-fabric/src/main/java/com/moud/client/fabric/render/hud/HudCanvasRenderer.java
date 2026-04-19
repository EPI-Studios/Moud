package com.moud.client.fabric.render.hud;

import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HudCanvasRenderer {

    private static final Set<String> CONTROL_TYPES = Set.of(
            "CanvasItem", "Control",
            "HBoxContainer", "VBoxContainer", "GridContainer",
            "MarginContainer", "ScrollContainer", "PanelContainer",
            "Label", "RichTextLabel",
            "TextureRect", "AnimatedTextureRect", "ColorRect", "ProgressBar",
            "Button", "TextureButton", "CheckBox", "HSlider", "VSlider", "LineEdit");

    static volatile List<HitResult> lastHitResults = List.of();
    public static volatile Map<Long, NodeRect> lastControlRects = Map.of();

    private HudCanvasRenderer() {}

    public static void render(DrawContext drawContext, MinecraftClient client) {
        List<SceneSnapshot.NodeSnapshot> nodes = ClientSceneBus.copyNodes();
        if (nodes.isEmpty()) {
            lastHitResults = List.of();
            lastControlRects = Map.of();
            return;
        }
        int virtualW = client.getWindow().getWidth();
        int virtualH = client.getWindow().getHeight();
        float scaleFactor = (float) client.getWindow().getScaleFactor();

        Map<Long, NodeRect> controlRects = new HashMap<>();
        List<HitResult> hits = new ArrayList<>();

        drawContext.getMatrices().push();
        try {
            drawContext.getMatrices().scale(1f / scaleFactor, 1f / scaleFactor, 1f);
            renderScene(drawContext, client.textRenderer, scaleFactor, nodes,
                    virtualW, virtualH, controlRects, hits);
        } finally {
            drawContext.getMatrices().pop();
            drawContext.setShaderColor(1f, 1f, 1f, 1f);
        }

        lastHitResults = List.copyOf(hits);
        lastControlRects = Map.copyOf(controlRects);
    }

    public static List<HitResult> renderNodesInto(DrawContext drawContext,
                                                  net.minecraft.client.font.TextRenderer textRenderer,
                                                  float scaleFactor,
                                                  List<SceneSnapshot.NodeSnapshot> nodes,
                                                  int virtualW,
                                                  int virtualH,
                                                  Map<Long, NodeRect> controlRects) {
        if (nodes == null || nodes.isEmpty()) return List.of();
        List<HitResult> hits = new ArrayList<>();
        renderScene(drawContext, textRenderer, scaleFactor, nodes,
                virtualW, virtualH, controlRects, hits);
        return hits;
    }


    private static void renderScene(DrawContext drawContext,
                                    net.minecraft.client.font.TextRenderer textRenderer,
                                    float scaleFactor,
                                    List<SceneSnapshot.NodeSnapshot> nodes,
                                    int virtualW, int virtualH,
                                    Map<Long, NodeRect> controlRects,
                                    List<HitResult> hits) {
        Map<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent = buildChildrenMap(nodes);
        ControlRenderContext ctx = new ControlRenderContext(drawContext, textRenderer, scaleFactor);

        for (SceneSnapshot.NodeSnapshot root : childrenByParent.getOrDefault(0L, List.of())) {
            walk(ctx, root, childrenByParent, 0, 0, virtualW, virtualH, false, 0, 0, controlRects, hits);
        }

        List<SceneSnapshot.NodeSnapshot> canvasLayers = new ArrayList<>();
        for (SceneSnapshot.NodeSnapshot n : nodes) {
            if (n != null && "CanvasLayer".equals(n.type())) canvasLayers.add(n);
        }
        canvasLayers.sort((a, b) -> Integer.compare(
                (int) ControlRenderContext.floatProp(a, "layer", 0f),
                (int) ControlRenderContext.floatProp(b, "layer", 0f)));
        for (SceneSnapshot.NodeSnapshot layer : canvasLayers) {
            if (!ControlRenderContext.boolProp(layer, "visible", true)) continue;
            for (SceneSnapshot.NodeSnapshot child : childrenByParent.getOrDefault(layer.nodeId(), List.of())) {
                walk(ctx, child, childrenByParent, 0, 0, virtualW, virtualH, false, 0, 0, controlRects, hits);
            }
        }
    }

    private static void walk(ControlRenderContext ctx,
                             SceneSnapshot.NodeSnapshot node,
                             Map<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent,
                             int parentX, int parentY, int parentW, int parentH,
                             boolean layoutOverride, int overrideX, int overrideY,
                             Map<Long, NodeRect> controlRects,
                             List<HitResult> hits) {
        if (node == null) return;

        boolean isControl = CONTROL_TYPES.contains(node.type());
        if (!isControl) {
            for (SceneSnapshot.NodeSnapshot child : childrenByParent.getOrDefault(node.nodeId(), List.of())) {
                if (child == null || "CanvasLayer".equals(child.type())) continue;
                walk(ctx, child, childrenByParent, parentX, parentY, parentW, parentH,
                        false, 0, 0, controlRects, hits);
            }
            return;
        }

        if (!ControlRenderContext.boolProp(node, "visible", true)) return;

        NodeRect rect = layoutOverride
                ? rectWithFixedPos(node, overrideX, overrideY)
                : resolveRect(node, parentX, parentY, parentW, parentH);

        if (controlRects != null) controlRects.put(node.nodeId(), rect);

        float mr = ControlRenderContext.floatProp(node, "modulate_r", 1f);
        float mg = ControlRenderContext.floatProp(node, "modulate_g", 1f);
        float mb = ControlRenderContext.floatProp(node, "modulate_b", 1f);
        float ma = ControlRenderContext.floatProp(node, "modulate_a", 1f);
        ctx.saveModulate(mr, mg, mb, ma);

        boolean clip = "ScrollContainer".equals(node.type());
        if (clip) ctx.enableScissor(rect.x(), rect.y(), rect.w(), rect.h());

        ControlRenderers.get(node.type()).render(ctx, node, rect.x(), rect.y(), rect.w(), rect.h());

        if (UiInputTracker.isInteractive(node.type())) {
            hits.add(new HitResult(node.nodeId(), node.type(), rect.x(), rect.y(), rect.w(), rect.h()));
        }

        List<SceneSnapshot.NodeSnapshot> rawChildren = childrenByParent.getOrDefault(node.nodeId(), List.of());
        List<SceneSnapshot.NodeSnapshot> children = new ArrayList<>(rawChildren.size());
        for (SceneSnapshot.NodeSnapshot c : rawChildren) {
            if (c != null && !"CanvasLayer".equals(c.type())) children.add(c);
        }
        if (!children.isEmpty()) {
            if (LayoutComputer.isLayoutContainer(node.type())) {
                List<LayoutComputer.ChildRect> layout = LayoutComputer.compute(
                        node, children, rect.x(), rect.y(), rect.w(), rect.h());
                for (int i = 0; i < children.size(); i++) {
                    LayoutComputer.ChildRect cr = (i < layout.size()) ? layout.get(i) : null;
                    if (cr != null) {
                        walk(ctx, children.get(i), childrenByParent,
                                rect.x(), rect.y(), rect.w(), rect.h(),
                                true, cr.x(), cr.y(), controlRects, hits);
                    } else {
                        walk(ctx, children.get(i), childrenByParent,
                                rect.x(), rect.y(), rect.w(), rect.h(),
                                false, 0, 0, controlRects, hits);
                    }
                }
            } else {
                for (SceneSnapshot.NodeSnapshot child : children) {
                    walk(ctx, child, childrenByParent,
                            rect.x(), rect.y(), rect.w(), rect.h(),
                            false, 0, 0, controlRects, hits);
                }
            }
        }

        if (clip) ctx.disableScissor();
        ctx.restoreModulate();
    }

    private static NodeRect resolveRect(SceneSnapshot.NodeSnapshot node,
                                        int parentX, int parentY, int parentW, int parentH) {
        float al = ControlRenderContext.floatProp(node, "anchor_left", 0f);
        float ar = ControlRenderContext.floatProp(node, "anchor_right", 0f);
        float at = ControlRenderContext.floatProp(node, "anchor_top", 0f);
        float ab = ControlRenderContext.floatProp(node, "anchor_bottom", 0f);
        float ml = ControlRenderContext.floatProp(node, "margin_left", 0f);
        float mr = ControlRenderContext.floatProp(node, "margin_right", 0f);
        float mt = ControlRenderContext.floatProp(node, "margin_top", 0f);
        float mb = ControlRenderContext.floatProp(node, "margin_bottom", 0f);
        float nx = ControlRenderContext.floatProp(node, "x", 0f);
        float ny = ControlRenderContext.floatProp(node, "y", 0f);
        float nw = ControlRenderContext.floatProp(node, "w", 100f);
        float nh = ControlRenderContext.floatProp(node, "h", 30f);
        float sx = ControlRenderContext.floatProp(node, "sx", 1f);
        float sy = ControlRenderContext.floatProp(node, "sy", 1f);

        int rx = (int) (parentX + al * parentW + ml + nx);
        int ry = (int) (parentY + at * parentH + mt + ny);
        int rw = Math.abs(ar - al) > 0.001f
                ? Math.max(1, (int) (parentX + ar * parentW + mr) - rx)
                : Math.max(1, (int) (nw * sx));
        int rh = Math.abs(ab - at) > 0.001f
                ? Math.max(1, (int) (parentY + ab * parentH + mb) - ry)
                : Math.max(1, (int) (nh * sy));
        return new NodeRect(rx, ry, rw, rh);
    }

    private static NodeRect rectWithFixedPos(SceneSnapshot.NodeSnapshot node, int x, int y) {
        float nw = ControlRenderContext.floatProp(node, "w", 100f);
        float nh = ControlRenderContext.floatProp(node, "h", 30f);
        float sx = ControlRenderContext.floatProp(node, "sx", 1f);
        float sy = ControlRenderContext.floatProp(node, "sy", 1f);
        return new NodeRect(x, y, Math.max(1, (int) (nw * sx)), Math.max(1, (int) (nh * sy)));
    }

    private static Map<Long, List<SceneSnapshot.NodeSnapshot>> buildChildrenMap(
            List<SceneSnapshot.NodeSnapshot> nodes) {
        Map<Long, List<SceneSnapshot.NodeSnapshot>> map = new HashMap<>();
        for (SceneSnapshot.NodeSnapshot n : nodes) {
            if (n != null) map.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n);
        }
        return map;
    }

    public record NodeRect(int x, int y, int w, int h) {}

    public record HitResult(long nodeId, String type, int x, int y, int w, int h) {
        public boolean contains(int mx, int my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}
