package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// sharp at one distance and blurred nearer and further
public final class DepthOfFieldEffect extends ScreenEffect {

    // in metres from the camera
    @Prop(min = 0) public double focusDistance = 8;

    // how deep the sharp band is on each side
    @Prop(min = 0) public double focusRange = 4;

    // how far past the band it takes to be fully blurred
    @Prop(min = 0.01) public double falloff = 12;

    // the most blur, in pixels
    @Prop(min = 0) public double size = 10;

    public DepthOfFieldEffect() {
        order = 10;
    }
}
