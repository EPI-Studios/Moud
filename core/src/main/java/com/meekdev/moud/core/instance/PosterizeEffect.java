package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class PosterizeEffect extends ScreenEffect {

    @Prop(min = 2) public double levels = 6;

    public PosterizeEffect() {
        order = 75;
    }
}
