package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;

// a rectangle on a surface. position and size are against the parent's rectangle
public class GuiObject extends Instance {

    public UDim2 position = UDim2.ZERO;
    public UDim2 size = UDim2.fromOffset(100, 100);

    // which point of this rectangle sits on position, as a fraction of its own size
    @Prop(min = 0, max = 1) public double anchorX;
    @Prop(min = 0, max = 1) public double anchorY;

    public Color backgroundColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double backgroundTransparency;

    @Prop(min = 0) public double cornerRadius;
    @Prop(min = 0) public double borderSize;
    public Color borderColor = Color.BLACK;

    public boolean visible = true;

    // higher is drawn over its siblings, ties go to the order they were added
    public int zIndex;

    public boolean clipsDescendants;
}
