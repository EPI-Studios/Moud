package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vec3;

// speech bubbles over the heads of whoever talks. the first one in the tree is used; without one there
// are no bubbles
public final class BubbleChat extends Instance {

    public boolean enabled = true;

    // how long a bubble stays, and how many a head holds before the oldest goes
    @Prop(min = 0) public double visibleTime = 8;
    @Prop(min = 1) public int maxBubbles = 3;

    // past this a bubble is not drawn, in metres
    @Prop(min = 0) public double maxDistance = 48;

    // above the top of the body
    public Vec3 offset = new Vec3(0, 0.35, 0);

    public Color backgroundColor = Color.WHITE;
    @Prop(min = 0, max = 1) public double backgroundTransparency = 0.1;
    public Color textColor = new Color(0.1f, 0.1f, 0.12f, 1);
    @Prop(min = 1) public double textSize = 9;
    @Prop(asset = true) public String font = "";
    @Prop(min = 0) public double cornerRadius = 6;
    @Prop(min = 0) public double padding = 4;
    public boolean tail = true;

    // the widest a bubble grows before it wraps, in its own pixels
    @Prop(min = 20) public double maxWidth = 180;

    // how many of its pixels make a metre, which is how big the whole bubble is
    @Prop(min = 8) public double pixelsPerMetre = 60;

    public boolean alwaysOnTop;

    public ChatAnimation animation = ChatAnimation.POP;
    @Prop(min = 0) public double animationTime = 0.2;
}
