package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;

public final class Slider extends GuiObject {

    public final Signal<Double> valueChanged = new Signal<>();
    public final Signal<Double> dragEnded = new Signal<>();

    public double value;
    public double minValue;
    public double maxValue = 1;
    @Prop(min = 0) public double step;

    public FillDirection fillDirection = FillDirection.HORIZONTAL;

    public Color fillColor = new Color(0.33f, 0.6f, 1f, 1f);
    @Prop(min = 0, max = 1) public double fillTransparency;
    @Prop(asset = true) public String fillImage = "";

    public Color grabberColor = Color.WHITE;
    @Prop(asset = true) public String grabberImage = "";
    public Vector3 grabberSize = new Vector3(14, 14, 0);

    @Prop(replicated = false) public boolean hovered;
    @Prop(replicated = false) public boolean dragging;

    public Slider() {
        size = UDim2.fromOffset(160, 6);
        backgroundColor = new Color(0.2f, 0.2f, 0.24f, 1f);
        cornerRadius = 3;
    }

    public double fraction() {
        double span = maxValue - minValue;
        if (!(span > 0)) return 0;
        return Math.clamp((value - minValue) / span, 0, 1);
    }

    public double valueAt(double fraction) {
        double span = Math.max(0, maxValue - minValue);
        double wanted = minValue + Math.clamp(fraction, 0, 1) * span;
        if (step > 0) wanted = minValue + Math.round((wanted - minValue) / step) * step;
        return Math.clamp(wanted, minValue, Math.max(minValue, maxValue));
    }

    @Override
    public boolean sinksInput() {
        return true;
    }
}
