package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class DepthOfFieldEffect extends ScreenEffect {

    @Prop(min = 0) public double focusDistance = 8;

    @Prop(min = 0) public double focusRange = 4;

    @Prop(min = 0.01) public double falloff = 12;

    @Prop(min = 0) public double size = 10;

    public DepthOfFieldEffect() {
        order = 10;
    }
}
