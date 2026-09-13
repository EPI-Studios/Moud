package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public class TextLabel extends GuiObject {

    public String text = "";
    public Color textColor = new Color(0.1f, 0.1f, 0.1f, 1f);

    // the height of a line in pixels. nine is the game's own text at its own size
    @Prop(min = 1) public double textSize = 9;
    @Prop(min = 0, max = 1) public double textTransparency;

    public boolean textWrapped;

    // the game's drop shadow, one of its pixels down and right
    public boolean textShadow;
    public HorizontalAlign textXAlignment = HorizontalAlign.CENTER;
    public VerticalAlign textYAlignment = VerticalAlign.CENTER;

    // the font this text is drawn in: empty for the game's own, another of the game's fonts like
    // minecraft:uniform, or a .ttf in the place or a resource pack. empty takes the nearest one set
    // above it first
    @Prop(asset = true) public String font = "";

    // text read as rich text: <b>, <color=gold>, <gradient>, <wave>, <img> and the rest the chat takes.
    // it wraps inside the label
    public boolean richText;
}
