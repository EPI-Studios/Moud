package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

// fewer colours, in flat bands
public final class PosterizeEffect extends ScreenEffect {

    // shades per channel
    @Prop(min = 2) public double levels = 6;

    public PosterizeEffect() {
        order = 75;
    }
}
