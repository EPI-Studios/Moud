package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public final class VignetteEffect extends ScreenEffect {

    @Prop(min = 0) public double radius = 0.75;
    @Prop(min = 0.001) public double softness = 0.45;
    public Color color = Color.BLACK;

    public VignetteEffect() {
        order = 60;
    }
}
