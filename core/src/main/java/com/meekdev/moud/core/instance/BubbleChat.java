package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public final class BubbleChat extends Instance {

    public boolean enabled = true;

    @Prop(min = 0) public double visibleTime = 8;
    @Prop(min = 1) public int maxBubbles = 3;

    @Prop(min = 0) public double maxDistance = 48;

    public Vector3 offset = new Vector3(0, 0.35, 0);

    public Color backgroundColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.1;
    public Color textColor = new Color(0.1f, 0.1f, 0.12f, 1);
    @Prop(min = 1) public double textSize = 9;
    @Prop(asset = true) public String font = "";
    @Prop(min = 0) public double cornerRadius = 6;
    @Prop(min = 0) public double padding = 4;
    public boolean tail = true;

    @Prop(min = 20) public double maxWidth = 180;

    @Prop(min = 8) public double pixelsPerMetre = 60;

    public boolean alwaysOnTop;

    public ChatAnimation animation = ChatAnimation.POP;
    @Prop(min = 0) public double animationTime = 0.2;
}
