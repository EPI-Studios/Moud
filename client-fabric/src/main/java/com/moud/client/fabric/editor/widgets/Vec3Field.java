package com.moud.client.fabric.editor.widgets;

import com.miry.ui.UiContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.DraggableNumberField;

public final class Vec3Field {
    private static final int AXIS_X = 0xFFE26161;
    private static final int AXIS_Y = 0xFF7CC36A;
    private static final int AXIS_Z = 0xFF6196E2;

    private final DraggableNumberField fx = new DraggableNumberField(0f, -Float.MAX_VALUE, Float.MAX_VALUE);
    private final DraggableNumberField fy = new DraggableNumberField(0f, -Float.MAX_VALUE, Float.MAX_VALUE);
    private final DraggableNumberField fz = new DraggableNumberField(0f, -Float.MAX_VALUE, Float.MAX_VALUE);
    private Vec3Listener listener;

    public Vec3Field() {
        fx.setListener(v -> fire(0));
        fy.setListener(v -> fire(1));
        fz.setListener(v -> fire(2));
    }

    public void setListener(Vec3Listener l) { this.listener = l; }

    public void setValues(float x, float y, float z) {
        fx.setValue(x);
        fy.setValue(y);
        fz.setValue(z);
    }

    public float x() { return fx.value(); }
    public float y() { return fy.value(); }
    public float z() { return fz.value(); }

    public void setSnapStep(float step) {
        fx.setSnapStep(step);
        fy.setSnapStep(step);
        fz.setSnapStep(step);
    }

    public void setDragSpeed(float speed) {
        fx.setDragSpeed(speed);
        fy.setDragSpeed(speed);
        fz.setDragSpeed(speed);
    }

    public int render(UiRenderer r, UiContext ctx, UiInput input, Theme theme,
                      int x, int y, int width, int height, boolean interactive) {
        int gap = 4;
        int axisW = 14;
        int per = (width - gap * 2) / 3;
        int[] tints = { AXIS_X, AXIS_Y, AXIS_Z };
        String[] labels = { "X", "Y", "Z" };
        DraggableNumberField[] fields = { fx, fy, fz };
        for (int i = 0; i < 3; i++) {
            int fxLeft = x + i * (per + gap);
            int fwLeft = (i == 2) ? (x + width - fxLeft) : per;
            r.drawRoundedRect(fxLeft, y, axisW, height, theme.design.radius_sm, tints[i]);
            r.drawText(labels[i], fxLeft + 4, r.baselineForBox(y, height), 0xFF101015);
            int innerX = fxLeft + axisW + 2;
            int innerW = fwLeft - axisW - 2;
            fields[i].render(r, ctx, input, theme, innerX, y, innerW, height, interactive);
        }
        return y + height;
    }

    private void fire(int axis) {
        if (listener != null) listener.onChanged(axis, fx.value(), fy.value(), fz.value());
    }
}
