package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// how bright light is squeezed into what a screen shows
public final class TonemapEffect extends ScreenEffect {

    public Tonemapper tonemapper = Tonemapper.ACES;
    @Prop(min = 0) public double exposure = 1;

    public TonemapEffect() {
        order = 30;
    }
}
