package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;

public final class ScrollingFrame extends GuiObject {

    public UDim2 canvasSize = UDim2.fromScale(0, 2);
    public AutomaticSize automaticCanvasSize = AutomaticSize.NONE;
    public Vector3 canvasPosition = Vector3.ZERO;

    @Prop(min = 0) public double scrollBarThickness = 12;
    public Color scrollBarImageColor = new Color(0.5f, 0.5f, 0.5f, 1f);
    @Prop(min = 0, max = 1) public double scrollBarImageTransparency;

    public ScrollingDirection scrollingDirection = ScrollingDirection.XY;
    public boolean scrollingEnabled = true;

    @Prop(readOnly = true) public Vector3 absoluteCanvasSize = Vector3.ZERO;
    @Prop(readOnly = true) public Vector3 absoluteWindowSize = Vector3.ZERO;

    private double canvasW = -1;
    private double canvasH = -1;
    private double windowW = -1;
    private double windowH = -1;

    public ScrollingFrame() {
        clipsDescendants = true;
        backgroundColor = new Color(0.95f, 0.95f, 0.95f, 1f);
        changed().connect(this::settle);
    }

    public void measured(double canvasAcross, double canvasDown, double windowAcross, double windowDown) {
        canvasW = canvasAcross;
        canvasH = canvasDown;
        windowW = windowAcross;
        windowH = windowDown;
        Vector3 held = clamp(canvasPosition);
        if (!held.equals(canvasPosition) && isAlive()) {
            Instances.setObj(this, def().property("canvasPosition"), held);
        }
    }

    public Vector3 clamp(Vector3 wanted) {
        return windowW < 0 ? wanted : clamp(wanted, canvasW, canvasH, windowW, windowH);
    }

    private void settle(PropertyDef property) {
        if (windowW < 0 || !property.name().equals("canvasPosition")) return;
        Vector3 held = clamp(canvasPosition);
        if (!held.equals(canvasPosition)) property.writeObj(this, held);
    }

    public Vector3 clamp(Vector3 wanted, double canvasW, double canvasH, double windowW, double windowH) {
        double x = scrollingDirection.x() ? Math.clamp(wanted.x(), 0, Math.max(0, canvasW - windowW)) : 0;
        double y = scrollingDirection.y() ? Math.clamp(wanted.y(), 0, Math.max(0, canvasH - windowH)) : 0;
        return new Vector3(x, y, 0);
    }

    @Override
    public boolean sinksInput() {
        return scrollingEnabled;
    }
}
