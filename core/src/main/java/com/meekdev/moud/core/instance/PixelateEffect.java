package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.clazz.Prop;

public final class PixelateEffect extends ScreenEffect {

    @Prop(min = 1) public double pixelSize = 4;

    public PixelateEffect() {
        order = 70;
    }
}
