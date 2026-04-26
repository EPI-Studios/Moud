package com.moud.client.fabric.editor.widgets;

import com.miry.ui.UiContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.DraggableNumberField;

public final class SliderWithNumber {
    private final DraggableNumberField field;
    private float min;
    private float max;
    private float snap;
    private SliderScale scale = SliderScale.LINEAR;
    private SliderListener listener;
    private boolean trackDragging;

    public SliderWithNumber(float initial, float min, float max) {
        this.min = min;
        this.max = max;
        this.field = new DraggableNumberField(initial, min, max);
        this.field.setListener(v -> {
            if (listener != null) listener.onChanged(v);
        });
    }

    public void setListener(SliderListener l) { this.listener = l; }
    public void setScale(SliderScale s) { this.scale = s == null ? SliderScale.LINEAR : s; }
    public void setSnapStep(float step) { this.snap = Math.max(0f, step); field.setSnapStep(step <= 0 ? 1e-6f : step); }
    public void setRange(float lo, float hi) { this.min = lo; this.max = hi; field.setRange(lo, hi); }

    public float value() { return field.value(); }
    public void setValue(float v) { field.setValue(v); }

    public int render(UiRenderer r, UiContext ctx, UiInput input, Theme theme,
                      int x, int y, int width, int height, boolean interactive) {
        int numberW = 70;
        int gap = 4;
        int trackX = x;
        int trackW = width - numberW - gap;
        int trackH = height;

        int trackBg = Theme.toArgb(theme.widgetBg);
        int fillBg = Theme.toArgb(theme.widgetActive);
        r.drawRoundedRect(trackX, y, trackW, trackH, theme.design.radius_sm, trackBg);

        float t = normalize(field.value());
        int fillW = Math.max(2, Math.round(trackW * t));
        r.drawRoundedRect(trackX, y, fillW, trackH, theme.design.radius_sm, fillBg);

        boolean canInteract = interactive && input != null;
        float mx = input != null ? input.mousePos().x : -1;
        float my = input != null ? input.mousePos().y : -1;
        boolean overTrack = canInteract && mx >= trackX && mx < trackX + trackW && my >= y && my < y + trackH;
        if (canInteract && overTrack && input.mousePressed()) {
            trackDragging = true;
        }
        if (trackDragging && canInteract && input.mouseDown()) {
            float frac = clamp01((mx - trackX) / Math.max(1f, (float) trackW));
            float newVal = denormalize(frac);
            if (snap > 0) newVal = Math.round(newVal / snap) * snap;
            if (newVal != field.value()) {
                field.setValue(newVal);
                if (listener != null) listener.onChanged(field.value());
            }
        }
        if (trackDragging && (!canInteract || !input.mouseDown())) {
            trackDragging = false;
        }

        int numX = trackX + trackW + gap;
        field.render(r, ctx, input, theme, numX, y, numberW, height, interactive);
        return y + height;
    }

    private float normalize(float v) {
        if (scale == SliderScale.LOG && min > 0 && max > 0) {
            double lo = Math.log(min);
            double hi = Math.log(max);
            return clamp01((float) ((Math.log(Math.max(min, v)) - lo) / (hi - lo)));
        }
        return clamp01((v - min) / Math.max(1e-6f, (max - min)));
    }

    private float denormalize(float t) {
        if (scale == SliderScale.LOG && min > 0 && max > 0) {
            double lo = Math.log(min);
            double hi = Math.log(max);
            return (float) Math.exp(lo + (hi - lo) * t);
        }
        return min + (max - min) * t;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
