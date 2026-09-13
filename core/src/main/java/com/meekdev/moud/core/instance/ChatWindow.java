package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.tween.Easing;

// how the chat looks. the first one in the tree is the one used; without one the chat looks like the game's
public final class ChatWindow extends Instance {

    public boolean enabled = true;

    // hidden and shown with the hide and show animations
    public boolean visible = true;

    // where the window is and how big, against the screen, and which point of it sits there
    public UDim2 position = new UDim2(0, 4, 1, -40);
    public UDim2 size = UDim2.fromOffset(320, 180);
    @Prop(min = 0, max = 1) public double anchorX;
    @Prop(min = 0, max = 1) public double anchorY = 1;

    // messages stack from the bottom up, or from the top down
    public VerticalAlign verticalAlignment = VerticalAlign.BOTTOM;
    public HorizontalAlign horizontalAlignment = HorizontalAlign.LEFT;

    // the window itself
    public ChatBackdrop backdrop = ChatBackdrop.FOCUSED;
    public Color backgroundColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.6;
    @Prop(min = 0) public double cornerRadius;
    @Prop(min = 0) public double padding = 2;

    // each message
    public Color lineBackgroundColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double lineBackgroundTransparency = 0.5;
    @Prop(min = 0) public double lineCornerRadius;
    @Prop(min = 0) public double linePaddingX = 2;
    @Prop(min = 0) public double linePaddingY = 1;
    @Prop(min = 0) public double lineGap;
    // the strip is as wide as the window, or only as wide as the message
    public boolean lineFitsText;

    // the text
    @Prop(asset = true) public String font = "";
    @Prop(min = 1) public double textSize = 9;
    public Color textColor = Color.WHITE;
    public boolean textShadow = true;
    public Color textStrokeColor = Color.BLACK;
    @Prop(min = 0, max = 1) public double textStrokeTransparency = 1;
    public Color prefixColor = Color.WHITE;

    public boolean timestamps;
    // java's date pattern, like HH:mm
    public String timestampFormat = "HH:mm";
    public Color timestampColor = new Color(0.6f, 0.6f, 0.6f, 1);

    // how long a message stays fully visible and how long it takes to fade, in seconds. zero never fades
    @Prop(min = 0) public double visibleTime = 10;
    @Prop(min = 0) public double fadeTime = 0.5;

    @Prop(min = 1) public int maxMessages = 100;

    // a message arriving, and one being deleted
    public ChatAnimation enterAnimation = ChatAnimation.FADE;
    public ChatAnimation exitAnimation = ChatAnimation.FADE;
    @Prop(min = 0) public double animationTime = 0.2;
    public Easing easing = Easing.QUAD;

    // the window being hidden and shown
    public ChatAnimation hideAnimation = ChatAnimation.FADE;

    // a glsl file the whole window is drawn through, like res://chat/crt.glsl
    @Prop(asset = true) public String shader = "";
}
