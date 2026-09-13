package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.instance.Instance;

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

    public boolean clipsDescendants;
}
