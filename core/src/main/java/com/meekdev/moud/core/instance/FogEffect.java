package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.math.Color;

// distance fading into a colour, thicker lower down if height falloff is set
public final class FogEffect extends ScreenEffect {

    public Color color = new Color(0.7f, 0.78f, 0.85f, 1);

    // in metres: clear before start, and density decides how fast it thickens after
    @Prop(min = 0) public double start = 16;
    @Prop(min = 0) public double density = 0.02;

    // how quickly it thins with height above base, zero for the same everywhere
    @Prop(min = 0) public double heightFalloff;
    public double baseHeight = 64;

    // whether the sky is fogged too
    public boolean sky;

    public FogEffect() {
        order = 0;
    }
}
