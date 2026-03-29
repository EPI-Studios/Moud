package com.moud.client.fabric.render.hud;

import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.SceneSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HudCanvasRenderer {

    static volatile List<HitResult> lastHitResults = List.of();

    private HudCanvasRenderer() {}

    public static void render(DrawContext drawContext, MinecraftClient client) {
        List<SceneSnapshot.NodeSnapshot> nodes = ClientSceneBus.copyNodes();
        if (nodes.isEmpty()) {
            lastHitResults = List.of();
            return;
        }

        Map<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent = buildChildrenMap(nodes);

        int screenW = client.getWindow().getWidth();
        int screenH = client.getWindow().getHeight();
        float scaleFactor = (float) client.getWindow().getScaleFactor();

        drawContext.getMatrices().push();
        drawContext.getMatrices().scale(1f / scaleFactor, 1f / scaleFactor, 1f);

        ControlRenderContext ctx = new ControlRenderContext(drawContext, client.textRenderer, scaleFactor);
        List<HitResult> hits = new ArrayList<>();

        try {
            sortedCanvasLayers(nodes).forEach(layer -> {
                if (!ControlRenderContext.boolProp(layer, "visible", true)) return;
                List<SceneSnapshot.NodeSnapshot> children = childrenByParent.getOrDefault(layer.nodeId(), List.of());
                for (SceneSnapshot.NodeSnapshot child : children) {
                    renderNode(ctx, child, childrenByParent, 0, 0, screenW, screenH, false, 0, 0, hits);
                }
            });
        } finally {
            drawContext.getMatrices().pop();
        }

        lastHitResults = List.copyOf(hits);
    }


    static void renderNode(ControlRenderContext ctx,
                           SceneSnapshot.NodeSnapshot node,
                           Map<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent,
                           int parentX, int parentY, int parentW, int parentH,
                           boolean layoutOverride, int overrideX, int overrideY,
                           List<HitResult> hits) {
        if (node == null) return;
        if (!ControlRenderContext.boolProp(node, "visible", true)) return;
        if (!ControlRenderers.isRegistered(node.type())) return;

        NodeRect rect = layoutOverride
                ? resolveRectWithPos(node, overrideX, overrideY)
                : resolveRect(node, parentX, parentY, parentW, parentH);

        float mr = ControlRenderContext.floatProp(node, "modulate_r", 1f);
        float mg = ControlRenderContext.floatProp(node, "modulate_g", 1f);
        float mb = ControlRenderContext.floatProp(node, "modulate_b", 1f);
        float ma = ControlRenderContext.floatProp(node, "modulate_a", 1f);
        ctx.saveModulate(mr, mg, mb, ma);

        boolean isScroll = "ScrollContainer".equals(node.type());
        if (isScroll) ctx.enableScissor(rect.x(), rect.y(), rect.w(), rect.h());

        ControlRenderers.get(node.type()).render(ctx, node, rect.x(), rect.y(), rect.w(), rect.h());

        if (UiInputTracker.isInteractive(node.type())) {
            hits.add(new HitResult(node.nodeId(), node.type(), rect.x(), rect.y(), rect.w(), rect.h()));
        }

        renderChildren(ctx, node, childrenByParent, rect, hits);

        if (isScroll) ctx.disableScissor();
        ctx.restoreModulate();
    }

    private static void renderChildren(ControlRenderContext ctx,
                                       SceneSnapshot.NodeSnapshot parent,
                                       Map<Long, List<SceneSnapshot.NodeSnapshot>> childrenByParent,
                                       NodeRect parentRect,
                                       List<HitResult> hits) {
        List<SceneSnapshot.NodeSnapshot> children = childrenByParent.getOrDefault(parent.nodeId(), List.of());
        if (children.isEmpty()) return;

        if (LayoutComputer.isLayoutContainer(parent.type())) {
            List<LayoutComputer.ChildRect> layout = LayoutComputer.compute(
                    parent, children, parentRect.x(), parentRect.y(), parentRect.w(), parentRect.h());
            for (int i = 0; i < children.size(); i++) {
                LayoutComputer.ChildRect cr = (i < layout.size()) ? layout.get(i) : null;
                if (cr != null) {
                    renderNode(ctx, children.get(i), childrenByParent,
                            parentRect.x(), parentRect.y(), parentRect.w(), parentRect.h(),
                            true, cr.x(), cr.y(), hits);
                } else {
                    renderNode(ctx, children.get(i), childrenByParent,
                            parentRect.x(), parentRect.y(), parentRect.w(), parentRect.h(),
                            false, 0, 0, hits);
                }
            }
        } else {
            for (SceneSnapshot.NodeSnapshot child : children) {
                renderNode(ctx, child, childrenByParent,
                        parentRect.x(), parentRect.y(), parentRect.w(), parentRect.h(),
                        false, 0, 0, hits);
            }
        }
    }


    private static NodeRect resolveRect(SceneSnapshot.NodeSnapshot node,
                                        int parentX, int parentY, int parentW, int parentH) {
        float al = ControlRenderContext.floatProp(node, "anchor_left",   0f);
        float ar = ControlRenderContext.floatProp(node, "anchor_right",  0f);
        float at = ControlRenderContext.floatProp(node, "anchor_top",    0f);
        float ab = ControlRenderContext.floatProp(node, "anchor_bottom", 0f);

        float ml = ControlRenderContext.floatProp(node, "margin_left",   0f);
        float mr = ControlRenderContext.floatProp(node, "margin_right",  0f);
        float mt = ControlRenderContext.floatProp(node, "margin_top",    0f);
        float mb = ControlRenderContext.floatProp(node, "margin_bottom", 0f);

        float nx = ControlRenderContext.floatProp(node, "x", 0f);
        float ny = ControlRenderContext.floatProp(node, "y", 0f);
        float nw = ControlRenderContext.floatProp(node, "w", 100f);
        float nh = ControlRenderContext.floatProp(node, "h", 30f);
        float sx = ControlRenderContext.floatProp(node, "sx", 1f);
        float sy = ControlRenderContext.floatProp(node, "sy", 1f);

        int rx = (int)(parentX + al * parentW + ml + nx);
        int ry = (int)(parentY + at * parentH + mt + ny);

        int rw, rh;
        if (Math.abs(ar - al) > 0.001f) {
            int right = (int)(parentX + ar * parentW + mr);
            rw = Math.max(1, right - rx);
        } else {
            rw = Math.max(1, (int)(nw * sx));
        }
        if (Math.abs(ab - at) > 0.001f) {
            int bottom = (int)(parentY + ab * parentH + mb);
            rh = Math.max(1, bottom - ry);
        } else {
            rh = Math.max(1, (int)(nh * sy));
        }

        return new NodeRect(rx, ry, rw, rh);
    }

    private static NodeRect resolveRectWithPos(SceneSnapshot.NodeSnapshot node, int x, int y) {
        float nw = ControlRenderContext.floatProp(node, "w", 100f);
        float nh = ControlRenderContext.floatProp(node, "h", 30f);
        float sx = ControlRenderContext.floatProp(node, "sx", 1f);
        float sy = ControlRenderContext.floatProp(node, "sy", 1f);
        return new NodeRect(x, y, Math.max(1, (int)(nw * sx)), Math.max(1, (int)(nh * sy)));
    }


    private static Map<Long, List<SceneSnapshot.NodeSnapshot>> buildChildrenMap(
            List<SceneSnapshot.NodeSnapshot> nodes) {
        Map<Long, List<SceneSnapshot.NodeSnapshot>> map = new HashMap<>();
        for (SceneSnapshot.NodeSnapshot n : nodes) {
            if (n != null) map.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n);
        }
        return map;
    }

    private static List<SceneSnapshot.NodeSnapshot> sortedCanvasLayers(
            List<SceneSnapshot.NodeSnapshot> nodes) {
        List<SceneSnapshot.NodeSnapshot> layers = new ArrayList<>();
        for (SceneSnapshot.NodeSnapshot n : nodes) {
            if (n != null && "CanvasLayer".equals(n.type())) layers.add(n);
        }
        layers.sort((a, b) -> Integer.compare(
                (int) ControlRenderContext.floatProp(a, "layer", 0f),
                (int) ControlRenderContext.floatProp(b, "layer", 0f)));
        return layers;
    }


    record NodeRect(int x, int y, int w, int h) {}

    public record HitResult(long nodeId, String type, int x, int y, int w, int h) {
        public boolean contains(int mx, int my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}