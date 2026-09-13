package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// the picture smeared along how the camera moved since the last frame
public final class MotionBlurEffect extends ScreenEffect {

    // how much of a frame's movement is smeared
    @Prop(min = 0) public double strength = 0.5;
    @Prop(min = 2, max = 32) public int samples = 12;

    public MotionBlurEffect() {
        order = 20;
    }
}
