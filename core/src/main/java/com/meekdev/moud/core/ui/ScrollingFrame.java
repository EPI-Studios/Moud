package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
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

    public ScrollingFrame() {
        clipsDescendants = true;
        backgroundColor = new Color(0.95f, 0.95f, 0.95f, 1f);
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
