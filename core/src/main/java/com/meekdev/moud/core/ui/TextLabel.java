package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public class TextLabel extends GuiObject {

    public String text = "";
    public Color textColor = new Color(0.1f, 0.1f, 0.1f, 1f);

    @Prop(min = 1) public double textSize = 9;
    @Prop(min = 0, max = 1) public double textTransparency;

    public boolean textWrapped;
    public boolean textScaled;

    public boolean textShadow;
    public HorizontalAlign textXAlignment = HorizontalAlign.CENTER;
    public VerticalAlign textYAlignment = VerticalAlign.CENTER;

    @Prop(asset = true) public String font = "";

    public boolean richText;
}
