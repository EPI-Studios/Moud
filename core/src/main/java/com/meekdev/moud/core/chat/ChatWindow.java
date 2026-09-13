package com.meekdev.moud.core.chat;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.tween.Easing;
import com.meekdev.moud.core.ui.HorizontalAlign;
import com.meekdev.moud.core.ui.VerticalAlign;

public final class ChatWindow extends Instance {

    public boolean enabled = true;

    public boolean visible = true;

    public UDim2 position = new UDim2(0, 4, 1, -40);
    public UDim2 size = UDim2.fromOffset(320, 180);
    @Prop(min = 0, max = 1) public double anchorX;
    @Prop(min = 0, max = 1) public double anchorY = 1;

    public VerticalAlign verticalAlignment = VerticalAlign.BOTTOM;
    public HorizontalAlign horizontalAlignment = HorizontalAlign.LEFT;

    public ChatBackdrop backdrop = ChatBackdrop.FOCUSED;
    public Color backgroundColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.6;
    @Prop(min = 0) public double cornerRadius;
    @Prop(min = 0) public double padding = 2;

    public Color lineBackgroundColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double lineBackgroundTransparency = 0.5;
    @Prop(min = 0) public double lineCornerRadius;
    @Prop(min = 0) public double linePaddingX = 2;
    @Prop(min = 0) public double linePaddingY = 1;
    @Prop(min = 0) public double lineGap;
    public boolean lineFitsText;

    @Prop(asset = true) public String font = "";
    @Prop(min = 1) public double textSize = 9;
    public Color textColor = Color.WHITE;
    public boolean textShadow = true;
    public Color textStrokeColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double textStrokeTransparency = 1;
    public Color prefixColor = Color.WHITE;

    public boolean timestamps;
    public String timestampFormat = "HH:mm";
    public Color timestampColor = new Color(0.6f, 0.6f, 0.6f, 1);

    @Prop(min = 0) public double visibleTime = 10;
    @Prop(min = 0) public double fadeTime = 0.5;

    @Prop(min = 1) public int maxMessages = 100;

    public ChatAnimation enterAnimation = ChatAnimation.FADE;
    public ChatAnimation exitAnimation = ChatAnimation.FADE;
    @Prop(min = 0) public double animationTime = 0.2;
    public Easing easing = Easing.QUAD;

    public ChatAnimation hideAnimation = ChatAnimation.FADE;

    @Prop(asset = true) public String shader = "";
}
