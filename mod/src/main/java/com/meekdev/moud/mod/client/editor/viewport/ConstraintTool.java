package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.mod.adapter.render.EditorOverlay;
import com.meekdev.moud.mod.client.editor.document.ConstraintKind;
import com.meekdev.moud.mod.client.editor.document.Joining;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SurfacePoint;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import org.jspecify.annotations.Nullable;

final class ConstraintTool {

    private static final double REACH = 512.0;
    private static final double NORMAL_LENGTH = 0.5;
    private static final float POINT_RADIUS = 5.0f;
    private static final float LINE_THICKNESS = 2.0f;
    private static final int PREVIEW = EditorStyle.withAlpha(EditorStyle.COLOR_HIGHLIGHT, 0.8f);

    private ConstraintKind kind = ConstraintKind.WELD;
    private @Nullable Part first;
    private @Nullable SurfacePoint firstPoint;
    private @Nullable Part hovered;
    private @Nullable SurfacePoint hoveredPoint;

    ConstraintKind kind() {
        return kind;
    }

    void kind(ConstraintKind chosen) {
        kind = chosen;
        cancel();
    }

    boolean waiting() {
        return first != null;
    }

    void cancel() {
        first = null;
        firstPoint = null;
    }

    int hoveredId() {
        return hovered == null ? 0 : hovered.id();
    }

    void hover(SceneDocument document, SceneView view, boolean inside, float mouseX, float mouseY, boolean snap, double step) {
        hovered = null;
        hoveredPoint = null;
        if (first != null && !first.isAlive()) cancel();
        Instance world = document.world();
        if (!inside || world == null || !(document.find(EditorOverlay.picked()) instanceof Part part) || !document.pickable(part)) return;
        Vector3 from = view.rayOrigin(mouseX, mouseY);
        Vector3 direction = view.rayDirection(mouseX, mouseY);
        Queries.Cast cast = Queries.raycast(world, from, direction, REACH, candidate -> candidate == part);
        SurfacePoint point = cast == null
                ? new SurfacePoint(Frames.center(part), direction.neg().normalize())
                : new SurfacePoint(cast.at(), cast.normal());
        if (snap && cast != null) {
            point = new SurfacePoint(Joining.snapOnFace(Transforms.world(part), part.size.mul(0.5), point, step), point.normal());
        }
        hovered = part;
        hoveredPoint = point;
    }

    void click(SceneDocument document) {
        Part part = hovered;
        SurfacePoint point = hoveredPoint;
        if (part == null || point == null) return;
        if (first == null || firstPoint == null) {
            first = part;
            firstPoint = point;
            return;
        }
        if (part == first) return;
        document.connect(kind, first, firstPoint, part, point);
        cancel();
    }

    String hint() {
        boolean points = kind.attached();
        if (first == null) return points ? "Click a point on the first part" : "Click the first part";
        if (hovered == first) return "Pick a different part";
        return (points ? "Click a point on the second part" : "Click the second part") + ", Escape cancels";
    }

    void draw(ImDrawList draw, SceneView view, float mouseX, float mouseY) {
        SurfacePoint start = firstPoint;
        SurfacePoint under = hoveredPoint;
        if (start != null) {
            float[] from = view.toScreen(start.at());
            float[] to = under == null ? new float[] {mouseX, mouseY} : view.toScreen(under.at());
            if (from != null && to != null) draw.addLine(from[0], from[1], to[0], to[1], PREVIEW, EditorScale.of(LINE_THICKNESS));
            point(draw, view, start, EditorStyle.COLOR_HIGHLIGHT);
        }
        if (under != null) point(draw, view, under, hovered == first ? EditorStyle.COLOR_DANGER : EditorStyle.COLOR_TEXT_FOCUS);
        draw.addText(mouseX + EditorScale.of(14), mouseY + EditorScale.of(10), EditorStyle.COLOR_HIGHLIGHT, kind.label() + ": " + hint());
    }

    private void point(ImDrawList draw, SceneView view, SurfacePoint point, int colour) {
        float[] at = view.toScreen(point.at());
        if (at == null) return;
        if (kind.attached()) {
            float[] tip = view.toScreen(point.at().add(point.normal().normalize().mul(NORMAL_LENGTH)));
            if (tip != null) draw.addLine(at[0], at[1], tip[0], tip[1], colour, EditorScale.of(LINE_THICKNESS));
        }
        draw.addCircleFilled(at[0], at[1], EditorScale.of(POINT_RADIUS), colour);
    }
}
