package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

public final class FogEffect extends ScreenEffect {

    public Color color = new Color(0.7f, 0.78f, 0.85f, 1);

    @Prop(min = 0) public double start = 16;
    @Prop(min = 0) public double density = 0.02;

    @Prop(min = 0) public double heightFalloff;
    public double baseHeight = 64;

    public boolean sky;

    public FogEffect() {
        order = 0;
    }
}
