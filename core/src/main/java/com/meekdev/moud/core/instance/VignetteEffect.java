package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

// the edges of the picture, darkened
public final class VignetteEffect extends ScreenEffect {

    // from the middle, where it starts, and how far it takes to reach full
    @Prop(min = 0) public double radius = 0.75;
    @Prop(min = 0.001) public double softness = 0.45;
    public Color color = Color.BLACK;

    public VignetteEffect() {
        order = 60;
    }
}
