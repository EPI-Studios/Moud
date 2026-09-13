package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class MotionBlurEffect extends ScreenEffect {

    @Prop(min = 0) public double strength = 0.5;
    @Prop(min = 2, max = 32) public int samples = 12;

    public MotionBlurEffect() {
        order = 20;
    }
}
