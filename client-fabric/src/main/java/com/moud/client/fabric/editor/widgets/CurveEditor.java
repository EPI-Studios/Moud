package com.moud.client.fabric.editor.widgets;

import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CurveEditor {
    private final List<CurvePoint> points = new ArrayList<>();
    private CurveListener listener;
    private int draggingIndex = -1;
    private int hoverIndex = -1;

    public CurveEditor() {
        points.add(new CurvePoint(0f, 0f));
        points.add(new CurvePoint(1f, 1f));
    }

    public void setListener(CurveListener l) { this.listener = l; }

    public List<CurvePoint> points() { return List.copyOf(points); }

    public void setPoints(List<CurvePoint> pts) {
        points.clear();
        if (pts != null) points.addAll(pts);
        sortPoints();
    }

    public float sample(float x) {
        if (points.isEmpty()) return 0f;
        if (x <= points.get(0).x()) return points.get(0).y();
        if (x >= points.get(points.size() - 1).x()) return points.get(points.size() - 1).y();
        for (int i = 0; i < points.size() - 1; i++) {
            CurvePoint a = points.get(i);
            CurvePoint b = points.get(i + 1);
            if (x >= a.x() && x <= b.x()) {
                float t = (x - a.x()) / Math.max(1e-6f, (b.x() - a.x()));
                return a.y() + (b.y() - a.y()) * t;
            }
        }
        return points.get(points.size() - 1).y();
    }

    public int render(UiRenderer r, UiInput input, Theme theme,
                      int x, int y, int width, int height, boolean interactive) {
        int bg = Theme.toArgb(theme.widgetBg);
        int gridArgb = Theme.mulAlpha(Theme.toArgb(theme.widgetOutline), 0.4f);
        int lineArgb = Theme.toArgb(theme.accent);
        int pointArgb = Theme.toArgb(theme.widgetActive);

        r.drawRoundedRect(x, y, width, height, theme.design.radius_sm, bg);
        for (int i = 1; i < 4; i++) {
            int gx = x + width * i / 4;
            int gy = y + height * i / 4;
            r.drawRect(gx, y, 1, height, gridArgb);
            r.drawRect(x, gy, width, 1, gridArgb);
        }

        for (int i = 0; i < points.size() - 1; i++) {
            CurvePoint a = points.get(i);
            CurvePoint b = points.get(i + 1);
            int ax = x + Math.round(a.x() * width);
            int ay = y + Math.round((1f - a.y()) * height);
            int bx = x + Math.round(b.x() * width);
            int by = y + Math.round((1f - b.y()) * height);
            r.drawCapsule(ax, ay, bx, by, 1.5f, lineArgb);
        }

        boolean canInteract = interactive && input != null;
        float mx = input != null ? input.mousePos().x : -1;
        float my = input != null ? input.mousePos().y : -1;
        hoverIndex = -1;
        for (int i = 0; i < points.size(); i++) {
            CurvePoint p = points.get(i);
            int px = x + Math.round(p.x() * width);
            int py = y + Math.round((1f - p.y()) * height);
            float dx = mx - px, dy = my - py;
            boolean over = canInteract && dx * dx + dy * dy <= 36f;
            if (over) hoverIndex = i;
            int radius = (i == draggingIndex || over) ? 6 : 4;
            r.drawCircle(px, py, radius, pointArgb);
        }

        if (canInteract) {
            if (input.mousePressed() && hoverIndex >= 0) {
                draggingIndex = hoverIndex;
            } else if (input.mousePressed() && mx >= x && mx < x + width && my >= y && my < y + height) {
                float px = clamp01((mx - x) / (float) width);
                float py = clamp01(1f - (my - y) / (float) height);
                points.add(new CurvePoint(px, py));
                sortPoints();
                draggingIndex = points.indexOf(new CurvePoint(px, py));
                fire();
            }
            if (draggingIndex >= 0 && input.mouseDown()) {
                float px = clamp01((mx - x) / (float) width);
                float py = clamp01(1f - (my - y) / (float) height);
                if (draggingIndex == 0) px = 0f;
                else if (draggingIndex == points.size() - 1) px = 1f;
                points.set(draggingIndex, new CurvePoint(px, py));
                sortPoints();
                fire();
            }
            if (draggingIndex >= 0 && !input.mouseDown()) {
                draggingIndex = -1;
            }
        }

        return y + height;
    }

    private void sortPoints() {
        points.sort(Comparator.comparingDouble(CurvePoint::x));
    }

    private void fire() {
        if (listener != null) listener.onChanged(List.copyOf(points));
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
