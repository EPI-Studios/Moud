package com.moud.client.fabric.editor.widgets;

import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GradientEditor {
    private final List<ColorStop> stops = new ArrayList<>();
    private GradientListener listener;
    private int selected = -1;
    private int draggingIndex = -1;

    public GradientEditor() {
        stops.add(new ColorStop(0f, 0xFF000000));
        stops.add(new ColorStop(1f, 0xFFFFFFFF));
    }

    public void setListener(GradientListener l) { this.listener = l; }

    public List<ColorStop> stops() { return List.copyOf(stops); }

    public void setStops(List<ColorStop> s) {
        stops.clear();
        if (s != null) stops.addAll(s);
        sort();
    }

    public int selectedIndex() { return selected; }

    public void setSelectedColor(int argb) {
        if (selected < 0 || selected >= stops.size()) return;
        ColorStop s = stops.get(selected);
        stops.set(selected, new ColorStop(s.position(), argb));
        fire();
    }

    public int sample(float t) {
        if (stops.isEmpty()) return 0;
        if (t <= stops.get(0).position()) return stops.get(0).argb();
        if (t >= stops.get(stops.size() - 1).position()) return stops.get(stops.size() - 1).argb();
        for (int i = 0; i < stops.size() - 1; i++) {
            ColorStop a = stops.get(i);
            ColorStop b = stops.get(i + 1);
            if (t >= a.position() && t <= b.position()) {
                float k = (t - a.position()) / Math.max(1e-6f, (b.position() - a.position()));
                return Theme.lerpArgbInt(a.argb(), b.argb(), k);
            }
        }
        return stops.get(stops.size() - 1).argb();
    }

    public int render(UiRenderer r, UiInput input, Theme theme,
                      int x, int y, int width, int height, boolean interactive) {
        int barH = Math.max(12, height - 14);
        int handleY = y + barH + 2;
        int handleH = height - barH - 2;

        r.drawRoundedRect(x, y, width, barH, theme.design.radius_sm, 0xFF202024);
        for (int i = 0; i < stops.size() - 1; i++) {
            ColorStop a = stops.get(i);
            ColorStop b = stops.get(i + 1);
            int ax = x + Math.round(a.position() * width);
            int bx = x + Math.round(b.position() * width);
            int w = Math.max(1, bx - ax);
            r.drawRoundedRect(ax, y, w, barH, 0f, a.argb(), b.argb(), b.argb(), a.argb(), 0f, 0);
        }

        boolean canInteract = interactive && input != null;
        float mx = input != null ? input.mousePos().x : -1;
        float my = input != null ? input.mousePos().y : -1;

        for (int i = 0; i < stops.size(); i++) {
            ColorStop s = stops.get(i);
            int hx = x + Math.round(s.position() * width);
            int hw = 8;
            int rectX = hx - hw / 2;
            boolean isSelected = i == selected;
            int outline = isSelected ? Theme.toArgb(theme.accent) : Theme.toArgb(theme.widgetOutline);
            r.drawRoundedRect(rectX, handleY, hw, handleH, theme.design.radius_sm, s.argb(), 1.5f, outline);
        }

        if (canInteract) {
            if (input.mousePressed()) {
                int hit = -1;
                for (int i = 0; i < stops.size(); i++) {
                    int hx = x + Math.round(stops.get(i).position() * width);
                    if (Math.abs(mx - hx) <= 6 && my >= handleY && my < handleY + handleH) {
                        hit = i;
                        break;
                    }
                }
                if (hit >= 0) {
                    selected = hit;
                    draggingIndex = hit;
                    if (listener != null) listener.onStopSelected(selected, stops.get(selected));
                } else if (mx >= x && mx < x + width && my >= y && my < y + barH) {
                    float pos = clamp01((mx - x) / (float) width);
                    int color = sample(pos);
                    ColorStop ns = new ColorStop(pos, color);
                    stops.add(ns);
                    sort();
                    selected = stops.indexOf(ns);
                    draggingIndex = selected;
                    fire();
                    if (listener != null) listener.onStopSelected(selected, ns);
                }
            }
            if (draggingIndex >= 0 && input.mouseDown()) {
                ColorStop cur = stops.get(draggingIndex);
                float pos = clamp01((mx - x) / (float) width);
                stops.set(draggingIndex, new ColorStop(pos, cur.argb()));
                sort();
                draggingIndex = stops.indexOf(new ColorStop(pos, cur.argb()));
                selected = draggingIndex;
                fire();
            }
            if (draggingIndex >= 0 && !input.mouseDown()) draggingIndex = -1;
        }

        return y + height;
    }

    private void sort() {
        stops.sort(Comparator.comparingDouble(ColorStop::position));
    }

    private void fire() {
        if (listener != null) listener.onChanged(List.copyOf(stops));
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
