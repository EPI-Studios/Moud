package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;

public class GuiObject extends Instance {

    public UDim2 position = UDim2.ZERO;
    public UDim2 size = UDim2.fromOffset(100, 100);

    @Prop(min = 0, max = 1) public double anchorX;
    @Prop(min = 0, max = 1) public double anchorY;

    public Color backgroundColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double backgroundTransparency;

    @Prop(min = 0) public double cornerRadius;
    @Prop(min = 0) public double borderSize;
    public Color borderColor = Color.BLACK;

    public boolean visible = true;

    public int zIndex;

    public int layoutOrder;

    public AutomaticSize automaticSize = AutomaticSize.NONE;

    public boolean clipsDescendants;

    @Prop(readOnly = true, replicated = false) public Vector3 absolutePosition = Vector3.ZERO;
    @Prop(readOnly = true, replicated = false) public Vector3 absoluteSize = Vector3.ZERO;

    public final Signal<Instance> mouseEnter = new Signal<>();
    public final Signal<Instance> mouseLeave = new Signal<>();
    public final Signal<Object[]> mouseMoved = new Signal<>();
    public final Signal<Object[]> mouseButton1Down = new Signal<>();
    public final Signal<Object[]> mouseButton1Up = new Signal<>();
    public final Signal<Object[]> mouseButton2Click = new Signal<>();
    public final Signal<Object[]> mouseWheelForward = new Signal<>();
    public final Signal<Object[]> mouseWheelBackward = new Signal<>();

    public boolean sinksInput() {
        return false;
    }
}
