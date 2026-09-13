package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public class TextLabel extends GuiObject {

    public String text = "";
    public Color textColor = new Color(0.1f, 0.1f, 0.1f, 1f);

    // in pixels
    @Prop(min = 1) public double textSize = 14;
    @Prop(min = 0, max = 1) public double textTransparency;

    public boolean textWrapped;
    public HorizontalAlign textXAlignment = HorizontalAlign.CENTER;
    public VerticalAlign textYAlignment = VerticalAlign.CENTER;
}
